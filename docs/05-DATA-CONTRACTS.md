# 数据契约、状态机与配置规范

## 1. 通用约定

- ID：领域对象使用 UUID；内容定义使用 `empires:snake_case` ResourceLocation。
- 时间：游戏逻辑使用 server gameTime（long ticks），UI 显示时换算。
- 数值：内部使用明确单位；所有比例使用 double/定点之一并全项目统一。涉及资源数量建议使用整数 long，避免浮点误差。
- 坐标：dimension ResourceLocation + BlockPos；不要只保存未限定维度的坐标。
- Schema：每份持久数据必须有 schemaVersion。
- 显示名称：用于显示，不得用于逻辑查找。
- 缺省值：读取旧存档时由迁移器填充，不能让业务代码处处猜测 null。

## 2. 核心对象

### FactionRecord

| 字段 | 类型 | 规则 |
|---|---|---|
| id | UUID | 永不变 |
| displayName | String | 显示用途；限制长度 |
| speciesId | ResourceLocation | 必须存在或映射为 fallback species |
| controllerType | enum | PLAYER/AI/NEUTRAL |
| status | enum | ACTIVE/EXILED/ELIMINATED |
| capitalSettlementId | UUID? | 唯一有效主城引用 |
| resourceLedger | map<ResourceId,long> | 不允许负库存，除非明确债务系统 |
| technologyIds | set<ResourceLocation> | 只记录已完成科技；进度单独存 |
| schemaVersion | int | 迁移依据 |

### SettlementRecord

id、ownerFactionId、dimensionId、anchorPosition、status、buildingIds、regionIds、createdGameTime、schemaVersion。

### CitizenRecord

citizenId、ownerFactionId、speciesId、lifeState、simState、professionId、homeSettlementId、workplaceBuildingId、health、hunger、morale（后续）、lastKnownDimension、lastKnownPosition、lastProcessedGameTime、schemaVersion。

### BuildingRecord

buildingId、blueprintId、blueprintVersion、ownerFactionId、settlementId、dimensionId、origin、rotation、state、constructionProgress、workPointsRequired、reservedMaterials、durability、worldSyncState、templateChecksum、schemaVersion。

### JobRecord

jobId、jobType、ownerFactionId、targetId、assignedCitizenId、state、priority、createdAtGameTime、updatedAtGameTime、retryCount、reservationIds、schemaVersion。

### Building cancellation and escrow

- `BuildingState.CANCELLED` is a terminal logical state for a cancelled plan/construction project; it is distinct from `DEMOLISHED` (a future physical teardown lifecycle).
- Cancelling PLANNED refunds nothing; RESERVED returns its full escrow; UNDER_CONSTRUCTION returns only the persisted remaining escrow. Consumed construction materials are not refunded.
- Cancellation atomically publishes faction inventory credit, BuildingRecord terminal state, and associated BuilderJobRecord cancellation. A cancelled builder job clears its worker assignment and does not increment retryCount.
- Cancellation preserves construction progress, placement cursor, worldSync metadata, and all already placed Minecraft blocks. It does not automatically remove or overwrite world blocks.
- CANCELLED records remain in the settlement reverse index and are excluded from active footprint/headquarters uniqueness checks. They remain available for audit/history.
- Repeating cancellation is a read-only no-op after verifying escrow is empty and no active builder job remains. Arithmetic overflow or stale snapshots fail before any map is published.

## BuilderJobRecord（M1-006）

BuilderJobRecord 是施工项目的持久化工作分配，不是居民实体或路径对象。字段：

- `id`：稳定 UUID；当前由 builder-job 命名空间和 Building UUID 确定性派生。
- `buildingId`、`ownerFactionId`、`settlementId`：目标建筑及归属快照，必须与 BuildingRecord 一致。
- `assignedCitizenId`：可空；CLAIMED/TRAVELLING/WORKING 必须有值，AVAILABLE/BLOCKED 必须为空。
- `state`：AVAILABLE、CLAIMED、TRAVELLING、WORKING、BLOCKED、COMPLETED、FAILED、CANCELLED。
- `priority`：0–100 的任务优先级；默认 50。
- `retryCount`、`createdAtGameTime`、`updatedAtGameTime`、`schemaVersion`。

每个 UNDER_CONSTRUCTION BuildingRecord 必须恰好有一个非终态 BuilderJobRecord；每个居民最多领取一个活动 BuilderJob。领取者必须是 ACTIVE、`empires:builder` 职业、归属同一 Faction；若 CitizenRecord 已绑定 homeSettlementId，该聚落必须与任务目标聚落一致。completed job 是历史记录，建筑之后进入 DAMAGED/DEMOLISHED 等状态不应使历史任务失效。

## 3. 状态枚举

### Citizen lifeState

- ALIVE：逻辑居民存活。
- DEAD：死亡，不得重新实体化。
- CAPTURED：被俘，归属/地点由俘虏机制管理。

### Citizen simState

- DORMANT：无活动实体，逻辑模拟。
- SPAWN_QUEUED：已排队实体化。
- ACTIVE：恰有一个权威活动实体。
- HIBERNATE_QUEUED：正在写回并进入休眠。

### Building state

- PLANNED
- RESERVED
- UNDER_CONSTRUCTION
- ACTIVE
- DAMAGED
- REPAIRING
- RUINED
- DEMOLISHED
- NEEDS_REPAIR（异常恢复状态）

### Job state

- AVAILABLE
- CLAIMED
- TRAVELLING
- WORKING
- BLOCKED
- COMPLETED
- FAILED
- CANCELLED

