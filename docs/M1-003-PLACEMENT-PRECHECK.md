# M1-003 — 大本营与建筑放置预检查

## 目标

把“逻辑规划”与“实际写入世界方块”之间的安全边界前移：通过完整预检后才创建 BuildingRecord；预检本身不写方块、不扣资源、不加载远程区块。实际开始放置时仍会再次检查，防止规划后地形或区块状态变化。

## 服务端入口

`BuildingPlacementService.planBuilding(...)` 接收 ServerLevel、CivilizationService、调用方提供的预期 faction ID、settlement ID、blueprint ID、origin、rotation 和 gameTime。

- 先核验预期 faction 与 settlement 的归属一致。
- settlement 必须 ACTIVE 且位于当前维度；faction 必须 ACTIVE。
- blueprint 必须存在并允许指定旋转。
- 载入模板并校验模板 checksum/尺寸。
- 完整预检成功后才调用 `CivilizationService.planBuilding` 落盘；该服务再次执行逻辑占地重叠、HQ 唯一性和首都聚落约束。
- 若任何预检失败，不创建建筑记录。

调试命令 `/empires debug plan-building` 是 OP-only 管理命令，因此从服务器 SavedData 读取目标 settlement 的 ownerFactionId 作为预期归属。未来玩家/AI 命令网关必须传入经过服务端认证的 faction ID，不能信任客户端自报值。

## 预检规则

1. **势力与聚落归属：** 预期 faction ID 必须等于 settlement.ownerFactionId；双方记录存在且为 ACTIVE；聚落维度与当前 ServerLevel 一致。
2. **旋转和模板：** blueprint 存在、旋转合法，模板尺寸与定义匹配，SHA-256 内容 checksum 通过。
3. **地形：** 对 requireSolidGround 蓝图，整个旋转后占地的每个支撑位置都必须有坚实向上支撑面。
4. **世界边界：** 检查完整 footprint 的每一格，而不是只检查模板中实际有方块的位置。
5. **建造高度：** 检查声明的完整垂直体积不越过 level 的 minBuildHeight/maxBuildHeight。
6. **区块：** 目标模板方块与支撑位置都必须位于已加载区块；只使用 `hasChunkAt` 观察，不申请 Chunk Ticket，也不强制加载。缺少区块时拒绝本次规划，玩家靠近后重试。
7. **方块冲突：** 遵循 blueprint 的 ProtectedBlocksPolicy；拒绝液体（除非定义允许）、BlockEntity 和不可替换方块。
8. **建筑占地：** 预检通过后由 CivilizationService 再次检查所有未拆除 BuildingRecord 的 footprint 重叠；同一服务还执行 M1-002 大本营唯一性检查。

## 放置与事务边界

- 成功预检会创建一条 `PLANNED / NOT_PLACED` 记录，返回模板方块数和 checksum。
- 成功预检不会放置任何方块，也不会扣除/预留任何资源。
- 后续 `beginPlacement` 仍会完整重查世界边界、建造高度、支撑地面、加载区块和目标方块状态。
- 分批放置期间继续使用既有 loaded-chunk-only 规则；区块卸载时暂停，不请求加载。
- 本任务不实现材料扣除、资源预留、施工或回滚；这些属于 M1-004 及之后。

## GameTests

新增 8 项 Forge GameTests，覆盖：
- 无坚实地基时拒绝，且不创建记录/不写方块。
- footprint 越过世界边界时拒绝。
- 声明体积越过建造高度时拒绝。
- 目标位置有不可替换方块时拒绝并保留原方块。
- expected faction 与 settlement owner 不匹配时拒绝。
- 与已有逻辑建筑 footprint 重叠时拒绝且不创建第二条记录。
- 远处区块未加载时拒绝且不强制加载。
- 预检成功会生成 PLANNED/NOT_PLACED 记录，但不会提前放置方块。

## 当前限制与未覆盖

- 当前玩家/AI 的正式身份命令网关尚未实现；OP-only debug 命令从 SavedData 读取 settlement owner。未来所有非管理员入口必须将已认证 faction ID 传入服务。
- 真实玩家建筑/保护模组的跨模组权限 API 尚未接入。当前按 Minecraft BlockState 可替换性、FluidState、BlockEntity 和逻辑 BuildingRecord footprint 保护。
- Dedicated Server、真实客户端及多人权限测试待执行。
- 本任务不更改 SavedData schema，不触碰用户旧存档，不新增依赖。

## 本地验收记录（2026-11-06）

<badge>PASS</badge> 用户本地实际日志确认：

- Forge GameTestServer：**22/22 required tests passed**，GameTestServer 正常保存三个维度并关闭。
- 原存档「新的世界2」成功进入；status 显示 `schema=3`、`worldId=6ba6a5f4-e5f9-4e01-bfaa-f86a0aeac64c`、`factions=1`、`settlements=2`、`citizens=3`、`readOnly=false`。
- `/empires debug validate` 最终返回 PASS：`factions=1, settlements=2, citizens=3, buildings=2, errors=0, warnings=0`，并显示无一致性问题。
- 日志中第一次输入 validate 时出现一次“未知或不完整的命令”，随后命令重新执行成功；最终结果为 PASS。
- 用户提供的日志片段未包含 Gradle 最终 `BUILD SUCCESSFUL` 行，因此本文只确认上述 GameTest 与原存档回归，不把未展示的构建状态单独记为 PASS。
- Dedicated Server 与真实多人权限仍未执行。

M1-003 的 GameTest 与原存档回归条件已通过；本任务可标记 PASS。构建结果以完整 Gradle 终端输出另行确认。

