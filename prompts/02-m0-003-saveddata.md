# 执行提示词：M0-003 世界级 SavedData

目标：实现 Minecraft Empires 的世界级文明数据入口。

必须先阅读：README、ARCHITECTURE、TECHNICAL-SPEC-M0-M2、DATA-CONTRACTS、testing/01-acceptance-matrix。

要求：
- 适配 Forge 1.20.1 的 SavedData API，不照搬更新版 API。
- 数据入口挂载在 Overworld，避免跨维度重复生成全球 Faction registry。
- 包含 schemaVersion、worldId、factionIds 索引和 simulationClock。
- 所有修改通过 Repository/Service；变更后正确标记 dirty。
- 提供最小 NBT 保存和读取。
- 处理首次创建、已有存档、未知 schema 和损坏数据。
- 不实现完整 Faction、Citizen 或 AI 系统，除非为了最小测试需要占位。

测试：新建世界、创建/修改数据、保存关闭重启、验证字段一致；切换维度不重复创建 registry；确保客户端不持有权威 SavedData。

交付：实现、测试、build 结果、API 依据链接、未解决风险。不得把内存测试描述为重启存档测试。
