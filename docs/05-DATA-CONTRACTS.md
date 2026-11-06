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

M1-005 施工进度继续保存在同一 BuildingRecord 中：RESERVED → UNDER_CONSTRUCTION 时必须保留完整初始 escrow。每次累计施工进度更新与剩余 escrow 在同一 SavedData mutation 中提交；对于每种资源，已消费总量为 floor(totalCost × completedWork / totalWork)，剩余 escrow 为 totalCost − consumed。所有批次都根据累计进度计算，不能按每批独立取整。expectedProgress 是施工批次的 compare-and-set 令牌；同一批次重放返回已提交结果，旧进度令牌提交不同批次必须拒绝。ACTIVE 的必要条件为 completedWork=workPointsRequired、escrow 为空且 worldSyncState=SYNCED。placementCursor 与 constructionProgress 是不同维度，不可互相代替。

## 7. 存档迁移

- 根数据含 schemaVersion。
- 每次 schema 变化编写 `vN_to_vN+1` migration。
- 迁移前备份/保留旧数据字段，迁移后验证关键引用。
- 未知更高版本时拒绝写入并给出清晰提示，防止旧版模组降级覆盖新存档。
- 数据迁移测试使用旧版本 fixture。

## 8. 同步 DTO

网络传输使用轻量 DTO，不直接序列化领域对象或 SavedData。只同步客户端可见字段。敏感库存和未探索 AI 信息不能泄漏给不该看到的玩家。
