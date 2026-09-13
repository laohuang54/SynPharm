# SynPharm 后端修复说明文档

> 依据《5. Java Spring Boot 后端修复方案》5.1–5.7 实施
> 开发分支：`feature/prediction-resolver-and-batch-loop`（已同步上游 `main` 并推送至 fork，待提交 PR）
> 生成时间：2026-09-09（2026-09-13 更新：完成与上游 main 的合并，冲突已解决，60 个测试全部通过）

---

## 1. 总览

本次修复覆盖修复方案的 7 个条目，全部在 **Java 后端（synpharm-backend）** 内完成，
未修改前端（synpharm-frontend）与算法引擎（synpharm-fastapi）的任何文件。

| 条目 | 内容 | 实现方式 |
|:---|:---|:---|
| 5.1 | 统一预测入口 | 新增 `POST /api/predict/general`；旧 `/dti /ppi /ddi` 保留并标注 deprecated |
| 5.2 | 标准化输入模型 | 新增 `ResolvedPredictionInput` 与 `PredictionInputResolver` 编排器 |
| 5.3 | UniProt / PDB 解析 | 新增 `UniProtResolver`、`PdbResolver`，支持 `6M0J:A` 形式与自动嗅探 |
| 5.4 | 外部数据缓存和超时 | Redis 缓存（正缓存 24h + 负缓存 10min）、连接/读取超时、Redis 故障降级直连 |
| 5.5 | 统一错误码 | 新增字符串错误码 `PredictionErrorCode`，`Result` 增加 `errorCode` 字段 |
| 5.6 | 批量数据闭环 | 新增 `batch_task_item` 明细表，逐行状态/错误/结果回写，任务中心可按行查询 |
| 5.7 | 收藏与健康检查 | 从零实现收藏模块；新增算法引擎健康检查（超时+重试+熔断） |

**兼容性原则**：所有改动均为增量式——旧接口、旧响应格式、批量 CSV 下载功能全部保留；
`Result.code` 整数码不变（前端依赖 `code !== 200` 判断），字符串错误码通过新增的
`errorCode` 字段返回，前端无感知。

---

## 2. 新增文件清单

### 2.1 错误码体系（5.5）

| 文件 | 作用 |
|:---|:---|
| `exception/PredictionErrorCode.java` | 9 个字符串错误码枚举（INPUT_RESOLVE_FAILED / UNIPROT_NOT_FOUND / PDB_NOT_FOUND / CHAIN_NOT_FOUND / INVALID_SEQUENCE / SEQUENCE_TOO_SHORT / INVALID_SMILES / FASTAPI_UNAVAILABLE / MODEL_UNAVAILABLE） |
| `exception/PredictionException.java` | 携带字符串错误码的预测专用异常 |

### 2.2 输入解析模块（5.2–5.4）

| 文件 | 作用 |
|:---|:---|
| `pipeline/resolve/ResolvedPredictionInput.java` | 标准化输入模型（algoType / inputType / ligandSmiles / targetSequence / proteinA / proteinB / drugA / drugB） |
| `pipeline/resolve/ProteinSequenceValidator.java` | 序列校验：宽松（字符集）与严格（≥31 残基）两种模式 |
| `pipeline/resolve/UniProtResolver.java` | UniProt ID → FASTA 序列，Redis 缓存 + 负缓存 + 降级直连 |
| `pipeline/resolve/PdbResolver.java` | PDB 引用 → RCSB 三步调用取链序列，支持 `6M0J:A` / 默认链 A / 多链拼接 |
| `pipeline/resolve/PredictionInputResolver.java` | 解析编排：按算法类型分发，smiles 类型下自动嗅探 UniProt/PDB |
| `pipeline/impl/UniprotInputParser.java` | inputType=uniprot 的管道解析器 |
| `pipeline/impl/PdbInputParser.java` | inputType=pdb 的管道解析器 |
| `config/ExternalApiConfig.java` | 外部数据源 RestClient（连接 3s / 读取 5s 超时，可配置） |

### 2.3 批量数据闭环（5.6）

| 文件 | 作用 |
|:---|:---|
| `sql/09_batch_task_item.sql` | 明细表建表脚本（幂等，IF NOT EXISTS） |
| `model/entity/BatchTaskItem.java` | 明细实体（batch_id / row_number / input_value / status / result_id / error_code / error_message） |
| `repository/mapper/BatchTaskItemMapper.java` | 明细 Mapper |
| `dto/response/BatchItemResponse.java` | 明细行响应 DTO |
| `dto/response/BatchItemPageResponse.java` | 明细分页响应 DTO |

