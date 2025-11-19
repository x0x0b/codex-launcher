package com.github.x0x0b.geminilauncher.settings.options

/**
 * Model selection for the `--model` argument.
 */
enum class Model {
    /** Do not pass --model. */
    DEFAULT,

    GEMINI_1_5_PRO,
    GEMINI_1_5_FLASH,
    GEMINI_PRO,
    GEMINI_ULTRA,

    /** Use customModel from settings. */
    CUSTOM;

    fun cliName(): String = when (this) {
        DEFAULT -> ""

        GEMINI_1_5_PRO -> "gemini-1.5-pro"
        GEMINI_1_5_FLASH -> "gemini-1.5-flash"
        GEMINI_PRO -> "gemini-pro"
        GEMINI_ULTRA -> "gemini-ultra"

        CUSTOM -> ""
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"

        GEMINI_1_5_PRO -> "Gemini 1.5 Pro"
        GEMINI_1_5_FLASH -> "Gemini 1.5 Flash"
        GEMINI_PRO -> "Gemini Pro"
        GEMINI_ULTRA -> "Gemini Ultra"

        CUSTOM -> "Custom..."
    }

    val supportsReasoningEffort: Boolean
        get() = false // Gemini currently doesn't use reasoning effort like o1/o3

    override fun toString(): String = toDisplayName()
}