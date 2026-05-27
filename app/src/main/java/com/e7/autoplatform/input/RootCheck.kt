package com.e7.autoplatform.input

object RootCheck {
    fun isRoot(): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec("su -c id")
            process.inputStream.bufferedReader().readText().contains("uid=0")
        }.getOrDefault(false)
    }
}
