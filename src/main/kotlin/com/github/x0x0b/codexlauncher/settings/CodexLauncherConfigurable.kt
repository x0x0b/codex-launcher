package com.github.x0x0b.codexlauncher.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class CodexLauncherConfigurable : SearchableConfigurable {
    private lateinit var root: JComponent
    private lateinit var modeDefaultRadio: JBRadioButton
    private lateinit var modeFullAutoRadio: JBRadioButton

    private val settings by lazy { service<CodexLauncherSettings>() }

    override fun getId(): String = "com.github.x0x0b.codexlauncher.settings"

    override fun getDisplayName(): String = "Codex Launcher"

    override fun createComponent(): JComponent {

        modeDefaultRadio = JBRadioButton(Mode.DEFAULT.toDisplayName())
        modeFullAutoRadio = JBRadioButton(Mode.FULL_AUTO.toDisplayName())

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
        }

        return root
    }

    override fun isModified(): Boolean {
        val s = settings.state
        return getMode() != s.mode
    }

    override fun apply() {
        val s = settings.state
        s.mode = getMode()
    }

    override fun reset() {
        val s = settings.state
        modeDefaultRadio.isSelected = (s.mode == Mode.DEFAULT)
        modeFullAutoRadio.isSelected = (s.mode == Mode.FULL_AUTO)
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
}
