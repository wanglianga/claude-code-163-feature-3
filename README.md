# 城市无主井盖认领巡检维修与赔付协同平台

市民发现井盖缺失、松动、异响、塌陷后在线上报，平台将事件推送给街道值班员先行临时围护（**应急处置线**，只看现场风险与到场时效），再依据井盖标识、地下管线、历史施工、周边商户、城管台账排查权属（**权属追责线**）。权属无法即时确认时，街道先行设置警示锥、临时盖板、夜间反光标识并垫付费用，事件标记**责任待定**；权属单位认领后派发维修工单，维修队补充到场时间、井室状况、井盖规格、封路与专业协同信息；维修完成后城管巡查员按照片、定位、路面平整度、车辆碾压测试验收；市民或车辆受损的，赔付专员依据上报时间、临时围护、权属确认、维修时长、事故证明定责赔付；最终费用由城管复核分摊，街道垫付费用按复核结果回补。平台沉淀高发路段、权属争议单位、维修超时、赔付金额统计，用于调整日常巡检路线与施工移交规则。

**本次扩展（赔付材料审核）**：车主称轮胎因井盖塌陷损坏等索赔场景下，赔付专员在审核工作台核验**事故时间、行车记录仪、交警证明、维修发票**四类材料，系统自动比对**平台当时的井盖状态**（首次上报、应急派单/到场/围护、权属确认时间）生成时间线核验结论——事故发生在首次上报前的，平台只登记**风险线索**；发生在上报后长时间未围护的，责任关联**值班处置时效**（对照高/中/低危到场时限）与**权属单位响应**。赔付审核通过后平台继续**追踪维修是否彻底完成**；同一井盖再次塌陷时，追踪单自动标记复发，**上一轮维修单位、材料规格（井盖规格）和验收人**进入新的责任判断。

## 技术栈

- **后端**：Spring Boot 3.3（Java 17）、Spring Data JPA、PostgreSQL 15、BCrypt 密码加密、内存令牌认证
- **前端**：Vue 3 + Vite + Vue Router + Pinia + Element Plus + ECharts
- **数据库**：PostgreSQL（用户级集群，端口 5433，免密 trust，不依赖系统服务）

## 目录结构

```
├── backend/            # Spring Boot 后端（端口 8080）
│   └── src/main/java/com/city/manhole/
│       ├── config/     # 认证拦截器、Web 配置、演示数据初始化
│       ├── controller/ # REST 接口
│       ├── service/    # 业务与状态机（应急线/权属线分离、赔付时间线核验）
│       ├── model/      # JPA 实体（事件/应急/排查/认领/工单/验收/赔付/材料/风险线索/维修追踪/分摊/通知/路线/规则）
│       └── repo/       # Spring Data 仓库
├── frontend/           # Vue 3 前端（端口 5173，代理 /api、/uploads 到 8080）
│   └── src/views/      # 登录/首页/上报/工作台/事件详情/赔付审核/分摊/统计/路线/规则/账号
├── scripts/            # 本地启动脚本
└── docker-compose.yml  # 一键容器化启动（PostgreSQL + 后端 + 前端）
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

启动后访问 **http://localhost:5173** 。首次启动后端会自动建表并写入演示数据（12 个账号、12 个覆盖各状态的事件、赔付材料/风险线索/维修追踪样例、巡检路线与移交规则）。

停止数据库：`bash scripts/db-stop.sh`

> 说明：数据库连接配置在 `backend/src/main/resources/application.yml`（jdbc:postgresql://localhost:5433/manhole，用户 postgres，trust 免密）。如需使用已有 PostgreSQL 实例，修改该文件中的地址/账号即可。

## Docker 一键启动

```bash
docker compose up --build -d     # PostgreSQL + 后端 + 前端
```

启动后访问 **http://localhost**（前端 80 端口，/api 与 /uploads 由 Nginx 反代到后端 8080）。数据持久化在 `pgdata` 卷中；停止用 `docker compose down`。

## 测试账号

| 账号 | 密码 | 角色 | 职责 |
|---|---|---|---|
| citizen / citizen2 | citizen123 | 市民 | 上报井盖问题、查看进度、补充赔付材料 |
| street | street123 | 街道值班员 | 应急派单、临时围护、权属排查 |
| gas / power / telecom / municipal | owner123 | 权属单位（燃气/电力/通信/市政） | 认领井盖、派发维修工单 |
| repair1 / repair2 | repair123 | 维修队（市政一队/燃气专业队） | 接单、填写维修信息、完成维修 |
| inspector | inspect123 | 城管巡查员 | 验收（照片/定位/平整度/碾压测试） |
| comp | comp123 | 赔付专员 | 材料核验、事故时间核验、定责赔付、维修追踪 |
| admin | admin123 | 城管管理员 | 统计分析、费用复核分摊、路线/规则/账号管理 |

市民也可在登录页自助注册（仅市民角色）。

## 业务流程（两条线 + 赔付审核扩展）

```
应急处置线：上报 → 街道派单 → 到场 → 临时围护(警示锥/临时盖板/夜间反光标识, 记录费用)
权属追责线：围护后 → 五类证据排查 → 确认权属 / 责任待定(街道垫付) → 权属单位认领
          → 派维修工单 → 接单/维修(井室状况/井盖规格/封路/专业协同) → 验收(4 项检查)
          → 费用复核分摊 → 结案
赔付线：    上报时有损伤自动立案 → 材料核验(行车记录仪/交警证明/维修发票)
          → 事故时间核验(对比平台上报/围护/权属确认时间线) → 定责(五要素分析) → 核定/驳回 → 赔付
          → 维修追踪(确认彻底完成) ⇢ 同一井盖再次塌陷 → 复发关联上一轮维修单位/材料规格/验收人
