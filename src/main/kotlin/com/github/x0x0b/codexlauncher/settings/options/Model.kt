package com.github.x0x0b.codexlauncher.settings.options

/**
 * Model selection for the `--model` argument.
 */
enum class Model {
    /** Do not pass --model. */
    DEFAULT,
    GPT_5_1,
    GPT_5_1_CODEX,
    CODEX_MINI_LATEST,
    /** Use customModel from settings. */
    CUSTOM;

    fun cliName(): String = when (this) {
        DEFAULT -> ""
        GPT_5_1 -> "gpt-5.1"
        GPT_5_1_CODEX -> "gpt-5.1-codex"
        CODEX_MINI_LATEST -> "gpt-5.1-codex-mini"
        CUSTOM -> ""
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"
        GPT_5 -> "gpt-5.1"
        GPT_5_CODEX -> "gpt-5.1-codex"
        CODEX_MINI_LATEST -> "gpt-5.1-codex-mini"
        CUSTOM -> "Custom..."
    }

    override fun toString(): String = toDisplayName()
}
