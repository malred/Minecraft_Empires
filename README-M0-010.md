# Minecraft Empires — M0-010 Block Placement Adapter v0.1.0

本补丁建立在已经应用并通过 M0-009 验收的工程之上。

目标环境：Minecraft 1.20.1 / Forge 47.4.28 / Java 17。

## 变更目标

把 `empires:warehouse_prototype` 从逻辑蓝图转为真实 Minecraft 方块结构，同时保持服务器权威、已加载区块限制、可恢复进度和幂等重试。

## 本版能力

- 新增 `BlueprintTemplate`：从数据资源包读取 3D JSON 模板；校验 schema、ID、尺寸、调色板索引、重复坐标、方块数量和 SHA-256。
- 新增官方仓库模板资源：3×3×3、25 个方块，圆石地板、橡木板墙和屋顶，入口留空。
- 新增 `BuildingPlacementService`：放置前验证维度、模板版本/manifest checksum、构建高度、世界边界、已加载区块、可替换规则和完整地面支撑。
- 新增 `BuildingPlacementScheduler`：在服务端 END Tick 分批放置，默认每 Tick 最多 8 个模板方块。
- 新增 `/empires debug place-building <buildingUUID>`、`resume-building-placement` 和 `building-placement`。
- BuildingRecord 内部 schema 1 → 2，新增持久化 `placement_cursor`；世界级 SavedData Schema 仍为 3。
- 如果放置时区块卸载，任务保持 PENDING 并暂停；不会 force-load 或请求远程区块。
- 若检测到不安全方块、错误模板或中途冲突，建筑进入 `NEEDS_REPAIR`，不会覆盖冲突方块。

## 明确不做

- 不扣除蓝图材料，也不预留资源；这是 M1 资源 escrow/施工阶段的工作。
- 不创建工人，不推进施工点数；方块放置与建筑施工进度仍是两个不同系统。
- 不支持模板 BlockEntity NBT、复杂 BlockState 属性、门/床等多方块连接态；本模板仅使用普通方块 ID。
- 不自动覆盖玩家修改的建筑，也不在区块加载时自动重建建筑。
- 不改变居民、势力、聚落或 M0-008 模拟逻辑。

## 应用

1. 关闭 Minecraft。
2. 备份项目目录和原存档 `新的世界2`。
3. 确认 M0-009 补丁已应用，并且 `/empires debug status` 显示 `schema=3`、`readOnly=false`。
4. 解压本 ZIP 到含 `build.gradle` 的工程根目录，允许覆盖清单中的文件。
5. 执行 `./gradlew.bat build`；通过后执行 `./gradlew.bat runClient`。
6. 使用 `TEST-STEPS-ZH.md` 验收 M0-010，并执行 M0-008/M0-009 回归测试。

本补丁尚未在用户 Windows Forge 环境编译或运行；必须以本地 Gradle 和游戏日志为准。
