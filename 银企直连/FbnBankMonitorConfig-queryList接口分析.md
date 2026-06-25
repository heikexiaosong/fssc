# FbnBankMonitorConfig#queryList 接口分析

> 模块：银企监控 — 银行监控配置  
> 代码路径：`cn.ztessc.controller.monitor.FbnBankMonitorConfigController#queryList`  
> 分析日期：2026-06-25

---

## 1. 接口概览

| 项目 | 说明 |
|------|------|
| 请求方式 | `POST` |
| 完整路径 | `/sys/fbn/fbnBankMonitorConfig/queryList` |
| Content-Type | `application/x-www-form-urlencoded`（`@RequestParam`） |
| 鉴权 | Cookie `ZTESSCAUTH` + Header `authkey` |
| 用途 | 银企监控首页/大屏列表查询，含扩展统计与汇总 |

### 1.1 调用示例

```bash
curl 'http://172.16.155.34/sys/fbn/fbnBankMonitorConfig/queryList' \
  -X 'POST' \
  -H 'Accept: application/json, text/plain, */*' \
  -H 'Accept-Language: zh-CN' \
  -H 'Content-Length: 0' \
  -b 'ZTESSCAUTH=...; user_language=zh-CN' \
  -H 'authkey: ...' \
  -H 'fromMenuCode: e776db98-11a8-4ad3-89a8-9865e289e574' \
  -H 'pageUri: /fbn/' \
  --insecure
```

> 上述请求未传任何 query/form 参数，将查询当前集团下所有有效（未逻辑删除）的银行监控配置。

### 1.2 响应示例

```json
{
    "msg": "success",
    "code": 0,
    "data": [
        {
            "id": "5d74f17b045d74f41e233137322e0000",
            "bankHeadCode": "102",
            "bankHeadName": "中国工商银行",
            "monitorStatus": "0",
            "monitorStatusName": "是",
            "connectStatus": "1",
            "connectStatusName": "否",
            "filePath": "2026/06/11/4555A618044DFEBBE8303137322E0009.png",
            "connectTime": "2026-06-11 09:17:51",
            "validityFlag": "0",
            "enabledFlag": "0",
            "createBy": "32094dc84707703c92163137322e26e2",
            "createDate": "2026-06-11 09:17:51",
            "lastUpdateBy": "32094dc84707703c92163137322e26e2",
            "lastUpdateDate": "2026-06-11 09:17:51",
            "groupId": "decf31c11401b609562b3137322e000b",
            "archiveFlag": null,
            "archiveDate": null,
            "countNum": 0,
            "accountCount": 7,
            "directRate": 57.14
        }
    ],
    "bankMonitorSumDTO": {
        "connectCount": 1,
        "connectSuccessCount": 0,
        "connectFailCount": 1
    },
    "type": "INFO"
}
```

---

## 2. 代码入口

**Controller**

- 文件：`zfs-fbn-core/src/main/java/cn/ztessc/controller/monitor/FbnBankMonitorConfigController.java`
- 类映射：`@RequestMapping("fbnBankMonitorConfig")`
- 方法：`@PostMapping("queryList")`

```java
@PostMapping("queryList")
@ApiOperation(value = "列表", response = FbnBankMonitorConfigDTO.class)
public Result queryList(@RequestParam Map<String, Object> params) {
    Result res = fbnBankMonitorConfigService.queryList(new Query(params));
    return res;
}
```

**Service**

- 文件：`zfs-fbn-core/src/main/java/cn/ztessc/service/monitor/FbnBankMonitorConfigService.java`
- 方法：`queryList(Map<String, Object> params)`

---

## 3. 处理流程

```
POST queryList
    │
    ▼
构建 Criteria 查询条件（splicingCondition）
    │
    ▼
查询表 FBN_BANK_MONITOR_CONFIG（findAll）
    │
    ├─ 无数据 → Result.ok()（空）
    │
    └─ 有数据
         │
         ├─ Entity → DTO（baseDataTranslateUtils，含字典翻译）
         ├─ dealBankHeadName()      → 调 base 服务补全银行名称
         ├─ queryCountNum()         → 查当日连接次数
         ├─ queryAccountInfo()      → 调 FMS 查账户数/直连率
         ├─ 多字段排序
         ├─ queryFbnBankMonitorSumDTO() → 汇总统计
         └─ Result.ok().put("data", dtoList).put("bankMonitorSumDTO", ...)
```