### 2.4 收藏模块（5.7）

| 文件 | 作用 |
|:---|:---|
| `model/entity/UserFavorite.java` | 收藏实体（表已存在，仅新建实体） |
| `repository/mapper/UserFavoriteMapper.java` | 收藏 Mapper（含物理删除 `deletePhysically`） |
| `resources/mapper/UserFavoriteMapper.xml` | 物理删除 SQL（绕过逻辑删除，避免撞唯一键） |
| `service/FavoriteService.java` / `service/impl/FavoriteServiceImpl.java` | 收藏业务：用户隔离、幂等收藏、归属校验 |
| `api/FavoriteController.java` | 收藏接口（POST/GET/DELETE /api/favorites） |
| `dto/request/FavoriteRequest.java` / `dto/response/FavoriteResponse.java` / `dto/response/FavoritePageResponse.java` | 收藏请求/响应 DTO |

### 2.5 算法健康检查（5.7）

| 文件 | 作用 |
|:---|:---|
| `client/SimpleCircuitBreaker.java` | 手写轻量熔断器（连续失败阈值 + 开启时长 + 半开探测） |
| `dto/response/AlgorithmHealthResponse.java` | 健康检查响应 DTO |
| `api/SystemController.java` | `GET /api/system/algorithm-health` 接口 |

### 2.6 单元测试

| 文件 | 覆盖内容 |
|:---|:---|
| `test/.../ProteinSequenceValidatorTest.java` | 字符集/最小长度/空白清理/宽松模式 |
| `test/.../PredictionInputResolverTest.java` | DTI/PPI/DDI 分发、自动嗅探、SMILES 校验、错误码 |
| `test/.../UniProtResolverTest.java` | FASTA 解析、404→负缓存、缓存命中、Redis 故障降级、ID 格式 |
| `test/.../PdbResolverTest.java` | 三步 RCSB 调用、默认链、多链、404 语义（结构/链）、缓存 |
| `test/.../FastApiClientHealthTest.java` | 健康检查 UP/DOWN、有限重试、熔断开启后不探测 |
| `test/.../FavoriteServiceImplTest.java` | 幂等收藏、他人结果拒绝、物理删除、归属校验、分页 |
| `test/.../BatchProcessServiceImplTest.java` | 闭环落库回写、行级失败不阻断、批次级失败置 FAIL 并抛出、幂等、旧批次补建明细、明细查询 |
| `test/.../PredictControllerTest.java` | /api/predict/general 成功与参数校验、旧接口兼容 |
| `test/.../GlobalExceptionHandlerTest.java` | 字符串错误码输出、管道阶段映射、既有异常不变 |

---

## 3. 修改文件清单

| 文件 | 改动 | 原因 |
|:---|:---|:---|
| `utils/Result.java` | 增加 `errorCode` 字段与三参 `error()` 重载 | 5.5 字符串错误码；`code` 整数码不变 |
| `exception/GlobalExceptionHandler.java` | 增加 `PredictionException`、`PipelineException` 处理器 | 让新错误码能返回给调用方（原先落到兜底 500） |
| `api/PredictController.java` | 增加 `/general` 端点；旧三端点标 `@Deprecated` + Swagger deprecated | 5.1 统一入口 |
| `pipeline/InputParser.java` | 增加默认三参 `parse(..., algoType)` | 解析器按算法类型区分逻辑，旧实现零改动 |
| `pipeline/impl/SmilesInputParser.java` | 覆写三参解析：接入 `PredictionInputResolver` 自动嗅探 | 旧接口传 UniProt ID/PDB 时自动解析 |
| `pipeline/DataPipelineFactory.java` | 单条/批量处理改调三参解析 | 解析器获得算法类型上下文 |
| `service/BatchProcessService.java` | 增加 `getBatchItems` 接口方法 | 5.6 明细查询 |
| `service/impl/BatchProcessServiceImpl.java` | 批量闭环重构（见 4.3） | 5.6 |
| `api/BatchUploadController.java` | 增加 `GET /api/batch/{batchId}/items` | 5.6 明细查询 |
| `client/FastApiClient.java` | 增加 `health()` 方法 | 5.7 健康检查 |
| `config/SecurityConfig.java` | `/api/system/algorithm-health` 加入 permitAll | 监控探测无需登录 |
| `resources/application.yml` | 增加 `prediction.resolver.*` 与 `fastapi.health-*` 配置 | 5.4/5.7 配置化 |
| `pom.xml` | 增加 `mockwebserver`（仅 test scope） | 测试中 mock 外部 HTTP |
| `sql/08_batch_task_alter.sql` | 改为真幂等（information_schema 判断列存在再 ALTER） | 修复 Docker 全新初始化中断链，见 7.1 |

