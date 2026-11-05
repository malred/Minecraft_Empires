# 系统规格：大本营

## 职责
提供文明主权锚点、时代门槛和重建流程。

## 核心数据
HeadquartersRecord：buildingId、factionId、settlementId、tier、state、lastKnownPosition、rebuildBlueprintId、schemaVersion。

## 状态
ACTIVE、UNDER_ATTACK、BREACHED、DESTROYED、REBUILDING。状态转换由 HeadquartersService 管理。

## 放置与唯一性
检查 faction 当前是否已有非 DESTROYED 的有效大本营。放置检查地形、重叠、世界边界、势力归属和成本。服务端执行最终检查。重复网络请求必须幂等。

## 摧毁和重建
摧毁时写入事件，关闭依赖 HQ 的功能但保留数据。重建需要特殊蓝图与材料；玩家可重新选址。遗产系统保证资源可获得但不保证无代价。服务器配置 PvP 保护。

## 验收
同势力不能建两个 HQ；不同势力可分别建 HQ；HQ 方块移除与逻辑记录一致；重启不复制 HQ；destroyed 后可按规则重建。
