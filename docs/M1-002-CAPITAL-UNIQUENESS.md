# M1-002 — 大本营唯一性服务

## 范围

本任务在服务层落实每个势力最多一个未拆除大本营的规则，并让只读世界校验报告存档中已有的重复大本营。

## 规则

1. 大本营按蓝图的 `BuildingCategory.HEADQUARTERS` 识别，不硬编码单个 blueprint ID。
2. 同一势力只能存在一条 state 不为 `DEMOLISHED` 的大本营记录。包括 `PLANNED`、`RESERVED`、`UNDER_CONSTRUCTION`、`ACTIVE`、`DAMAGED`、`RUINED` 和 `NEEDS_REPAIR`。
3. 只有显式进入 `DEMOLISHED` 状态才释放名额。当前没有独立的 DESTROYED/REBUILDING 生命周期，本任务不把 RUINED 当成已拆除。
4. 若势力已绑定 capital settlement，新大本营只能规划在该聚落。
5. `setCapitalSettlement` 允许首次绑定和相同值幂等调用；不允许静默迁移到另一聚落。已有未拆除大本营时，首都还必须与该大本营所属聚落一致。
6. 不同势力可以分别规划自己的大本营。
7. 所有规则均由服务端 `CivilizationService` 执行；validator 只报告异常，不修复数据。

## 持久化与兼容

- 不增加或修改 SavedData / FactionRecord / BuildingRecord 字段。
- 不改变 schema 版本，不迁移存档。
- 不触碰世界方块、区块加载或现有玩家建筑。
- 如果旧存档已有重复大本营，validator 会报告 ERROR；本任务不会自动删除或选择保留哪一条。

## 自动化覆盖

新增 Forge GameTests（5 项）：
- 同一势力第二次规划大本营被拒绝，且不会写入第二条记录；显式拆除后可重新规划。
- 不同势力可各自规划一座大本营。
- 首都相同值重复绑定是幂等的，改绑到另一聚落被拒绝；已有大本营时不能把首都绑定到别的聚落。
- validator 能检测同一势力多条未拆除大本营记录。

## 本地验收记录（2026-11-06）

<badge>PASS</badge> 用户本地实际执行结果：

- `gradlew.bat build runGameTestServer --console=plain`：`BUILD SUCCESSFUL`。
- Forge GameTests：M0-012 8 项 + M1-001 1 项 + M1-002 5 项，共 **14/14 required tests passed**。
- `runClient` 成功进入既有世界「新的世界2」。
- `/empires debug status`：`schema=3`、`readOnly=false`、`worldId=6ba6a5f4-e5f9-4e01-bfaa-f86a0aeac64c`、`factions=1`、`settlements=2`、`citizens=3`。
- `/empires debug validate`：PASS，0 errors、0 warnings；记录数量为 factions=1、settlements=2、citizens=3、buildings=2。
- 客户端正常退出；主世界、下界和末地均报告区块已保存。
- 日志中的 schema 4 read-only 信息来自 `futureSchemaIsPreservedReadOnly` 自动化 fixture，不属于正式存档；正式存档仍为 schema 3 且可写。

因此 M1-002 本地验收为 **PASS**。Dedicated Server 测试仍未执行。

## 当前限制

本任务不提供拆除命令、毁灭/重建流程、首都迁移授权流程或大本营完成激活逻辑。这些需要在后续施工/重建任务中定义显式事务。
