# Minecraft Empires

Minecraft Java Edition 的文明经营、基地建设与即时战略模组。项目愿景是让玩家与 AI 文明在同一套规则下发展、建造、扩张、外交和战争；远距离区域以抽象数据模拟，不依赖持续加载区块。

## 当前技术基线

- Minecraft Java Edition **1.20.1**
- Minecraft Forge **47.4.28**
- Java **17**
- Gradle Wrapper **8.8**
- Mod ID：`empires`
- Java package：`dev.minecraftempires`

> 早期设计草案曾将 NeoForge 作为候选方向；当前工程实际使用 Forge 47.4.28。开发、API 和测试一律以仓库中的 `gradle.properties`、`build.gradle` 和现有源码为准，不要擅自迁移 NeoForge 或升级 Minecraft 版本。

## 当前开发状态

### 已实现并有实际游戏测试记录

- 世界级 `EmpiresWorldData` SavedData，schema 3；保留 world UUID 和既有势力、聚落、居民数据。
- Faction、Settlement、Citizen 逻辑记录及其引用校验。
- Citizen 实体与逻辑身份分离的基础绑定/查询/生成/移除命令。
- M0-008 远程数据模拟调度器：服务器运行时推进，默认不做离线追赶，不要求远程区块加载。当前木材增量仍是**测试信号**，不是正式经济产出。
- M0-009 BlueprintDefinition、BuildingRecord、建筑状态及聚落建筑反向索引。
- M0-010 JSON 蓝图模板校验、SHA-256 内容检查、服务端分批放置、已加载区块限制、持久化 placement cursor、幂等处理。
- 用户已在 Forge 客户端实测：一个 25 方块仓库模板从 `cursor=0` 到 `25/25`、`worldSync=SYNCED`，退出并重新进入同一存档后状态保留；重复放置被拒绝。

### 最新开发中的内容

- M0-011 只读世界数据一致性诊断：`/empires debug validate [page]`。用户于 2026-11-06 在本地完成 `gradlew.bat build`（BUILD SUCCESSFUL）和原存档命令测试（PASS：0 errors、0 warnings）。Dedicated Server 与损坏数据 fixture 仍未测试。

### M1 开发中

- M1-001 已加入大本营核心方块、蓝图物品和 `empires:headquarters_hall` 官方蓝图（7×8×7、204 个方块），并新增注册/模板校验 GameTest；用户于 2026-11-06 本地 build 成功，M0/M1 共 9/9 GameTests 通过。创造物品栏仍待人工确认。
- 大本营核心是受管结构标记方块，不注册可直接放置的 BlockItem；蓝图物品已加入工具与实用物品栏。M1-002 服务端势力唯一性约束已完成；用户于 2026-11-06 本地 build + 14/14 GameTests PASS，原存档 status/validate PASS（0 errors/0 warnings）。Dedicated Server 测试仍待执行。
- M1-003 已加入世界感知放置预检：地形、完整 footprint 的边界/高度、区块加载、方块可替换性、势力归属和既有建筑重叠；新增 8 项 GameTests；用户于 2026-11-06 本地 22/22 GameTests 通过，原存档 status 正常且 validate PASS（0 errors/0 warnings）。本次日志片段未包含 Gradle 最终构建行。M1-004 ResourceLedger 全额 escrow 原子预留与 OP 调试命令已验收：用户本地 29/29 Forge GameTests 通过；「新的世界2」原存档 worldId/schema 不变，status 正常，validate PASS（0 errors/0 warnings）。本次提供的日志片段未显示 Gradle 最终 BUILD SUCCESSFUL 行；Dedicated Server/真实多人测试仍待执行。施工消耗、退款和完成结算留给 M1-005 至 M1-008。详见 [M1-004 验收记录](docs/M1-004-RESOURCE-RESERVATION.md)。

### 明确尚未完成

