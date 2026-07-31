# 应收信用证 · 交单登记 / 到账登记 · 逆向撤回 SQL 与字段计算说明

> 配套文档：《应收信用证-交单登记-到账登记-数据流转与逆向撤回分析.md》
>
> 本文档专注于 **撤回操作的 SQL 清单** 与 **每个字段新值的来源/计算逻辑**，便于 DBA 直接执行或开发封装为"撤回"接口。
>
> 生成时间：2026-07-30
> 适用版本：`zfs-fms-settlement-core` 4.2.0

---

## 目录

- [一、撤回前置约定](#一撤回前置约定)
- [二、变量与占位符](#二变量与占位符)
- [三、撤回 SQL 总览（执行顺序）](#三撤回-sql-总览执行顺序)
- [四、分步 SQL 与字段新值计算逻辑](#四分步-sql-与字段新值计算逻辑)
  - [步骤 A：撤销第 2 步「到账登记」](#步骤-a撤销第-2-步到账登记)
  - [步骤 B：撤销第 1 步「交单登记」](#步骤-b撤销第-1-步交单登记)
- [五、字段新值计算逻辑速查表](#五字段新值计算逻辑速查表)
- [六、事务与执行注意事项](#六事务与执行注意事项)

---

## 一、撤回前置约定

1. **执行顺序必须为：先撤第 2 步「到账登记」→ 再撤第 1 步「交单登记」**。原因：
   - 撤第 1 步会逻辑删除交单主表（`enabled_flag='1'`），若先做这步，第 2 步撤回时无法通过 `register_id` 关联到子表与流水；
   - 主信用证状态在两步撤回中需要**两次**回写（A6 把 `3` 回退到 `6`；B3 把 `6` 二次回退到原始状态）。

2. **全过程必须包裹在一个事务中**，任一步失败整体回滚，避免状态不一致。

3. **本次示例的实体 ID**（来自原始请求 payload）：

   | 名称 | 值 | 来源 |
   |---|---|---|
   | 交单登记 ID（`register_id`） | `fa40522b-7a5e-4474-b2ca-3ed852e0caa2` | 第 2 步 payload 的 `id` 字段 |
   | 应收信用证 ID（`rcl_id`） | `55aa5f53-0c35-4a90-a272-cd8eef2ece02` | 第 1 步 payload 的 `rclId` 字段 |

4. **`original_letter_status`（原始信用证状态）**：第 1 步「交单登记」执行前，主信用证的状态。源码 `save` 方法**没有记录原状态**，因此需要调用方传入。常见取值：
   - `4` 新开（最常见）
   - `2` 签收
   - `5` 入库

5. **当前操作人**：执行撤回的用户 ID（用于审计字段 `last_update_by`）。下文记为 `:operator`。

---

## 二、变量与占位符

为保证 SQL 通用性，统一使用以下占位符（DBA 执行时按实际值替换；Java 代码使用预编译参数绑定）：

| 占位符 | 含义 | 示例值 |
|---|---|---|
| `:register_id` | 交单登记 ID | `fa40522b-7a5e-4474-b2ca-3ed852e0caa2` |
| `:rcl_id` | 应收信用证 ID | `55aa5f53-0c35-4a90-a272-cd8eef2ece02` |
| `:original_letter_status` | 信用证原始状态码 | `4`（新开） |
| `:operator` | 撤回操作人 ID | `45eeab491e3e7198cdce3132372e076b` |
| `:now` | 当前时间戳 | `SYSDATE`（Oracle/达梦）/ `NOW()`（MySQL）/ `CURRENT_TIMESTAMP`（PG） |
| `:child_ids` | 到账子表 ID 列表（动态查询结果） | 由 A0 查询得出 |

---

## 三、撤回 SQL 总览（执行顺序）

```
┌─────────────────────────── 步骤 A：撤销到账登记 ───────────────────────────┐
│  A0  查询到账子表 ID 列表（不入库，仅为后续步骤准备 :child_ids）         │
│  A1  逻辑删除到账子表（FMS_RCL_PRESENT_REGISTER_CHILD）                  │
│  A2  释放流水关联（FMS_REGISTER_TRANS_ASSOCIA）                          │
│  A3  释放流水标签（FMS_TRANSACTION_DETAILS_LABLE）                       │
│  A4  回写交单主表（FMS_RCL_PRESENT_REGISTER）：状态 3→6，清空到账字段    │
│  A5  回退主信用证状态（FMS_RECEIVE_CREDIT_LETTER）：3→6                  │
└──────────────────────────────────────────────────────────────────────────┘
┌─────────────────────────── 步骤 B：撤销交单登记 ───────────────────────────┐
│  B0  查询该证下剩余有效交单数量（用于判断 B3 是否执行）                  │
│  B1  逻辑删除交单附件（FMS_ATTACHMENT，busType=RCL_PRESENT）             │
│  B2  逻辑删除交单主表（FMS_RCL_PRESENT_REGISTER）                        │
│  B3  二次回退主信用证状态（FMS_RECEIVE_CREDIT_LETTER）：6→原始状态      │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 四、分步 SQL 与字段新值计算逻辑

### 步骤 A：撤销第 2 步「到账登记」

#### A0. 查询到账子表 ID 列表（准备工作）

```sql
-- 用途：查出本次需要释放流水/标签所依赖的 child_id 列表
SELECT id
  FROM FMS_RCL_PRESENT_REGISTER_CHILD
 WHERE register_id = :register_id
   AND data_type = '1'              -- 1=应收
   AND enabled_flag = '0'           -- 仅查有效数据
   AND validity_flag = '0';
```

> **说明**：把查询结果收集为 `:child_ids` 列表，传给 A2 / A3 的 `IN` 子句。若结果为空（即原本没填到账明细，如本次 `registerList:[]` 场景），则 A2/A3 跳过即可。

---

#### A1. 逻辑删除到账子表

```sql
UPDATE FMS_RCL_PRESENT_REGISTER_CHILD
   SET enabled_flag   = '1',        -- 1=已删除（不可见）
       validity_flag  = '1',        -- 1=已失效
       last_update_by = :operator,
       last_update_date = :now
 WHERE register_id = :register_id
   AND data_type   = '1'
   AND enabled_flag = '0';
```

| 字段 | 新值 | 数据来源 / 计算逻辑 |
|---|---|---|
| `enabled_flag` | `'1'` | 固定常量，符合 JPA 逻辑删除约定（`EnumType.EnabledFlag.NO`） |
| `validity_flag` | `'1'` | 固定常量，与 `FmsRclPresentRegisterChildDao.remove` 的实现保持一致 |
| `last_update_by` | `:operator` | 当前登录用户 ID，来自 `UserUtils.getUserId()` |
| `last_update_date` | `:now` | 当前时间戳，对应 `DateUtil.date()` |

> **计算逻辑**：直接复用 `FmsRclPresentRegisterChildService.logicDeleteByRegister(registerId)`，等价 SQL 即上面这条 UPDATE。

---

#### A2. 释放流水关联

```sql
UPDATE FMS_REGISTER_TRANS_ASSOCIA
   SET enabled_flag   = '1',
       validity_flag  = '1',
       last_update_by = :operator,
       last_update_date = :now
 WHERE child_id IN (:child_ids);
```

| 字段 | 新值 | 数据来源 / 计算逻辑 |
|---|---|---|
| `enabled_flag` | `'1'` | 固定，逻辑删除 |
| `validity_flag` | `'1'` | 固定，逻辑删除 |
| `last_update_by` | `:operator` | 当前用户 ID |
| `last_update_date` | `:now` | 当前时间 |

> **计算逻辑**：对应 `FmsRegisterTransAssociaService.releaseRelationFlowByChildIdList(childIdList)`。它的作用是把"被本次到账登记占用的资金流水"释放，让流水能被其他业务重新关联。
>
> **为何以 `child_id` 为条件**：流水关联表通过 `child_id` 指向到账明细，到账明细被删了，对应的关联也必须释放，否则流水被永久占用。

---

#### A3. 释放流水标签

```sql
UPDATE FMS_TRANSACTION_DETAILS_LABLE
   SET enabled_flag   = '1',
       validity_flag  = '1',
       last_update_by = :operator,
       last_update_date = :now
 WHERE bus_details_id IN (:child_ids);
```

| 字段 | 新值 | 数据来源 / 计算逻辑 |
|---|---|---|
| `enabled_flag` | `'1'` | 固定，逻辑删除 |
| `validity_flag` | `'1'` | 固定，逻辑删除 |
| `last_update_by` | `:operator` | 当前用户 ID |
| `last_update_date` | `:now` | 当前时间 |

> **计算逻辑**：对应 `FmsTransactionDetailsLableService.releaseFlowLableListByBusDetailsId(childIdList)`。流水标签表通过 `bus_details_id` 指向到账明细（业务明细），到账明细删除时必须同步释放标签，否则流水会被认为"已被其他业务占用"，后续到账登记时报错 `fms.transAssociaNo.already.associated`。

---

#### A4. 回写交单主表

```sql
UPDATE FMS_RCL_PRESENT_REGISTER
   SET status          = '6',       -- 出库
       be_over         = NULL,      -- 完结标志置空
       receive_account = NULL,
       receive_mode    = NULL,
       receive_type    = NULL,
       receive_date    = NULL,
       receive_amount  = NULL,
       account_fee     = NULL,
       operator        = NULL,
       operator_date   = NULL,
       account_remark  = NULL,
       last_update_by  = :operator,
       last_update_date = :now
 WHERE id = :register_id;
```

| 字段 | 新值 | 数据来源 / 计算逻辑 |
|---|---|---|
| `status` | `'6'`（出库） | **逆向计算**：第 2 步到账登记把 `status` 从 `6` 推进到 `3`（因 `beOver='0'` 命中 `setLetterStatus`），撤回即把 `3` 还原为 `6`。常量来自 `FmsConstants.letterStatus.LETTER_6.getCode()` |
| `be_over` | `NULL` | **逆向计算**：第 2 步把 `be_over` 从 NULL 改为 `'0'`（已完结），撤回还原为 NULL。注意不要写 `''`，必须 `NULL` |
| `receive_account` | `NULL` | 第 2 步 `setData` 写入的"收款账号"，撤回置空 |
| `receive_mode` | `NULL` | 第 2 步 `setData` 写入的"收款方式"，撤回置空 |
| `receive_type` | `NULL` | 第 2 步 `setData` 写入的"收款类型"，撤回置空 |
| `receive_date` | `NULL` | 第 2 步 `setData` 写入的"到账日期"，撤回置空 |
| `receive_amount` | `NULL` | 第 2 步 `setData` 写入的"到账金额"，撤回置空 |
| `account_fee` | `NULL` | 第 2 步 `setData` 写入的"到账手续费"，撤回置空 |
| `operator` | `NULL` | 第 2 步 `setData` 写入的"经办人"，撤回置空 |
| `operator_date` | `NULL` | 第 2 步 `setData` 写入的"经办日期"，撤回置空 |
| `account_remark` | `NULL` | 第 2 步 `setData` 写入的"到账备注"，撤回置空 |
| `last_update_by` | `:operator` | 当前用户 ID |
| `last_update_date` | `:now` | 当前时间 |

> **核心逆向思路**：第 2 步 `addAccount` 调用了 `setData()`（930–946 行）写入 9 个到账字段，加上 `setStatus(3)` 和 `setBeOver('0')`。撤回时把这 11 个字段**全部置 NULL 或回退到第 1 步结束时的值**，即让交单主表回到"刚做完第 1 步"的状态。

---

#### A5. 回退主信用证状态（第一次）

```sql
UPDATE FMS_RECEIVE_CREDIT_LETTER
   SET status          = '6',       -- 出库
       last_update_by  = :operator,
       last_update_date = :now
 WHERE id = :rcl_id
   AND status = '3';                -- 防御性条件：仅在当前是"到账"时才回退
```

| 字段 | 新值 | 数据来源 / 计算逻辑 |
|---|---|---|
| `status` | `'6'`（出库） | **逆向计算**：第 2 步 `setLetterStatus` 把主表状态从 `6` 推进到 `3`，撤回即还原为 `6` |
| `last_update_by` | `:operator` | 当前用户 ID |
| `last_update_date` | `:now` | 当前时间 |

> **为何加 `AND status='3'`**：防御性编程。若主信用证当前不是 `3`（例如已被其他业务推进），就不应该被本次撤回误改。
>
> **风险提示**：如果该信用证下**还有别的交单**已到账，主表状态可能本应保持 `3`，此时不应回退。Java 代码层通过 `findByParentId` 判断剩余交单状态后再决定是否回退（详见配套文档 5.2 节）。SQL 执行时建议先查一下：

```sql
-- 预检查：该证下是否还有其他交单处于"到账(3)"状态
SELECT COUNT(1)
  FROM FMS_RCL_PRESENT_REGISTER
 WHERE rcl_id = :rcl_id
   AND id <> :register_id          -- 排除本次撤回的交单
   AND status = '3'                -- 其他到账交单
   AND enabled_flag = '0';
-- 若结果 > 0，则 A5 应跳过（不回退主信用证状态）
```

---

### 步骤 B：撤销第 1 步「交单登记」

#### B0. 预检查剩余交单数量

```sql
SELECT COUNT(1)
  FROM FMS_RCL_PRESENT_REGISTER
 WHERE rcl_id = :rcl_id
   AND enabled_flag = '0';
-- 若结果为 0（B2 执行后必然为 0），则 B3 应该执行；否则 B3 跳过
```

---

#### B1. 逻辑删除交单附件

```sql
UPDATE FMS_ATTACHMENT
   SET enabled_flag   = '1',
       validity_flag  = '1',
       last_update_by = :operator,
       last_update_date = :now
 WHERE bus_id   = :register_id
   AND bus_type = 'RCL_PRESENT';
```

| 字段 | 新值 | 数据来源 / 计算逻辑 |
|---|---|---|
| `enabled_flag` | `'1'` | 逻辑删除 |
| `validity_flag` | `'1'` | 逻辑删除 |
| `last_update_by` | `:operator` | 当前用户 ID |
| `last_update_date` | `:now` | 当前时间 |

> **为何以 `bus_id + bus_type` 为条件**：附件表是公共表，被多个业务共用。交单附件固定使用 `bus_type='RCL_PRESENT'`（见 `FmsConstants.busType.RCL_PRESENT`），加上 `bus_id=交单ID` 精确定位。
>
> **本次场景影响 0 行**：原始 payload 中 `attachmentList:[]`，第 1 步实际没生成附件记录。但 SQL 仍需保留，覆盖一般情况。

---

#### B2. 逻辑删除交单主表

```sql
UPDATE FMS_RCL_PRESENT_REGISTER
   SET enabled_flag   = '1',
       validity_flag  = '1',
       last_update_by = :operator,
       last_update_date = :now
 WHERE id = :register_id;
```

| 字段 | 新值 | 数据来源 / 计算逻辑 |
|---|---|---|
| `enabled_flag` | `'1'` | 逻辑删除（不可见） |
| `validity_flag` | `'1'` | 逻辑删除（失效） |
| `last_update_by` | `:operator` | 当前用户 ID |
| `last_update_date` | `:now` | 当前时间 |

> **计算逻辑**：直接对应 `FmsRclPresentRegisterService.remove(new String[]{registerId})`，等价 SQL 即上面这条 UPDATE。
>
> **注意**：保留所有业务字段（如 `doc_no`、`present_amount` 等）不清理，仅改 `enabled_flag/validity_flag`，便于审计追溯。

---

#### B3. 二次回退主信用证状态

```sql
UPDATE FMS_RECEIVE_CREDIT_LETTER
   SET status          = :original_letter_status,   -- 如 '4' 新开
       last_update_by  = :operator,
       last_update_date = :now
 WHERE id = :rcl_id
   AND status = '6';                                -- 防御：仅在当前是"出库"时回退
```

| 字段 | 新值 | 数据来源 / 计算逻辑 |
|---|---|---|
| `status` | `:original_letter_status`（如 `'4'`） | **业务推断**：第 1 步「交单登记」执行前主信用证的原始状态。源码未记录原状态，需调用方传入。常见为 `'4'`（新开）。仅在 B0 查询结果为 0（该证下已无其他有效交单）时才执行 |
| `last_update_by` | `:operator` | 当前用户 ID |
| `last_update_date` | `:now` | 当前时间 |

> **执行条件**：B0 预检查 `COUNT = 0`（即本次撤回后该证下无其他有效交单）才执行 B3。若该证下还有其他交单，主信用证状态保持 `6`（出库）不变。

---

## 五、字段新值计算逻辑速查表

下表汇总每个被更新字段的"新值"以及"新值是怎么算出来的"。

### 5.1 公共审计字段（所有 UPDATE 共用）

| 字段 | 新值 | 计算逻辑 |
|---|---|---|
| `last_update_by` | `:operator` | 取当前登录用户 ID，对应 Java `UserUtils.getUserId()`。若为定时任务/系统撤回，使用 `"-1"` |
| `last_update_date` | `:now` | 取当前时间戳，对应 Java `DateUtil.date()` / `new Date()`。不同数据库函数不同：Oracle/达梦用 `SYSDATE`，MySQL 用 `NOW()`，PostgreSQL 用 `CURRENT_TIMESTAMP` |

### 5.2 逻辑删除统一字段（A1/A2/A3/B1/B2 共用）

| 字段 | 新值 | 计算逻辑 |
|---|---|---|
| `enabled_flag` | `'1'` | 固定常量。约定：`'0'`=有效，`'1'`=已删除（不可见）。对应 `EnumType.EnabledFlag.NO` |
| `validity_flag` | `'1'` | 固定常量。约定：`'0'`=有效，`'1'`=已失效。与 `enabled_flag` 同步置 1，遵循项目 `remove` 方法的统一约定 |

### 5.3 业务字段（A4 / A5 / B3）

| 表 | 字段 | 新值 | 计算逻辑 |
|---|---|---|---|
| `FMS_RCL_PRESENT_REGISTER` | `status` | `'6'`（出库） | **状态机逆向**：第 2 步把 status 从 `6`→`3`（因 beOver=0），撤回还原为 `6`。常量来自 `FmsConstants.letterStatus.LETTER_6.getCode()` |
| `FMS_RCL_PRESENT_REGISTER` | `be_over` | `NULL` | **逆向清空**：第 2 步把 beOver 从 NULL→`'0'`，撤回还原为 NULL。注意必须用 `NULL` 而非空字符串 |
| `FMS_RCL_PRESENT_REGISTER` | `receive_account` | `NULL` | **逆向清空**：第 2 步 `setData()` 写入，撤回置 NULL |
| `FMS_RCL_PRESENT_REGISTER` | `receive_mode` | `NULL` | 同上 |
| `FMS_RCL_PRESENT_REGISTER` | `receive_type` | `NULL` | 同上 |
| `FMS_RCL_PRESENT_REGISTER` | `receive_date` | `NULL` | 同上 |
| `FMS_RCL_PRESENT_REGISTER` | `receive_amount` | `NULL` | 同上 |
| `FMS_RCL_PRESENT_REGISTER` | `account_fee` | `NULL` | 同上 |
| `FMS_RCL_PRESENT_REGISTER` | `operator` | `NULL` | 同上 |
| `FMS_RCL_PRESENT_REGISTER` | `operator_date` | `NULL` | 同上 |
| `FMS_RCL_PRESENT_REGISTER` | `account_remark` | `NULL` | 同上 |
| `FMS_RECEIVE_CREDIT_LETTER` (A5) | `status` | `'6'`（出库） | **状态机逆向**：第 2 步 `setLetterStatus` 把主表从 `6`→`3`，撤回还原为 `6`。仅在该证下无其他到账交单时才执行 |
| `FMS_RECEIVE_CREDIT_LETTER` (B3) | `status` | `:original_letter_status`（如 `'4'`） | **业务推断**：撤回第 1 步后，若该证下已无任何有效交单，把主信用证状态回退到第 1 步执行前的原始状态。原始状态源码未记录，由调用方传入。仅在 B0 预检查 `COUNT=0` 时执行 |

### 5.4 字段计算逻辑分类总结

字段新值的来源可以归纳为 4 类：

1. **固定常量**：如 `enabled_flag='1'`、`status='6'`，直接来自枚举常量 `FmsConstants.letterStatus` / `EnumType.EnabledFlag`。
2. **当前上下文**：如 `last_update_by`、`last_update_date`，取当前用户与当前时间。
3. **状态机逆向运算**：业务状态的还原，遵循正向推进路径的反向回退（`3`→`6`→原始状态）。
4. **逆向清空（NULL）**：第 2 步 `setData()` 写入的字段，撤回时全部置 NULL，让交单主表回到"刚做完第 1 步"的字段快照。
5. **业务推断**：如 B3 的 `original_letter_status`，需要结合业务记录或调用方传入（源码未保存原状态）。

---

## 六、事务与执行注意事项

### 6.1 必须在事务中执行

整套撤回 SQL 必须在**同一个事务**中执行，任一步失败整体回滚。Java 实现方法上加：

```java
@Transactional(rollbackFor = Exception.class)
```

DBA 手工执行时，建议用：

```sql
-- Oracle/达梦
SET TRANSACTION READ WRITE;
-- ... 所有 SQL ...
COMMIT;   -- 全部成功才提交
-- ROLLBACK;  -- 出错时回滚

-- MySQL
START TRANSACTION;
-- ... 所有 SQL ...
COMMIT;
```

### 6.2 执行顺序的强约束

```
A0 → A1 → A2 → A3 → A4 → A5 → B0 → B1 → B2 → B3
```

- A0 必须最先执行，否则 A2/A3 没有 `:child_ids`；
- A1 必须在 A2/A3 **之后**？实际无强制要求，但建议先释放流水关联/标签再删子表，逻辑更清晰；
- A5 之前必须先执行预检查（判断是否还有其他到账交单）；
- B3 之前必须先执行 B0 预检查（判断是否还有其他有效交单）。

### 6.3 并发控制

撤回与正常到账登记使用同一把锁（key = `RECEIVE_REGISTER_` + registerId）。Java 实现建议复用：

```java
String mutexKey = FmsConstants.REDIS_RECEIVE_REGISTER_KEY + registerId;
RLock lock = redis.getDistributeLock(mutexKey);
boolean locked = lock.tryLock();
// ... 撤回逻辑 ...
lock.unlock();
```

DBA 手工执行时，建议提前通知运营暂停对该交单的所有操作。

### 6.4 数据库兼容性

| 数据库 | 当前时间函数 |
|---|---|
| Oracle / 达梦 | `SYSDATE` |
| MySQL | `NOW()` |
| PostgreSQL | `CURRENT_TIMESTAMP` |
| SQL Server | `GETDATE()` |

`UPDATE ... SET col = NULL` 在所有数据库下语义一致，无需特殊处理。

### 6.5 审计建议

撤回是高风险反向操作，建议：

1. 执行前对涉及的 6 张表做快照（`SELECT * FROM ... WHERE ...` 保存到审计表）；
2. 加 `@SysLog(value="撤回", desc="应收信用证交单/到账登记撤回")`；
3. 方法入口打 INFO 日志，异常打 WARN 日志（符合《阿里巴巴Java手册》日志规范）。

### 6.6 验证 SQL（撤回完成后自检）

```sql
-- 1. 交单主表：应已逻辑删除，enabled_flag=1
SELECT id, status, be_over, enabled_flag, validity_flag
  FROM FMS_RCL_PRESENT_REGISTER
 WHERE id = :register_id;
-- 预期：enabled_flag='1', validity_flag='1'

-- 2. 到账子表：应全部逻辑删除
SELECT COUNT(1)
  FROM FMS_RCL_PRESENT_REGISTER_CHILD
 WHERE register_id = :register_id
   AND data_type = '1'
   AND enabled_flag = '0';
-- 预期：0

-- 3. 流水关联：应全部释放
SELECT COUNT(1)
  FROM FMS_REGISTER_TRANS_ASSOCIA
 WHERE child_id IN (:child_ids)
   AND enabled_flag = '0';
-- 预期：0

-- 4. 流水标签：应全部释放
SELECT COUNT(1)
  FROM FMS_TRANSACTION_DETAILS_LABLE
 WHERE bus_details_id IN (:child_ids)
   AND enabled_flag = '0';
-- 预期：0

-- 5. 交单附件：应逻辑删除
SELECT COUNT(1)
  FROM FMS_ATTACHMENT
 WHERE bus_id = :register_id
   AND bus_type = 'RCL_PRESENT'
   AND enabled_flag = '0';
-- 预期：0

-- 6. 主信用证状态：应为 '6' 或 :original_letter_status
SELECT id, status
  FROM FMS_RECEIVE_CREDIT_LETTER
 WHERE id = :rcl_id;
-- 预期：status='6'（A5 后）或 status=:original_letter_status（B3 后）
```

---

## 附录：完整 SQL 脚本（可直接复制执行）

> ⚠️ 执行前请替换所有 `:xxx` 占位符为实际值。建议先在测试环境验证。

```sql
-- ================================================================
-- 应收信用证误操作撤回脚本（撤销交单登记 + 到账登记）
-- 执行顺序：A0 → A1 → A2 → A3 → A4 → A5 → B0 → B1 → B2 → B3
-- ================================================================

-- 变量声明（MySQL 写法，Oracle/达梦请改为 :var 绑定变量）
SET @register_id            = 'fa40522b-7a5e-4474-b2ca-3ed852e0caa2';
SET @rcl_id                 = '55aa5f53-0c35-4a90-a272-cd8eef2ece02';
SET @original_letter_status = '4';
SET @operator               = '45eeab491e3e7198cdce3132372e076b';

START TRANSACTION;

-- ============ 步骤 A：撤销到账登记 ============

-- A0. 查询到账子表 ID（手工执行时把结果填到下方 IN 列表）
SELECT id FROM FMS_RCL_PRESENT_REGISTER_CHILD
 WHERE register_id = @register_id AND data_type = '1'
   AND enabled_flag = '0' AND validity_flag = '0';

-- A1. 逻辑删除到账子表
UPDATE FMS_RCL_PRESENT_REGISTER_CHILD
   SET enabled_flag = '1', validity_flag = '1',
       last_update_by = @operator, last_update_date = NOW()
 WHERE register_id = @register_id AND data_type = '1' AND enabled_flag = '0';

-- A2. 释放流水关联（把 A0 查到的 id 列表填入 IN）
UPDATE FMS_REGISTER_TRANS_ASSOCIA
   SET enabled_flag = '1', validity_flag = '1',
       last_update_by = @operator, last_update_date = NOW()
 WHERE child_id IN ('<填入A0查询结果>', '<...>');

-- A3. 释放流水标签
UPDATE FMS_TRANSACTION_DETAILS_LABLE
   SET enabled_flag = '1', validity_flag = '1',
       last_update_by = @operator, last_update_date = NOW()
 WHERE bus_details_id IN ('<填入A0查询结果>', '<...>');

-- A4. 回写交单主表（状态 3→6，清空到账字段，be_over 置 NULL）
UPDATE FMS_RCL_PRESENT_REGISTER
   SET status = '6',
       be_over = NULL,
       receive_account = NULL, receive_mode = NULL, receive_type = NULL,
       receive_date = NULL, receive_amount = NULL, account_fee = NULL,
       operator = NULL, operator_date = NULL, account_remark = NULL,
       last_update_by = @operator, last_update_date = NOW()
 WHERE id = @register_id;

-- A5. 回退主信用证状态（3→6）
UPDATE FMS_RECEIVE_CREDIT_LETTER
   SET status = '6',
       last_update_by = @operator, last_update_date = NOW()
 WHERE id = @rcl_id AND status = '3';

-- ============ 步骤 B：撤销交单登记 ============

-- B0. 预检查剩余有效交单
SELECT COUNT(1) FROM FMS_RCL_PRESENT_REGISTER
 WHERE rcl_id = @rcl_id AND enabled_flag = '0';
-- 若结果为 0，则 B3 才执行

-- B1. 逻辑删除交单附件
UPDATE FMS_ATTACHMENT
   SET enabled_flag = '1', validity_flag = '1',
       last_update_by = @operator, last_update_date = NOW()
 WHERE bus_id = @register_id AND bus_type = 'RCL_PRESENT';

-- B2. 逻辑删除交单主表
UPDATE FMS_RCL_PRESENT_REGISTER
   SET enabled_flag = '1', validity_flag = '1',
       last_update_by = @operator, last_update_date = NOW()
 WHERE id = @register_id;

-- B3. 二次回退主信用证状态（仅当 B0 结果为 0 时执行）
UPDATE FMS_RECEIVE_CREDIT_LETTER
   SET status = @original_letter_status,
       last_update_by = @operator, last_update_date = NOW()
 WHERE id = @rcl_id AND status = '6';

COMMIT;
-- 若中途出错：ROLLBACK;
```

---

> **文档结束**
>
> 配套文档：
> - 《应收信用证-交单登记-到账登记-数据流转与逆向撤回分析.md》（侧重代码逻辑与字段流转分析）
> - 本文档（侧重撤回 SQL 与字段计算逻辑）
