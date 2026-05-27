package com.e7.autoplatform.input

class SendeventBuilder(
    private val device: String = "/dev/input/event2"
) {
    fun tap(x: Int, y: Int): List<String> {
        return listOf(
            "sendevent $device 3 57 0",
            "sendevent $device 3 53 $x",
            "sendevent $device 3 54 $y",
            "sendevent $device 3 58 50",
            "sendevent $device 0 0 0",
            "sendevent $device 3 57 -1",
            "sendevent $device 0 0 0"
        )
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, steps: Int = 12): List<String> {
        val cmds = mutableListOf<String>()
        cmds.add("sendevent $device 3 57 0")
        cmds.add("sendevent $device 3 53 $x1")
        cmds.add("sendevent $device 3 54 $y1")
        cmds.add("sendevent $device 3 58 50")
        cmds.add("sendevent $device 0 0 0")

        for (i in 1..steps) {
            val x = x1 + (x2 - x1) * i / steps
            val y = y1 + (y2 - y1) * i / steps
            cmds.add("sendevent $device 3 53 $x")
            cmds.add("sendevent $device 3 54 $y")
            cmds.add("sendevent $device 0 0 0")
        }

        cmds.add("sendevent $device 3 57 -1")
        cmds.add("sendevent $device 0 0 0")
        return cmds
    }
}
