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

### 真实客户端：UNDER_CONSTRUCTION 部分施工取消

**PASS — 用户于 2026-11-06 在既有世界「新的世界2」完成 50% 施工后取消的真实客户端验收。**

测试对象：
- Building：`140d3154-f8e5-4975-ba13-bd226302d699`。
- Faction：`c2a593a0-ac36-4f90-8bbf-dd0cb5660a32`（Test Wolves）。
- Settlement：`cca48d0a-89af-4b8c-b651-b612390baaf3`。
- Blueprint：`empires:warehouse_prototype`，origin `(2170,63,1853))。
- 取消前 Builder Job：`2f0e1eed-ccac-3240-8ee9-ccd3fb5f14d6`。

实际过程与观察：
1. 取消测试前势力库存中圆石/木板各为 9；分别追加 9 后，两种材料库存均为 18。
2. `plan-building` 创建 PLANNED 记录；随后预留材料，Building 变为 RESERVED，可用库存各剩 9，escrow 各为 9。
3. `start-construction` 成功，Building 变为 UNDER_CONSTRUCTION，进度 `0/100`；Builder Job 自动创建为 AVAILABLE、worker=none。
4. 执行 `apply-construction-work <buildingUUID> 0 50` 后，Building 保持 UNDER_CONSTRUCTION、进度 `50/100`；escrow 为圆石/木板各 5，`worldSync=PENDING)。
5. 等待放置后，`building-placement` 报告 `cursor=12/25`、`originChunkLoaded=true)；`show-building` 同时确认 `placementCursor=12)。
6. 取消命令返回 `CANCELLED)、`escrowCleared=true)、`progress=50/100)、`placedBlocksPreserved=true)。
7. 取消后 `show-building` 确认状态为 CANCELLED，进度仍为 `50/100`，escrow 为空，`worldSync=PENDING)、`placementCursor=12)。
8. 取消后库存显示圆石/木板各为 14。即每种材料都只退回剩余 5 个；已按累计进度消耗的 4 个没有退回。
9. `list-builder-jobs <buildingUUID>` 显示关联 Job 为 CANCELLED、worker=none、retries=0。
10. 取消后约 24 秒内的后续查询仍显示 `cursor=12/25)、Building CANCELLED；游标没有继续推进。最终 Validator PASS，0 errors、0 warnings。单人服务器断开后，日志显示三个维度的区块均已保存，模组服务正常停止。

本轮测试边界：
- 日志通过 cursor=12/25 的取消前后保持，以及取消后延迟查询未推进，验证了放置进度停止；但没有独立的截图/文字记录说明用户逐块目视核对了已呈现方块。因此记录为游标/调度停止 PASS，不额外声称完成独立的方块视觉检查。
- Builder Job 在取消前为 AVAILABLE、worker=none。此次验证了关联 Job 随施工取消而变为 CANCELLED，但没有实际领取者，因此**没有单独验证已分配工人的释放**。
- 本轮附件是客户端命令日志，没有新的 Gradle build/GameTest 摘要；55/55 GameTests 与 BUILD SUCCESSFUL 仍以此前用户提供的自动化测试日志为证据。

### 尚待验收

- **UNDER_CONSTRUCTION 真实客户端取消：待完成。** 需验证按当前剩余 escrow 退款、已放置方块保留、施工进度/cursor/worldSync 保留、Builder Job 取消并释放工人、PENDING 调度不再写方块。
- Dedicated Server、真实多人权限与并发场景：未验收。
