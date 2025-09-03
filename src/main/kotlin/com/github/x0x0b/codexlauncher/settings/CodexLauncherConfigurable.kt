package com.github.x0x0b.codexlauncher.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.JBUI
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
import com.intellij.openapi.options.ShowSettingsUtil
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

class CodexLauncherConfigurable : SearchableConfigurable {
    private lateinit var root: JComponent
    private lateinit var modeDefaultRadio: JBRadioButton
    private lateinit var modeFullAutoRadio: JBRadioButton
    private lateinit var modelCombo: JComboBox<Model>
    private lateinit var customModelField: JBTextField
    private lateinit var openFileOnChangeCheckbox: JBCheckBox
    private lateinit var enableNotificationCheckbox: JBCheckBox
    private lateinit var mcpConfigInputArea: JBTextArea

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
        
        // File opening control
        openFileOnChangeCheckbox = JBCheckBox("Open files automatically when changed (experimental)")
        
        // Notification control
        enableNotificationCheckbox = JBCheckBox("Enable notifications when events are completed by Codex CLI")
        
        // MCP Configuration controls
        mcpConfigInputArea = JBTextArea(10, 50)
        mcpConfigInputArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        mcpConfigInputArea.lineWrap = false
        mcpConfigInputArea.wrapStyleWord = false

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
            }
            group("File Handling") {
                row {
                    cell(openFileOnChangeCheckbox)
                }
            }
            group("Notifications") {
                row {
                    cell(enableNotificationCheckbox)
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
            group("MCP Configuration") {
                row {
                    comment("Paste JSON configuration to convert to TOML format")
                }
                row("JSON Input:") {
                    cell(JBScrollPane(mcpConfigInputArea))
                        .resizableColumn()
                }
//                FIXME: Link to MCP settings does not work as expected
//                row {
//                    val mcpSettingsLink = HyperlinkLabel("Open MCP server settings")
//                    mcpSettingsLink.addHyperlinkListener {
//                        ShowSettingsUtil.getInstance().showSettingsDialog(
//                            null,
//                            "com.intellij.mcpserver.settings"
//                        )
//                    }
//                    cell(mcpSettingsLink)
//                }
            }
        }

        return root
    }

    override fun isModified(): Boolean {
        val s = settings.state
        return getMode() != s.mode ||
                getModel() != s.model ||
                getCustomModel() != s.customModel ||
                getOpenFileOnChange() != s.openFileOnChange ||
                getEnableNotification() != s.enableNotification ||
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
        s.openFileOnChange = getOpenFileOnChange()
        s.enableNotification = getEnableNotification()
        s.mcpConfigInput = getMcpConfigInput()
    }

    override fun reset() {
        val s = settings.state
        modeDefaultRadio.isSelected = (s.mode == Mode.DEFAULT)
        modeFullAutoRadio.isSelected = (s.mode == Mode.FULL_AUTO)
        modelCombo.selectedItem = s.model
        customModelField.text = s.customModel
        customModelField.isEnabled = (s.model == Model.CUSTOM)
        openFileOnChangeCheckbox.isSelected = s.openFileOnChange
        enableNotificationCheckbox.isSelected = s.enableNotification
        mcpConfigInputArea.text = s.mcpConfigInput
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
    
    private fun getOpenFileOnChange(): Boolean {
        return openFileOnChangeCheckbox.isSelected
    }
    
    private fun getEnableNotification(): Boolean {
        return enableNotificationCheckbox.isSelected
    }
    
    private fun getMcpConfigInput(): String {
        return mcpConfigInputArea.text ?: ""
    }
}
