# M0-011 — World Data Validation

## 目标

为已存在的 OP 调试命令补齐只读的一致性诊断能力。M0-011 不改变世界 SavedData schema，不进行自动修复，不读取或修改真实 Minecraft 方块。

## 命令

```mcfunction
/empires debug validate
/empires debug validate <page>
```

- 仅 OP（permission level 2）可执行。
- 默认显示第 1 页。
- 每页最多 12 条诊断。
- 输出总势力、聚落、居民、建筑数量，以及 ERROR/WARNING 数量。
- 有 ERROR 时命令返回失败状态；仅 WARNING 时仍返回成功状态。
- 查询是即时只读操作，不写入 SavedData。

## 当前检查项

### Faction / Settlement

- Faction 的 capitalSettlementId 必须引用存在且归属相同的 Settlement。
- 非 ACTIVE 的 capital Settlement 报告 WARNING。
- Settlement owner Faction 必须存在。
- Settlement 的 buildingIds 每一项都必须存在，并且 BuildingRecord.settlementId 必须反向指向该 Settlement。

### Citizen

- Citizen owner Faction 必须存在。
- 非空 homeSettlementId 必须存在且属于相同 Faction。
- 多个逻辑 CitizenRecord 不得绑定同一个 Minecraft entity UUID。
- 每个 Faction 的 populationCountCache 与 ACTIVE CitizenRecord 数量进行比较；不一致时报告 WARNING。

人口统计以 CitizenRecord 为准，不通过扫描已加载实体推断人口。

### Building

- Building owner Faction、Settlement 必须存在且归属一致。
- Building dimension 必须与所属 Settlement dimension 一致。
- Settlement.buildingIds 必须包含对应 Building ID。
- Blueprint 必须存在；版本、manifest checksum、rotation 必须匹配当前 BlueprintCatalog。
- placementCursor 不得超过蓝图尺寸体积。
- NOT_PLACED 不能有非零 cursor。
- SYNCED 且 cursor 为零会报告 WARNING。
- 同一维度下，除 DEMOLISHED 外的建筑占地不能相互重叠。

## 明确不检查的内容

- 不扫描真实 Minecraft Entity 列表，也不把未加载实体解释为居民丢失。
- 不加载或检查远程 Chunk。
- 不检查世界里的实际方块是否被玩家修改、破坏或替换。
- 不加载结构 JSON；结构模板自身的 SHA-256 由 M0-010 的 BlueprintTemplate 加载/放置流程验证。这里检查的是 BuildingRecord 与 BlueprintDefinition 的 manifest checksum。
- 不自动改正人口缓存、索引、BuildingRecord 或任何 NBT。
- 当前没有一键修复命令；后续修复必须作为单独、可回滚并有测试的任务实现。

## 验收步骤

1. 关闭 Minecraft，备份工程和现有世界。
2. 在工程根目录运行：

   ```powershell
   .\gradlew.bat build
   ```

3. 构建成功后运行：

   ```powershell
   .\gradlew.bat runClient
   ```

4. 进入既有世界（不要重建世界），执行：

   ```mcfunction
   /empires debug status
   /empires debug validate
   ```

5. 若摘要显示多页，逐页执行：

   ```mcfunction
   /empires debug validate 2
   /empires debug validate 3
   ```

6. 检查诊断输出，并再次运行 `/empires debug status`。worldId、schema、Faction/Settlement/Citizen/Building 数量不应因 validate 命令而改变。
7. 保存退出，再进入同一世界，重复 validate。结果应保持一致（除非期间游戏状态发生变化）。

## 验收状态

- [x] 代码已提交到 GitHub main。
- [x] 命令已实现分页输出。
- [x] 文档已同步。
- [ ] 用户本地 `gradlew.bat build`：NOT RUN。
- [ ] Forge 客户端命令测试：NOT RUN。
- [ ] Dedicated Server 测试：NOT RUN。
- [ ] 自动化/损坏数据 fixture 测试：NOT RUN。

本提交作者没有在用户 Windows Forge 环境中执行构建或启动游戏；不得把上述 NOT RUN 项标记为通过。
