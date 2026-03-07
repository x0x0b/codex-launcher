package com.github.x0x0b.codexlauncher.settings.options

/**
 * Model reasoning effort choices.
 *
 * `CUSTOM` is a display/persistence marker only. It is not a direct CLI token:
 * - `cliName()` returns an empty string for `CUSTOM`
 * - `toDisplayName()` renders `CUSTOM` as "Custom..."
 * - callers must resolve and validate a separate persisted custom value before CLI use
 */
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
