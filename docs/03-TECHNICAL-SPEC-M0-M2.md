# M0–M2 技术规格书

**适用版本：** Minecraft 1.20.1 / Forge 47.4.x / Java 17  
**目标读者：** 模组工程师、AI 编程助手、测试人员  
**规则：** 本规格优先于 AI 的自行猜测。遇到 Forge API 细节不确定时，先查 1.20.1 Forge 文档/MDK，不得照搬新版本 API。

## 0. 目标与范围

M0 验证底层持久化、统一 ID、NPC 生命周期、模拟调度、远程抽象运行和最小蓝图写入。M1 交付玩家可用的大本营和建筑蓝图施工。M2 交付可工作的居民职业、资源采集与仓储。

### 不包含

完整外交、多人联盟、复杂科技树、完整三种族单位美术、巨龙飞行、玩家自建蓝图编辑器、全自动真实物流、远程无限 Chunk 加载。

## 1. 工程初始化

### 1.1 版本

- Minecraft 1.20.1。
- Forge 47.4.28 作为当前候选 MDK 构建（开始编码时核对官方页面；若升级到同分支 47.4.x，记录 ADR）。
- Java 17 64-bit。
- 使用官方 MDK 自带 Gradle Wrapper 与 ForgeGradle 配置，不凭空指定另一个 Wrapper 版本。
- GeckoLib 4.8.4 可选，仅在需要动画的阶段引入；M0 不依赖 GeckoLib。

### 1.2 首次构建验收

- `./gradlew tasks` 成功。
- `./gradlew build` 成功。
- 开发客户端启动并进入新世界。
- 开发专用服务器启动并创建世界。
- 专用服务器没有客户端类加载错误。
- Mods 列表中出现 Empires 模组和正确版本。
- 新世界保存、关闭、重启后不报错。

### 1.3 命名

- mod id：`empires`。
- Java 包名：由项目所有者确定正式反向域名；未确定前用临时包名并在发布前迁移。
- 所有资源路径小写 snake_case。
- 逻辑 ID 使用 UUID，内容 ID 使用 ResourceLocation。

## 2. M0-01：世界级文明数据

### 2.1 交付

`EmpiresWorldData extends SavedData`，作为 Overworld 的文明注册表入口。

至少字段：
- `schemaVersion: int`
- `worldId: UUID`（首次创建时生成）
- `nextDebugSequence: long`（可选，仅调试）
- `factionIds: List<UUID>`
- `simulationClock: long`
- `createdAtGameTime: long`
- `lastMigrationVersion: int`

初期可把少量 FactionRecord 内嵌，避免过早分片；但代码必须通过 repository/service 访问，禁止 UI/实体直接修改内部 Map。

### 2.2 API 草案

```java
interface CivilizationRepository {
    Optional<FactionRecord> findFaction(UUID id);
    List<UUID> factionIds();
    void createFaction(FactionRecord record);
    void updateFaction(FactionRecord record);
    boolean removeFaction(UUID id, RemovalPolicy policy);
    void markDirty();
}
```

所有具体 API 需适配 Forge 1.20.1 映射与 SavedData 工厂形式。数据修改后必须标记 dirty。不得在客户端访问该仓库。

### 2.3 验收

- 新世界创建空 registry。
- 创建一个 Faction 后关闭并重启，UUID、种族、名称、资源和 capital 引用一致。
- 重复加载 SavedData 不创建第二份 registry。
- 维度切换不创建另一份全局 Faction Registry。
- 损坏/未知 schema 有明确错误与备份/恢复策略，不静默覆盖。

## 3. M0-02：Faction 与 Settlement 基础对象

FactionRecord 初始字段：
- id, displayName, speciesId, controllerType（PLAYER/AI/NEUTRAL）
- status（ACTIVE/EXILED/ELIMINATED）
- capitalSettlementId（nullable）
- resource ledger
- population count/cache（不可作为唯一居民真相）
- creationGameTime, schemaVersion

SettlementRecord 初始字段：
- id, ownerFactionId, dimensionId
- anchor BlockPos
- status（ACTIVE/ABANDONED/DESTROYED）
- buildingIds, regionIds
- creationGameTime, schemaVersion

