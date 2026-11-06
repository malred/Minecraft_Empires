# 系统规格：蓝图与施工

## 蓝图定义
蓝图是内容定义，不是一次性方块快照。包含 ID、schema、模板版本、尺寸、旋转、材料、施工点数、岗位、放置规则、方块保护规则和 checksum。

## 放置阶段
Preview → Validate → Reserve Materials → Create BuildingRecord → Queue Construction → Apply Blocks → Activate。

## 校验
拒绝超尺寸、越界、未加载区块、冲突结构、不可替换方块、未授权区域和不可信 BlockEntity NBT。校验失败不得扣材料。

## 施工
施工逻辑状态按 PLANNED → RESERVED → UNDER_CONSTRUCTION → ACTIVE 推进。M1-005 以 BuildingRecord 作为持久化 ConstructionProject 聚合：RESERVED 时持有全额材料 escrow；UNDER_CONSTRUCTION 按累计 work points 的比例减少 escrow；只有 work points 完成、escrow 为空且世界模板 SYNCED 后才能 ACTIVE。施工批次以 expectedProgress 做 compare-and-set，避免重试重复计入。placementCursor 只代表模板写入游标，不代表施工进度。

后续施工阶段由 ConstructionProject / Job 系统保存进度、材料 escrow、worker job、分阶段 block placement queue。每 tick 有方块放置预算。工人离开/死亡可重新分配工作，进度不丢。

## 玩家改造
建筑完工后记录受管方块和 checksum。默认不在区块加载时自动覆盖玩家改造。修理只针对明确受管且已损坏的部分。自定义蓝图编辑/分享后续开发。

## 验收
放置预览与服务端结果一致；材料只扣一次；施工可暂停/恢复/取消；未加载区域不写方块；玩家改造不会被加载流程覆盖。
