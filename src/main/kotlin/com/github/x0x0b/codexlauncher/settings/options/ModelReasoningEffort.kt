package com.github.x0x0b.codexlauncher.settings.options

enum class ModelReasoningEffort {
    DEFAULT,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH;

    fun cliName(): String = when (this) {
        DEFAULT -> ""
        MINIMAL -> "minimal"
        LOW -> "low"
        MEDIUM -> "medium"
        HIGH -> "high"
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"
        MINIMAL -> "Minimal"
        LOW -> "Low"
        MEDIUM -> "Medium"
        HIGH -> "High"
    }

    override fun toString(): String = toDisplayName()
}
