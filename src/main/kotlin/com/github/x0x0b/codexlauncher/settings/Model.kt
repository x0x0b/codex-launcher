package com.github.x0x0b.codexlauncher.settings

/**
 * Model selection for the `--model` argument.
 */
enum class Model {
    /** Do not pass --model. */
    DEFAULT,
    GPT_5,
    GPT_5_CODEX,
    CODEX_MINI_LATEST,
    /** Use customModel from settings. */
    CUSTOM;

    fun cliName(): String = when (this) {
        DEFAULT -> ""
        GPT_5 -> "gpt-5"
        GPT_5_CODEX -> "gpt-5-codex"
        CODEX_MINI_LATEST -> "codex-mini-latest"
        CUSTOM -> ""
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"
        GPT_5 -> "gpt-5"
        GPT_5_CODEX -> "gpt-5-codex"
        CODEX_MINI_LATEST -> "codex-mini-latest"
        CUSTOM -> "Custom..."
    }

    override fun toString(): String = toDisplayName()
}
