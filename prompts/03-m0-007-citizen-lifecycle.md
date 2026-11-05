# 执行提示词：M0-007 Citizen 实体生命周期

目标：实现 CitizenRecord 与实体的稳定绑定、实体化、休眠和对账。

必须遵守：CitizenId 是逻辑身份；Minecraft Entity UUID 是运行时实体标识，不可混为一谈。CitizenRecord 是身份真相。不能因 chunk unload 或 Entity 缺失判定死亡。

实现：
- CitizenRecord repository。
- NPC Entity 持有 CitizenId NBT。
- CitizenLifecycleService。
- spawn lease/防并发重复生成机制。
- entity 加载注册和状态回写。
- 休眠前保存状态并释放任务锁。
- reconciliation 检查同 CitizenId 重复实体。

线程：所有实体创建、删除和 World 操作由服务器线程执行。

测试：spawn/save/reload、hibernate/realize、重复实体注入、死亡后不复活、spawn 失败释放 lease、玩家离开但 chunk 尚加载的状态切换。

先给出状态机和关键崩溃窗口处理，再编码。不得为了通过测试而通过静态 Map 假装跨重启持久化。
