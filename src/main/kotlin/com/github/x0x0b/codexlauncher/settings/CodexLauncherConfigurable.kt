package com.github.x0x0b.codexlauncher.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import com.intellij.openapi.ui.Messages
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

class CodexLauncherConfigurable : SearchableConfigurable {
    private lateinit var root: JComponent
    private lateinit var modeDefaultRadio: JBRadioButton
    private lateinit var modeFullAutoRadio: JBRadioButton
    private lateinit var modelCombo: JComboBox<Model>
    private lateinit var customModelField: JBTextField
    private lateinit var modelReasoningEffortCombo: JComboBox<ModelReasoningEffort>
    private lateinit var openFileOnChangeCheckbox: JBCheckBox
    private lateinit var enableNotificationCheckbox: JBCheckBox
    private lateinit var winShellCombo: JComboBox<WinShell>
    private lateinit var mcpConfigInputArea: JBTextArea
    private lateinit var mcpServerWarningLabel: JBLabel
    private lateinit var fileHandlingWarningLabel: JBLabel
    private lateinit var notificationsWarningLabel: JBLabel

    private val settings by lazy { service<CodexLauncherSettings>() }

    companion object {
        private val ALLOWED_CUSTOM_MODEL_REGEX = Regex("^[A-Za-z0-9._-]*$")
    }

    override fun getId(): String = "com.github.x0x0b.codexlauncher.settings"

    override fun getDisplayName(): String = "Codex Launcher"

    override fun createComponent(): JComponent {
        // Mode controls
        modeDefaultRadio = JBRadioButton(Mode.DEFAULT.toDisplayName())
        modeFullAutoRadio = JBRadioButton(Mode.FULL_AUTO.toDisplayName())

        // Model controls
        modelCombo = ComboBox(Model.entries.toTypedArray())
        customModelField = JBTextField()
        customModelField.emptyText.text = "e.g. gpt-5"
        customModelField.isEnabled = false

        // Model reasoning effort controls
        modelReasoningEffortCombo = ComboBox(ModelReasoningEffort.entries.toTypedArray())

        // File opening control
        openFileOnChangeCheckbox = JBCheckBox("Open files automatically when changed")
        fileHandlingWarningLabel = JBLabel("File Handling is unavailable when WSL shell is selected.").apply {
            foreground = UIUtil.getErrorForeground()
            border = JBUI.Borders.emptyTop(4)
            isVisible = false
        }

        // Notification control
        enableNotificationCheckbox = JBCheckBox("Enable notifications when events are completed by Codex CLI")
        notificationsWarningLabel = JBLabel("Notifications are unavailable when WSL shell is selected.").apply {
            foreground = UIUtil.getErrorForeground()
            border = JBUI.Borders.emptyTop(4)
            isVisible = false
        }

        // Windows shell selection (Windows only)
        if (SystemInfo.isWindows) {
            winShellCombo = ComboBox(WinShell.entries.toTypedArray())
            winShellCombo.addActionListener { updateWslDependentAvailability() }
        }

        // MCP Configuration controls
        mcpConfigInputArea = JBTextArea(5, 50)
        mcpConfigInputArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        mcpConfigInputArea.lineWrap = false
        mcpConfigInputArea.wrapStyleWord = false
        mcpConfigInputArea.emptyText.text = "Paste MCP stdio config here"

        mcpServerWarningLabel = JBLabel("Integrated MCP Server is unavailable when WSL shell is selected.").apply {
            foreground = UIUtil.getErrorForeground()
            border = JBUI.Borders.emptyTop(4)
            isVisible = false
        }

        // Block invalid characters at input time
        (customModelField.document as? AbstractDocument)?.documentFilter = object : DocumentFilter() {

            override fun insertString(fb: FilterBypass, offset: Int, string: String?, attr: AttributeSet?) {
                if (string == null) return
                val doc = fb.document
                val current = doc.getText(0, doc.length)
                val next = StringBuilder(current).insert(offset, string).toString()
                if (ALLOWED_CUSTOM_MODEL_REGEX.matches(next)) {
                    super.insertString(fb, offset, string, attr)
                }
            }

            override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
                val doc = fb.document
                val current = doc.getText(0, doc.length)
                val next = StringBuilder(current).replace(offset, offset + length, text ?: "").toString()
                if (ALLOWED_CUSTOM_MODEL_REGEX.matches(next)) {
                    super.replace(fb, offset, length, text, attrs)
                }
            }
        }
        modelCombo.addActionListener {
            val selected = (modelCombo.selectedItem as? Model) ?: Model.DEFAULT
            customModelField.isEnabled = (selected == Model.CUSTOM)
        }

