package com.github.x0x0b.codexlauncher.settings.options

enum class ModelReasoningEffort {
    DEFAULT,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    EXTRA_HIGH;

    fun cliName(): String = when (this) {
        DEFAULT -> ""
        MINIMAL -> "minimal"
        LOW -> "low"
        MEDIUM -> "medium"
        HIGH -> "high"
        EXTRA_HIGH -> "xhigh"
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"
        MINIMAL -> "Minimal"
        LOW -> "Low"
        MEDIUM -> "Medium"
        HIGH -> "High"
        EXTRA_HIGH -> "Extra High"
    }

    override fun toString(): String = toDisplayName()
}
