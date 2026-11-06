# M1-001 — Headquarters Content Registration

## 目标

为后续大本营规划、唯一性约束和施工流程注册第一套正式核心内容。该任务只负责游戏内容注册与可校验蓝图定义，不提前实现材料预留、建筑激活或玩家归属映射。

## 新增内容

- Block ID：`empires:headquarters_core`
  - 作为受管大本营结构中的逻辑核心标记。
  - 有意不注册 BlockItem；玩家不能绕过服务层把核心方块随意放在世界中。
  - 由蓝图放置适配器在服务端主线程按模板写入。
  - 破坏掉落为 `empires:headquarters_blueprint`。
- Item ID：`empires:headquarters_blueprint`
  - 单个堆叠。
  - 加入原版 Tools & Utilities 创造模式物品栏。
  - 当前作为蓝图选择内容标识；真正的放置/规划交互将在 M1-003/M1-009 实现。
- Blueprint ID：`empires:headquarters_hall`
  - Category：HEADQUARTERS。
  - 尺寸：7×8×7。
  - 模板：`empires:structures/headquarters_hall`。
  - 方块条目：204。
  - 核心 marker：`empires:headquarters_core`。
  - 旋转：0/90/180/270。
  - 材料需求：49 stone_bricks、139 spruce_planks、12 spruce_log、3 glass。
  - Work points：800（初始设计值，未做平衡测试）。
  - Required profession：`empires:builder`。

## 设计边界

1. 不修改 SavedData schema，不触碰现有 Faction/Settlement/Citizen/Building 记录。
2. 不自动把任何现有 Settlement 设置为 capital。
3. 不允许直接放置核心 marker 方块；它只能由受管蓝图路径创建。
4. 蓝图结构是首个可运行的视觉原型，不是最终种族建筑美术；正式外观可通过新 blueprint version 替换，不覆盖旧 BuildingRecord 指向的版本。
5. 本任务不实现 HQ 唯一性、放置地形/边界检查、材料预留、ConstructionProject、Builder Job 或激活流程。这些依次由 M1-002 至 M1-008 负责。

## 测试

新增 GameTest：`headquartersContentIsRegisteredAndTemplateValid`，检查 Registry ID、Blueprint category、模板尺寸/204 个方块、SHA-256 以及核心 marker 引用。

执行：

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer --console=plain
```

GameTest 使用独立 `run-gametest` 目录，不使用玩家的普通单人世界存档。

## 验收状态

- [x] 注册类、蓝图与结构资源已提交。
- [x] GameTest 已加入 `empires.m1_001` batch。
- [ ] 用户本地 build：待运行。
- [ ] 用户本地 GameTest：待运行。
- [ ] 客户端创造模式物品栏显示：待人工确认。
- [ ] 真实世界施工：尚不属于本任务范围。
