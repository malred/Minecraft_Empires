# 系统规格：文明与聚落

## 职责
管理 Faction、Settlement、所有权、主城引用和文明状态。它是其他系统的归属与权限来源。

## 规则
- Faction 由 UUID 标识；种族和控制类型创建后默认不可随意更改，若允许转化必须通过明确事件。
- 每个势力最多一个 ACTIVE 大本营。
- Settlement 归属一个 Faction；转让必须通过服务层并更新建筑、居民、军队和区域引用。
- Faction 不因大本营方块消失就从注册表删除。
- Neutral 可是无主聚落或中立势力，二者数据需区分。

## 服务
`FactionService`：create、rename、setStatus、get。  
`SettlementService`：create、transferOwnership、setStatus、bindCapital。  
`OwnershipService`：验证玩家/AI 是否有权操作指定对象。

## 事件
FactionCreated、FactionStatusChanged、SettlementCreated、SettlementOwnershipChanged、CapitalBound、CapitalLost。

## 验收
重启后 ID 和引用一致；重复绑定第二个 ACTIVE capital 被拒绝；势力转让不留下旧 owner 引用；大本营损毁保留 Faction 记录。
