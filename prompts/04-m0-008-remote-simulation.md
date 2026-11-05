# 执行提示词：M0-008 远距离抽象模拟

目标：证明 AI 势力在其基地 Chunk 未加载时仍能进行纯数据模拟。

范围：实现简化 SimulationScheduler 和一个可重复测试的资源累积模拟；不做完整 Utility AI、战斗和单位实体化。

要求：
- 使用服务端 gameTime 和 interval。
- 保存 lastProcessedGameTime/已结算周期，避免重启重复入账。
- 每轮有可配置预算。
- 输入是纯数据快照，不能访问未加载 World/BlockState/Entity。
- 不创建 Chunk Ticket。
- 默认服务器关闭期间不累积离线资源。
- 通过 debug 命令可查看最后处理时间、待处理队列和模拟统计。

测试：将 AI 记录放在远离玩家的位置，确认对应 Chunk 未加载；服务端运行期间库存增长；重启不重复结算；Chunk Ticket 数量不因模拟增长。
