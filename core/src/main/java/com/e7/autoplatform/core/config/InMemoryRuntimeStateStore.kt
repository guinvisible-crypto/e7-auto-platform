package com.e7.autoplatform.core.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryRuntimeStateStore : RuntimeStateStore {
    private val mutex = Mutex()
    private val map = mutableMapOf<String, String>()

    override suspend fun putInt(key: String, value: Int) {
        mutex.withLock { map[key] = value.toString() }
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int = mutex.withLock {
        map[key]?.toIntOrNull() ?: defaultValue
    }

    override suspend fun putString(key: String, value: String) {
        mutex.withLock { map[key] = value }
    }

    override suspend fun getString(key: String, defaultValue: String): String = mutex.withLock {
        map[key] ?: defaultValue
    }

    override suspend fun clear() {
        mutex.withLock { map.clear() }
    }
}
