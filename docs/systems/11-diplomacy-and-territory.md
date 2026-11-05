# 系统规格：外交、贸易和领土

## 区域
RegionRecord 保存 RegionId、dimension、chunk bounds、terrain tags、resource nodes、controllerFactionId、contested state、exploredBy 和 settlement references。

## 占领
控制权来源包括大本营影响、前哨站、驻军和争夺进度。占领变化是领域事件，不能由玩家在客户端自行设置。

## 外交
DiplomacyRelation 是有向/无向关系需统一选择；推荐每对 faction 保存一份规范化双边关系，避免 A→B 与 B→A 分叉。关系数值和状态分开：状态是协议，数值是倾向。

## 贸易
TradeOffer 包含资源、数量、付款、交付点、有效期、发起方和状态。交易使用账本事务，商队实体仅作表现；商队被袭击时实际交付规则由服务器结算。

## 首发范围
MVP 只需要探索和简单区域控制；完整同盟、通行权、赔偿和多方外交在 M7。

## 验收
区域不会被两个势力同时标记为无争议控制者；贸易不能复制资源；关系在重启后不丢；战争/停战按唯一协议 ID 处理。