```

- 事件主状态：`REPORTED → SECURED → OWNER_CONFIRMED → REPAIRING → REPAIRED → ACCEPTED → CLOSED`
- 应急线状态：`PENDING → DISPATCHED → SECURED`；权属线状态：`UNCONFIRMED → INVESTIGATING → CONFIRMED / PENDING`
- **时间线核验结论**：`BEFORE_REPORT`（事故在首次上报前→只登记风险线索）/ `AFTER_REPORT_UNSECURED`（上报后围护前→关联值班处置时效与权属响应）/ `AFTER_SECURED`（围护完成后）
- **维修追踪状态**：`TRACKING → COMPLETED / RECOLLAPSED`；确认彻底完成要求事件已验收通过
- 责任待定事件由街道先行处置并垫付，权属单位后续可认领，费用复核时按追责结论分摊（分摊合计必须等于总费用，否则系统拒绝）。
- 维修超时阈值 48 小时；应急到场时限：高危 120 / 中危 240 / 低危 480 分钟（`application.yml` 可调）。

## 验证方式（均已实际执行）

1. **后端编译**：`cd backend && mvn compile` 通过。
2. **前端构建**：`cd frontend && npm run build` 通过；`npm run dev` 冒烟（首页 200、Compensation.vue 编译 200、/api 代理 200）。
3. **赔付材料审核全流程**（curl 实际执行，comp 账号）：
   - 裕华路轮胎受损案（事件 11）：审核工作台返回时间线核验 `AFTER_REPORT_UNSECURED`，正确算出"围护超时 180 分钟、值班处置时效责任成立、权属未确认"；行车记录仪已核验、交警证明/维修发票待核验。
   - 事故时间改为上报前 → 结论自动变为 `BEFORE_REPORT` 并**自动生成风险线索**；改回上报后 → 风险线索自动移除。
   - 新增材料、核验"存疑"（不带原因被正确拒绝）；审核定责 APPROVED（6:4 分摊）后**自动生成维修追踪 TRACKING**。
4. **维修追踪与复发关联**（curl 实际执行）：
   - 事件 8（已结案）追踪单确认彻底完成 → `COMPLETED`；事件 11（未验收）确认被正确拒绝（"维修尚未验收通过"）。
   - 市民在同一地址（建设大街北段）再次上报 → 事件 8 追踪单自动转 `RECOLLAPSED` 并关联新事件，新事件详情返回复发关联（上一轮维修单位"市政维修一队"、材料规格"Φ650 复合井盖"、验收人"巡查员-赵敏"），赔付专员收到复发通知。
   - 人民路再次塌陷案（事件 12）审核工作台返回事件 1 的复发关联（燃气专业维修队 / Φ700 重型球墨铸铁井盖 / 巡查员-赵敏 / 上次责任：权属单位全责）。
5. **风险线索**：事件 1（事故在首次上报前）线索列表返回事故/上报时间与登记说明。
6. **角色权限验证**：市民访问赔付列表/维修追踪/风险线索/核验材料均被拒；市民可给自己的案件补充材料、查看自己案件工作台，给他人案件补材料或查看被拒；未登录返回 401。
7. **回归验证**：既有全流程（上报→围护→排查→认领→维修→验收→赔付→分摊结案）接口行为不变。

人工界面验证建议路径：登录 comp（comp123）→「赔付管理」→ 待审核案件点「审核定责」，在抽屉中录入事故时间并核验、核验材料、查看复发关联、引用时间线结论提交定责；「维修追踪」页签确认彻底完成或查看复发标记；「风险线索」页签查看上报前事故线索。事件详情页可见"复发关联"卡片。

## 主要接口

| 接口 | 说明 |
|---|---|
| `POST /api/auth/login` `POST /api/auth/register` | 登录 / 市民注册 |
| `POST /api/incidents` `GET /api/incidents` `GET /api/incidents/{id}` | 上报（含同一井盖复发检测）/ 角色范围列表 / 聚合详情（含复发关联） |
| `POST /api/incidents/{id}/emergency/dispatch` `/secure` | 应急派单 / 完成围护 |
| `POST /api/incidents/{id}/investigation` `/claim` | 权属排查 / 主动认领 |
| `POST /api/incidents/{id}/repair-order` `POST /api/repair-orders/{id}/accept` `/finish` | 派单 / 接单 / 完成维修 |
| `POST /api/incidents/{id}/acceptance` | 验收（四项检查） |
| `GET /api/compensations` `PUT /{id}` `POST /{id}/decide` `/pay` | 赔付列表 / 补充（含事故时间，自动时间线核验）/ 定责（核定后生成维修追踪）/ 赔付 |
| `GET /api/compensations/{id}/review` | 审核工作台（案件+平台时间线+材料+风险线索+追踪+复发关联） |
| `POST /api/compensations/{id}/materials` `POST /api/compensations/materials/{mid}/verify` | 提交材料 / 核验材料（通过/存疑） |
| `GET /api/compensations/follow-ups` `POST /api/compensations/follow-ups/{id}/complete` | 维修追踪列表 / 确认彻底完成 |
| `GET /api/compensations/risk-clues` | 风险线索（事故在首次上报前） |
| `GET /api/cost-allocations` `POST /api/cost-allocations/{id}/review` | 费用分摊复核 |
| `GET /api/stats/*` | overview / hotspots / disputes / repair-timeouts / compensation / emergency |
| `GET/POST/DELETE /api/admin/users` `/routes` `/rules` | 账号 / 巡检路线 / 移交规则 |
| `POST /api/files` `GET /uploads/*` | 照片上传与访问 |