约束：每个 Faction 同一时刻最多一个 ACTIVE capital BuildingRecord；唯一性由服务层校验，不能只靠 UI。

## 4. M0-03：居民身份与实体生命周期

### 4.1 CitizenRecord

初始字段：
- citizenId UUID
- ownerFactionId UUID
- speciesId ResourceLocation
- lifeState（ALIVE/DEAD/CAPTURED）
- simState（DORMANT/SPAWN_QUEUED/ACTIVE/HIBERNATE_QUEUED）
- homeSettlementId nullable
- professionId nullable
- workplaceBuildingId nullable
- health, hunger（范围和单位统一）
- lastKnownDimension, lastKnownPosition
- equipment/inventory reference（首版可以为空）
- schemaVersion

### 4.2 EmpiresCitizenEntity

- Entity NBT 保存 CitizenId、必要的短期运行状态和 schemaVersion。
- Entity 不负责创建逻辑居民记录；只有管理服务能创建 CitizenRecord。
- Entity 加载时向 LifecycleService 注册。
- Entity 自然卸载/保存时把可持久状态安全回写到记录。
- 死亡事件由服务更新 lifeState，不能只因 entity missing 就判死亡。

### 4.3 实体化与休眠

实现 `CitizenLifecycleService`：
- `requestRealization(citizenId, reason)`
- `registerLoadedEntity(citizenId, entity)`
- `requestHibernate(citizenId, reason)`
- `onEntityRemoved(citizenId, removalReason)`
- `reconcileLoadedEntities(level)`

要求：幂等；处理重入；spawn lease 有超时；同一 ID 不允许两个 ACTIVE 实体；异常时回退到可恢复状态。

### 4.4 M0 测试

- Spawn → 保存 → 重启 → 同一个 CitizenId 恢复。
- 实体离开模拟范围后休眠，逻辑记录仍存在。
- 再进入模拟范围只生成一个实体。
- 故意制造重复实体，reconciliation 保留一个权威实体并输出日志。
- 实体死亡后不因重载复活。
- 玩家离开区域但 chunk 尚未卸载时也能按规则切换为抽象状态。

## 5. M0-04：远距离模拟调度器

### 5.1 组件

- `SimulationScheduler`：按游戏时间触发调度。
- `SimulationBudget`：本 tick 可处理任务上限。
- `SimulationClock`：last processed gameTime。
- `SimulationProfile`：各子系统频率和开关。
- `FactionSimulationContext`：纯数据快照，不包含 World/Entity 引用。

### 5.2 初始 cadence

经济 20 ticks；人口/工作计划 100 ticks；AI 策略 200 ticks；外交 600 ticks。每次处理预算必须可配置。M0 先只做简单的“资源每 interval 增长”和“AI 下一个计划状态变更”示范，不实现复杂战斗。

### 5.3 线程规则

M0 全部调度可先在服务器主线程运行，确保正确性。后续若有后台计算，只能传入 immutable snapshot，结果回主线程通过 Command Gateway 提交。后台线程不得持有 World/Entity/Level/BlockState。

### 5.4 验收

- 玩家走远、AI 所在 chunk 未加载时，AI 的纯数据资源变化仍发生。
- 检查区块加载状态，模拟期间没有为了 AI 基地调用强制加载。
- 每 tick 工作量受预算限制。
- 服务端重启不重复发放同一 simulation interval 的收益。
- 默认关服不累计离线资源。

## 6. M0-05：蓝图与最小建筑

首个蓝图只需一个简单 3D 结构（例如 3×3 仓库原型），验证从 BlueprintDefinition 到 BuildingRecord 再到方块放置的路径。

BlueprintDefinition 字段：id、schemaVersion、size、palette/template reference、materials、workPoints、placementRules、allowedRotations、protectedBlocksPolicy。

BuildingRecord 字段：buildingId、blueprintId、blueprintVersion、ownerFactionId、dimension、origin、rotation、state、progress、reservedMaterials、durability、worldSyncState。

M0 只验证放置，不要求工人实体动画施工。

方块放置：服务器主线程；仅修改已加载 chunk；分批设置 block budget；所有位置先验证；重复应用同一 placement operation 必须幂等。模板 NBT 必须过滤不可信 BlockEntity 数据。

## 7. M1：大本营、蓝图和施工

