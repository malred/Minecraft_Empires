# 自动化测试入口

## 目标

减少开发者重复输入 Gradle 命令的次数。当前脚本把现有工程构建和 Forge GameTests 串成一个本地验收入口；它不替代真实客户端体验测试，也不连接或修改玩家正式存档。

## 环境

- Windows PowerShell（项目当前使用 `gradlew.bat`）。
- Java 17。
- 从仓库根目录或任意目录执行均可；脚本会自行定位仓库根目录。

## 一键运行

在 PowerShell 中执行：

```powershell
.\scripts\test-all.ps1
```

脚本依次运行：

1. `.\gradlew.bat build --console=plain`
2. `.\gradlew.bat runGameTestServer --console=plain`

任一阶段 Gradle 返回非零，或缺少对应成功标记，脚本都会以退出码 1 结束。全部通过时退出码为 0。

## 可选参数

只运行 GameTests（例如刚完成构建后）：

```powershell
.\scripts\test-all.ps1 -SkipBuild
```

只运行构建：

```powershell
.\scripts\test-all.ps1 -SkipGameTests
```

## 报告位置

每次运行都会在：

```text
build/reports/empires-automation/<时间戳>/
```

保存独立的 `build.log`、`gametests.log`（按实际执行项生成）和 `summary.txt`。该目录属于 Gradle build 输出，不需要提交到 Git。

## 安全边界与当前限制

- GameTest 使用 `build.gradle` 中配置的 `run-gametest` 工作目录，与正常客户端的 `run` 工作目录隔离。
- 该脚本不启动 `runClient`，不连接「新的世界2」，也不对正式存档执行命令。
- 该脚本目前不启动 Dedicated Server、不配置 RCON，也不模拟玩家 UI。不要把它描述为真实客户端端到端测试。
- M1-008A 的已分配 Builder Job 取消与工人释放逻辑已经由现有 GameTest `cancellingConstructionRefundsOnlyRemainingEscrowAndReleasesBuilder` 覆盖；不需要为了这一条边界再让用户手动进游戏重复验证。
- 后续若引入 RCON 集成测试，必须使用专用临时服务器目录、独立测试世界和仅绑定本机的 RCON 配置；密码不得写入仓库或日志。正式存档不得成为自动化测试目标。

## 手动客户端验收何时仍然需要

仅在需要确认视觉呈现、真实玩家交互、真实存档兼容性或客户端/服务器联机行为时，才要求人工验收。能由 GameTests 稳定验证的领域逻辑优先放入自动化测试。