---

## 4. 关键行为说明

### 4.1 输入解析与自动嗅探（5.2/5.3）

- 输入格式沿用既有约定：两个输入逗号分隔（`输入A,输入B`）。
- `inputType=uniprot` / `inputType=pdb`：显式声明，强制走对应解析器（DDI 不支持这两种类型）。
- `inputType=smiles`（含旧 `/dti /ppi /ddi` 兼容接口）：**自动嗅探**——
  第二个参数（PPI 为两个参数）若匹配 UniProt ID 格式（如 `P12345`、`P0DTC2`）则查 UniProt；
  若匹配 PDB 引用格式（4 位 ID，可带 `:链`，如 `6M0J:A`）则查 RCSB；
  否则按蛋白质序列处理（宽松校验：仅字符集，**不强制最小长度**，保证存量输入不受影响）。
- PDB 语法：`6M0J`（默认链 A）、`6M0J:A`、`6m0j.a`（大小写不敏感、`:`/`.` 均可）、
  `6M0J:A,B`（多链按顺序拼接序列）。
- 序列校验：自动移除换行/空白；非法字符 → `INVALID_SEQUENCE`；
  严格模式（uniprot/pdb 解析结果）长度 < 31 → `SEQUENCE_TOO_SHORT`。
- SMILES 校验（DDI 及 DTI 配体）：非空、长度 ≤2000、字符集、括号/方括号配平 →
  不合法抛 `INVALID_SMILES`；深度化学校验仍由 FastAPI 模型负责。

### 4.2 UniProt / PDB 外部数据源（5.4）

- UniProt：`GET https://rest.uniprot.org/uniprotkb/{id}.fasta`，解析 FASTA。
- RCSB：三步调用——`/rest/v1/core/entry/{id}`（结构校验）→
  `/rest/v1/core/polymer_entity_instance/{id}/{chain}`（链→实体映射）→
  `/rest/v1/core/polymer_entity/{id}/{entityId}`（取序列）。
- 缓存（Redis）：
  - 正缓存 `synpharm:uniprot:{id}` / `synpharm:pdb:{id}:{chain}`，TTL 默认 24 小时；
  - 负缓存 `...:missing:...`，TTL 默认 10 分钟（404 防穿透）；
  - **Redis 不可用时自动降级直连外部 API**，不影响预测主流程。
- 超时：连接默认 3 秒、读取默认 5 秒（`prediction.resolver.*` 可配）。

### 4.3 批量数据闭环（5.6）

处理链路（改造后）：

```
上传 CSV → 保存文件 → 建 batch_task → 逐行建 batch_task_item（状态0）
→ 事务提交后投递 MQ → 消费者处理：
    按 50 行分块 → 行级解析（失败只标记该行，批次继续）
    → 批量调用 FastAPI（按序返回，每行必有结果，失败行含 error 字段）
    → 成功行：建隐式 predict_task + predict_result，回写 item.result_id、状态2
    → 失败行：写 error_code / error_message、状态3
→ 从 DB 重算成功/失败数 → 写结果 CSV（下载功能不变）→ 批次置 SUCCESS
```

- **行级失败**（解析错误 / 模型单行失败）不阻断批次，`fail_count` 准确累加。
- **批次级失败**（算法引擎整体不可用）：剩余行标记 `FASTAPI_UNAVAILABLE`，
  批次置 FAIL 后**异常继续向上抛出** → 消费者 `basicNack` → 消息进入死信队列
  `batch.task.dlq`（修复了原先"消费者永远 ack、死信形同虚设"的问题）。
- **幂等/重投递**：已成功（状态2）的行跳过；状态3 的行重试；批次统计从 DB 重算。
- **旧批次兼容**：改造前上传、无明细的批次，处理时自动从 CSV 补建明细。
- 批量每行结果现在可在**结果中心**（predict_result 表，datasetSource=batch-predict）
  和**任务中心**（`GET /api/batch/{batchId}/items`）查询。

