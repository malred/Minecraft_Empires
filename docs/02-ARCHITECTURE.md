# 系统架构与工程规范

## 1. 架构目标

- 逻辑模拟不依赖区块持续加载。
- 玩家与 AI 调用同一业务服务。
- 实体、方块和客户端 UI 都是逻辑层的适配/表现。
- 服务端权威，客户端只提交意图和渲染同步数据。
- 保存数据稳定、可迁移、可测试。
- M0 可用小规模原型验证关键假设。

## 2. 总体分层

```text
Client Presentation
  ├─ HUD / Menus / World Map / RTS Input / Renderers
  └─ Client Cache (read-only snapshots)
          │ C2S intents / S2C snapshots
Network Boundary
  ├─ Packet decoding + validation
  ├─ Permission / ownership checks
  └─ Command Gateway
Application Services (server authoritative)
  ├─ CivilizationService / SettlementService
  ├─ BuildingService / ConstructionService
  ├─ PopulationService / JobService
  ├─ EconomyService / InventoryService
  ├─ TechnologyService / MilitaryService
  ├─ DiplomacyService / FactionAIService
  └─ SimulationScheduler / LODCoordinator
Domain Model (plain Java where possible)
  ├─ Faction / Settlement / Region / CitizenRecord
  ├─ BuildingRecord / Blueprint / Job / Army / Treaty
  └─ Rules / State Machines / Value Objects
Minecraft Adapters
  ├─ Entities / Blocks / BlockEntities / World interaction
  ├─ Chunk lifecycle / Structure placement / Entity realization
  └─ Forge events and registries
Persistence
  ├─ World SavedData (authoritative civilization index)
  ├─ Entity/BlockEntity/Chunk persistence as appropriate
  ├─ Blueprint assets and schema versions
  └─ migration + backup + validation
```

## 3. 模块边界

| 模块 | 负责 | 不负责 |
|---|---|---|
| civilization | Faction、Settlement、身份和归属 | NPC 寻路、UI |
| worldmap | Region、控制权、资源点归属 | 方块实际放置 |
| building | 建筑记录、模板引用、状态 | 工人如何走路 |
| construction | 材料预留、施工任务、进度 | 直接处理客户端按钮 |
| population | CitizenRecord、住房、职业 | 决定世界区块是否加载 |
| jobs | 任务领取、锁定、超时、重试 | 具体动画 |
| economy | 资源账本、生产、消耗、交易 | UI 图标 |
| species | 种族定义、修正、trait 注册 | 在业务代码中写死种族分支 |
| military | UnitRecord、Squad、ArmyGroup、命令 | 客户端选框绘制 |
| combat | 伤害规则、实时/抽象战斗适配 | 持续加载远方区块 |
| faction_ai | 战略决策、人格和计划 | 直接修改 World 方块 |
| simulation | 调度、LOD、远程结算、对账 | 业务内容的硬编码特例 |
| networking | 包协议、验证、同步 | 直接存储权威状态 |

## 4. 世界数据与存储策略

### 4.1 权威数据

世界级文明注册表与索引保存在 Overworld 的 SavedData 中，避免每个维度各自创建一份互相冲突的文明全局注册表。具体实现必须以 Forge 1.20.1 SavedData API 为准。

建议分层存储：
- `EmpiresWorldData`：schemaVersion、world UUID、Faction ID 索引、ID 序列、模拟时钟、迁移标记。
- `FactionRecord`：可序列化领域数据；初期可嵌入主 SavedData，数据量上来后按 faction 分片。
- `SettlementRecord` / `RegionRecord`：按实际数据量决定内嵌或单独文件；避免一个 NBT 文件无限增大。
- `CitizenRecord`：逻辑身份、职业、需求、状态、工作绑定；不要把所有居民状态只留在实体 NBT。
- `BuildingRecord`：逻辑建筑状态、坐标、模板版本、进度、耐久、方块同步状态。

