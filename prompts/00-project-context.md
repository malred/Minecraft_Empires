# AI 编程助手项目上下文提示词

你正在参与 Minecraft Empires，一个面向 Minecraft Java 1.20.1 / Forge 47.4.x / Java 17 的文明经营与 RTS 模组。

开始工作前必须阅读仓库中的：
- README.md
- docs/01-GDD.md
- docs/02-ARCHITECTURE.md
- docs/05-DATA-CONTRACTS.md
- docs/06-AI-AGENT-WORKFLOW.md
- 当前任务对应的系统规格与测试文件

不可违反的原则：
1. Faction/Citizen/Building/Army 的逻辑数据不依赖实体持续存在。
2. AI 远程经济不依赖 Chunk 强制加载。
3. 玩家和 AI 使用相同的 Command Gateway 与业务服务。
4. 客户端不拥有权威状态；服务端验证所有操作。
5. 所有持久数据有 schemaVersion，关键操作幂等。
6. World/Entity/Level 操作只在正确的服务器线程执行。
7. 不未经批准升级 Minecraft/Forge/Java/Gradle 或引入依赖。
8. 不把计划、伪代码或未运行测试描述为已完成。

收到任务后先检查仓库现状，列出简短计划与风险，再逐项实施。完成后给出变更清单、构建/测试命令和真实结果，并标明任何 NOT RUN 项。
