# 系统规格：种族

## 首发种族
Bearfolk、Wolfkin、Dragonkin（龙裔/龙人）。

## 数据驱动
SpeciesDefinition 提供显示名、本地化 key、模型/rig ID、traits、modifier、起始科技/建筑引用。Trait 是注册 ID，具体行为由注册模块实现。不能将 JSON 解释为任意可执行代码。

## 属性计算
使用统一 Modifier Pipeline：base stat → species modifier → profession modifier → technology modifier → temporary effect。每一步可追踪来源，避免同一加成重复应用。

## 种族边界
熊族：石材、负重、重装。狼族：森林、侦察、机动。龙裔：火焰抗性、冶炼、特殊材料。所有种族共用居民、建筑、任务和库存服务。

## 模型与动画
首发允许不同模型/贴图，但基础动作集合需覆盖 idle、walk、work、hurt、attack、death。GeckoLib 是可选表现依赖；领域数据不依赖 GeckoLib 类型。龙裔使用人形/双足 rig；自由飞行巨龙留到后期。

## 验收
未知种族配置报错并定位文件；不同种族 modifier 按 pipeline 只应用一次；所有种族都能进入通用 job 和 building 系统。
