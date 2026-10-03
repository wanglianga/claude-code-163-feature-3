# 城市无主井盖认领巡检维修与赔付协同平台

市民发现井盖缺失、松动、异响、塌陷后在线上报，平台将事件推送给街道值班员先行临时围护（**应急处置线**，只看现场风险与到场时效），再依据井盖标识、地下管线、历史施工、周边商户、城管台账排查权属（**权属追责线**）。权属无法即时确认时，街道先行设置警示锥、临时盖板、夜间反光标识并垫付费用，事件标记**责任待定**；权属单位认领后派发维修工单，维修队补充到场时间、井室状况、井盖规格、封路与专业协同信息；维修完成后城管巡查员按照片、定位、路面平整度、车辆碾压测试验收；市民或车辆受损的，赔付专员核验**事故时间、行车记录仪、交警证明、维修发票、平台当时井盖状态**五项材料后定责赔付；最终费用由城管复核分摊，街道垫付费用按复核结果回补。平台沉淀高发路段、权属争议单位、维修超时、赔付金额统计，用于调整日常巡检路线与施工移交规则。

**赔付材料审核扩展（本次新增）**：

- **五项材料核验**：立案自动创建「事故时间 / 行车记录仪 / 交警证明 / 维修发票 / 平台当时井盖状态」五项待核验材料，专员逐项补充内容、上传附件、核验通过/不通过，全部核验完成方可定责。
- **事故时间时效判定**：录入事故时间后系统自动对照首次上报、围护、权属确认、维修时点生成平台状态快照——事故在首次上报前的，平台仅记录风险线索；事故在上报后长时间未围护的，责任关联值班处置时效（对照高/中/低危到场时限）与权属单位响应时效。
- **赔付后维修追踪**：赔付核定后开始追踪维修是否彻底完成（追踪中 → 验收通过后转彻底完成）。
- **再次塌陷责任追溯**：同一井盖（同地址或 100 米内）维修后再次塌陷的，新事件自动标记并关联上一事件，上一轮维修单位、材料规格、验收人进入新的责任判断，上一案件维修追踪转为「再次塌陷」。

## 技术栈

- **后端**：Spring Boot 3.3（Java 17）、Spring Data JPA、PostgreSQL 15、BCrypt 密码加密、内存令牌认证
- **前端**：Vue 3 + Vite + Vue Router + Pinia + Element Plus + ECharts
- **数据库**：PostgreSQL（用户级集群，端口 5433，免密 trust，不依赖系统服务）

## 目录结构

```
├── backend/            # Spring Boot 后端（端口 8080，含 Dockerfile）
│   └── src/main/java/com/city/manhole/
│       ├── config/     # 认证拦截器、Web 配置、演示数据初始化
│       ├── controller/ # REST 接口
│       ├── service/    # 业务与状态机（应急线/权属线分离、赔付材料核验与维修追踪）
│       ├── model/      # JPA 实体（事件/应急/排查/认领/工单/验收/赔付/赔付材料/分摊/通知/路线/规则）
│       └── repo/       # Spring Data 仓库
├── frontend/           # Vue 3 前端（端口 5173，代理 /api、/uploads 到 8080，含 Dockerfile + nginx.conf）
│   └── src/views/      # 登录/首页/上报/工作台/事件详情/赔付/分摊/统计/路线/规则/账号
├── docker-compose.yml  # 一键启动（db + backend + frontend）
└── scripts/            # 本地启动脚本
```

## 本地启动

前置要求：JDK 17、Maven 3.6+、Node 18+、PostgreSQL 15（已安装即可，脚本自建用户级集群）。

```bash
# 方式一：一键启动（数据库 + 后端 + 前端）
bash scripts/start-all.sh

# 方式二：分步启动
bash scripts/db-start.sh          # 1. 启动 PostgreSQL（端口 5433，自动建库 manhole）
bash scripts/start-backend.sh     # 2. 启动后端  http://localhost:8080
bash scripts/start-frontend.sh    # 3. 启动前端  http://localhost:5173
```

