package com.github.x0x0b.codexlauncher.settings

enum class Mode {
    DEFAULT, // 引数を渡さない
    FULL_AUTO; // --full-autoを渡す

    companion object {
        fun fromString(value: String?): Mode = when (value) {
            "DEFAULT" -> DEFAULT
            "FULL_AUTO" -> FULL_AUTO
            else -> throw IllegalArgumentException("Unknown mode: $value")
        }
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default (No arguments)"
        FULL_AUTO -> "Full Auto (--full-auto)"
    }
}
