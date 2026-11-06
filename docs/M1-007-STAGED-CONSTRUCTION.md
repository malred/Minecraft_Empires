# M1-007 — 分阶段方块施工

## 目标

将 M1-005 的逻辑施工进度与 M0-010 的真实方块放置连接起来：蓝图计划与材料预留不修改世界；开工后 Builder Job 代表施工任务，施工工作点决定允许呈现的模板方块前缀，服务端调度器在已加载区块内按预算逐批放置。

## 核心状态约定

- `constructionProgress`：已提交的累计工作点，范围 `0..workPointsRequired`。
- `placementCursor`：已确认存在于世界中的模板方块数量，范围 `0..templateBlockCount`。
- `worldSyncState=PENDING`：施工队列仍可能有可放置方块；也可能因工作进度尚未解锁下一批而暂时无操作。
- `worldSyncState=SYNCED`：完整模板均已确认放置。
- `ACTIVE`：工作点完成、escrow 为空、完整模板 SYNCED。

模板保持确定性顺序（当前为 Y、Z、X），因此按前缀施工时自然从较低层向较高层呈现。工作点到方块数映射：

```text
targetBlocks = floor(constructionProgress * templateBlockCount / workPointsRequired)
```

使用 BigInteger 计算，避免乘法溢出；满进度强制映射到全部模板方块。放置游标只向前移动，旧 schema 4 存档中已存在的游标不会回退。

## 生命周期

1. `planBuilding`：加载并校验模板、地形、边界、冲突和区块；只创建 PLANNED 逻辑记录，不写方块。
2. `reserveBuildingMaterials`：全额原子预留材料。
3. `startConstruction`：RESERVED → UNDER_CONSTRUCTION，同时创建唯一 AVAILABLE Builder Job；若尚未放置，队列状态转为 PENDING。
4. Builder 工作提交：通过 expectedProgress compare-and-set 推进工作点并按累计比例扣减 escrow；不直接操作 World。
5. Server END tick：读取施工进度，最多放置配置预算内的目标前缀；只访问已加载区块，不发起 chunk ticket。
6. 完整模板落地：保存 SYNCED 游标；随后激活建筑并在同一 SavedData mutation 中完成 Builder Job。
7. 发生冲突/异常：暂停在 NEEDS_REPAIR，保留 UNDER_CONSTRUCTION、工作点、escrow 和游标；修复冲突后可恢复放置。

M1-006 尚未提供居民工作 tick/寻路，因此当前工作点仍由 OP 调试命令或后续服务调用提交；M1-007 负责将已提交工作点转为可见方块，不把自动寻路/自动工时伪装成已实现。

## 恢复与兼容

- 施工工作点、材料 escrow、placement cursor、worldSyncState、Builder Job 均持久化。
- 放置每个方块后仅在期望方块实际存在时推进游标；重放已放置方块是幂等的。
- 若崩溃发生在最终 SYNCED 保存与 ACTIVE 转换之间，调度器在后续 tick 检测并补做激活。
- 施工中的区块未加载时暂停；重新加载后从持久化游标继续。
- 不新增 SavedData/BuildingRecord 字段，不改变 schema 4，不新增依赖。
- 旧 schema 4 中已存在的 UNDER_CONSTRUCTION + NOT_PLACED 项目会由调度器安全加入 PENDING，不重置已有工作点、escrow 或游标。
- 旧 schema 4 中已完成放置但尚未施工的 PLANNED 项目仍按既有放置路径处理；已有 placement cursor 不会被新算法回退。
- 旧调试命令 `place-building` 仍保留给 PLANNED 项目的显式完整放置验收；正常施工流程不需要先执行它。

## 安全边界

- 所有 World/Level/BlockState 操作仅在服务器主线程。
- 每 tick 使用既有 `BUILDING_PLACEMENT_BLOCKS_PER_TICK` 预算。
- 不强制加载区块；目标区块未加载时暂停。
- 不覆盖不可替换方块、流体（除非蓝图规则允许）或 BlockEntity。
- 不把未完成建筑标记为 ACTIVE。
- 本任务不实现自动寻路、自动分配工人、搬运实体动画、取消退款或损坏修理。

## 本地测试辅助命令

