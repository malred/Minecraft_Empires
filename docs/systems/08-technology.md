# 系统规格：科技

## 分支
农业、工业、建筑、军事、文明管理；种族特色节点作为扩展分支。

## TechnologyDefinition
id、schemaVersion、era、prerequisites、researchCost、researchDurationTicks、effects、unlockBuildings、unlockUnits。

## 研发流程
Validate prerequisites → reserve cost → create ResearchProject → advance progress → apply unlock/effects → persist completion。多人场景必须由所属 faction 权限决定谁能发起研究。

## 效果机制
Modifier ID 通过注册表绑定到安全的代码效果；不执行任意脚本。科技重载时不得重复叠加永久 modifier，应用效果要基于已完成科技集合重新计算或保证幂等。

## 验收
前置科技不能绕过；材料只扣一次；重启进度保持；重复加载科技不会重复加成；缺失科技数据有迁移/fallback 策略。