```bash
# 方式三：Docker Compose 一键启动（db + backend + frontend 三容器）
docker compose up -d --build     # 前端同为 http://localhost:5173
```

启动后访问 **http://localhost:5173** 。首次启动后端会自动建表并写入演示数据（12 个账号、11 个覆盖各状态的事件——含 1 起"同一井盖再次塌陷致轮胎损坏"演示事件、巡检路线与移交规则）。

停止数据库：`bash scripts/db-stop.sh`

> 说明：数据库连接配置在 `backend/src/main/resources/application.yml`（jdbc:postgresql://localhost:5433/manhole，用户 postgres，trust 免密）。如需使用已有 PostgreSQL 实例，修改该文件中的地址/账号即可。

## 测试账号

| 账号 | 密码 | 角色 | 职责 |
|---|---|---|---|
| citizen / citizen2 | citizen123 | 市民 | 上报井盖问题、查看进度 |
| street | street123 | 街道值班员 | 应急派单、临时围护、权属排查 |
| gas / power / telecom / municipal | owner123 | 权属单位（燃气/电力/通信/市政） | 认领井盖、派发维修工单 |
| repair1 / repair2 | repair123 | 维修队（市政一队/燃气专业队） | 接单、填写维修信息、完成维修 |
| inspector | inspect123 | 城管巡查员 | 验收（照片/定位/平整度/碾压测试） |
| comp | comp123 | 赔付专员 | 赔付案件审核定责、标记赔付 |
| admin | admin123 | 城管管理员 | 统计分析、费用复核分摊、路线/规则/账号管理 |

市民也可在登录页自助注册（仅市民角色）。

## 业务流程（两条线）

```
应急处置线：上报 → 街道派单 → 到场 → 临时围护(警示锥/临时盖板/夜间反光标识, 记录费用)
权属追责线：围护后 → 五类证据排查 → 确认权属 / 责任待定(街道垫付) → 权属单位认领
          → 派维修工单 → 接单/维修(井室状况/井盖规格/封路/专业协同) → 验收(4 项检查)
          → 费用复核分摊 → 结案
赔付线：    上报时有损伤自动立案(含五项待核验材料) → 录入事故时间(自动时效判定)
          → 材料逐项核验 → 定责(五要素分析) → 核定/驳回 → 赔付
          → 维修追踪(验收通过转彻底完成 / 再次塌陷自动回溯)
```

- 事件主状态：`REPORTED → SECURED → OWNER_CONFIRMED → REPAIRING → REPAIRED → ACCEPTED → CLOSED`
- 应急线状态：`PENDING → DISPATCHED → SECURED`；权属线状态：`UNCONFIRMED → INVESTIGATING → CONFIRMED / PENDING`
- 责任待定事件由街道先行处置并垫付，权属单位后续可认领，费用复核时按追责结论分摊（分摊合计必须等于总费用，否则系统拒绝）。
- 维修超时阈值 48 小时；应急到场时限：高危 120 / 中危 240 / 低危 480 分钟（`application.yml` 可调）。

## 验证方式（均已实际执行）

1. **后端编译**：`cd backend && mvn compile` 通过。
2. **前端构建**：`cd frontend && npm run build` 通过。
3. **全流程 API 验证**（curl 实际执行）：市民上报(含损伤自动立赔付案) → 街道派单/围护 → 排查确认燃气 → 燃气派单 → 维修队接单/完成 → 巡查员验收通过 → 赔付定责并赔付 → 管理员复核分摊 → 事件 `CLOSED`，费用合计校验一致。
4. **赔付材料核验验证**（curl 实际执行，案件为人民路交口"轮胎因井盖塌陷损坏"案）：
   - 录入事故时间早于首次上报 → 判定 `BEFORE_REPORT`，快照提示"平台仅记录为风险线索"；改为上报后未围护时段 → 判定 `AFTER_REPORT_UNSECURED`，快照给出未围护时长 1 小时 17 分钟并对照高危 120 分钟时限、关联值班处置时效；未来时间被正确拒绝。
   - 未核验材料时定责被拒（提示 5 项未核验）；未提交的材料不能核验通过；五项材料（事故时间/行车记录仪/交警证明/维修发票/平台状态）逐项补充并核验通过后定责成功。
