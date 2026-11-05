# 执行提示词：M0-001 初始化 Forge 工程

目标：建立可运行的 Minecraft 1.20.1 Forge 模组工程，不实现任何复杂游戏功能。

规范：
- 先确认仓库是否已有工程，不覆盖现有项目。
- 使用官方 Forge 1.20.1 MDK；当前候选 Forge 47.4.28，下载/构建前复核官方页面。
- Java 17 64-bit。
- 保留 MDK 自带 Gradle Wrapper 与兼容配置，不自行升级 Gradle/ForgeGradle。
- mod id 为 `empires`；正式 Java package 需使用项目方确认的反向域名，未确认前使用清晰标注的临时包名。
- 先不引入 GeckoLib 或其他第三方依赖。

任务：
1. 初始化 Gradle/Forge 工程。
2. 建立 mod 主入口和最小注册。
3. 建立 common/client/server 包边界。
4. 建立基础日志和配置入口。
5. 保留可用的 client 与 dedicated server run configuration。
6. 写出 README 的本地构建说明。

验收：
- `./gradlew tasks` 成功。
- `./gradlew build` 成功。
- 开发客户端启动并进入世界。
- Dedicated Server 启动并创建世界。
- Dedicated Server 不加载 client-only classes。
- Mods 列表显示 empires。

交付：修改文件列表、实际运行的命令、完整测试结果、未运行的测试及原因。不要声称没有运行过的客户端/服务器验证已通过。
