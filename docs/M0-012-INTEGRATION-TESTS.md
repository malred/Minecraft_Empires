# M0-012 — M0 Integration Test Suite

## 目标

将 M0-008、M0-009、M0-010 与 M0-011 的关键数据契约纳入 Forge 1.20.1 GameTest 自动化测试，并保留需要完整客户端/重启/区块生命周期才能验证的人工验收项。

目标环境固定以实际工程为准：Minecraft 1.20.1 / Forge 47.4.28 / Java 17。

## 自动化测试

当前共 8 个必需 GameTest，均属于 `empires.m0_012` batch。实际 placement 集成测试在隔离结构范围内创建 3×3 石质地基并放置官方仓库模板，使用注入的内存 SavedData repository，不会向 GameTestServer 的持久 SavedData 写入模拟势力/建筑。

测试类：

`src/main/java/dev/minecraftempires/common/civilization/M0IntegrationGameTests.java`

使用 Forge/Minecraft 内置 GameTest 框架，不增加第三方依赖。测试使用 `minecraft:empty` 结构模板，所有数据测试都在内存中构建独立 `EmpiresWorldData`，不打开、覆盖或删除用户的 `新的世界2`。

| GameTest | 覆盖内容 | 预期 |
|---|---|---|
| `savedDataRoundTripPreservesWorldAndBuilding` | SavedData NBT save/load；世界 UUID、势力/聚落/居民/建筑数量、建筑 UUID、已完成 placement cursor | 重载后仍可写、校验 PASS、无 WARNING |
| `pendingPlacementCursorSurvivesSavedDataRoundTrip` | PENDING 状态和 cursor=8 的中途放置记录 NBT 往返 | 游标、PENDING 状态和 PLANNED 逻辑状态保持 |
| `officialWarehouseTemplateLoadsAndMatchesChecksum` | 从服务器 ResourceManager 读取官方 JSON 模板 | 3×3×3、25 个 block entries、SHA-256 与 catalog 一致 |
| `validatorReportsPopulationCacheMismatch` | 构造 ACTIVE CitizenRecord 与错误人口缓存 | 报告 1 个 WARNING，不报告结构 ERROR |
| `validatorDetectsOverlappingBuildings` | 构造占地相交的两条建筑记录 | 报告 1 个 ERROR |
| `validatorDetectsDuplicateCitizenEntityLinks` | 两名逻辑居民绑定相同 Entity UUID | 报告 1 个 ERROR |
| `futureSchemaIsPreservedReadOnly` | 加载高于当前 schema 的模拟 NBT | 进入只读模式，未知字段及 schema 原样保留 |

这些测试不扫描实时 Entity，也不访问/修改结构模板之外的世界方块。

## 运行

在 Windows PowerShell 的项目根目录执行：

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
```

Gradle 的 GameTestServer 使用独立工作目录：

`run-gametest`

该目录已被 `.gitignore` 的 `run-*/` 规则忽略，不与普通 `run/` 单人游戏目录共用。GameTestServer 会运行 `empires.m0_012` batch 中的必需测试并在结束时退出。要求退出码为 0，日志中所有 required tests 均通过。

若失败，请提供：
- 第一个 GameTest failure；
- 对应异常堆栈；
- GameTestServer 日志末尾；
- `BUILD FAILED` 附近的 Gradle 输出。

不要为了让测试通过而删除断言或改变正式数据校验规则。

## 仍需人工验收的项目

GameTest 不替代下列真实运行场景：

1. 原存档 `新的世界2`：运行 `/empires debug status` 与 `/empires debug validate`，确认 worldId 不变、schema=3、readOnly=false、原有 Faction/Settlement/Citizen/Building 数量不变。
2. 原存档回归 M0-008 远程模拟：确认游戏时间运行时推进、资源测试信号按周期变化、`worldAccess=DATA_ONLY`，且无强制加载票据。
3. M0-010 正常放置：真实方块出现在正确坐标，cursor 达到 25/25、worldSync=SYNCED，重复执行受保护。
4. M0-010 中途终止恢复：必须在 cursor 小于 25 的情况下中断 GameTest/客户端进程，再启动后确认从已保存 cursor 继续；当前单机 Tick 速度很快，不能把放置开始后立即退出当作中途恢复测试。
5. 区块卸载暂停/恢复：待目标区块未加载时任务暂停，不强制加载；重新进入后恢复。
6. 篡改/损坏蓝图 JSON：模板 checksum 不匹配时拒绝放置，不修改目标方块。
7. Dedicated Server：GameTestServer 启动本身提供自动化专用服务器环境；但仍需独立执行普通 `runServer` 启动/停服验证。

## 当前验收状态（2026-11-06）

- [x] 八个 GameTest 方法已加入源码。
- [x] GameTestServer 采用独立 `run-gametest` 工作目录。
- [x] 不增加第三方依赖。
- [ ] 用户本地 `gradlew.bat build`：等待本轮新测试代码后运行。
- [ ] 用户本地 `gradlew.bat runGameTestServer`：等待运行。
- [ ] 原存档完整回归：部分项目已通过，仍需本轮回归确认。
- [ ] 中途强制中断/区块卸载/模板损坏：NOT RUN。
- [ ] 普通 Dedicated Server `runServer`：NOT RUN。

注意：M0-011 已由用户于 2026-11-06 实际执行 `gradlew.bat build` 并在原存档运行 `/empires debug validate`，获得 0 errors / 0 warnings。新增的 M0-012 GameTests 尚未在用户环境运行，不得提前标记通过。
