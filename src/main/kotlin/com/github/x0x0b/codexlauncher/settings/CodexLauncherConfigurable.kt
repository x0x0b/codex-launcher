package com.github.x0x0b.codexlauncher.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

class CodexLauncherConfigurable : SearchableConfigurable {
    private lateinit var root: JComponent
    private lateinit var modeDefaultRadio: JBRadioButton
    private lateinit var modeFullAutoRadio: JBRadioButton
    private lateinit var modelCombo: JComboBox<Model>
    private lateinit var customModelField: JBTextField

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
        }

        return root
    }

    override fun isModified(): Boolean {
        val s = settings.state
        return getMode() != s.mode ||
                getModel() != s.model ||
                getCustomModel() != s.customModel
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
    }

    override fun reset() {
        val s = settings.state
        modeDefaultRadio.isSelected = (s.mode == Mode.DEFAULT)
        modeFullAutoRadio.isSelected = (s.mode == Mode.FULL_AUTO)
        modelCombo.selectedItem = s.model
        customModelField.text = s.customModel
        customModelField.isEnabled = (s.model == Model.CUSTOM)
    }

    fun getMode(): Mode {
        if (modeDefaultRadio.isSelected) {
            return Mode.DEFAULT
        } else if (modeFullAutoRadio.isSelected) {
            return Mode.FULL_AUTO
        } else {
            // Fallback
            return Mode.DEFAULT
        }
    }

    private fun getModel(): Model {
        return (modelCombo.selectedItem as? Model) ?: Model.DEFAULT
    }

    private fun getCustomModel(): String {
        return customModelField.text?.trim() ?: ""
    }
}
