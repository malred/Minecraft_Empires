# M0-001 工程骨架交付报告

**状态：** 源代码骨架已生成；Forge 构建/游戏启动尚未验证。

## 已交付

- Forge 1.20.1 / 47.4.28 / Java 17 Gradle 工程配置 overlay。
- 主 mod entrypoint `@Mod("empires")`。
- Common Config 注册。
- Client-only MOD event subscriber，使用 `Dist.CLIENT` 限定。
- Server lifecycle event subscriber，不引用客户端类。
- `mods.toml` 与资源目录。
- 官方 MDK 引导脚本：下载 MDK、校验 SHA-1、保留官方 Wrapper，再覆盖本项目代码。
- GDD、架构、M0–M2 规格、系统文档、测试文档和 AI 提示词。

## 未验证项目

当前生成环境没有安装 Gradle，运行时只有 OpenJDK 21，且无法从容器内访问 Forge Maven 下载 MDK。因此没有执行：

- `./gradlew tasks`
- `./gradlew build`
- Forge 开发客户端启动
- Dedicated Server 启动

这四项必须在装有 JDK 17 且能访问 Forge Maven 的本地机器上，运行 bootstrap 脚本组装官方 MDK 后完成。不得将本交付报告视为构建通过证明。

## 版本来源

Forge 官方 1.20.1 下载页在本次制作时列出 47.4.28，并列出 MDK SHA-1：`eee9145e892bb5ca5fe72323bd81c8522c53417`。bootstrap 脚本会再次验证该值。若官方文件已更新或校验值发生变化，停止引导并重新核对官方页面。