所有状态转换通过服务层方法执行，禁止任意 setter 修改状态。

## 4. 种族配置示例

```json
{
  "schema_version": 1,
  "id": "empires:bearfolk",
  "display_name": "Bearfolk",
  "presentation": {
    "model": "empires:bearfolk",
    "rig": "humanoid_heavy"
  },
  "traits": ["empires:stone_worker", "empires:heavy_lifter"],
  "modifiers": {
    "empires:quarry_output": 1.25,
    "empires:carry_capacity": 1.30,
    "empires:heavy_unit_health": 1.15
  },
  "starting_content": {
    "buildings": ["empires:bear_quarry"],
    "technologies": []
  }
}
```

解析规则：未知 trait 或 modifier 必须有明确错误/忽略策略；配置非法时报告文件和 JSON path；不要静默把整个势力改成另一个种族。

## 5. 建筑蓝图配置示例

```json
{
  "schema_version": 1,
  "id": "empires:lumber_camp",
  "category": "production",
  "footprint": [7, 5, 6],
  "template": "empires:structures/lumber_camp",
  "allowed_rotations": [0, 90, 180, 270],
  "materials": {
    "minecraft:oak_log": 80,
    "minecraft:oak_planks": 120,
    "minecraft:cobblestone": 40
  },
  "construction": {
    "work_points": 600,
    "required_professions": ["empires:builder"]
  },
  "jobs": {
    "empires:lumberjack": 4,
    "empires:hauler": 1
  },
  "production": {
    "output": "empires:wood",
    "amount_per_interval": 24,
    "interval_ticks": 1200
  }
}
```

以上数量仅为占位示例。内容 schema 应使用 Codec/等价校验机制；验证方块 ID、模板边界、材料数量和资源 ID。

## 6. 资源账本事务

所有库存变化使用统一事务：

`requestId → validate → reserve/debit → apply operation → commit/refund → record result`

同一个 requestId 重复提交必须返回之前结果，不可再次扣费。资源扣除和项目创建应在服务层同一事务边界处理。MVP 不做跨世界数据库事务，但必须为存档中断设计恢复状态（如 RESERVED 项目可重建/释放）。

M1-004 建筑材料预留采用全额 escrow：FactionRecord.resources 代表可用库存，BuildingRecord.reservedMaterials 代表项目锁定库存。单项目的 Building UUID 是稳定幂等键；PLANNED → RESERVED 事务必须在同一 SavedData mutation 中同步扣减 faction 可用库存并写入完整 building escrow。材料不足时拒绝整个事务，不允许部分扣费或负余额。当前不实现通用 requestId 回执表；网络命令网关上线时仍须对 requestId 做跨请求防重放处理。

M1-005 施工进度继续保存在同一 BuildingRecord 中：RESERVED → UNDER_CONSTRUCTION 时必须保留完整初始 escrow。每次累计施工进度更新与剩余 escrow 在同一 SavedData mutation 中提交；对于每种资源，已消费总量为 floor(totalCost × completedWork / totalWork)，剩余 escrow 为 totalCost − consumed。所有批次都根据累计进度计算，不能按每批独立取整。expectedProgress 是施工批次的 compare-and-set 令牌；每次成功提交后旧令牌失效，任何旧令牌重放都必须拒绝且不得再次推进进度/减少 escrow。若未来网络网关要在重试时返回原结果，必须另行持久化 requestId 回执，不能仅凭工作量和进度推断请求身份。ACTIVE 的必要条件为 completedWork=workPointsRequired、escrow 为空且 worldSyncState=SYNCED。placementCursor 与 constructionProgress 是不同维度，不可互相代替。

M1-006 Builder Job 的创建与施工启动一起提交：`RESERVED → UNDER_CONSTRUCTION` 的 SavedData mutation 同时写入唯一 AVAILABLE BuilderJobRecord；施工完成并转为 ACTIVE 时，同一 mutation 将关联活动任务转为 COMPLETED。任务领取使用记录快照 compare-and-set，验证 worker 的 ACTIVE 状态、builder profession、Faction/Settlement 归属及并发唯一性。释放/阻塞任务时清除 assignedCitizenId 并递增 retryCount；BLOCKED → AVAILABLE 重试不清除历史计数。任务只保存逻辑 UUID，不通过任务状态强制加载区块。

## 7. 存档迁移

- 根数据含 schemaVersion。
- 每次 schema 变化编写 `vN_to_vN+1` migration。
- 迁移前备份/保留旧数据字段，迁移后验证关键引用。
- 未知更高版本时拒绝写入并给出清晰提示，防止旧版模组降级覆盖新存档。
- M1-006 将世界级 SavedData schema 从 3 升至 4，新增必需的 `builder_jobs` 列表；CitizenRecord 和 BuildingRecord 自身 schema 不变。v3 → v4 迁移为每个既有 UNDER_CONSTRUCTION 建筑生成一个稳定 ID 的 AVAILABLE BuilderJobRecord，不修改 faction ledger、citizen、building、settlement 或真实世界方块。v4 缺少任务列表、任务目标引用不一致、活动任务重复或 worker 不符合资格时，保留原始 NBT 并将世界数据置为只读，不尝试猜测修复。
- 数据迁移测试使用旧版本 fixture。

## 8. 同步 DTO

网络传输使用轻量 DTO，不直接序列化领域对象或 SavedData。只同步客户端可见字段。敏感库存和未探索 AI 信息不能泄漏给不该看到的玩家。
