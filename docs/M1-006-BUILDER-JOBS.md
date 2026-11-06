# M1-006 — Builder Job 持久化与生命周期

## 目标与边界

为 M1-005 的逻辑施工项目接入持久化 Builder Job。工作任务只保存领域 UUID 和生命周期状态，不代表居民实体一定已加载，也不要求目标区块保持加载。

本任务实现：
- `empires:builder` 职业标记;
- 每个 UNDER_CONSTRUCTION 建筑对应一个稳定 ID 的 BuilderJobRecord；
- Builder Job 创建、领取、开始工作、释放、阻塞、重试和完成；
- 同一施工建筑最多一个活动 Job，同一居民最多持有一个活动 Job；
- 领取任务时校验 worker ACTIVE、Builder profession、Faction 与可用 Settlement 归属；
- 建筑开工与 Job 创建、建筑激活与 Job 完成，分别在同一 SavedData mutation 中提交；
- schema 3 → 4 向后兼容迁移以及 Validator 一致性检查。

本任务不实现：
- 居民自动选工/自动调度；
- Minecraft 寻路、TRAVELLING 实际移动和工作范围判定；
- 工具/材料实体搬运；
- 将施工 work points 自动绑定到居民工作 tick；
- 分阶段方块出现、取消退款、修复或损坏生命周期。
这些由 M1-007/M1-008/M1 后续集成任务处理。

## 数据与状态

BuilderJobRecord 持久化字段包括：
- 稳定 job UUID（由 building UUID 确定性派生）；
- buildingId、ownerFactionId、settlementId；
- assignedCitizenId（可空）；
- state、priority（0–100，默认 50）、retryCount；
- createdAtGameTime、updatedAtGameTime、schemaVersion。

状态：
`AVAILABLE → CLAIMED → WORKING → COMPLETED`

异常/释放分支：
- `CLAIMED/WORKING → AVAILABLE`：主动释放，worker 清空，retryCount +1；
- `CLAIMED/WORKING → BLOCKED`：阻塞，worker 清空，retryCount +1；
- `BLOCKED → AVAILABLE`：重试，不清除 retry 历史。
- `TRAVELLING` 为后续接入实体寻路保留；FAILED/CANCELLED 为后续失败/取消处理预留。

AVAILABLE 与 BLOCKED 不保留 assignedCitizenId；CLAIMED/TRAVELLING/WORKING 必须关联 worker。COMPLETED 是历史记录，后续建筑损坏/拆除不应反向改变已完成 Job。

### 身份和并发约束

1. 每个 UNDER_CONSTRUCTION BuildingRecord 必须有且只有一个非终态 BuilderJobRecord。
2. 每个 Citizen 最多领取一个活动 Job。
3. worker 必须 ACTIVE、职业为 `empires:builder`、Faction 与任务一致；若 CitizenRecord.homeSettlementId 非空，必须与 Job.settlementId 相同。
4. Job 只能操作其记录中的 buildingId；目标 building 必须仍为 UNDER_CONSTRUCTION。
5. Job 更新通过持久记录快照 compare-and-set；过期状态不允许覆盖新状态。
6. 所有数据写入走 SavedData；不通过 Job 状态触发 chunk force-load。

## 原子性与生命周期

- `RESERVED → UNDER_CONSTRUCTION` 与唯一 AVAILABLE BuilderJobRecord 创建由 `saveConstructionStart` / 单次 SavedData mutation 共同提交。
- claim、start、release、block、retry 使用 expected job snapshot 做 compare-and-set。
- 当施工项目在满足 M1-005 完成条件后变成 ACTIVE，同一 construction SavedData mutation 会把关联活动 Job 变成 COMPLETED。
- 不能在建筑未 ACTIVE 时单独完成 Job。
- 被分配的 worker 若要修改 profession/status/owner/home，需要先释放活动 Job。

## Schema 迁移与旧存档保护

世界级 EmpiresWorldData schema 从 3 升至 4；CitizenRecord 和 BuildingRecord 的单体 schema 不变。