        root = panel {
            if (SystemInfo.isWindows) {
                group("Windows Shell") {
                    row("Shell") {
                        cell(winShellCombo)
                    }
                    row {
                        comment("WSL will be treated the same as Mac/Linux for command formatting.")
                    }
                }
            }
            group("Mode") {
                buttonsGroup {
                    row{
                        cell(modeDefaultRadio)
                    }
                    row{
                        cell(modeFullAutoRadio)
                    }
                }
            }
            group("Model") {
                row("Model") {
                    cell(modelCombo)
                }
                row("Custom model id") {
                    cell(customModelField)
                        .resizableColumn()
                        .applyToComponent { columns = 50 }
                }
                row {
                    comment("Some models may not support all reasoning effort levels.")
                }
                row("Model reasoning effort") {
                    cell(modelReasoningEffortCombo)
                }
            }
            group("File Handling") {
                row {
                    cell(openFileOnChangeCheckbox)
                }
                row {
                    cell(fileHandlingWarningLabel)
                }
            }
            group("Notifications (Experimental)") {
                row {
                    cell(enableNotificationCheckbox)
                }
                row {
                    cell(notificationsWarningLabel)
                }
                row {
                    comment("Customize notification sounds and display options in Settings | Appearance & Behavior | Notifications | CodexLauncher.")
                }
                row {
                    val link = HyperlinkLabel("Learn more about IntelliJ notification settings")
                    link.setHyperlinkTarget("https://www.jetbrains.com/help/idea/notifications.html")
                    cell(link)
                }
            }
            group("Integrated MCP Server (Experimental)") {
                row {
                    comment("In Tools > MCP Server, click the Copy Stdio Config button and paste it into the input field below. (2025.2+)")
                }
                row {
                    cell(mcpServerWarningLabel)
                }
                row("Stdio Config:") {
                    cell(JBScrollPane(mcpConfigInputArea))
                        .resizableColumn()
                }
                row {
                    val link = HyperlinkLabel("Learn more about integrated MCP Server")
                    link.setHyperlinkTarget("https://www.jetbrains.com/help/idea/mcp-server.html")
                    cell(link)
                }
            }
        }

        updateWslDependentAvailability()

