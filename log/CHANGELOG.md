# 更新日志

## [v3.2.0] - 2026-08-23

### 今日主题

完成 SynPharm 前后端全面联调，核心业务模块接入真实 API，预测业务闭环完成。

### 新增功能

1. **预测结果管理完善**
   - 接入真实接口 `GET /api/results`、`GET /api/results/{id}`
   - 结果详情由 Mock 改为真实后端数据
   - 支持展示算法类型、靶点、SMILES、结合亲和力、置信度、交互信息

2. **预测结果删除**
   - 接入 `DELETE /api/results/{id}`
   - 前端结果列表增加删除操作
   - 删除成功刷新列表
   - 说明设计逻辑：`PredictTask` 与 `PredictResult` 分离，删除结果不删除任务记录

3. **预测历史**
   - 新增 `GET /api/predict/history`
   - `Predict.vue` 增加预测历史 Tab
   - 展示用户历史预测记录

4. **批量预测联调**
   - 完成 `POST /api/batch/upload`、`GET /api/batch/status/{batchId}`、`GET /api/batch/download/{batchId}`
   - 验证上传、任务创建、状态查询流程
   - 状态 SUCCESS 测试通过

5. **Dashboard 统计修复**
   - Dashboard 已接入真实统计数据
   - 移除统计页面 Mock 依赖

### 修复

- `Predict.vue`、`Results.vue`、`Tasks.vue` 核心页面去 Mock
- DTI/PPI/DDI 字段兼容修复
- `bindingAffinity` 空值保护
- `Visualization.vue` 改为通过 `GET /api/results/{id}` 获取真实数据

### JSON 契约对齐（接口数据流通）

- 预测结果字段统一：`id`/`algoType`/`targetId`/`targetName`/`ligandSmiles`/`bindingAffinity`/`confidenceScore`/`confidenceLevel`/`interactions`/`createdAt`
- 前端 API 与后端响应保持一致

### 测试验证

已通过：
- `POST /api/auth/login`
- `GET /api/users/profile`
- `PUT /api/users/profile`
- `GET /api/tasks`
- `GET /api/results`
- `GET /api/results/{id}`
- `DELETE /api/results/{id}`
- `GET /api/predict/history`
- `POST /api/batch/upload`
- `GET /api/batch/status/{batchId}`

### 待办（未完成）

1. **批量下载问题**
   - `GET /api/batch/download/{batchId}`
   - 当前 CSV 只有表头，没有数据
   - 需要后端检查批量结果落库和查询关联

2. **邮箱验证码**
   - `POST /api/auth/captcha/send`
   - 当前提示邮件服务未配置
   - 影响注册和忘记密码

3. **靶点库**
   - `Targets` 页面仍使用 Mock 数据
   - 待接入真实 `GET /api/targets`、`GET /api/targets/{id}`

4. **保存结果按钮**
   - 当前预测完成后后端已自动保存 `predict_result`
   - 按钮无实际业务
   - 需要删除或改造成收藏功能

---

## [v3.1.0] - 2026-08-07

### 今日主题

完善注册登录、修复 FastAPI 认证、打通前后端 JSON 契约、核心视图接入真实 API、安全加固、产出批量处理技术设计。

### 新增功能

1. **独立注册接口**
   - 新增 `POST /api/auth/register`（邮箱 + 昵称 + 密码 + 验证码，type=register）
   - 注册成功自动登录返回 Token；邮箱/密码唯一性校验、并发兜底

2. **密码登录**
   - 新增 `PasswordLoginStrategy`（loginType=password，BCrypt 校验）
   - 登录页新增"密码登录"入口，与"验证码登录"并列

3. **开发模式验证码回显**
   - 未配置邮件服务时可本地联调：验证码直接返回给前端显示
   - 后加固为显式开关 `CAPTCHA_DEV_MODE`（生产必须 false）

4. **预测核心链路补齐**
   - 新增 `PpiAlgoExecutor` / `DdiAlgoExecutor`（PPI/DDI 单条+批量真实调用 FastAPI）
   - 单条预测结果落库（隐式任务 + `predict_result`）
   - 新增 `GET /api/predict/history` 预测历史
   - `/api/results` 列表/详情/删除从空实现改为真实 DB 查询

5. **批量处理技术设计**
   - 产出 `docs/modules/predict/批量处理技术设计文档.md`（RabbitMQ 方案，含拓扑/消息/可靠性/实施计划，待实施）

### 修复