schema 3 → 4：
- 新增 `builder_jobs` 列表；
- 为每个已存在的 UNDER_CONSTRUCTION BuildingRecord 派生稳定 job UUID，并创建 AVAILABLE Job；
- 不修改 faction resources、citizens、building progress/escrow、settlement index 或实际世界方块；
- 保留 worldId 和其他旧数据；
- 新保存时写出 schema 4 与 builder_jobs。

schema 4 如果缺少任务列表、任务目标/归属错误、同一 worker/建筑存在多个活动 Job，或分配的 worker 不符合资格，则使用既有只读 fallback 保留原始 NBT，不做自动猜测修复。玩家不需要删除或重建存档。

## OP 验收命令

所有命令均位于 OP-only 的 `/empires debug` 下：

```mcfunction
/empires debug assign-builder <citizenUUID>
/empires debug create-builder-job <buildingUUID> [priority]
/empires debug list-builder-jobs [buildingUUID]
/empires debug claim-builder-job <jobUUID> <citizenUUID>
/empires debug start-builder-job <jobUUID> <citizenUUID>
/empires debug release-builder-job <jobUUID> <citizenUUID>
/empires debug block-builder-job <jobUUID> <citizenUUID>
/empires debug retry-builder-job <jobUUID>
/empires debug complete-builder-job <jobUUID>
```

常用流程（先确保建筑已经通过 M1-004 预留材料）：

```mcfunction
/empires debug start-construction <buildingUUID>
/empires debug list-builder-jobs <buildingUUID>
/empires debug assign-builder <citizenUUID>
/empires debug claim-builder-job <jobUUID> <citizenUUID>
/empires debug start-builder-job <jobUUID> <citizenUUID>
```

之后继续使用 M1-005 的建筑施工调试命令推进逻辑 work points。达到 ACTIVE 后，Job 会由同一 SavedData mutation 自动完成。此命令流程用于测试领域生命周期，不会让 NPC 寻路或让工人实体显示施工动画。

## GameTests

新增 9 项，当前完整回归预计 **47 项**（此前 M0/M1 的 38 项 + 本任务 9 项）：

1. 开工同时创建一个 AVAILABLE Job，重复开工不产生重复 Job；
2. 未分配 Builder profession 的 citizen 无法领取，ACTIVE Builder 可领取；
3. 同一 Builder 不能并发领取两个建筑任务；
4. 非 assigned worker 无法启动 Job，重复启动安全；
5. release 清空 worker、增加 retryCount，其他 Builder 可接手；
6. BLOCKED 任务可回到 AVAILABLE，保留 retry 历史；
7. Building ACTIVE 之前不能完成 Job；Building 激活时 Job 原子完成；
8. schema 3 迁移为已有 UNDER_CONSTRUCTION 项目生成 AVAILABLE Job，并保持 worldId/既有记录；
9. 已领取/开始工作的 Job 经 SavedData round-trip 后保留状态，Validator PASS。

## 本地验收状态

**待用户本地验收。** 助手已在 GitHub feature branch 上完成代码和文档修改，但没有本地 Forge Gradle 环境，未运行 build、GameTestServer、客户端或 Dedicated Server。不能将编译或 47 项测试标记为 PASS。

建议本地执行：

```powershell
git pull --ff-only origin main
.\gradlew.bat build runGameTestServer --console=plain
```

本地应确认：
- 编译无错误；
- `All 47 required tests passed :)`；
- Gradle 最终 `BUILD SUCCESSFUL`；
- 用已有「新的世界2」存档启动客户端后，`/empires debug status` 显示 worldId 仍为 `6ba6a5f4-e5f9-4e01-bfaa-f86a0aeac64c`、schema=4、readOnly=false；
- `/empires debug validate` 为 PASS、0 errors、0 warnings；
- Faction/Settlement/Citizen/Building 数量和建筑方块未被迁移破坏。

运行前先备份世界目录。迁移不要求创建新世界或重新放置已有建筑。
