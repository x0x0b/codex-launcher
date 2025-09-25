package com.github.x0x0b.codexlauncher.settings.options

/**
 * Windows shell selection for command formatting.
 *
 * - `POWERSHELL_LT_73`: PowerShell prior to 7.3 (legacy escaping)
 * - `POWERSHELL_73_PLUS`: PowerShell 7.3 or later (modern escaping)
 * - `WSL`: Windows Subsystem for Linux; treated like Mac/Linux in formatting
 */
enum class WinShell {
    POWERSHELL_LT_73,
    POWERSHELL_73_PLUS,
    WSL;

    fun toDisplayName(): String = when (this) {
        POWERSHELL_LT_73 -> "PowerShell (< 7.3)"
        POWERSHELL_73_PLUS -> "PowerShell (>= 7.3)"
        WSL -> "WSL"
    }
}