### 3.1 核心 Service 逻辑

```java
public Result queryList(Map<String, Object> params) {
    // 前端不传参数时查全表，大行数据量不大
    Criteria<FbnBankMonitorConfigEntity> criteria = new Criteria<>();
    splicingCondition(criteria, params);
    List<FbnBankMonitorConfigEntity> list = this.findAll(criteria);
    if (CollUtil.isEmpty(list)) {
        return Result.ok();
    }
    List<FbnBankMonitorConfigDTO> dtoList =
        baseDataTranslateUtils.baseDataTranslate(list, FbnBankMonitorConfigDTO.class);
    dealBankHeadName(dtoList);
    queryCountNum(dtoList);
    queryAccountInfo(dtoList);
    dtoList = dtoList.stream().sorted(
        Comparator.comparing(FbnBankMonitorConfigDTO::getMonitorStatus)
            .thenComparing(FbnBankMonitorConfigDTO::getConnectStatus)
            .thenComparing(FbnBankMonitorConfigDTO::getAccountCount, Comparator.reverseOrder())
            .thenComparing((o1, o2) ->
                Collator.getInstance(Locale.CHINA).compare(o1.getBankHeadName(), o2.getBankHeadName()))
    ).collect(Collectors.toList());
    FbnBankMonitorSumDTO bankMonitorSumDTO = queryFbnBankMonitorSumDTO(dtoList);
    return Result.ok().put("data", dtoList).put("bankMonitorSumDTO", bankMonitorSumDTO);
}
```

---

## 4. 查询条件

方法：`FbnBankMonitorConfigService#splicingCondition`

| 条件 | 来源 | 说明 |
|------|------|------|
| `enabledFlag = YES` | 固定 | 只查未逻辑删除的数据 |
| `id` | 可选请求参数 | 按主键过滤 |
| `validityFlag` | 可选请求参数 | 按有效/无效过滤 |
| 其他 | `ConditionUtils.splicingCondition` | 通用动态条件（如 groupId 等） |

**无参请求行为：** 返回当前条件下所有有效银行监控配置记录。

---

## 5. 数据表与实体

| 项目 | 值 |
|------|-----|
| 表名 | `FBN_BANK_MONITOR_CONFIG` |
| Entity | `FbnBankMonitorConfigEntity` |
| DTO | `FbnBankMonitorConfigDTO` |
| 汇总 DTO | `FbnBankMonitorSumDTO` |

### 5.1 主要字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `bankHeadCode` | String | 银行总行编码 |
| `bankHeadName` | String | 银行名称（非表字段，远程查询填充） |
| `monitorStatus` | String | 是否监控，字典 `MONITOR_STATUS` |
| `monitorStatusName` | String | 监控状态名称（字典翻译） |
| `connectStatus` | String | 连接状态，字典 `CONNECT_STATUS` |
| `connectStatusName` | String | 连接状态名称（字典翻译） |
| `filePath` | String | 监控截图/凭证路径 |
| `connectTime` | Date | 最近连接状态变更时间 |
| `countNum` | Integer | 当日连接次数（非表字段，统计填充） |
| `accountCount` | Integer | 银行账号数量（非表字段，FMS 填充） |
| `directRate` | BigDecimal | 直连率 %（非表字段，FMS 填充） |
| `validityFlag` | String | `0` 有效，`1` 无效 |
| `enabledFlag` | String | `0` 未删除，`1` 已删除 |
| `groupId` | String | 集团 ID |

### 5.2 状态码约定

本项目常用标志（`Constant.DEFAULT_FLAG_YES = "0"`，`DEFAULT_FLAG_NO = "1"`）：

| 字段 | 值 | 字典名称（示例） | 业务含义 |
|------|-----|-----------------|----------|
| `monitorStatus` | `"0"` | 是 | 已开通监控 |
| `monitorStatus` | `"1"` | 否 | 未开通监控 |
| `connectStatus` | `"0"` | 是 | 已连接 |
| `connectStatus` | `"1"` | 否 | 已断开 |
| `connectStatus` | `"3"` | — | 未开通（空值时 getter 默认） |

