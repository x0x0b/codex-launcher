package com.github.x0x0b.codexlauncher.settings

import com.github.x0x0b.codexlauncher.cli.CodexArgsBuilder
import com.github.x0x0b.codexlauncher.settings.options.Model
import com.github.x0x0b.codexlauncher.settings.options.ModelReasoningEffort
import com.github.x0x0b.codexlauncher.settings.options.Mode
import com.github.x0x0b.codexlauncher.settings.options.WinShell
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Application-level settings service for Codex Launcher plugin.
 * 
 * This service manages the persistent configuration including:
 * - Launch mode (DEFAULT, FULL_AUTO)
 * - Model selection (DEFAULT, GPT_5, CODEX_MINI_LATEST, CUSTOM)
 * - Custom model identifier for CUSTOM mode
 * - File opening behavior preferences
 * 
 * Settings are automatically persisted to CodexLauncher.xml in the IDE's configuration directory.
 * 
 * @since 1.0.0
 */

@Service(Service.Level.APP)
@State(name = "CodexLauncherSettings", storages = [Storage("CodexLauncher.xml")])
class CodexLauncherSettings : PersistentStateComponent<CodexLauncherSettings.State> {
    /**
     * Data class representing the persistent state of the plugin settings.
     * 
     * @property mode The launch mode for codex execution
     * @property model The selected model for codex
     * @property customModel Custom model identifier when model is set to CUSTOM
     * @property openFileOnChange Whether to automatically open files when they change
     * @property enableNotification Whether to enable notifications
     * @property isPowerShell73OrOver Whether using PowerShell 7.3 or later (legacy; use winShell instead)
     * @property winShell Preferred Windows shell selection (Windows only)
     */
    data class State(
        var mode: Mode = Mode.DEFAULT,
        var model: Model = Model.DEFAULT,
        var customModel: String = "",
        var modelReasoningEffort: ModelReasoningEffort = ModelReasoningEffort.DEFAULT,
        var openFileOnChange: Boolean = false,
        var enableNotification: Boolean = false,
        var mcpConfigInput: String = "",
        var isPowerShell73OrOver: Boolean = false, // Legacy flag, use winShell instead
        var winShell: WinShell = WinShell.POWERSHELL_LT_73
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        // Migrate legacy isPowerShell73OrOver flag to winShell enum
        if (state.isPowerShell73OrOver) {
            state.winShell = WinShell.POWERSHELL_73_PLUS
            state.isPowerShell73OrOver = false
        }

        XmlSerializerUtil.copyBean(state, this.state)
    }

    /**
     * Builds and returns the command-line arguments for codex based on current settings,
     * including notify command configuration.
     * 
     * @param port HTTP service port for notify command
     * @return A space-separated string of command-line arguments
     */
    fun getArgs(port: Int): String = CodexArgsBuilder.build(state, port).joinToString(" ")
}