5. **维修追踪与再次塌陷验证**（curl 实际执行）：赔付核定后案件维修追踪为"追踪中" → 验收通过自动转"维修彻底完成"；市民在同一地址再次上报塌陷 → 新事件自动标记 `recollapse` 并关联上一事件，上一案件追踪自动转"再次塌陷"，赔付专员与管理员收到通知；事件详情返回上一轮维修单位/材料规格/验收人（距上次维修 77 天）。
6. **责任待定流程验证**：滨河路事件（责任待定）→ 燃气主动认领 → 维修 → 验收 → 赔付按 8:2 分摊 → 复核结案；分摊金额不等于总费用时接口正确拒绝（实测 45100≠44100 被拒）。
7. **角色权限验证**：各角色账号调用各自接口成功；越权访问（市民核验材料、市民查看赔付列表）均被拒绝，未登录返回 401。
8. **统计接口验证**：总览（含再次塌陷事件数）/高发路段/权属争议/维修超时/赔付/应急时效均返回正确数据。
9. **文件上传验证**：上传 PNG 返回 `/uploads/xxx.png`，经后端与 Vite 代理访问均 200。

> 说明：Docker Compose 一键启动配置（`docker-compose.yml` + `backend/Dockerfile` + `frontend/Dockerfile`）已提供；本验证环境无 Docker，未实际构建镜像，镜像构建步骤沿用标准 Maven/NPM 构建命令（与上述第 1、2 项一致）。

人工界面验证建议路径：登录 comp/comp123（赔付专员），在「赔付」打开"人民路交口轮胎损坏"案 →「材料核验」录入事故时间查看时效分析、逐项核验五项材料 →「审核定责」（对话框内展示时效快照与再次塌陷追溯）；登录任意角色查看事件详情页顶部的再次塌陷追溯卡片；管理员在「统计分析」查看"再次塌陷事件"指标。

## 主要接口

| 接口 | 说明 |
|---|---|
| `POST /api/auth/login` `POST /api/auth/register` | 登录 / 市民注册 |
| `POST /api/incidents` `GET /api/incidents` `GET /api/incidents/{id}` | 上报 / 角色范围列表 / 聚合详情 |
| `POST /api/incidents/{id}/emergency/dispatch` `/secure` | 应急派单 / 完成围护 |
| `POST /api/incidents/{id}/investigation` `/claim` | 权属排查 / 主动认领 |
| `POST /api/incidents/{id}/repair-order` `POST /api/repair-orders/{id}/accept` `/finish` | 派单 / 接单 / 完成维修 |
| `POST /api/incidents/{id}/acceptance` | 验收（四项检查） |
| `GET/PUT/POST /api/compensations...` | 赔付列表 / 补充 / 定责 / 赔付 |
| `GET /api/compensations/{id}` | 案件详情（五项材料 + 时效分析 + 再次塌陷追溯） |
| `POST /api/compensations/{id}/accident-time` | 录入事故时间（自动判定上报前/后并生成平台状态快照） |
| `PUT/POST /api/compensations/{id}/materials/{mid}[/verify]` | 补充材料 / 逐项核验 |
| `GET /api/cost-allocations` `POST /api/cost-allocations/{id}/review` | 费用分摊复核 |
| `GET /api/stats/*` | overview / hotspots / disputes / repair-timeouts / compensation / emergency |
| `GET/POST/DELETE /api/admin/users` `/routes` `/rules` | 账号 / 巡检路线 / 移交规则 |
| `POST /api/files` `GET /uploads/*` | 照片上传与访问 |
