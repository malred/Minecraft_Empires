# 系统规格：人口、职业与任务

## 居民身份
CitizenRecord 是持久身份；Citizen Entity 是实时表现。居民可在 DORMANT 时继续参与抽象经济，按游戏规则计算需求和工作结果。

## 首发职业
builder、hauler、lumberjack、farmer、miner/quarry_worker、militia。职业定义岗位要求、允许任务、技能成长和基础效率。

## 岗位
BuildingDefinition 声明岗位数量；BuildingService 计算占用数；CitizenAssignmentService 检查职业资格、所属势力、住所/工作地点和岗位容量。

## Job 生命周期
AVAILABLE → CLAIMED → TRAVELLING → WORKING → COMPLETED。异常状态 BLOCKED/FAILED/CANCELLED。Job 使用 reservation token 锁目标、资源或库存；超时自动释放。

## 寻路
首发用 Minecraft PathNavigation 处理附近真实实体的局部路径；战略远程模拟只记录目标/距离/耗时估算，不生成实体。路径失败不可无限重试，应退避并改派。

## 需求
M2 只实现食物、住房、安全、休息。需求效果应可配置，不能直接删除居民或制造不可逆软锁。

## 验收
居民身份跨实体休眠稳定；岗位数不超限；同一任务不被两名居民同时领取；任务超时会释放；居民死亡后职位和资源锁正确清理。