- **FastAPI API Key 认证 bug**：`APIKeyHeader` 隐式注入未生效导致正确 key 也 401，改为手动读取请求头 `X-API-Key`
- **登录 Token 字段不匹配**：后端返回 `accessToken`，前端误读 `token` → 修复为 `response.accessToken`
- **调试后门**：移除 `POST /api/auth/debug/login`（前端同步移除）
- **任务模块必崩**：补 `PredictTaskMapper.xml`，修复 `GET /api/tasks` 的 `Invalid bound statement`；清理无绑定的 `selectByTaskId`
- **实体与表不对齐**：`PredictResult`/`PredictTask` 去掉表不存在的 `updated_at`，补齐缺失列
- **安全**：JWT 密钥移除硬编码默认值（fail-fast）；`AuthenticationEntryPoint` 统一返回 401；前端 baseURL 生产走相对路径（nginx 同源反代）

### JSON 契约对齐（接口数据流通）

- 后端 ↔ FastAPI：`PredictRequest`/`AlgoResponse` 加 `@JsonNaming(SnakeCaseStrategy)`，修复 camelCase/snake_case 断裂
- 前后端请求：DDI 字段统一为 `drugASmiles/drugBSmiles`
- 前后端响应：`PredictResultResponse` 按前端期望改造（`id`/`ligandSmiles`/`datasetInfo`/interactions 字段）
- 前端 `api/predict.ts`/`types` 对齐；结果列表改分页 `{total,list}`
- 前端核心视图接真实 API 并去 Mock：`Predict.vue`（真实预测）、`Results.vue`（真实结果）、`Tasks.vue`（真实任务）

### 部署 / 文档

- 上线流程文档：创建 → 更新核心问题总览 → 合并为单一文档
- `AI预测核心模块-SpringBoot后端技术开发文档.md`：批量处理架构更新为 RabbitMQ（§2.4）
- 代码多次提交推送 GitHub（`main`）

### 待办（未完成）

- Dashboard / Profile / Targets 统计页仍用 Mock（依赖后端统计接口，待实现）
- 批量处理 RabbitMQ 改造（技术设计已完成，待按实施计划落地）

---

## [v2.3.0] - 2026-07-24

### 新增功能

1. **独立注册接口**
   - 新增 `/api/auth/register` 接口，支持用户通过邮箱验证码注册
   - 注册成功后自动登录，返回JWT Token

2. **验证码有效期调整**
   - 将邮箱验证码有效期从5分钟改为1分钟
   - 提高安全性，减少验证码被滥用的风险

3. **管理员调试登录接口** ⚠️
   - 新增 `/api/auth/debug/login` 接口
   - 输入固定验证码 `zhihuyaoyan` 即可直接登录系统
   - 登录后角色为 `admin`，便于开发调试
   - **注意**：此接口仅用于开发环境，生产环境需删除

4. **前端注册页面更新**
   - 添加验证码输入框（6位数字）
   - 添加发送验证码按钮，支持60秒倒计时
   - 实时表单验证，包含验证码格式校验

### 修改文件

**后端文件**：
- `src/main/java/com/synpharm/dto/request/RegisterRequest.java` - 新增注册请求DTO，包含captcha字段
- `src/main/java/com/synpharm/service/AuthService.java` - 新增register方法接口
- `src/main/java/com/synpharm/service/impl/AuthServiceImpl.java` - 实现注册逻辑
- `src/main/java/com/synpharm/api/AuthController.java` - 新增注册和调试登录接口
- `src/main/java/com/synpharm/service/impl/EmailCaptchaServiceImpl.java` - 验证码有效期改为1分钟

**前端文件**：
- `src/api/auth.ts` - 新增sendCaptcha和debugLogin接口
- `src/stores/auth.ts` - 新增sendCaptcha方法，更新register参数类型
- `src/views/Register.vue` - 添加验证码输入框和发送按钮，实现倒计时功能

**文档文件**：
- `用户认证模块技术设计文档.md` - 更新接口列表、验证码有效期、新增注册和调试接口文档

### 技术实现

- 注册流程：发送验证码 → 输入验证码 → 验证验证码 → 创建用户 → 生成Token
- 调试登录：验证固定验证码 → 查询或创建调试用户 → 生成Token（admin角色）
- 验证码验证：Redis存储，1分钟过期，使用后立即删除

### 安全注意事项

1. 验证码有效期缩短至1分钟，降低暴力破解风险
2. 调试接口仅用于开发，生产环境部署前必须删除
3. 注册密码使用BCrypt加密存储
4. 所有输入参数均经过后端校验

## [v2.2.0] - 2026-07-23

### 初始版本

- 实现QQ邮箱验证码登录注册合一功能
- 策略模式支持多种登录方式扩展
- JWT无状态认证
- 登录限流（5次失败锁定15分钟）
- Token黑名单机制
- Redis缓存验证码和限流数据
