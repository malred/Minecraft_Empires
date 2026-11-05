# 系统规格：AI 势力

## 设计
AI 使用纯数据战略状态和同一 Command Gateway，不使用外部 LLM 作为核心依赖，不直接改世界方块或资源余额。

## 架构
FactionBrain：读取 FactionSnapshot → 计算可行行动 → Utility Scorer 排序 → 产生 Command → Command Gateway 校验并执行 → 记录结果。

## 初始状态机
SURVIVE（食物/住宅）→ ECONOMY（基础生产）→ EXPAND（前哨/资源点）→ DEFEND（防御）→ MILITARY（训练）→ DIPLOMACY/WAR（后续）。

## 人格
conqueror、merchant、guardian、scholar、opportunist。人格只修改效用评分、目标偏好和风险容忍度，不绕过成本/权限/冷却。

## 规划
每轮最多提交有限数量命令；失败命令记原因并增加退避，防止在同一 tick 无限重复。AI 必须有可解释日志：当前目标、候选评分、选择原因。

## 测试
固定世界快照和随机种子时，AI 规划结果可复现；AI 无法无成本建造；资源不足时不会持续重试；未加载基地仍能产出战略计划。
