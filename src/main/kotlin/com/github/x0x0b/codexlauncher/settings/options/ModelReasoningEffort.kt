package com.github.x0x0b.codexlauncher.settings.options

enum class ModelReasoningEffort {
    DEFAULT,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    EXTRA_HIGH,
    CUSTOM;

    fun cliName(): String = when (this) {
        DEFAULT -> ""
        MINIMAL -> "minimal"
        LOW -> "low"
        MEDIUM -> "medium"
        HIGH -> "high"
        EXTRA_HIGH -> "xhigh"
        CUSTOM -> ""
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"
        MINIMAL -> "Minimal"
        LOW -> "Low"
        MEDIUM -> "Medium"
        HIGH -> "High"
        EXTRA_HIGH -> "Extra High"
        CUSTOM -> "Custom..."
    }

    override fun toString(): String = toDisplayName()
}
