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

代码已提交到 `codex/m1-007-staged-construction`。**用户于 2026-11-06 在本地提供验收日志：`runGameTestServer --console=plain` 完成 55/55 required GameTests，Gradle `BUILD SUCCESSFUL in 29s`。** GameTests 自动化验收通过。真实客户端取消命令/退款、旧存档回归、Dedicated Server/多人仍未验收。用户日志仍有 Gradle 9.0 deprecation 提示，本轮不阻断构建。