字典翻译通过 `@BaseDataTranslate` 注解实现：

```java
@BaseDataTranslate(
    dataType = EnumType.CacheDataType.SYSDICT_BASE,
    method = EnumType.CacheMethod.findByCodeAndParentCode,
    params = "'MONITOR_STATUS',monitorStatus,groupId",
    showCol = "monitorStatusName=name")
private String monitorStatus;

@BaseDataTranslate(
    dataType = EnumType.CacheDataType.SYSDICT_BASE,
    method = EnumType.CacheMethod.findByCodeAndParentCode,
    params = "'CONNECT_STATUS',connectStatus,groupId",
    showCol = "connectStatusName=name")
private String connectStatus;
```

---

## 6. 扩展统计逻辑

### 6.1 银行名称翻译 — `dealBankHeadName`

- **远程调用：** `ZfsFbnDaoBaseClient.findBankList`
- **接口：** `POST bank/findBankList`
- **服务：** `ZFS_BASE`（基础平台）
- **作用：** 根据 `bankHeadCode` 列表批量查询，填充 `bankHeadName`

### 6.2 当日连接次数 — `queryCountNum`

- **数据表：** `FBN_BANK_MONITOR_COUNT`
- **Service：** `FbnBankMonitorCountService#queryList`

查询条件：

| 参数 | 值 |
|------|-----|
| `bankHeadCode_IN` | 当前列表所有总行编码 |
| `createDateNow` | 当天日期 |
| `validityFlag` | `"0"`（有效） |
| `enabledFlag` | 有效（固定） |

- 有记录 → 取 `countNum`
- 无记录 → 默认 `0`
- **含义：** 当天通过 `connectBank` 接口上报的连接次数

### 6.3 账户数与直连率 — `queryAccountInfo`

- **远程调用：** `ZfsFbnDaoFmsClient.queryListByBankHeadCode`
- **接口：** `POST fmsaccount/queryListByBankHeadCode`
- **服务：** `ZFS_FMS`（资金管理系统）

返回结构：

```java
Map<String, Map<String, Integer>> res
// res.get("sum")       → 各总行账户总数
// res.get("monoblock") → 各总行直连账户数
```

计算公式：

```
directRate = monoblockCount × 100 ÷ accountCount（保留 2 位小数，四舍五入）
```

**示例数据验证：**

- `accountCount = 7`，`directRate = 57.14`
- 推算直连账户数 ≈ `4`（`4 / 7 × 100 = 57.14`）

### 6.4 汇总统计 — `queryFbnBankMonitorSumDTO`

仅统计 **已开通监控**（`monitorStatus = "0"`）的银行：

| 字段 | 计算逻辑 |
|------|----------|
| `connectCount` | 已开通监控的银行总数 |
| `connectSuccessCount` | 其中 `connectStatus = "0"`（已连接）的数量 |
| `connectFailCount` | `connectCount - connectSuccessCount` |

**示例验证：**

```json
{
    "connectCount": 1,
    "connectSuccessCount": 0,
    "connectFailCount": 1
}
```

对应：`monitorStatus="0"`（已监控）且 `connectStatus="1"`（断开）→ 1 家监控中、0 家已连、1 家断开。

---

## 7. 排序规则

注释说明：**连接 > 断开 > 未开通；账户数从多到少；银行名称中文拼音升序**

实际 Comparator 链：

| 优先级 | 字段 | 方向 | 说明 |
|--------|------|------|------|
| 1 | `monitorStatus` | 升序 | `"0"`（已监控）在前 |
| 2 | `connectStatus` | 升序 | `"0"` < `"1"` < `"3"` |
| 3 | `accountCount` | 降序 | 账户多的在前 |
| 4 | `bankHeadName` | 升序 | 中文 Collator 拼音排序 |

`connectStatus` 为空时，getter 默认返回 `"3"`（未开通）：

```java
public String getConnectStatus() {
    if (StrUtil.isBlank(connectStatus)) {
        connectStatus = String.valueOf(Constant.THREE);
    }
    return connectStatus;
}
```

---

## 8. 响应结构

