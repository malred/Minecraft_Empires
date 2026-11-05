# M0-010 + M0-008/M0-009 回归测试步骤（中文）

## 一、应用前准备

1. 关闭 Minecraft 客户端。
2. 备份整个 `MinecraftEmpires` 工程目录。
3. 备份原世界 `新的世界2`。不要新建世界代替它，也不要删除旧存档。
4. 确认你已经应用并测试 M0-009。本补丁是增量补丁，不能直接覆盖在只有 M0-008 的旧工程上。
5. 将本 ZIP 解压到包含 `build.gradle` 的项目根目录，允许覆盖清单中的文件。

## 二、编译

```powershell
Set-Location "D:\code-d\projects\mc\Minecraft_Empires_M0_001_Starter\MinecraftEmpires"
.\gradlew.bat build
```

必须看到 `BUILD SUCCESSFUL` 后再启动客户端：

```powershell
.\gradlew.bat runClient
```

如果失败，先发回第一个 `error:`、`What went wrong` 和相关文件行号，不要自行删除代码绕过错误。

## 三、M0-008 远距离模拟回归

进入原来的 `新的世界2`，执行：

```mcfunction
/empires debug status
/empires debug simulation
/empires debug show-resources c2a593a0-ac36-4f90-8bbf-dd0cb5660a32
```

记录当前木材值。然后远离 AI 聚落（约 x=2000,z=2000），例如：

```mcfunction
/tp @s 5000.5 200 5000.5
```

保持客户端和集成服务器运行至少 150 个游戏 Tick，再执行：

```mcfunction
/empires debug simulation
/empires debug show-resources c2a593a0-ac36-4f90-8bbf-dd0cb5660a32
/forceload query 2000 2000
```

预期：

- `simulationClock` / `lastProcessedGameTime` 向前推进。
- `worldAccess=DATA_ONLY`。
- AI 木材按完成周期增长（测试值默认每周期 +1）。
- `/forceload query 2000 2000` 显示没有被标记为强制加载。

保存退出，完全关闭客户端，再次 `runClient` 进入同一世界。确认服务器启动日志重新建立 baseline；停服期间没有补发木材。

## 四、M0-009 建筑数据回归

执行：

```mcfunction
/empires debug status
/empires debug building-count
/empires debug list-blueprints
/empires debug list-buildings cca48d0a-89af-4b8c-b651-b612390baaf3
/empires debug show-building e28b1b7c-f309-4d26-afb3-6635245fcaaf
```

原来的世界级数据应仍为 Schema 3、`readOnly=false`，原 Faction/Settlement/Citizen 和建筑 UUID 不变。原来的测试建筑是在 y=121 做的逻辑规划；若它处于空中，请保留 `PLANNED / NOT_PLACED`，不要直接拿它做真实方块放置测试。

## 五、M0-010 实际方块放置

### 1. 创建新的测试蓝图记录

切换创造模式：

```mcfunction
/gamemode creative
```

传送到远程聚落附近一个空旷、平坦且有完整地面支撑的位置。建议在 x=2015,z=2005 附近寻找，和旧测试蓝图 x=2005,z=2005 保持距离。降落到地面，确保玩家脚下是计划放置结构的底层高度。

在地面站稳后执行：

```mcfunction
/empires debug plan-building cca48d0a-89af-4b8c-b651-b612390baaf3 empires:warehouse_prototype 90
```

复制命令返回的新 Building UUID。初始状态应为 `PLANNED`、`NOT_PLACED`、cursor=0。

规划坐标是结构西北角的底层方块位置。规划完成后先离开 3×3 占地范围（例如向东移动 5 格），不要站在即将生成的地板上；同时留在附近，确保整个结构区块仍处于加载状态。

### 2. 开始放置

确保整个 3×3×3 结构所在区块已经加载，然后执行：

```mcfunction
/empires debug place-building 你的新BUILDING_UUID
```