### 7.1 大本营

- `HeadquartersBlock` 或等价受管建筑锚点。
- 每 faction 一个 capital uniqueness check。
- 放置条件：世界范围、地形、距离、领土、无重叠、库存。
- 激活后绑定 `capitalSettlementId`。
- 破坏时通过统一事件更新状态，不允许只依赖方块被移除。

### 7.2 资源预留

建造开始时通过 ResourceLedger 原子预留所需材料。撤销/取消时按规则返还未消耗部分。施工阶段材料逐批消耗或在开始时 escrow；选择一种策略并全项目统一。首版建议：开始施工时全额预留到项目 escrow，完成或取消时结算。

### 7.3 工人施工

Builder Job 状态：AVAILABLE → CLAIMED → TRAVELLING → WORKING → DELIVERING/COMPLETED；异常为 BLOCKED/FAILED/CANCELLED。

施工进度由 work points 推进。方块按分段/施工阶段出现；不要求每个 block 都有独立施工动画。无工人时进度暂停。

### 7.4 验收

- 玩家可放置大本营蓝图并完成施工。
- 不能重复建立第二个大本营。
- 材料不足不能开始施工。
- 施工中退出世界/重启后进度保持。
- 工人不足时施工暂停。
- 取消项目时预留材料处理正确。
- 大本营破坏和重建流程不会生成重复主权中心。

## 8. M2：人口、工作、资源和仓储

### 8.1 首发建筑

- 大本营：唯一主权中心。
- 住宅：提供住房容量。
- 伐木场：提供伐木工岗位。
- 仓库：扩大库存容量。
- 农场：提供食物生产。
- 兵营可在 M2 后段做占位解锁；完整训练进入 M4。

### 8.2 首发资源点

森林资源点、裸露石材点、裸露铁矿点。资源点包含 ResourceNodeId、kind、dimension/position/region、remainingAmount、regenerationPolicy、owner/claim、schemaVersion。

资源点不可在 chunk 未加载时扫描真实方块。远程模拟只读取已登记的节点数据；节点首次登记必须发生在合法加载/生成时。

### 8.3 Job System

JobRecord：jobId、jobType、ownerFactionId、targetId、assignedCitizenId、state、priority、createdAt、updatedAt、reservationIds、retryCount。

任务调度选择符合 profession/trait/距离/任务优先级的 CitizenRecord。任务锁有超时机制；居民死亡、休眠或路径失败时释放锁并重新排队。

### 8.4 生产

生产速率使用统一单位，例如“资源单位 / 100 ticks”或“单位 / 游戏小时”。本项目必须选定一种内部单位，UI 可换算；禁止不同建筑各用不同时间基准。

M2 默认采用共享库存逻辑：建筑每个生产周期从节点或输入库存扣除，向势力 Ledger 入账。实体搬运只作为表现或“真实物流”模式，不影响默认库存正确性。

### 8.5 验收

- 居民能被分配岗位并显示职业。
- 伐木工只采合法森林资源节点。
- 节点储量正确减少。
- 产出进入仓库/势力库存一次且仅一次。
- 工作目标不可达时任务能释放并重试。
- 居民休眠后工作状态合理恢复。
- 食物消耗和住房容量不导致人口负数或重复创建。

## 9. 网络与安全

Client 不能直接写 faction inventory、citizen profession、building owner、army result。所有 C2S 包只传意图和目标 ID。Server 验证距离、权限、状态、材料和速率。包应含 requestId，服务端记忆近期已处理请求以防重复。

## 10. 日志与调试命令

开发阶段实现仅 OP 可用的 debug 命令：
- `/empires debug factions`
- `/empires debug faction <id>`
- `/empires debug citizen <id>`
- `/empires debug simulation`
- `/empires debug realize <citizenId>`
- `/empires debug hibernate <citizenId>`
- `/empires debug validate`

正式发行可由 config 关闭。日志使用稳定 ID、状态和原因，不记录每 tick 的高频噪声。

## 11. 完成定义（Definition of Done）

每个任务至少满足：代码能编译；服务端/客户端侧隔离正确；相关数据保存可恢复；有单元或集成测试；没有新增未经说明的依赖；日志可诊断；文档同步；没有已知重复实体、重复资源或区块强制加载问题。