        return root
    }

    override fun isModified(): Boolean {
        val s = settings.state
        return getMode() != s.mode ||
                getModel() != s.model ||
                getCustomModel() != s.customModel ||
                getModelReasoningEffort() != s.modelReasoningEffort ||
                getOpenFileOnChange() != s.openFileOnChange ||
                getEnableNotification() != s.enableNotification ||
                (SystemInfo.isWindows && getWinShell() != s.winShell) ||
                getMcpConfigInput() != s.mcpConfigInput
    }

    override fun apply() {
        // Validate custom model id before saving
        val selected = getModel()
        val custom = getCustomModel()
        if (selected == Model.CUSTOM && !ALLOWED_CUSTOM_MODEL_REGEX.matches(custom)) {
            throw ConfigurationException("Invalid custom model id. Allowed: letters, digits, '.', '-', '_'")
        }
        val s = settings.state
        s.mode = getMode()
        s.model = getModel()
        s.customModel = getCustomModel()
        s.modelReasoningEffort = getModelReasoningEffort()
        s.openFileOnChange = getOpenFileOnChange()
        s.enableNotification = getEnableNotification()
        if (SystemInfo.isWindows) {
            s.winShell = getWinShell()
            // update legacy field
            s.isPowerShell73OrOver = false
        }
        s.mcpConfigInput = getMcpConfigInput()
    }

    override fun reset() {
        val s = settings.state
        modeDefaultRadio.isSelected = (s.mode == Mode.DEFAULT)
        modeFullAutoRadio.isSelected = (s.mode == Mode.FULL_AUTO)
        modelCombo.selectedItem = s.model
        customModelField.text = s.customModel
        customModelField.isEnabled = (s.model == Model.CUSTOM)
        modelReasoningEffortCombo.selectedItem = s.modelReasoningEffort
        openFileOnChangeCheckbox.isSelected = s.openFileOnChange
        enableNotificationCheckbox.isSelected = s.enableNotification
        if (SystemInfo.isWindows) {
            winShellCombo.selectedItem = s.winShell
        }
        mcpConfigInputArea.text = s.mcpConfigInput
        updateWslDependentAvailability()
    }

    fun getMode(): Mode {
        return when {
            modeDefaultRadio.isSelected -> Mode.DEFAULT
            modeFullAutoRadio.isSelected -> Mode.FULL_AUTO
            else -> Mode.DEFAULT // Fallback
        }
    }

    private fun getModel(): Model {
        return (modelCombo.selectedItem as? Model) ?: Model.DEFAULT
    }

    private fun getCustomModel(): String {
        return customModelField.text?.trim() ?: ""
    }

    private fun getModelReasoningEffort(): ModelReasoningEffort {
        return (modelReasoningEffortCombo.selectedItem as? ModelReasoningEffort) ?: ModelReasoningEffort.DEFAULT
    }

    private fun getOpenFileOnChange(): Boolean {
        return openFileOnChangeCheckbox.isSelected
    }

    private fun getEnableNotification(): Boolean {
        return enableNotificationCheckbox.isSelected
    }

    private fun getWinShell(): WinShell {
        return if (SystemInfo.isWindows) {
            (winShellCombo.selectedItem as? WinShell) ?: WinShell.POWERSHELL_LT_73
        } else {
            WinShell.POWERSHELL_LT_73
        }
    }

    private fun updateWslDependentAvailability() {
        if (!::mcpConfigInputArea.isInitialized ||
            !::mcpServerWarningLabel.isInitialized ||
            !::fileHandlingWarningLabel.isInitialized ||
            !::notificationsWarningLabel.isInitialized
        ) {
            return
        }
        val isWslSelected = SystemInfo.isWindows &&
                ::winShellCombo.isInitialized &&
                (winShellCombo.selectedItem as? WinShell) == WinShell.WSL

        mcpServerWarningLabel.isVisible = isWslSelected
        fileHandlingWarningLabel.isVisible = isWslSelected
        notificationsWarningLabel.isVisible = isWslSelected

        mcpConfigInputArea.isEnabled = !isWslSelected
        openFileOnChangeCheckbox.isEnabled = !isWslSelected
        enableNotificationCheckbox.isEnabled = !isWslSelected

        mcpConfigInputArea.toolTipText = if (isWslSelected) {
            "Integrated MCP Server is unavailable when WSL shell is selected."
        } else {
            null
        }

        openFileOnChangeCheckbox.toolTipText = if (isWslSelected) {
            "File Handling is unavailable when WSL shell is selected."
        } else {
            null
        }

        enableNotificationCheckbox.toolTipText = if (isWslSelected) {
            "Notifications are unavailable when WSL shell is selected."
        } else {
            null
        }
    }

    private fun getMcpConfigInput(): String {
        return mcpConfigInputArea.text ?: ""
    }
}