- M0-011 Dedicated Server 验收、损坏数据 fixture 自动化测试。
- M0-012 集成验收进行中：新增 8 个 Forge GameTests，覆盖 SavedData 完成/中途游标往返、真实模板校验与方块放置、人口缓存、建筑重叠、重复实体 UUID、未来 schema 只读保护；用户于 2026-11-06 已在本地完成 `gradlew.bat build`（BUILD SUCCESSFUL in 38s）和 `gradlew.bat runGameTestServer --console=plain`（8/8 required tests passed，BUILD SUCCESSFUL in 1m 5s）。
- 手工集成验收仍需覆盖中途强制停止恢复、目标区块卸载/重新加载、模板篡改和原存档回归。
- M1：正式大本营、材料预留、施工项目、Builder Job、施工进度、完成/激活、取消与修复。
- M2 及以后：完整职业、资源点、生产仓储、种族、科技、军事 RTS、AI 文明、外交和多人等。

## 开发与运行

在项目根目录（包含 `gradlew.bat`）用 PowerShell 执行：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

Gradle 8.8 目前会提示部分旧 API/插件功能已弃用；这类提示不等于当前构建失败。调查详细警告：

```powershell
.\gradlew.bat build --warning-mode all
```

## 常用 OP 调试命令

```mcfunction
/empires debug status
/empires debug simulation
/empires debug validate
/empires debug validate 2
/empires debug list-blueprints
/empires debug building-count
/empires debug list-buildings <settlementUUID>
/empires debug show-building <buildingUUID>
/empires debug building-placement <buildingUUID>
/empires debug place-building <buildingUUID>
/empires debug resume-building-placement <buildingUUID>
/empires debug reserve-building-materials <buildingUUID>
```

`validate` 只检查已加载的逻辑 SavedData，不读写真实区块或方块，不自动修复存档。详细范围和已知限制见 [M0-011 验收说明](docs/M0-011-WORLD-DATA-VALIDATION.md)。

## 核心架构约束

1. 服务端权威：Client 不能直接修改文明、资源、居民、建筑或军事状态。
2. 逻辑记录是真相：CitizenRecord / BuildingRecord 不依赖实体一直加载。
3. 不为远程 AI 基地使用持续 Chunk Ticket 或强制加载。
4. Minecraft World、Level、Entity 和 BlockState 的访问仅在安全的服务端线程进行。
5. 重要数据变更必须正确标记 SavedData dirty；schema 变化必须有迁移策略。
6. 不覆盖既有存档和玩家建筑；任何修复/重建操作都必须明确、可验证且有恢复方案。
7. 不引入未经说明的第三方依赖。

## 关键文档

- [游戏设计文档](docs/01-GDD.md)
- [系统架构](docs/02-ARCHITECTURE.md)
- [M0-M2 技术规格](docs/03-TECHNICAL-SPEC-M0-M2.md)
- [开发路线图](docs/04-ROADMAP.md)
- [数据契约](docs/05-DATA-CONTRACTS.md)
- [AI 编程助手协作规范](docs/06-AI-AGENT-WORKFLOW.md)
- [M0-010 方块放置适配器](docs/M0-010-BLOCK-PLACEMENT-ADAPTER.md)
- [M0-011 世界数据校验](docs/M0-011-WORLD-DATA-VALIDATION.md)
- [M0-012 集成测试](docs/M0-012-INTEGRATION-TESTS.md)
- [M1-001 大本营内容注册](docs/M1-001-HEADQUARTERS-CONTENT.md)
- [M1-002 大本营唯一性服务](docs/M1-002-CAPITAL-UNIQUENESS.md)
- [M1-003 放置预检查](docs/M1-003-PLACEMENT-PRECHECK.md)
- [M1-004 材料原子预留](docs/M1-004-RESOURCE-RESERVATION.md)

## 存档与测试提醒

运行新代码前先备份工程目录和世界存档。不要为了测试新建筑而删除或重建既有世界。当前已知测试世界为 `新的世界2`；保持原有 world UUID 和建筑记录，是后续兼容性验收的一部分。

本项目仍处于开发原型阶段。README 中的“已实现”不代表所有边界条件、Dedicated Server 或长期性能测试都已通过；请以各任务的实际验收记录为准。