所有写操作必须触发对应 SavedData/BlockEntity 的 dirty 标记。对高频字段采用批量写入/脏标记合并，但不能在崩溃时丢失关键事务。保存格式增加 schemaVersion 和迁移代码。

### 4.2 不要滥用 Capability

Forge Capability 适合给实体、物品、区块或 BlockEntity 附加能力/数据，但不是所有全局文明数据的替代品。需要同步到客户端时显式编写网络包；Capabilities 默认不会自动同步。区块和 BlockEntity 的持久数据变更需要确保 owner 被标记 dirty。主文明数据优先使用 SavedData。

### 4.3 事务与资源账本

材料扣除、预留、退还和交易必须通过 Inventory/Ledger 服务，不允许多个建筑自己直接修改同一个库存 Map。所有扣费操作具有 transaction ID，至少做到单次命令幂等；防止客户端重发或重连造成重复扣费。

## 5. 统一 ID 规范

- FactionId：UUID（持久且全球唯一）。
- SettlementId：UUID。
- CitizenId：UUID（逻辑身份，跨实体生命周期稳定）。
- BuildingId：UUID（单个建筑实例）；BlueprintId：ResourceLocation。
- JobId / ArmyGroupId / TreatyId：UUID。
- 所有数据定义 ID 使用 `empires:path_name` 注册名。
- Runtime Entity UUID 不应直接取代 CitizenId。实体只是 CitizenId 当前的一个表现实例。

## 6. AI 与玩家使用同一命令服务

玩家输入与 AI 决策都转化为领域命令，例如：
- `PlaceBuildingCommand`
- `AssignCitizenCommand`
- `SetProductionPriorityCommand`
- `TrainUnitCommand`
- `MoveArmyCommand`
- `DeclareWarCommand`

Command Gateway 负责验证控制权、前置条件、资源、距离和冷却。玩家和 AI 不允许绕过相同规则直接改领域对象。

## 7. 模拟调度

使用一个 World Simulation Scheduler 分批处理任务，不为每个居民/建筑创建永久高频 Tick。

初始建议频率（可配置、需要性能测试）：
- 经济和建筑生产：20 ticks。
- 人口需求/任务规划：100 ticks。
- AI 战略评估：200 ticks。
- 外交评估：600 ticks。
- 低频世界事件：1200 ticks。

调度器采用 budget：每次最多处理指定数量的 faction/job/record，超出部分排队到下一轮。任务排序必须稳定，避免不同服务端运行时出现明显随机偏差。

使用 `gameTime` 作为世界运行时基准；默认服务器关闭时不继续模拟。持久化 lastProcessedGameTime 仅用于防止重复处理，不实现无限离线累积。

## 8. 实体生命周期与 LOD

### 8.1 核心原则

CitizenRecord 是身份真相；NPC Entity 是当前加载区域中的表现和执行器。Entity 不存在不意味着居民死亡；Entity 被加载也不意味着可创建新 CitizenRecord。

### 8.2 状态

`DORMANT → SPAWN_QUEUED → ACTIVE → HIBERNATE_QUEUED → DORMANT`

特殊分支：`DEAD`、`CAPTURED`、`MISSING_RECONCILIATION`。状态变更必须由服务器端 LifecycleService 执行。

### 8.3 实体化流程

1. 玩家接近某 settlement/area，触发 realization demand。
2. 查询已加载区块和实体，检查是否已有该 CitizenId 的实体。
3. 对没有活动实体的存活 CitizenRecord 创建 spawn lease/锁。
4. 在安全的服务器线程创建实体并写入 CitizenId。
5. 实体加入世界后注册运行时映射，完成 lease。
6. 若创建失败，释放 lease 并保留 DORMANT 状态。

### 8.4 休眠流程

1. 实体超出实时模拟范围，且不在战斗/交互/搬运关键事务中。
2. 将可持久化状态写回 CitizenRecord（生命、位置、工作状态、装备等）。
3. 取消或安全挂起短期任务，释放锁定资源。
4. 标记休眠事务完成，再移除实体表现。
5. 任何崩溃恢复时执行 reconciliation，检查实体和记录是否重复。

