# M1-005 — ConstructionProject 状态机

## 目标与边界

本任务把 M1-004 的 RESERVED 材料预留推进为可持久化的施工项目。施工项目仍以现有 BuildingRecord 为唯一持久化聚合，不另建平行项目表，也不增加 SavedData 或 BuildingRecord schema 字段。

本任务实现逻辑施工进度和生命周期门槛；不实现 Builder Job、工人寻路、分阶段方块出现、取消退款、修复和损坏生命周期。这些分别留给 M1-006、M1-007、M1-008。

## 状态转换

本任务允许的主要施工路径：

PLANNED → RESERVED → UNDER_CONSTRUCTION → ACTIVE

- PLANNED → RESERVED 仍只能由 M1-004 的原子材料预留操作执行。
- RESERVED → UNDER_CONSTRUCTION 通过 CivilizationService.startConstruction(buildingId) 开始。项目必须有完整初始 escrow，且当前物理放置状态为 NOT_PLACED 或 SYNCED。
- UNDER_CONSTRUCTION → UNDER_CONSTRUCTION 通过正数 work points 更新累计进度和 escrow。
- UNDER_CONSTRUCTION → ACTIVE 只有在进度达到 workPointsRequired、escrow 清空且 worldSyncState=SYNCED 时允许。
- 如果工作已完成但方块尚未同步，项目保持 UNDER_CONSTRUCTION、进度封顶且 escrow 为空；待世界同步后调用 activateCompletedBuilding。

placementCursor 与 constructionProgress 是独立指标：前者记录模板写方块位置，后者记录施工工作点。不能用其中一个替代另一个，也不能仅凭模板放置完成就将建筑视为 ACTIVE。

## 材料 escrow 消耗规则

M1-004 在预留时已从 Faction 可用库存扣除完整蓝图成本。施工过程中不再修改 Faction 库存，而是逐步减少 BuildingRecord 的 escrow。

每种材料按累计施工比例计算已经消耗的总量：

    consumed = floor(totalCost × completedWork / totalWork)
    remainingEscrow = totalCost - consumed

这里根据累计进度计算，不按每一批 work points 单独四舍五入；因此分批大小或重试顺序不会改变最终材料总量。乘法使用 BigInteger，避免 long 乘法溢出。达到 100% 进度时 escrow 必须为空。

## 幂等与并发

applyConstructionWork(buildingId, expectedProgress, workPoints) 使用 expectedProgress 作为 compare-and-set 令牌：

- 工作批次必须有正数 work points。
- 若当前进度等于 expectedProgress，服务应用该批工作并原子保存新进度与对应 escrow。
- 同一 expectedProgress 和 workPoints 的已提交批次重试时，返回相同的持久化结果，不重复增加进度或消耗材料。
- 使用旧 expectedProgress 提交不同批次时，拒绝为 stale batch，记录和 escrow 不变。
- 超出剩余项目工作量的请求会封顶到 workPointsRequired，不会溢出或超过项目上限。

这是项目内的进度比较令牌，不是通用网络 requestId 收据系统；未来网络 Command Gateway 仍需按数据契约实现 requestId 防重放。

## SavedData 一致性

通过 Repository 的 saveConstructionUpdate(expected, updated) 写入。EmpiresWorldData 在单次服务器线程 mutation 中：

1. 确认持久化记录与 expected 快照仍一致，拒绝过期更新。
2. 确认建筑身份、蓝图、位置、模板 checksum、work point 总量等不可变字段没有变化。
3. 验证状态转换、进度单调性和更新后 escrow 与累计进度匹配。
4. 对 ACTIVE 再验证进度已完成、escrow 为空且 worldSync 已 SYNCED。
5. 发布新 BuildingRecord 和 settlement reverse index，最后统一 setDirty()。

Faction 已在 M1-004 预留事务中扣除；施工工作不再触碰 Faction 账本，避免重复扣费。

## Validator 规则

- PLANNED：progress 必须为 0，escrow 必须为空。
- RESERVED：progress 必须为 0，escrow 必须等于蓝图全额成本。
- UNDER_CONSTRUCTION：escrow 必须等于按累计 work points 计算的剩余材料。
- ACTIVE：必须达到完整 work points、escrow 为空且 worldSync 为 SYNCED。
- Blueprint workPoints 与 BuildingRecord.workPointsRequired 必须一致。

已完成但世界仍未同步的 UNDER_CONSTRUCTION 项目是合法等待状态。若工作完成且方块已同步但仍未显式激活，Validator 会给出 warning 供恢复流程处理。

## OP 调试命令

仅限 OP：

    /empires debug start-construction <buildingUUID>
    /empires debug apply-construction-work <buildingUUID> <expectedProgress> <workPoints>
    /empires debug activate-building <buildingUUID>

示例：某项目当前 progress 为 0，提交 25 work points：

    /empires debug apply-construction-work <buildingUUID> 0 25

重复执行相同的 expectedProgress=0, workPoints=25 批次是安全的。新的工作批次必须先读取最新 progress，并将其作为 expectedProgress。调试命令只是 M1-005 的验收入口；真实居民工人和任务调度将在 M1-006 接入。

## GameTests

新增 8 项（完整回归预计 **37 项**：此前 M0/M1 已通过 29 项 + 本任务 8 项）：

1. 开工只接受 RESERVED，转换重试不重复扣费。
2. 工作按累计比例消耗 escrow；相同批次重试幂等。
3. 旧进度令牌不能提交不同的新工作批次。
4. 超大工作量封顶到总工作点，且不发生 long 溢出。
5. 工作完成但方块未 SYNCED 时不能激活；同步后可激活。
6. 方块已 SYNCED 的项目在最后一批工作完成时转为 ACTIVE。
7. 部分进度和 escrow 经 SavedData round trip 保持一致且 Validator 通过。
8. Validator 检测施工中 escrow 与累计进度不匹配。

## 持久化与未覆盖范围

- 不增加 SavedData schema 字段，不增加 BuildingRecord 字段，不改 schema=3。
- 不改变 M1-004 的全额预留语义；施工只减少项目 escrow。
- 不新增第三方依赖。
- 本任务未实现取消/退款、工人任务、施工方块分阶段呈现、损坏修复和 Dedicated Server 测试。
- 用户本地世界「新的世界2」不需要重建或迁移。

## 本地验收状态

**待本地验收。** 代码与 8 项 Forge GameTests 已提交开发分支。需用户执行完整 build/GameTest 回归及既有存档 status/validate 检查后，再记录 PASS。助手环境未运行 Forge Gradle build、GameTestServer 或 Minecraft 客户端。
