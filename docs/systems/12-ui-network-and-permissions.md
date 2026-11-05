# 系统规格：UI、网络与权限

## UI 原则
HUD 与管理界面读取 server snapshot；Client 不直接查询/修改 SavedData。UI 显示资源、人口、建筑队列、警报和军队状态；近距离世界交互仍可使用 Minecraft 常规输入。

## 初期界面
- 文明总览。
- 蓝图选择/放置预览。
- 建筑详情和工作岗位。
- 居民分配。
- 军队选择和编队（M4）。
- 世界地图（后续）。

## 网络命令
所有 C2S 请求仅表达意图：PlaceBlueprint、AssignProfession、SetPriority、IssueArmyOrder 等。服务端校验后执行，发送结果/增量快照。

## 权限
玩家必须属于 Faction 或拥有管理员权限。每个对象检查 ownerFactionId。多人模式不能相信客户端传来的 factionId；应从 server player profile/session 中解析归属。

## 速率与安全
限制请求频率、列表长度、坐标范围和模板大小。所有 ID 必须存在。防止重放 requestId。世界编辑命令校验 chunk 已加载，不接受客户端要求服务器无限加载区块。

## 验收
伪造包不能修改他人库存/建筑；合法命令可以重连恢复；UI 不展示未探索势力的隐藏资源；Dedicated Server 不加载任何 client-only class。
