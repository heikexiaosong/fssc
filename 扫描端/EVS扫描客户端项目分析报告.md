# EVS 扫描客户端项目分析报告

> **项目路径**：`d:\evs_scan\evs_scan_420`  
> **Python 解释器**：`D:\evs_scan\Python27`  
> **影像系统地址**：`http://192.168.2.170/evssys/`  
> **分析日期**：2026-06-02  
> **分析方式**：代码静态分析 + 本地 SQLite 探测 + EIS 接口实际调用验证

---

## 目录

1. [项目定位与架构](#一项目定位与架构)
2. [实际接口调用验证](#二实际接口调用验证)
3. [完整功能说明](#三完整功能说明)
4. [问题分析报告](#四问题分析报告)
5. [优化方案](#五优化方案仅方案不改代码)
6. [总结](#六总结)

---

## 一、项目定位与架构

### 1.1 系统定位

**EVS Scan Client（电子影像扫描客户端）** 是财务共享/电子影像系统（EIS）的**桌面侧扫描工作站**：

| 组件 | 说明 |
|------|------|
| **本地 Web 服务** | Python 2.7 + web.py，默认端口 **8888**（`web_ui/setup.py`） |
| **扫描硬件桥接** | TWAIN 客户端 `twain_client/`，JSONP 监听 **127.0.0.1:48008** |
| **远端影像系统** | `http://192.168.2.170/evssys/`（配置项 `config.EIS_IP`） |
| **本地存储** | SQLite `web_ui/data/data.db` + 图片 `static/images/scan/{任务编号}/` |

### 1.2 架构示意

```
┌─────────────────────────────────────────────────────────────┐
│                    浏览器 UI (jQuery)                        │
│  scan.html / scan2.html / scan3.html / result.html …        │
└────────────┬──────────────────────────────┬─────────────────┘
             │ HTTP :8888                    │ JSONP :48008
             ▼                               ▼
┌────────────────────────┐      ┌──────────────────────────┐
│  web.py 应用层          │      │  TWAIN 扫描仪客户端       │
│  scanService / login…  │      │  twain_client/           │
└────────────┬───────────┘      └──────────────────────────┘
             │
    ┌────────┼────────┐
    ▼        ▼        ▼
 SQLite   磁盘图片   GhEvsInterface
 data.db  scanPath   HTTP → EIS 后端
```

### 1.3 目录结构（核心）

| 目录/文件 | 作用 |
|-----------|------|
| `web_ui/setup.py` | 应用入口，启动 8888 端口 |
| `web_ui/controllers/controller.py` | URL 路由、Session 过滤 |
| `web_ui/service/scanService.py` | 核心业务（约 3900 行，49+ API 类） |
| `web_ui/service/loginService.py` | 登录、定时任务、版本升级 |
| `web_ui/service/resultService.py` | 上传结果查询 |
| `web_ui/service/recognitionService.py` | OCR 识别（遗留） |
| `web_ui/service/configService.py` | 系统配置 |
| `web_ui/model/` | DAO、上传线程、OCR 线程、EIS 接口 |
| `web_ui/model/interface.py` | `GhEvsInterface` 封装所有 EIS HTTP 调用 |
| `web_ui/templates/` | 页面模板（28 个文件） |
| `web_ui/static/js/scan.js` | 前端主控制器 |
| `twain_client/` | TWAIN 本地扫描服务 |

### 1.4 技术栈

- **语言**：Python 2.7
- **Web 框架**：web.py（内嵌 `libs/web`）
- **数据库**：SQLite
- **前端**：jQuery + 自定义 Dialog/模板
- **图像**：PIL、OpenCV（`libs/ocr`）、pyzbar 条码
- **远程通信**：urllib2 HTTP POST + multipart 上传
- **国际化**：gettext（`i18n/`）

### 1.5 认证与会话

- 每个请求经 `Loginfilter` 校验 Session `ticket`（JWT）
- Session 存储：`user`、`ticket`、`appList`、`currentApp`、`scanConfig`、`ftpMap`
- 登录密码前端 AES-ECB 加密（密钥 `ZTE@FSSCZTE@FSSC`，见 `static/js/secret.js`）

### 1.6 三种任务类型（belong）

| 类型 | 常量 | 说明 |
|------|------|------|
| 日常扫描 | `DAILY_SCAN` | 本地创建，支持条码自动分组 |
| 评价任务 | `APPRAISE_TASK` | 从 EIS 拉取待重扫/评价任务 |
| 无任务补扫 | `NO_TASK` | 无单据补扫 |

---

## 二、实际接口调用验证

### 2.1 测试环境

| 项 | 值 |
|----|-----|
| EIS 地址 | `http://192.168.2.170/evssys/` |
| 账号 | XYY057 |
| 本地配置 EIS_IP | 与上述一致（`config` 表 19 条记录） |

### 2.2 登录链路

| 步骤 | 结果 |
|------|------|
| 明文密码 POST | ❌ 失败：`rawPassword cannot be null` |
| AES-ECB 加密后 POST（与 `secret.js` 算法一致） | ✅ 成功 |
| 返回内容 | `ok: true`，JWT `Authorization`，用户：张晓红 / XYY057 |

**加密说明**：密钥 `ZTE@FSSCZTE@FSSC`，模式 AES-128-ECB，PKCS7 填充，Base64 输出。

### 2.3 登录后接口探测

| 接口 | 结果摘要 |
|------|----------|
| `POST scan/baseInfo` | `code: S`，应用数 1（`EIS` 电子影像系统），含 scanConfig（dpi、duplex、imageFormat 等） |
| `POST scan/IsLogin` | `ok: true` |
| `POST scan/GetServCmdInfo` (BACKTASK) | `code: F`（当前账号/条件下无评价任务，属业务数据非连通问题） |

### 2.4 本地 SQLite 实测（2026-06-02）

| 表名 | 记录数 | 说明 |
|------|--------|------|
| `imgHead` | 2 | 用户 XYY057，`DAILY_SCAN`，状态 `WAIT` |
| `imgLine` | 2 | 对应图片行 |
| `uploadTask` | 0 | 无待上传队列 |
| `uploadTaskLog` | 0 | 无上传日志 |
| `config` | 19 | 含 EIS_IP、DPI、条码规则等 |
| `ocrResult` | 0 | 无识别结果 |

**样例任务编号**：

- `NY-BX2605130078`
- `XYY057-202606011612441`

### 2.5 本地服务运行

- 日志 `web_ui/logs/2026-06-01_client.log` 显示服务曾在 **8888** 端口运行
- 历史日志曾指向其他 EIS 环境（如 `ufssc.hbslft.com`），说明可通过登录页「网络配置」切换服务器

---

## 三、完整功能说明

### 3.1 用户与系统管理

| 功能 | 路由 | 处理类 | 说明 |
|------|------|--------|------|
| 登录 | `/login` | `loginService.login` | AES 加密密码，EIS 鉴权，初始化 Session |
| 登出 | `/logout` | `loginService.logout` | 清除 Session，调用 EIS logout |
| 首页/扫描入口 | `/`, `/scan` | `scan` | 渲染主扫描页 |
| 系统配置 | `/configure` | `configure` | 扫描仪、空白页、条码规则、数据保留等 |
| 切换应用 | `/changeapp` | `changeapp` | 多 appCode 切换 |
| 获取扫描参数 | `/getClientParams` | `getClientParams` | 构建 TWAIN 客户端参数字符串 |

**登录 POST 类型**：`login`、`netSet`、`testNetSet`、`isLogin`、`checkVersion`、`upVersion`

**后台定时任务（登录后可启动）**：

- 评价任务定时拉取（部分逻辑已注释）
- 自动上传失败任务（60 秒间隔）
- 历史数据清理（按 `DATA_HOLD_TIME`，默认 3 个月）

### 3.2 扫描业务

| 功能 | 路由 | 处理类 | 说明 |
|------|------|--------|------|
| 日常扫描 | `/dailyScan` | `dailyScan` | TWAIN 扫图 → 空白页过滤 → 条码分组 → 落库 |
| 附件扫描 | `/attachmentScan` | `attachmentScan` | 向已有任务追加附件 |
| 重扫 | `/rescan` | `rescan` | 整任务替换（加密图不可重扫） |
| 补扫 | `/addScan` | `addScan` | 追加页面，带 ADDSCAN 水印 |
| 替扫 | `/replaceScan` | `replaceScan` | 替换选中页 |
| 异步条码识别 | `/asyncScan` | `asyncScan` | 扫前启动 `OcrBarcodeThread` |
| 导入图片 | `/importImg` | `importImg` | PDF/TIF/图片，带进度条 |
| 导入进度 | `/getImportProgress` | `getImportProgress` | 轮询导入进度 |
| 导入刷新 | `/importRefresh` | `importRefresh` | 导入完成后刷新任务列表 |
| EVS 跳转扫描 | `/scan1` | `scan1` | 影像系统「待重扫」跳转入口 |

**扫描客户端交互流程**：

1. `POST /getClientParams` → 获取 DPI、双面、格式等
2. JSONP `http://127.0.0.1:48008/?method=scan&...` → TWAIN 返回文件路径
3. （日常扫描）`POST /asyncScan` → 启动条码识别
4. `POST /dailyScan`（等）→ 服务端保存、更新 DB、返回 HTML 片段

### 3.3 图片编辑

| 功能 | 路由 | 处理类 |
|------|------|--------|
| 删除图片 | `/deleteImg` | `deleteImg` |
| 编辑图片 | `/editImg` | `editImg` |
| 移动图片 | `/moveImg` | `moveImg` |
| 旋转图片 | `/rotateImg` | `rotateImg` |
| 预览翻页 | `/goToImg` | `goToImg` |
| 图片切割 | `/cuttingImg` | `cuttingImg` |
| 图片合并 | `/mergeImg` | `mergeImg` |
| 调整顺序 | `/againImgOrd` | `againImgOrd` |
| 复制图片查询 | `/copyImgQuery` | `copyImgQuery` |
| 复制图片 | `/copyImg` | `copyImg` |

### 3.4 任务管理

| 功能 | 路由 | 处理类 | 说明 |
|------|------|--------|------|
| 添加任务 | `/addTask` | `addTask` | 新建 headNum |
| 删除任务 | `/delTask` | `delTask` | 删库 + 删文件 |
| **合并任务** | `/mergeTask` | `mergeTask` | 多任务合并到第一个（仅日常扫描） |
| 拆分任务 | `/partTask` | `partTask` | 从某张图拆出新任务 |
| 修改编号 | `/editNum` | `editNum` | 改 headNum + 重命名文件夹 |
| 编辑任务头 | `/editHead` | `editHead` | 更新 headDesc 等 |
| 保存用户表单 | `/saveUserForm` | `saveUserForm` | 公司/业务类型等 |
| 按任务保存表单 | `/saveUserFormByHead` | `saveUserFormByHead` | 可能触发文件夹重命名 |
| 显示图片列表 | `/showImages` | `showImages` | 渲染 scan3 右侧面板 |
| 自动添加任务 | `/autoAddTask` | `autoAddTask` | 快捷建单 |

**合并任务限制**：评价任务（`APPRAISE_TASK`）和无任务补扫（`NO_TASK`）不可合并。

### 3.5 EIS 任务交互

| 功能 | 路由 | 处理类 |
|------|------|--------|
| 评价/无任务查询 | `/appraiseTaskQuery` | `appraiseTaskQuery` |
| 下载评价/无任务 | `/appraiseTaskAddScan` | `appraiseTaskAddScan` |
| 获取任务 | `/getTask` | `getTask` |
| 导出 Excel | `/exportAppariseTaskOrNoTask` | `exportAppariseTaskOrNoTask` |

**已废弃（代码仍保留）**：`noTaskQuery`、`noTaskAddScan`

### 3.6 上传与结果

| 功能 | 路由 | 处理类 | 说明 |
|------|------|--------|------|
| 上传 | `/uploadFile` | `uploadFile` | `UploadThread` HTTP 分片上传 |
| 重新上传 | `/reUploadFile` | `reUploadFile` | 失败任务重试 |
| 上传队列 | `/queue` | `queue` | 待传任务、日志、时间窗 |
| 结果查询 | `/result` | `resultQuery` | 分页 + EIS 比对状态 |
| 退回修改 | `/backForModify` | `backForModify` | 状态改回 WAIT |
| 结果页删除 | `/delTaskOnResultPage` | `delTaskOnResultPage` |
| 结果导出 | `/exceptImg` | `exceptImg` | Excel 导出 |

**上传流程（`UploadThread`）**：

```
1. FtpInfo      → 询问 EIS 是否允许上传
2. uploadFile   → 逐张 multipart POST 到 EIS
3. UploadLog    → 写上传日志
4. 更新状态     → imgHead.status = SUCCESS / FAILURE
限制：单任务 ≤ 1000 张、≤ 500MB
```

**任务状态（headStatus）**：

| 状态 | 含义 |
|------|------|
| `TODO` | 初始 |
| `WAIT` | 待上传 |
| `UPLOADING` | 上传中 |
| `SUCCESS` | 上传成功 |
| `FAILURE` | 上传失败 |
| `BACK` | 退回 |

### 3.7 OCR / 识别（遗留功能）

| 功能 | 路由 | 处理类 |
|------|------|--------|
| 识别查询 | `/recognition` | `recognitionQuery` |
| 识别详情 | `/recognitionDetail/(.*)` | `recognitionDetail` |
| 确认识别 | `/confirmRecognition/(.*)` | `confirmRecognition` |

> 数据库表 `ocrResult`、`ocrLog` 在 `initDatabase.py` 中标注为**已废弃**，部分逻辑仍引用。

### 3.8 外部 EIS API 清单

`GhEvsInterface`（`model/interface.py`）封装的接口：

| 方法 | EIS 路径 | 用途 |
|------|----------|------|
| `Login` | `init/login` | 用户登录 |
| `Logout` | `init/logout` | 登出 |
| `IsLogin` | `scan/IsLogin` | 会话校验 |
| `baseInfo` | `scan/baseInfo` | 应用列表、扫描配置、FTP 映射 |
| `FtpInfo` | `scan/FtpInfo` | 上传前权限校验 |
| `uploadFile` | `scan/uploadFile` | 单张图片上传 |
| `UploadLog` | `scan/UploadLog` | 上传日志 |
| `GetServCmdInfo` | `scan/GetServCmdInfo` | 评价/无任务列表查询 |
| `getAppraiseType` | `scan/combo/list` | 评价类型下拉 |
| `exportAppariseTaskOrNoTask` | `scan/exportExcelServCmdInfo` | 导出 Excel |
| `getCompareStatus` | `scan/getCompareStatus` | 影像比对状态 |
| `getScanVersion` | `test/scanVersion/compare` | 版本比对升级 |

### 3.9 数据库表结构

| 表名 | 主键 | 关联 | 说明 |
|------|------|------|------|
| `imgHead` | headId | — | 任务主表（headNum、status、belong、userForm…） |
| `imgLine` | lineId | headId | 图片行（imgNameP、imgOrd、uploadId…） |
| `uploadTask` | uploadTaskId | headId | 上传任务队列 |
| `uploadTaskLog` | uploadTaskLogId | headId | 上传日志 |
| `ocrResult` | resultId | lineId | OCR 结果（已废弃） |
| `config` | configId | — | 本地配置键值 |
| `scanLog` | id | — | 扫描操作审计 |
| `importProgressLog` | id | — | 导入进度 |

### 3.10 UI 页面结构

| 模板 | 角色 |
|------|------|
| `widgets/main.html` | 页面骨架、导航、Session 保活 |
| `modules/scan.html` | 扫描工具栏 |
| `modules/scan2.html` | 左侧任务树 |
| `modules/scan3.html` | 右侧图片面板 |
| `modules/login.html` | 登录 + 网络配置 |
| `modules/configure.html` | 系统设置 |
| `modules/result.html` | 上传结果查询 |
| `modules/queue.html` | 上传队列弹窗 |
| `widgets/appraiseTaskLov.html` | 评价任务查询 LOV |

---

## 四、问题分析报告

### 4.1 严重 / 功能缺陷

| 编号 | 问题 | 影响 | 相关代码 |
|------|------|------|----------|
| P1 | **`/saveComment` 已注册路由，但无 `saveComment` 处理类** | 保存任务说明功能不可用 | `controller.py:59`，`scan.js` 调用 |
| P2 | **`mergeTask` 数据清理不完整** | 合并后 `uploadTask`/`uploadTaskLog`/`imgHead` 残留，编号无法复用 | `deleteByHeadId` 仅删 3 张表 |
| P3 | **`refreshPhoto` 前端有调用，路由未注册** | 右键刷新图片失效 | `functionMenu.html`，无 urls 映射 |
| P4 | **合并操作无完整事务** | `moveImgLine` 已提交后若文件移动或删除失败，源任务 head 残留 | `mergeTask.POST` |

**P2 典型场景**（已复现逻辑）：

- 任务 A：`ZJHG-BX26050800031`
- 任务 B：`ZJHG-BX2605080003`
- 合并后 B 的 `imgHead` 未彻底删除 → 修改其他任务为 B 的编号时报「已经存在」

**`deleteByHeadId` vs `deleteByHeadIds` 对比**：

| 表 | deleteByHeadId | deleteByHeadIds |
|----|----------------|-----------------|
| ocrResult | ✅ | ✅ |
| imgLine | ✅ | ✅ |
| imgHead | ✅ | ✅ |
| uploadTask | ❌ | ✅ |
| uploadTaskLog | ❌ | ✅ |

### 4.2 安全与稳定性

| 编号 | 问题 | 说明 |
|------|------|------|
| S1 | SQL 字符串拼接 | `headId in (...)` 等未参数化，存在注入风险 |
| S2 | `eval()` 解析用户输入 | `scan.js` 等处仍用 eval 解析 JSON |
| S3 | Python 2.7 已 EOL | 无安全更新 |
| S4 | AES 密钥硬编码 | `secret.js` 中 `ZTE@FSSCZTE@FSSC` |
| S5 | 全局 `uploadingHeadId` | 多任务并发上传可能互相干扰 |

### 4.3 代码质量 / 可维护性

| 编号 | 问题 |
|------|------|
| Q1 | `scanService.py` 单文件近 4000 行 |
| Q2 | 大量 `.pyc` 与 `.py` 混仓 |
| Q3 | 废弃接口未清理（`noTaskQuery`、`moveImg1`、`refreshDocGroup`） |
| Q4 | `getTask` 与 `getAppraiseTask` 参数可能不匹配 |
| Q5 | OCR 表废弃但仍被部分逻辑引用 |
| Q6 | 异常处理不统一，部分吞掉错误细节 |

### 4.4 mergeTask 当前代码状态

**已改进**：

- `eval` → `json.loads`
- 循环变量改为 `srcHeadId`，避免被 `moveImgLine` 返回值覆盖
- 合并前保存 `srcHeadNum`，删除后仍可清理文件夹
- `try/except` 返回「合并失败，请删除任务后重新合并」

**仍缺失**：

- 未实现 `cleanupMergedTask()` 统一事务清理
- 仍调用 `deleteByHeadId`，未清理 `uploadTask`、`uploadTaskLog`

---

## 五、优化方案（仅方案，不改代码）

### 阶段一：缺陷修复（预估 1–2 周）

#### 5.1.1 任务合并数据一致性

1. 新增 `cleanupMergedTask(headId)`，在**单事务**中按序删除：
   - `ocrResult`（按 lineId）
   - `imgLine`
   - `uploadTask`
   - `uploadTaskLog`
   - `imgHead`
2. 合并流程顺序：**迁移图片 → 更新行号 → cleanupMergedTask → 删除物理目录**
3. 任一环节失败：返回明确错误，并记录日志便于人工处理
4. 补充测试用例：合并后立即用被合并编号创建/修改任务应成功

#### 5.1.2 补齐缺失接口

| 项 | 方案 |
|----|------|
| `saveComment` | 实现 POST 处理类，更新 `imgHead.headDesc` 或约定字段；或移除路由与前端调用 |
| `refreshPhoto` | 在 `urls` 注册 `refreshPhoto`，实现为重新渲染 `scan3` |

#### 5.1.3 安全快速修补

- 全局 `eval` → `json.loads`
- DAO 层 `IN` 查询改为参数化或 ORM 安全写法
- `headNum` 增加数据库唯一索引（若尚未有）

### 阶段二：架构重构（预估 1–2 月）

#### 5.2.1 服务拆分建议

```
web_ui/
├── services/
│   ├── scan_service.py      # 扫描相关
│   ├── task_service.py      # 任务 CRUD、合并、拆分
│   ├── upload_service.py    # 上传队列
│   └── image_service.py     # 图片编辑
├── repositories/
│   └── task_repository.py   # 统一任务删除（5 表 + 文件）
└── clients/
    └── eis_client.py        # GhEvsInterface 重构
```

#### 5.2.2 统一数据访问

- 抽象 `TaskRepository.delete_task(head_id)`：
  - 保证与 `deleteByHeadIds` 行为一致
  - `deleteByHeadId` 委托给同一实现
- 所有「删任务」入口统一调用

#### 5.2.3 上传并发

- 每 `headId` 独立锁或队列
- 去掉全局 `uploadingHeadId`
- 状态机：WAIT → UPLOADING → SUCCESS/FAILURE

### 阶段三：技术栈升级（预估 2–4 月）

| 项 | 建议 |
|----|------|
| 运行时 | Python 3.10+ |
| Web | Flask/FastAPI 或 web.py Py3 分支 |
| 图像 | Pillow 替代 PIL |
| HTTP | requests 替代 urllib2 |
| 前端 | 可选前后端分离（Vue/React + REST API） |
| TWAIN | 保留本地 Agent（48008）桥接 |
| 密钥 | 环境变量或加密配置，避免硬编码 |

### 阶段四：运维与质量（持续）

| 项 | 内容 |
|----|------|
| 日志 | 结构化日志（requestId、headId、耗时） |
| 测试 | DAO 删除完整性单测；mergeTask 集成测试；Mock EIS |
| 构建 | `.pyc` 不纳入版本库，CI 从 `.py` 编译 |
| 数据库 | headNum 唯一索引；定期 VACUUM；清理任务审计日志 |

### 阶段五：业务增强（按优先级）

1. 合并/拆分二次确认 + 操作审计表
2. 扫描页与结果页状态同步（轮询或 WebSocket）
3. 大文件断点续传
4. 评价任务拉取失败重试与离线队列

---

## 六、总结

| 维度 | 结论 |
|------|------|
| **系统性质** | EIS 电子影像的本地扫描工作站（扫描 → 本地缓存 → 上传 → 结果查询） |
| **技术栈** | Python 2.7 / web.py / SQLite / jQuery / TWAIN |
| **核心价值** | 扫图 → 分组/编辑 → 上传 EIS → 结果查询与比对 |
| **最大风险** | Py2 EOL、巨型单文件服务、任务删除/合并不完整、缺失 API |
| **已知 Bug** | 合并后关联表残留导致任务编号重复校验失败 |
| **接口验证** | EIS 登录、baseInfo、IsLogin 连通正常；评价任务列表视业务数据而定 |

---

## 附录 A：完整 URL 路由表

| URL | 处理类 |
|-----|--------|
| `/` | `scan` |
| `/login` | `login` |
| `/logout` | `logout` |
| `/result` | `resultQuery` |
| `/scan` | `scan` |
| `/scan1` | `scan1` |
| `/dailyScan` | `dailyScan` |
| `/asyncScan` | `asyncScan` |
| `/rescan` | `rescan` |
| `/replaceScan` | `replaceScan` |
| `/addScan` | `addScan` |
| `/attachmentScan` | `attachmentScan` |
| `/editImg` | `editImg` |
| `/importImg` | `importImg` |
| `/deleteImg` | `deleteImg` |
| `/moveImg` | `moveImg` |
| `/rotateImg` | `rotateImg` |
| `/goToImg` | `goToImg` |
| `/editNum` | `editNum` |
| `/editHead` | `editHead` |
| `/addTask` | `addTask` |
| `/delTask` | `delTask` |
| `/mergeTask` | `mergeTask` |
| `/partTask` | `partTask` |
| `/getTask` | `getTask` |
| `/appraiseTaskQuery` | `appraiseTaskQuery` |
| `/appraiseTaskAddScan` | `appraiseTaskAddScan` |
| `/noTaskQuery` | `noTaskQuery` |
| `/noTaskAddScan` | `noTaskAddScan` |
| `/showImages` | `showImages` |
| `/getClientParams` | `getClientParams` |
| `/uploadFile` | `uploadFile` |
| `/reUploadFile` | `reUploadFile` |
| `/backForModify` | `backForModify` |
| `/delTaskOnResultPage` | `delTaskOnResultPage` |
| `/changeapp` | `changeapp` |
| `/saveUserForm` | `saveUserForm` |
| `/saveUserFormByHead` | `saveUserFormByHead` |
| `/recognition` | `recognitionQuery` |
| `/confirmRecognition/(.*)` | `confirmRecognition` |
| `/recognitionDetail/(.*)` | `recognitionDetail` |
| `/configure` | `configure` |
| `/queue` | `queue` |
| `/autoAddTask` | `autoAddTask` |
| `/importRefresh` | `importRefresh` |
| `/copyImgQuery` | `copyImgQuery` |
| `/copyImg` | `copyImg` |
| `/cuttingImg` | `cuttingImg` |
| `/mergeImg` | `mergeImg` |
| `/againImgOrd` | `againImgOrd` |
| `/exceptImg` | `exceptImg` |
| `/saveComment` | ⚠️ **缺失处理类** |
| `/exportAppariseTaskOrNoTask` | `exportAppariseTaskOrNoTask` |
| `/getImportProgress` | `getImportProgress` |

## 附录 B：scanService.py 全部处理类

| 类名 | 简要说明 |
|------|----------|
| `scan` | 首页/扫描入口 |
| `scan1` | EVS 待重扫跳转扫描 |
| `dailyScan` | 日常扫描 |
| `attachmentScan` | 附件扫描 |
| `rescan` | 重扫 |
| `addScan` | 补扫 |
| `replaceScan` | 替扫 |
| `fileScan` | 文件扫描（内部） |
| `getImportProgress` | 导入进度 |
| `importImg` | 导入图片 |
| `uploadFile` | 上传 |
| `reUploadFile` | 重新上传 |
| `backForModify` | 退回修改 |
| `delTaskOnResultPage` | 结果页删任务 |
| `changeapp` | 切换应用 |
| `saveUserForm` | 保存表单配置 |
| `saveUserFormByHead` | 按任务保存表单 |
| `showImages` | 显示图片列表 |
| `addTask` | 添加任务 |
| `delTask` | 删除任务 |
| `mergeTask` | 合并任务 |
| `partTask` | 拆分任务 |
| `getTask` | 获取评价任务 |
| `editNum` | 修改任务编号 |
| `deleteImg` | 删除图片 |
| `editImg` | 编辑图片 |
| `moveImg1` | 移动图片（遗留，未路由） |
| `moveImg` | 移动图片 |
| `refreshDocGroup` | 刷新文档组（未路由） |
| `refreshPhoto` | 刷新图片（未路由） |
| `rotateImg` | 旋转图片 |
| `goToImg` | 预览翻页 |
| `getClientParams` | 扫描仪参数 |
| `appraiseTaskQuery` | 评价任务查询 |
| `appraiseTaskAddScan` | 下载评价/无任务 |
| `noTaskQuery` | 无任务查询（废弃） |
| `noTaskAddScan` | 无任务下载（废弃） |
| `editHead` | 编辑任务头 |
| `queue` | 上传队列 |
| `autoAddTask` | 自动建任务 |
| `copyImgQuery` | 复制图片查询 |
| `copyImg` | 复制图片 |
| `importRefresh` | 导入后刷新 |
| `cuttingImg` | 切割图片 |
| `mergeImg` | 合并图片 |
| `asyncScan` | 异步条码识别 |
| `againImgOrd` | 调整图片顺序 |
| `exportAppariseTaskOrNoTask` | 导出 Excel |
| `exceptImg` | 结果导出 |

---

*文档由代码分析与接口探测自动生成，供项目评审与后续改造参考。*
