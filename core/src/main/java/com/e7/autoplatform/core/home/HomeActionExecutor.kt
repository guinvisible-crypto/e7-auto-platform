package com.e7.autoplatform.core.home

interface HomeActionExecutor {
    suspend fun tapDismiss()
    suspend fun pressBack()
    suspend fun waitStep(delayMs: Long)
    suspend fun ensureForeground()
}