### 4.4 收藏（5.7）

- 接口：`POST /api/favorites`（收藏，重复收藏幂等）、
  `GET /api/favorites`（我的收藏，分页，带结果摘要）、
  `DELETE /api/favorites/{id}`（取消收藏）。
- 用户隔离：只能收藏**本人**的预测结果；只能删除**本人**的收藏。
- 唯一约束 `uk_user_result(user_id, result_id)` 表内已存在；
  取消收藏采用**物理删除**，避免逻辑删除后重新收藏同一结果撞唯一键。

### 4.5 算法健康检查（5.7）

- `GET /api/system/algorithm-health`（免登录，供监控/Docker 探测）：
  Java 侧主动探测 FastAPI `GET /health/`（该路径无鉴权）。
- 行为：响应超时（默认 5s）→ 有限重试（默认 2 次，指数退避 100/200ms）→
  连续失败达阈值（默认 5 次）熔断开启（默认 30s，期内直接返回 DOWN 不探测）→
  到期半开允许一次探测，成功即恢复。
- 返回：`{ status: UP/DOWN, fastapiStatus, latencyMs, message, checkedAt }`。

---

## 5. 新增 API 一览

| 方法 | 路径 | 鉴权 | 说明 |
|:---|:---|:---:|:---|
| POST | `/api/predict/general` | 是 | 统一预测入口，body：`{inputType, algoType, outputType, inputValue, fileUrl}` |
| GET | `/api/batch/{batchId}/items?page=&pageSize=&status=` | 是 | 批量任务逐行明细（含归属校验），status 可选 0/1/2/3 |
| POST | `/api/favorites` | 是 | 收藏结果，body：`{resultId, note?}` |
| GET | `/api/favorites?page=&pageSize=` | 是 | 我的收藏（分页） |
| DELETE | `/api/favorites/{id}` | 是 | 取消收藏（仅本人，物理删除） |
| GET | `/api/system/algorithm-health` | 否 | 算法引擎健康检查（熔断+重试） |

错误响应示例（修复方案 5.5 要求的格式，`code` 保持整数、`errorCode` 为字符串）：

```json
{
  "code": 6001,
  "errorCode": "SEQUENCE_TOO_SHORT",
  "message": "蛋白质序列长度不足，至少需要 31 个残基",
  "data": null
}
```

---

## 6. 新增配置项（application.yml，均有默认值）

```yaml
fastapi:
  health-timeout: 5000             # 健康检查响应超时 ms
  health-retries: 2                # 有限重试次数
  health-breaker-threshold: 5      # 连续失败 N 次开启熔断
  health-breaker-open-ms: 30000    # 熔断开启时长 ms

prediction:
  resolver:
    uniprot-base-url: https://rest.uniprot.org
    rcsb-base-url: https://data.rcsb.org
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
    cache-ttl-hours: 24            # 正缓存 TTL
    negative-cache-ttl-minutes: 10 # 404 负缓存 TTL
```

以上均可通过同名环境变量覆盖（如 `UNIPROT_BASE_URL`、`FASTAPI_HEALTH_TIMEOUT`）。

---

## 7. 部署与迁移注意

1. **新表 `batch_task_item`**：执行 `sql/09_batch_task_item.sql`（幂等，可重复执行）。
   - Docker 全新部署：MySQL 容器首次初始化会自动执行 `sql/` 目录全部脚本，无需额外操作。
   - **已部署环境**：Docker MySQL 只在首次启动执行初始化脚本，需手动执行：
     `docker exec -i <mysql容器> mysql -uroot -p synpharm < sql/09_batch_task_item.sql`
     或直接连库执行脚本内容。
2. 改造前已上传的存量批量任务：无明细记录，消费时会**自动从 CSV 补建明细**，无需人工迁移。
3. 外部网络依赖：UniProt / RCSB 为国外站点，生产环境请确认服务器可访问；
   网络不可达时预测会返回 `INPUT_RESOLVE_FAILED`（不阻塞其他功能）。
4. 收藏唯一键 `uk_user_result` 表内已存在（05_user_favorite.sql），无需新增迁移。

### 7.1 部署实测中发现并修复的两个 SQL 问题（2026-09-10 Docker 实机验证）

