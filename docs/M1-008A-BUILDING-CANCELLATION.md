# M1-008A — 建筑取消与材料退款

## 目标

为建筑计划/施工流程提供安全、可重试的取消操作。取消只修改逻辑数据与资源账本，不擅自删除 Minecraft 世界中的方块。

## 规则

- 允许状态：`PLANNED`、`RESERVED`、`UNDER_CONSTRUCTION`。
- 终态：`CANCELLED`。重复取消是只读幂等操作，不再次退款。
- `PLANNED`：没有 escrow，不产生退款。
- `RESERVED`：退还全部预留材料。
- `UNDER_CONSTRUCTION`：只退还当前剩余 escrow；已按施工进度消耗的材料不退。
- 取消保留施工进度、placement cursor、worldSync 状态和已放置方块；不做世界方块删除。
- 施工中的 Builder Job 同步转为 `CANCELLED`，清除 assignedCitizenId；取消不增加 retryCount。
- 取消后保留建筑与任务历史记录；不删除 BuildingRecord、BuilderJobRecord 或 Settlement 反向索引。
- 不允许取消 `ACTIVE`、`DAMAGED`、`REPAIRING`、`RUINED`、`DEMOLISHED`、`NEEDS_REPAIR` 等其他状态；已建成建筑拆除属于后续独立流程。

## 原子性与安全

`CivilizationService.cancelBuilding` 计算退款并准备 immutable replacement records；`EmpiresWorldData.putBuildingCancellation` 在单次 server-thread SavedData mutation 中校验并发布 Faction inventory、Building terminal state、Builder Job cancellation。退款使用 `ResourceLedger.plus`，算术溢出会在发布前失败。事务通过 Building snapshot、Faction snapshot 和 Builder Job snapshot 校验，避免陈旧数据覆盖。

建筑取消后，即使其旧 `worldSyncState` 为 `PENDING`，放置调度器也只处理 `PLANNED` 或 `UNDER_CONSTRUCTION`，不会继续向世界放置方块。已放置的部分结构保持原样，需玩家自行处理；后续可另做带保护检查的拆除功能。

新增状态 `BuildingState.CANCELLED` 是枚举值追加，不增加 BuildingRecord/SavedData 字段，不提升 schema 4；旧记录序列化格式不变。

## OP 调试命令

```mcfunction
/empires debug cancel-building <buildingUUID>
```

命令只允许 OP 执行。成功输出包含 building UUID、CANCELLED 状态、escrow 是否清空、施工进度和 `placedBlocksPreserved=true`。

## GameTests

新增 4 项 `empires.m1_008a` 测试：
1. 取消 PLANNED 不增加库存，重复取消不产生变化。
2. 取消 RESERVED 全额退款，重复取消不重复退款。
3. 取消 UNDER_CONSTRUCTION 只退剩余 escrow；施工进度、游标和 worldSync 保留；关联 Builder Job 变为 CANCELLED 并释放工人；Validator 无错误；重复取消不重复退款。
4. 取消后即使 worldSync 仍为 PENDING，后续放置 tick 也不再写入方块。

## 验收状态

### 自动化验收

**PASS — 用户本地验收，2026-11-06。** `runGameTestServer --console=plain` 完成 **55/55 required GameTests**，Gradle `BUILD SUCCESSFUL in 29s`。新增 4 项 M1-008A 测试均包含在回归中。用户日志有 Gradle 9.0 deprecation 提示，但本轮构建成功。

### 真实客户端：RESERVED 取消与全额退款

**PASS — 用户于 2026-11-06 在既有世界「新的世界2」完成 RESERVED 状态真实客户端验收。**

存档与测试对象：
- `schema=4`、`worldId=6ba6a5f4-e5f9-4e01-bfaa-f86a0ae64c64`、`readOnly=false`。
- 进入测试前 `/empires debug validate`：PASS，0 errors、0 warnings。
- 测试势力：`c2a593a0-ac36-4f90-8bbf-dd0cb5660a32`（Test Wolves）。
- 测试聚落：`cca48d0a-89af-4b8c-b651-b612390baaf3`。
- 本轮新建建筑：`3a8ebd16-33f5-4d3b-955f-6169f6817437`，蓝图 `empires:warehouse_prototype`，位置 `(2157,63,1843)`。

实际命令与观察：
1. `plan-building` 成功，产生 `PLANNED` 记录；命令明确报告未放置方块、未扣材料。
2. `reserve-building-materials` 将建筑转为 `RESERVED`；escrow 为 `minecraft:cobblestone=9`、`minecraft:oak_planks=9`。
3. `show-building` 确认 `RESERVED`、`progress=0/100`、escrow 为上述完整材料。
4. 第一次 `cancel-building` 返回 `CANCELLED`、`escrowCleared=true`、`progress=0/100`、`placedBlocksPreserved=true`。
5. 取消后 `show-building` 确认 `CANCELLED` 且 escrow 为空；`show-resources` 显示圆石与木板各恢复为 9。
6. 对同一 UUID 再次执行取消；第二次取消后两种材料仍各为 9，证明没有重复退款。
7. `list-buildings` 保留该 `CANCELLED` 历史记录；最终 `validate` 为 PASS，0 errors、0 warnings。原有 ACTIVE 建筑记录仍在列表中。

本轮测试边界：
- 该项目在取消时仍为 `progress=0/100`、`worldSync=NOT_PLACED`、`placementCursor=0`，因此**本轮没有实际已放置方块可供检查**。`placedBlocksPreserved=true` 是命令返回的保护标记，不应据此声称已完成施工中方块保留的视觉验收。
- 本轮未测试 `UNDER_CONSTRUCTION` 取消、剩余 escrow 部分退款、已放置方块保留、Builder Job 工人释放或 PENDING 放置停止。
- 本轮日志片段没有 Gradle 最终构建摘要；自动化构建与 55/55 GameTests 的 PASS 依据此前用户提供的独立日志。

### 尚待验收

- **UNDER_CONSTRUCTION 真实客户端取消：待完成。** 需验证按当前剩余 escrow 退款、已放置方块保留、施工进度/cursor/worldSync 保留、Builder Job 取消并释放工人、PENDING 调度不再写方块。
- Dedicated Server、真实多人权限与并发场景：未验收。
