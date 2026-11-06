# M1-004 — ResourceLedger 与原子材料预留

## 决策

采用技术规格 7.2 推荐的 **全额预留到项目 escrow** 策略：

- FactionRecord.resources 表示势力当前可用库存。
- 建筑进入 `RESERVED` 时，一次性从可用库存扣除蓝图全额成本。
- 同额材料写入 BuildingRecord.reservedMaterials，作为项目 escrow。
- 本任务不逐步消费 escrow；施工消耗、取消返还、完成结算由后续施工/取消状态机任务实现。

这样两个项目不能重复使用同一份可用材料，也不会产生负库存。

## 原子事务边界

服务入口：`CivilizationService.reserveBuildingMaterials(buildingId)`。

1. 验证世界数据可写、建筑存在且状态为 `PLANNED`。
2. 验证所属势力存在且 ACTIVE。
3. 验证蓝图存在、版本和 manifest checksum 与建筑记录一致。
4. 从 BlueprintDefinition 读取完整材料成本。
5. 对不可变 ResourceLedger 执行一次性全量减法；任何一种材料不足时直接拒绝，原 ledger 不变。
6. 构造新的 FactionRecord（扣除可用库存）与 BuildingRecord（状态 `RESERVED`、保存全额 escrow）。
7. 经 Repository 的单一 reservation 操作交给 EmpiresWorldData，在一次 SavedData mutation 内校验并发布两个记录和聚落索引，再统一 setDirty。

仓库层再次验证：
- building 必须从 PLANNED 转到 RESERVED；
- escrow 必须恰好等于蓝图的完整材料成本；
- 新 faction balance 必须等于旧 balance 减去 escrow；
- 不允许同时修改 faction 的其他字段或建筑身份/位置字段。

所有调用在服务器主线程执行；中间 immutable replacement 在校验完成前不会发布。SavedData 保存的是同一个世界数据根对象。

## 幂等语义

Building UUID 是此单项目预留事务的稳定 idempotency key：

- 第一次 PLANNED → RESERVED 会扣费并写入 escrow。
- 同一 building UUID 重试时，RESERVED 以及后续已进入施工/活动/损坏生命周期的状态只返回现有记录，不再扣费。
- 不同 building UUID 是不同事务；第二个项目必须重新通过当前可用库存检查。
- NEEDS_REPAIR 和 DEMOLISHED 不允许新发起预留。

当前还没有通用网络 requestId/交易回执表；未来面向玩家/AI 的命令网关仍须把请求 ID 与建筑 ID 结合处理跨请求重放。

## ResourceLedger API

- `contains(required)` 检查全额可负担。
- `minus(cost)` 返回新 ledger；任何材料不足都在原 ledger 不变的情况下抛出明确错误。
- `plus(other)` 使用 `Math.addExact` 检测 long 溢出。
- 账本仍只保存非负整数，零余额条目被移除。

## 校验规则

WorldDataValidator 新增检查：
- PLANNED building 不得携带 escrow。
- RESERVED building 的 escrow 必须等于当前蓝图完整材料成本。

后续当施工开始逐批消耗 escrow 时，需要将 RESERVED 的完整额校验扩展为 UNDER_CONSTRUCTION 等状态的剩余 escrow 合法性校验。

## 调试命令

OP-only：
`/empires debug reserve-building-materials <buildingUUID>`

命令显示预留后的可用库存和项目 escrow。重复执行同一 UUID 不会再次扣费。

## GameTests

新增 7 项（完整回归预计 29 项：M0-012 8 + M1-001 1 + M1-002 5 + M1-003 8 + M1-004 7）：
1. 足额库存：准确扣除并完整保存 escrow。
2. 同一 building UUID 重试幂等，不重复扣费。
3. 缺少任意一种材料时拒绝，所有库存和 BuildingRecord 保持原样。
4. 两个项目竞争库存时，只允许第一个足额预留成功，第二个不出现负库存/部分 escrow。
5. Faction 可用库存和 Building escrow 一起通过 SavedData round trip。
6. Validator 检测 RESERVED escrow 与蓝图成本不匹配。
7. ResourceLedger 加法溢出被拒绝且原 ledger 不变。

## 持久化与范围

- 不增加 SavedData / FactionRecord / BuildingRecord 字段。
- 不改变 schema 版本；继续使用现有 BuildingRecord.reservedMaterials 持久字段。
- 不新增第三方依赖。
- 不实现施工点消耗、取消/退款、竣工结算、玩家物品栏扣费或通用跨请求事务回执；这些留给后续任务。
- 用户现有世界不需要迁移或重建。

## 本地验收状态

**PASS — 29/29 Forge GameTests 与既有存档回归通过。**

用户本地提供的测试日志确认：

- GameTestServer 报告 `29 GAME TESTS COMPLETE`，并明确输出 `All 29 required tests passed :)`。
- 客户端成功进入既有世界「新的世界2」；`/empires debug status` 显示 schema=3、worldId=`6ba6a5f4-e5f9-4e01-bfaa-f86a0aeac64c`、factions=1、settlements=2、citizens=3、readOnly=false。
- `/empires debug validate` 报告 PASS：buildings=2、errors=0、warnings=0，无一致性问题。
- 单人服务器正常保存并退出；日志显示主世界及下界、末地的区块均已保存，Minecraft Empires server lifecycle 正常停止。

验收边界：

- 用户贴出的日志片段未包含 Gradle 的最终 `BUILD SUCCESSFUL` 行，因此**单独的 Gradle build 结果未从该片段确认**；29 项 GameTests 已实际通过。
- Dedicated Server 启动/停止与真实多人权限测试尚未提供结果，仍待专项验收。
- 本任务没有增加 SavedData schema 字段或版本；既有世界 worldId 与 schema 保持不变，无需重建或迁移存档。
