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

新增 Forge GameTests：
- 同一势力第二次规划大本营被拒绝，且不会写入第二条记录。
- 不同势力可各自规划一座大本营。
- 首都相同值重复绑定是幂等的，改绑到另一聚落被拒绝。
- validator 能检测同一势力多条未拆除大本营记录。

## 当前限制

本任务不提供拆除命令、毁灭/重建流程、首都迁移授权流程或大本营完成激活逻辑。这些需要在后续施工/重建任务中定义显式事务。
