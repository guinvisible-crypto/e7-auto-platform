package com.e7.autoplatform.core.config

interface RuntimeStateStore {
    suspend fun putInt(key: String, value: Int)
    suspend fun getInt(key: String, defaultValue: Int = 0): Int

    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String, defaultValue: String = ""): String

    suspend fun clear()
}