不能在 chunk unload 回调中无条件生成/删除实体。需要同时考虑玩家离开但 chunk 暂时仍处于加载/实体 tick 范围、实体自然存档、维度切换、死亡、玩家交互和服务器关闭。

### 8.5 防重复与对账

- 每个 CitizenId 最多一个 ACTIVE runtime entity。
- Spawn lease 必须带过期/恢复机制。
- Entity NBT 带 CitizenId 和数据 schema。
- chunk/entity 加载时注册；若 CitizenRecord 已死亡/被捕获则按规则处理。
- 重复实体只保留一个权威实体，其余进入安全清理路径并记警告日志。
- 不可因实体暂时没加载就推断居民死亡。

## 9. 建筑逻辑与真实方块

BuildingRecord 保存 BlueprintId、模板版本、位置、旋转、状态、耐久、施工进度、模板 checksum、方块同步状态和玩家修改策略。模板可以是 StructureTemplate/NBT 或自定义 palette 形式，M0 不需要实现玩家蓝图编辑器。

世界方块放置只能发生在服务器线程且目标 chunk 已加载时。对大模板分批放置，设置每 tick block placement budget；处理 tile/block entity NBT 白名单；不能对未加载区块强制访问。

若玩家修改建筑：记录编辑保护/差异策略。首发可采取“已完工建筑默认不自动重写；修理只处理被认定为建筑自身损坏的受管方块”。不允许每次加载都无条件重建整个蓝图。

## 10. 远程战争和实时切换

远程战争保存战斗快照：双方 UnitRecord、兵种、生命/士气、补给、地形修正、建筑防御、随机种子和进度。模拟结果生成唯一 BattleResult ID，幂等应用伤亡和战利品。

进入实时区域时：
- 冻结该战场的抽象模拟；
- 将单位快照转成实体/小队表现；
- 记录映射关系；
- 继续服务器实时战斗。
离开实时区域时：
- 读取真实单位状态；
- 生成新的抽象战斗快照；
- 关闭该战场的实体模拟。

切换必须是原子化状态转换，不能在同一单位上同时运行抽象与实时战斗。

## 11. 线程模型

- 所有 Minecraft World、Entity、Level、BlockState 操作只在适当的服务器主线程执行。
- 可将纯数据计算（如 Utility AI 评分、战斗数学、区域路径评分）放入后台线程，但输入必须是不可变快照，输出作为命令回到主线程提交。
- 后台线程不得持有或访问 World/Entity/Level 引用。
- 客户端只接收必要快照，不接触权威持久化对象。

## 12. 网络模型

客户端发送“意图/命令请求”，服务端校验并执行。客户端不能提交库存结果、伤害结果、目标阵营资源余额或有效权限作为可信事实。服务端发送可见范围内的状态快照/增量。

所有包要验证：字段长度、资源 ID 是否有效、坐标边界、玩家是否有控制权、目标是否存在、命令频率和重复 request ID。

## 13. 错误恢复

每个长期任务有状态和 lastUpdated。程序启动时执行恢复扫描：施工项目、资源预留、spawn lease、战斗快照、迁移记录。无法恢复的对象进入显式 `NEEDS_REPAIR` 状态和日志，不能静默删除。

## 14. 架构决策记录（ADR）

- ADR-001：首发 Minecraft 1.20.1 Forge，因生态/兼容性优先。
- ADR-002：纯数据文明模拟为真相来源，实体只做近距离表现。
- ADR-003：玩家和 AI 共用应用服务和领域规则。
- ADR-004：首发龙族为双足龙裔，不含自由飞行巨龙。
- ADR-005：服务器运行时间内模拟；默认停服不离线增长。
- ADR-006：客户端不拥有权威游戏状态。
- ADR-007：先做小规模 Vertical Slice，通过测试后扩大势力和单位数量。
