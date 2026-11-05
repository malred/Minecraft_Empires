# 存档、迁移与故障恢复测试

## 保存范围
测试世界级 Faction、Settlement、CitizenRecord、BuildingRecord、Inventory、JobRecord、ResearchProject、ArmyGroup、BattleSnapshot 和 schemaVersion。

## 故障注入
- 在材料预留后强制关闭服务器。
- 在施工进度更新后强制关闭。
- 在实体 Spawn Lease 创建后、实体加入世界前关闭。
- 在实体休眠写回后、discard 前关闭。
- 在抽象战斗结算中途关闭。
- 在资源交易扣款与交付之间关闭。

## 恢复要求
重启后执行 reconciliation：未完成 reservation 要么恢复项目要么释放；spawn lease 过期后可重新尝试；已提交 transaction 不得再次执行；未提交 transaction 按恢复策略重放或回滚；战斗结果只能应用一次。

## 迁移
每次 schema 增加 fixture：旧存档 → 新版本加载 → migration → 关键 ID 和引用检查 → 保存 → 再加载。未知更高 schema 时拒绝写入并备份日志。

## 验收记录模板
- 测试 ID：
- 模组版本：
- Forge/Java：
- 世界 seed：
- 初始存档 hash：
- 故障注入点：
- 预期：
- 实际：
- PASS/FAIL：
- 日志/附件：
