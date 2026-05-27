package com.e7.autoplatform.input

import java.io.DataOutputStream

class RootInputExecutor {
    fun send(commands: List<String>): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            commands.forEach {
                os.writeBytes(it)
                os.writeBytes("\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        }.getOrDefault(false)
    }
}
