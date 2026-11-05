# ADR-0001：通过官方 MDK 组装 M0-001 工程

- 状态：Accepted
- 日期：2026-11-05

## 背景

Forge MDK 包含与 ForgeGradle 兼容的 Gradle Wrapper 脚本、Wrapper JAR 和运行配置。自行猜测或重建 Wrapper 版本会增加引导失败风险。本次执行容器不能访问 Forge Maven，也没有本地 Gradle/Wrapper JAR。

## 决策

交付项目源代码与 Gradle 配置 overlay，并提供 Windows PowerShell / POSIX shell 引导脚本。脚本下载 Forge 官方 1.20.1-47.4.28 MDK，先校验 SHA-1，再保留官方 Wrapper 文件，最后覆盖本项目文件。

## 后果

- 优点：不伪造 Gradle Wrapper JAR；用户得到官方 MDK 的 Wrapper 和运行配置。
- 限制：第一次组装需要网络，并需在本地安装 JDK 17。
- 限制：容器内本次不能完成 Forge 构建或游戏启动验证。