开发阶段可由 OP 使用以下命令向指定势力的可用资源账本追加指定资源（不会覆盖其他资源，也不会自动换算不同资源 ID）：

```mcfunction
/empires debug add-resource <factionUUID> <resourceId> <amount>
```

例如仓库蓝图所需材料可分别追加 `minecraft:oak_planks` 9 个、`minecraft:cobblestone` 9 个。该命令仅用于开发验收，不代表正式经济产出；已预留到建筑 escrow 的材料不在可用账本中。

## GameTests

M1-007 的测试现在覆盖两个层次：

1. 纯映射测试：验证工作点到模板方块前缀的确定性映射、单调性、零进度和满进度边界。
2. 世界集成测试：实际调用 `processPending`，验证零工作不放置、50% 工作只呈现 12/25 个真实方块、受保护方块冲突进入 NEEDS_REPAIR 且游标不前进、清除冲突后恢复、每次放置调用遵守配置预算、100% 工作后完成 25/25 方块并原子激活建筑/完成 Builder Job。
3. 区块安全测试：施工目标位于未加载远端区块时保持 PENDING/cursor=0，不请求强制加载。
4. SavedData 恢复测试：部分施工后序列化并重新加载权威数据，由新的 Repository/Service 从持久化游标继续放置，验证不会重头放置且最终可激活。

**自动化执行记录（用户本地，2026-11-06）：** 用户在提交 `3b65896` 后执行 `gradlew.bat build`，结果 **BUILD SUCCESSFUL in 17s**；随后执行 `gradlew.bat runGameTestServer --console=plain`，日志显示 **50 GAME TESTS COMPLETE / All 50 required tests passed**。这 50 项包括原有 48 项及本专项新增的两项真实世界分批施工/冲突恢复和未加载区块安全测试。编译日志另有 3 条 `ResourceLocation(String, String)` deprecated-for-removal 警告；下一提交改为 Forge 47.4.x 已提供的 `ResourceLocation.fromNamespaceAndPath` 工厂方法。
 
本轮继续新增第 3 项 M1-007 GameTest：在部分放置后序列化并重新加载 `EmpiresWorldData`，由新 Repository/Service 从持久化游标继续实际放置，最终完成并激活。该新增测试尚未包含在用户提供的 50 项结果中，必须在下一次本地 build/GameTests 后才能标记 PASS。

## 客户端验收记录（2026-11-06）

用户在「新的世界2」中完成仓库蓝图真实客户端施工流程：50% 工作点时实际放置 12/25 方块；施工检测到地形冲突后暂停，修复地形并执行恢复命令后继续；推进到 100% 后记录为 `ACTIVE`、`worldSync=SYNCED`、`placementCursor=25`、escrow 为空，关联 Builder Job 为 `COMPLETED`。退出并重新进入世界后，建筑状态/游标仍为上述值，用户确认世界中的仓库方块也完整保留；`/empires debug validate` 为 PASS（0 errors、0 warnings）。

## 后续恢复验证边界

SavedData 序列化/反序列化测试验证逻辑施工进度、游标和 Builder Job 可从持久化数据恢复；这不等价于真实的区块卸载/重新加载。真实 chunk unload/reload 仍需在客户端或专用服务器中让目标 chunk 确实触发卸载事件，再返回使其加载，确认已放置方块未重写、游标不回退且剩余前缀继续施工。

## 验收状态

| 项目 | 状态 |
|---|---|
| 真实客户端分阶段方块施工、冲突恢复、完工激活 | PASS（用户日志及目视确认） |
| 退出/重新进入后建筑逻辑记录与实际方块保留 | PASS（用户日志及目视确认） |
| 原存档 Validator | PASS（0 errors、0 warnings） |
| 50 项 Forge GameTests（含两项世界集成测试） | PASS（用户本地日志，提交 3b65896） |
| 本次新增 SavedData 游标恢复 GameTest | NOT RUN（等待下一轮本地构建与测试） |
| 目标区块真实卸载后重新加载恢复 | NOT RUN（需独立验收，不能由远端未加载测试代替） |
| Dedicated Server / 真实多人 | NOT RUN |

运行新增代码前仍建议备份「新的世界2」及工程目录。测试所用的远端坐标仅存在于 GameTest 临时测试世界，不会写入玩家存档。
