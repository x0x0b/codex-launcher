package com.github.x0x0b.codexlauncher.settings.options

/**
 * Model selection for the `--model` argument.
 *
 * `CUSTOM` is a display/persistence marker only. It is not a direct CLI token:
 * - `cliName()` returns an empty string for `CUSTOM`
 * - callers must resolve and validate a separate persisted custom model id before CLI use
 */
enum class Model {
    /** Do not pass --model. */
    DEFAULT,

    // 5.5 models
    GPT_5_5,

    // 5.4 models
    GPT_5_4,
    GPT_5_4_PRO,

    // 5.3 models
    GPT_5_3_CODEX,

    // 5.2 models
    GPT_5_2_CODEX,
    GPT_5_2,

    // New gpt-5.1 based models (optional additions)
    GPT_5_1,
    GPT_5_1_CODEX,
    GPT_5_1_CODEX_MAX,
    GPT_5_1_CODEX_MINI,

    // Legacy / existing models (kept for enterprise compatibility)
    GPT_5,
    GPT_5_CODEX,
    CODEX_MINI_LATEST,

    /** Use customModel from settings. */
    CUSTOM;

    fun cliName(): String = when (this) {
        DEFAULT -> ""
        // 5.5 models
        GPT_5_5 -> "gpt-5.5"

        // 5.4 models
        GPT_5_4 -> "gpt-5.4"
        GPT_5_4_PRO -> "gpt-5.4-pro"

        // 5.3 models
        GPT_5_3_CODEX -> "gpt-5.3-codex"

        // 5.2 models
        GPT_5_2_CODEX -> "gpt-5.2-codex"
        GPT_5_2 -> "gpt-5.2"

        // 5.1 models
        GPT_5_1 -> "gpt-5.1"
        GPT_5_1_CODEX -> "gpt-5.1-codex"
        GPT_5_1_CODEX_MAX -> "gpt-5.1-codex-max"
        GPT_5_1_CODEX_MINI -> "gpt-5.1-codex-mini"

        // Legacy
        GPT_5 -> "gpt-5"
        GPT_5_CODEX -> "gpt-5-codex"
        CODEX_MINI_LATEST -> "codex-mini-latest"

        CUSTOM -> ""
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"
        // 5.5 models
        GPT_5_5 -> "gpt-5.5"

        // 5.4 models
        GPT_5_4 -> "gpt-5.4"
        GPT_5_4_PRO -> "gpt-5.4-pro"

        // 5.3 models
        GPT_5_3_CODEX -> "gpt-5.3-codex"

        // 5.2 models
        GPT_5_2_CODEX -> "gpt-5.2-codex"
        GPT_5_2 -> "gpt-5.2"

        // 5.1 models
        GPT_5_1 -> "gpt-5.1"
        GPT_5_1_CODEX -> "gpt-5.1-codex"
        GPT_5_1_CODEX_MAX -> "gpt-5.1-codex-max"
        GPT_5_1_CODEX_MINI -> "gpt-5.1-codex-mini"

        // Legacy
        GPT_5 -> "gpt-5"
        GPT_5_CODEX -> "gpt-5-codex"
        CODEX_MINI_LATEST -> "codex-mini-latest"

        CUSTOM -> "Custom..."
    }

    override fun toString(): String = toDisplayName()
}
