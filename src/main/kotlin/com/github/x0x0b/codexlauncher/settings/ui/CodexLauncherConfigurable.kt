package com.github.x0x0b.codexlauncher.settings.ui

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.settings.options.Model
import com.github.x0x0b.codexlauncher.settings.options.ModelReasoningEffort
import com.github.x0x0b.codexlauncher.settings.options.Mode
import com.github.x0x0b.codexlauncher.settings.options.WinShell
import com.intellij.ide.DataManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JEditorPane
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import java.awt.Component
import java.awt.Font
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import java.util.function.Consumer
import java.util.function.Predicate

class CodexLauncherConfigurable : SearchableConfigurable {
    private lateinit var root: JComponent
    private lateinit var modeFullAutoCheckbox: JBCheckBox
    private lateinit var modelCombo: JComboBox<Model>
    private lateinit var customModelField: JBTextField
    private lateinit var modelReasoningEffortCombo: JComboBox<ModelReasoningEffort>
    private lateinit var openFileOnChangeCheckbox: JBCheckBox
    private lateinit var enableNotificationCheckbox: JBCheckBox
    private lateinit var enableSearchCheckbox: JBCheckBox
    private lateinit var enableCdProjectRootCheckbox: JBCheckBox
    private lateinit var winShellCombo: JComboBox<WinShell>
    private lateinit var mcpConfigInputArea: JBTextArea
    private lateinit var mcpServerWarningLabel: JBLabel
    private lateinit var fileHandlingWarningLabel: JBLabel
    private lateinit var notificationsWarningLabel: JBLabel

    private val settings by lazy { service<CodexLauncherSettings>() }

    companion object {
        private val ALLOWED_CUSTOM_MODEL_REGEX = Regex("^[A-Za-z0-9._-]*$")
        private const val MCP_SERVER_CONFIGURABLE_ID = "com.intellij.mcpserver.settings"
        private const val NOTIFICATIONS_CONFIGURABLE_ID = "reference.settings.ide.settings.notifications"
        private const val TERMINAL_CONFIGURABLE_ID = "terminal"
        private const val COMMENT_FONT_SIZE_DELTA = 0.8f
    }

    override fun getId(): String = "com.github.x0x0b.codexlauncher.settings"

    override fun getDisplayName(): String = "Codex Launcher"