| 问题 | 根因 | 修复 |
|:---|:---|:---|
| Docker 全新初始化时 09 脚本不执行 | `08_batch_task_alter.sql` 直接 `ADD COLUMN`，而 `06` 建表已含该列 → 报 1060 错误中断整个 docker-entrypoint-initdb.d 初始化链（此前 08 是最后一个脚本所以未暴露） | 08 改为真幂等写法：先查 information_schema 判断列是否存在，再决定是否加列 |
| 09 脚本报 1064 语法错误 | `row_number` 是 MySQL 8.0 保留字（窗口函数 ROW_NUMBER） | 建表语句中用反引号 `` `row_number` `` 转义 |

### 7.2 本地部署验证记录（2026-09-10）

- 方式：Docker Desktop + Compose 6 服务全量部署（mysql/redis/rabbitmq/backend/fastapi/frontend）
- 环境修复：本机 Docker 原配置的阿里云个人加速器已失效（403），经确认后更换为 DaoCloud + 1ms.run 公共镜像源
- 端口：80 被系统占用，前端改用 **8081**（`deploy/.env` 中 `FRONTEND_PORT=8081`）
- 验证结果：
  - 6 个容器全部 healthy；MySQL 初始化 7 张表全部就位（含 `batch_task_item`）
  - `GET /api/system/algorithm-health` → UP（真实探测 FastAPI /health/，延迟 138ms）
  - 游客登录 → `POST /api/predict/general` 纯序列输入 → 预测成功并落库
  - `POST /api/predict/general` 传 UniProt ID `P0DTC2` → 自动嗅探解析真实序列 → 预测成功（5.3 外部解析链路真实可用）

---

## 8. 兼容性保证（原有功能不受影响）

| 原有行为 | 保证方式 |
|:---|:---|
| 前端调用 `/api/predict/dti /ppi /ddi` | 接口保留，仅标注 deprecated；请求/响应格式不变 |
| 前端 `request.ts` 的 `code !== 200` 判断 | `Result.code` 仍为整数，字符串码走新增 `errorCode` 字段 |
| 批量 CSV 上传/轮询/下载 | 链路不变，结果 CSV 格式不变，仅增强行级记录与统计 |
| 单条预测历史/结果中心 | 落库逻辑不变；批量行结果额外进入结果中心（增量） |
| 存量纯序列输入（含短序列演示数据） | smiles 类型下序列仅做字符集宽松校验，不强制最小长度 |
| 认证/安全 | SecurityConfig 仅新增一个 permitAll 健康检查路径，其余不动 |
| FastAPI 契约 | 未修改 FastAPI 任何文件；批量按序返回约定已由 FastAPI 实现保证 |
| MQ 死信 | 原先"永远 ack"的缺陷按 5.6 要求修正（批次级失败才进死信） |

---

## 9. 测试说明

- 测试命令：`cd synpharm-backend && mvn -s maven-settings.xml test`
- 共 9 个测试类、40+ 用例，覆盖 5.1–5.7 全部新增功能。
- 测试策略：外部 HTTP（UniProt/RCSB/FastAPI）用 **MockWebServer** 模拟；
  Redis、Mapper、MQ 用 **Mockito** 模拟；**单测不依赖** MySQL/Redis/RabbitMQ 实例。

> **测试执行结果（2026-09-09 实测）：**
> `Tests run: 60, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`
> 各测试类：PdbResolverTest 9 ✓、PredictionInputResolverTest 11 ✓、
> ProteinSequenceValidatorTest 6 ✓、UniProtResolverTest 8 ✓、
> BatchProcessServiceImplTest 7 ✓、FavoriteServiceImplTest 8 ✓、
> FastApiClientHealthTest 4 ✓、PredictControllerTest 3 ✓、GlobalExceptionHandlerTest 4 ✓

---

## 10. 遗留事项（本次未做，需另行确认）

1. **前端对接**：前端 `Predict.vue` 已有 uniprot/pdb 输入类型 UI 但未接新 `/general` 接口；
   批量明细、收藏、健康检查的前端页面未实现（本次按约定只改后端）。
2. **仓库文档**：`README.md`、`docs/api/接口文档.md` 未同步更新（本说明文档即变更说明）。
3. 收藏表 `user_favorite` 的 `deleted` 字段已无实际用途（物理删除），未删列以免动表结构。

---

*本说明由 Claude Code 依据实际代码改动生成，与 `feature/prediction-resolver-and-batch-loop` 分支一一对应。*
