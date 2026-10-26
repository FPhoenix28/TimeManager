package com.example.timemanager.config

/** Draft values only: persistence happens after the explicit Save action. */
data class DurationInput(val hours: String, val minutes: String, val seconds: String) {
    fun totalSeconds(): Long {
        val h = hours.toIntOrNull()
        val m = minutes.toIntOrNull()
        val s = seconds.toIntOrNull()
        require(h != null && h in 0..99 && m != null && m in 0..59 && s != null && s in 0..59) {
            "小时须为0–99，分钟和秒须为0–59"
        }
        return (h * 3600L + m * 60L + s).also { require(it > 0) { "时间必须至少为1秒" } }
    }

    companion object {
        const val MAX_SECONDS = 359_999L
        fun fromSeconds(seconds: Long) = DurationInput(
            (seconds / 3600L).toString(), (seconds % 3600L / 60L).toString(), (seconds % 60L).toString(),
        )
    }
}

fun formatPreciseDuration(seconds: Long): String = buildString {
    val h = seconds / 3600L
    val m = seconds % 3600L / 60L
    val s = seconds % 60L
    if (h > 0) append("${h}小时")
    if (m > 0) append("${m}分")
    if (s > 0 || isEmpty()) append("${s}秒")
}