    override fun createComponent(): JComponent {

        // Model controls
        modelCombo = ComboBox(Model.entries.toTypedArray())
        customModelField = JBTextField()
        customModelField.emptyText.text = "e.g. gpt-5"
        customModelField.isEnabled = false

        // Model reasoning effort controls
        modelReasoningEffortCombo = ComboBox(ModelReasoningEffort.entries.toTypedArray())

        // Options controls
        modeFullAutoCheckbox = JBCheckBox("--full-auto (Low-friction sandboxed automatic execution)")
        enableSearchCheckbox = JBCheckBox("--search (Enable web search)")
        enableCdProjectRootCheckbox = JBCheckBox("--cd <project root> (Turn on only when you explicitly need to set the working directory.)")

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
            winShellCombo = ComboBox(WinShell.entries.toTypedArray()).apply {
                renderer = object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: JList<*>?,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean
                    ): Component {
                        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                        if (value is WinShell) {
                            text = value.toDisplayName()
                        }
                        return component
                    }
                }
            }
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
                        this.largeComment(
                            "<span style='color:#ef454a;'>Please select the option according to the setting specified in </span><a href='terminal'>Settings &gt; Tools &gt; Terminal &gt; Application Settings &gt; Shell Path</a>.",
                            action = HyperlinkEventAction { openApplicationConfigurable(TERMINAL_CONFIGURABLE_ID) }
                        )
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
                    this.largeComment("Some models may not support all reasoning effort levels.")
                }
                row("Model reasoning effort") {
                    cell(modelReasoningEffortCombo)
                }
            }
            group("Options") {
                row {
                    cell(modeFullAutoCheckbox)
                }
                row {
                    cell(enableSearchCheckbox)
                }
                row {
                    cell(enableCdProjectRootCheckbox)
                }
                row {
                    this.largeComment("For more information, run codex --help")
                }
            }
            group("File Handling") {
                row {
                    cell(fileHandlingWarningLabel)
                }
                row {
                    cell(openFileOnChangeCheckbox)
                }
                row {
                    this.largeComment("Changes will take effect after restarting Codex.")
                }
            }
            group("Notifications") {
                row {
                    cell(notificationsWarningLabel)
                }
                row {
                    cell(enableNotificationCheckbox)
                }
                row {
                    this.largeComment(
                        "Customize notification sounds and display options in <a href='notifications'>Settings &gt; Appearance &amp; Behavior &gt; Notifications &gt; CodexLauncher</a>.",
                        action = HyperlinkEventAction { openApplicationConfigurable(NOTIFICATIONS_CONFIGURABLE_ID) }
                    )
                }
                row {
                    this.largeComment("Changes will take effect after restarting Codex.")
                }
                row {
                    val link = HyperlinkLabel("Learn more about IntelliJ notification settings")
                    link.setHyperlinkTarget("https://www.jetbrains.com/help/idea/notifications.html")
                    cell(link)
                }
            }
            group("Integrated MCP Server (Experimental)") {
                row {
                    cell(mcpServerWarningLabel)
                }
                row {
                    this.largeComment(
                        "In <a href='mcp'>Tools &gt; MCP Server</a>, click the Copy Stdio Config button and paste it into the input field below. (2025.2+)",
                        action = HyperlinkEventAction { openApplicationConfigurable(MCP_SERVER_CONFIGURABLE_ID) }
                    )
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
                getEnableSearch() != s.enableSearch ||
                getEnableCdProjectRoot() != s.enableCdProjectRoot ||
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
        s.enableSearch = getEnableSearch()
        s.enableCdProjectRoot = getEnableCdProjectRoot()
        if (SystemInfo.isWindows) {
            s.winShell = getWinShell()
            // update legacy field
            s.isPowerShell73OrOver = false
        }
        s.mcpConfigInput = getMcpConfigInput()
    }

    override fun reset() {
        val s = settings.state
        modeFullAutoCheckbox.isSelected = (s.mode == Mode.FULL_AUTO)
        modelCombo.selectedItem = s.model
        customModelField.text = s.customModel
        customModelField.isEnabled = (s.model == Model.CUSTOM)
        modelReasoningEffortCombo.selectedItem = s.modelReasoningEffort
        openFileOnChangeCheckbox.isSelected = s.openFileOnChange
        enableNotificationCheckbox.isSelected = s.enableNotification
        enableSearchCheckbox.isSelected = s.enableSearch
        enableCdProjectRootCheckbox.isSelected = s.enableCdProjectRoot
        if (SystemInfo.isWindows) {
            winShellCombo.selectedItem = s.winShell
        }
        mcpConfigInputArea.text = s.mcpConfigInput
        updateWslDependentAvailability()
    }

    fun getMode(): Mode {
        return if (modeFullAutoCheckbox.isSelected) Mode.FULL_AUTO else Mode.DEFAULT
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

    private fun getEnableSearch(): Boolean {
        return enableSearchCheckbox.isSelected
    }

    private fun getEnableCdProjectRoot(): Boolean {
        return enableCdProjectRootCheckbox.isSelected
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

    private fun Row.largeComment(text: String, action: HyperlinkEventAction? = null): Cell<JEditorPane> {
        val commentCell = if (action != null) {
            comment(text, action = action)
        } else {
            comment(text)
        }
        return commentCell.applyToComponent {
            font = font.deriveFont(font.size2D + COMMENT_FONT_SIZE_DELTA)
        }
    }


    private fun openApplicationConfigurable(configurableId: String) {
        val dataContext = DataManager.getInstance().getDataContext(root)
        val settings = Settings.KEY.getData(dataContext)
        if (settings != null) {
            val target = settings.find(configurableId)
            if (target != null) {
                settings.select(target)
                return
            }
        }

        val predicate = Predicate<com.intellij.openapi.options.Configurable> { configurable ->
            configurable is SearchableConfigurable && configurable.id == configurableId
        }
        val noopConsumer = Consumer<com.intellij.openapi.options.Configurable> { }
        runCatching {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, predicate, noopConsumer)
        }
    }

}