```json
{
    "code": 0,
    "msg": "success",
    "type": "INFO",
    "data": [ /* FbnBankMonitorConfigDTO[] */ ],
    "bankMonitorSumDTO": { /* FbnBankMonitorSumDTO */ }
}
```

### 8.1 `bankMonitorSumDTO` 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `connectCount` | int | 已开通监控的银行总数 |
| `connectSuccessCount` | int | 当前已连接的银行数 |
| `connectFailCount` | int | 当前断开的银行数 |

---

## 9. 外部依赖

| Feign Client | 目标服务 | 接口 | 用途 |
|--------------|----------|------|------|
| `ZfsFbnDaoBaseClient` | ZFS_BASE | `POST bank/findBankList` | 银行名称 |
| `ZfsFbnDaoFmsClient` | ZFS_FMS | `POST fmsaccount/queryListByBankHeadCode` | 账户数、直连率 |

---

## 10. 相关接口对比

| 接口 | 路径 | 区别 |
|------|------|------|
| 分页列表 | `POST .../page` | 分页返回，不含 countNum/accountCount/directRate/汇总 |
| 全量列表 | `POST .../queryList` | 全量 + 扩展统计 + 排序 + 汇总（本文档） |
| 连接上报 | `POST .../connectBank` | 更新连接状态、写当日次数、记录连接日志、异步发邮件 |
| 详情 | `GET .../{id}` | 单条查询 |
| 保存 | `POST .../` | 新增配置 |
| 更新 | `PUT .../` | 修改配置 |
| 删除 | `DELETE .../` | 逻辑删除 |
| 启用/禁用 | `PUT .../enable`、`PUT .../disable` | 修改 validityFlag |

---

## 11. 示例数据业务解读

针对响应中工商银行（`bankHeadCode=102`）：

| 维度 | 值 | 解读 |
|------|-----|------|
| 监控配置 | `monitorStatus=0/是` | 已开通银企监控 |
| 连接状态 | `connectStatus=1/否` | 当前银企链路断开 |
| 最近变更 | `connectTime=2026-06-11 09:17:51` | 上次连接状态更新时间 |
| 当日次数 | `countNum=0` | 今天尚无连接上报记录 |
| 账户规模 | `accountCount=7` | FMS 中该总行共 7 个账户 |
| 直连率 | `directRate=57.14%` | 约 4 个账户为直连 |
| 汇总 | `connectFailCount=1` | 全集团 1 家监控银行处于断开状态 |

---

## 12. 注意事项

1. **无参全表查询：** 代码注释明确前端可不传参查全表，依赖大行数据量小的前提。
2. **字典语义：** `0/1` 在本模块表示「是/否」，与部分通用布尔约定相反，以系统字典为准。
3. **`countNum` 与 `connectStatus` 独立：** 连接状态来自配置表；当日次数来自计数表，仅 `connectBank` 上报时递增。
4. **排序与注释略有差异：** 第一排序键是 `monitorStatus` 而非 `connectStatus`，实际效果是「已监控的银行优先展示」。
5. **FMS 不可用影响：** `queryAccountInfo` 远程调用失败或返回空时，`accountCount` 默认为 `0`，`directRate` 默认为 `0`。

---

## 13. 源码索引

| 文件 | 说明 |
|------|------|
| `zfs-fbn-core/.../controller/monitor/FbnBankMonitorConfigController.java` | Controller 入口 |
| `zfs-fbn-core/.../service/monitor/FbnBankMonitorConfigService.java` | 核心业务逻辑 |
| `zfs-fbn-core/.../dto/monitor/FbnBankMonitorConfigDTO.java` | 返回 DTO |
| `zfs-fbn-core/.../dto/monitor/FbnBankMonitorSumDTO.java` | 汇总 DTO |
| `zfs-fbn-core/.../entity/monitor/FbnBankMonitorConfigEntity.java` | 数据库实体 |
| `zfs-fbn-core/.../service/monitor/FbnBankMonitorCountService.java` | 连接次数查询 |
| `zfs-fbn-core/.../client/ZfsFbnDaoBaseClient.java` | Base 服务 Feign |
| `zfs-fbn-core/.../client/ZfsFbnDaoFmsClient.java` | FMS 服务 Feign |
