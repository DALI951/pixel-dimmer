package com.dali951.pixeldimmer

enum class MaskMode(val label: String) {
    DOT("Dot"),
    LINE("Line"),
    BOTH("Both");

    companion object {
        fun fromString(value: String?): MaskMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DOT
        }
    }
}

data class MaskConfig(
    val enabled: Boolean = false,
    val mode: MaskMode = MaskMode.DOT,
    val dotX: Int = 18,
    val dotY: Int = 900,
    val dotSize: Int = 36,
    val lineY: Int = 900,
    val lineHeight: Int = 3,
    val calibrationWidth: Int = 1440,
    val calibrationHeight: Int = 3088,
    val fineMode: Boolean = true
) {
    companion object {
        const val MIN_DOT_SIZE = 2
        const val MAX_DOT_SIZE = 160
        const val MIN_LINE_HEIGHT = 1
        const val MAX_LINE_HEIGHT = 40
    }
}