默认每 Tick 放置最多 8 个方块。模板共 25 个方块，通常 4 个 Tick 左右完成。等待约 1 秒后查询：

```mcfunction
/empires debug building-placement 你的新BUILDING_UUID
/empires debug show-building 你的新BUILDING_UUID
/empires debug show-resources c2a593a0-ac36-4f90-8bbf-dd0cb5660a32
```

预期：

- `state=PLANNED`（M0-010 只证明放置，不代表施工完成）。
- `worldSync=SYNCED`。
- `placementCursor=25/25`。
- 世界里出现 3×3 圆石地板、橡木板墙体和屋顶；正面中央留有入口，rotation=90 时入口方向随蓝图旋转。
- 不扣除木材、石材或橡木板等逻辑材料；资源预留/扣除属于后续 M1。

再次执行 `place-building`，应被拒绝，因为建筑已经 SYNCED，不会重复放置。

随后检查：

```mcfunction
/forceload query 2015 2005
/empires debug simulation
```

确认没有强制加载票据，M0-008 模拟仍然运行。

### 3. 重启持久化

保存退出并完全关闭客户端，再次运行 `runClient`，进入 `新的世界2`：

```mcfunction
/empires debug status
/empires debug building-count
/empires debug building-placement 你的新BUILDING_UUID
/empires debug show-building 你的新BUILDING_UUID
/empires debug simulation
```

预期：同一 Building UUID、`SYNCED`、cursor=25/25 保持不变，结构仍在，不会重放或复制；世界根 Schema 仍是 3，`readOnly=false`。M0-008 时钟在启动时建立新 baseline，没有离线木材补发。

## 六、区块暂停/恢复的可选测试

仅使用单独的新测试建筑：放置开始后让目标区块卸载。scheduler 应暂停，不得强制加载；返回并重新加载区块后继续。不要对已完成的正式测试建筑做破坏性试验。

若出现 `NEEDS_REPAIR`，先查看错误原因并清除冲突方块，再确保目标结构区块全部加载，执行：

```mcfunction
/empires debug resume-building-placement 你的BUILDING_UUID
```

适用于可恢复的方块冲突；不要用它绕过未知蓝图版本或模板 checksum 错误。

## 七、请发回这些结果

1. `gradlew.bat build` 结尾。
2. 初次加载原世界的 `/empires debug status`。
3. M0-008 两次 simulation 状态、木材前后数值和 `/forceload query`。
4. 新建筑的 plan-building 和 place-building 返回信息。
5. `building-placement` 查询结果与建筑外观截图（可选）。
6. 重启后的 building-placement 和 simulation 结果。

所有尚未运行的专用服务器、恢复注入和边界测试都先记为 NOT RUN，不把它们提前算作通过。


## 八、M0-011 世界数据一致性诊断

在最新 M0-011 代码中，增加以下只读诊断命令：

```mcfunction
/empires debug validate
/empires debug validate 2
```

默认显示第 1 页，每页最多 12 条诊断；第二个参数是页码。摘要会列出 Faction、Settlement、Citizen、Building 数量，以及错误/警告数。若没有问题，应显示 `World validation: PASS` 和 `No consistency issues found.`。

校验内容包括：势力首都引用、聚落所有者、聚落与建筑双向索引、居民所有者/住所引用、重复实体 UUID 链接、ACTIVE 人口缓存、建筑所有者/维度/聚落、蓝图 ID/版本/manifest checksum/旋转、放置游标范围，以及同维度建筑占地重叠。

该命令不加载区块、不扫描 Minecraft 实体、不检查真实方块外观、不自动修复数据。实体 UUID 只检查逻辑记录之间是否重复；不因目标实体未加载就判定居民丢失。物理模板 JSON 的完整 SHA-256 验证仍由 `building-placement` 查询/放置路径执行。

执行后请保存：命令输出全部页、`/empires debug status`、`gradlew.bat build` 末尾。若出现 ERROR，不要手工编辑 NBT 或删除记录，先把完整诊断发回。
