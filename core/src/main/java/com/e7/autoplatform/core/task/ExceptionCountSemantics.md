# exceptionCount 语义定义（统一版）

## 增加时机（只在失败事件发生时增加）
- 任务返回 `TaskRunResult.Interrupted`。
- 引擎执行出现未捕获异常并进入中断态。

## 不增加时机
- `TaskRunResult.Retry`（同一次任务重试不计入失败次数）。
- 启动后的 `recoverAfterCrash()` 恢复判定过程（只读取并判定，不增加）。

## 恢复判定
- 若 `exceptionCount > maxRetryCount`：停止恢复并清零（进入 `IDLE`）。
- 否则：进入 `RECOVERING`，保留 `exceptionCount` 和 `currentTaskIndex` 继续。

## 重置时机
- 引擎完整成功结束后调用 `resetRecoveryState()`，状态恢复初始值。
