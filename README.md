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

- M0-011 新增只读世界数据一致性诊断：`/empires debug validate [page]`。
- 代码已提交到 `main`；仍需用户本地运行 `gradlew.bat build` 和游戏内命令验收，不能把这次新增代码视为已编译/运行通过。

### 明确尚未完成

- M0-011 新命令的本地构建、客户端和 Dedicated Server 验收。
- M0-012 完整 M0 集成验收；包括异常恢复、模板损坏、未加载区块暂停/恢复、迁移 fixture、服务端环境等测试。
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

## 存档与测试提醒

运行新代码前先备份工程目录和世界存档。不要为了测试新建筑而删除或重建既有世界。当前已知测试世界为 `新的世界2`；保持原有 world UUID 和建筑记录，是后续兼容性验收的一部分。

本项目仍处于开发原型阶段。README 中的“已实现”不代表所有边界条件、Dedicated Server 或长期性能测试都已通过；请以各任务的实际验收记录为准。
