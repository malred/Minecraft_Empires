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

新增 M1-007 测试覆盖工作点到方块数量的确定性映射、单调性、零进度不放置及满进度到达完整模板。用户于 2026-11-06 在提交 `632101a` 本地运行 `gradlew.bat build`，结果 `BUILD SUCCESSFUL`；Forge GameTests 为 **48/48 required tests passed**。同一版本客户端成功加载旧存档 schema 4，worldId 不变、readOnly=false，世界校验 PASS（0 errors/0 warnings）。

## 验收状态

分阶段放置实现及其 48 项自动化测试已通过上述本地验证；真实游戏中的新建筑分阶段外观、冲突恢复、Chunk 卸载/重载以及 Dedicated Server 仍未完成验收。随后添加的 `add-resource` OP 调试命令尚未由用户重新 build/GameTests 验证。运行新代码前备份「新的世界2」及工程目录。
