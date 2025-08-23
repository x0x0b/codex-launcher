package com.github.x0x0b.codexlauncher.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "CodexLauncherSettings", storages = [Storage("CodexLauncher.xml")])
class CodexLauncherSettings : PersistentStateComponent<CodexLauncherSettings.State> {
    data class State(
        var mode: Mode = Mode.DEFAULT,
        var model: Model = Model.DEFAULT,
        var customModel: String = ""
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun getArgs(): String {
        val parts = mutableListOf<String>()
        if (state.mode == Mode.FULL_AUTO) {
            parts += "--full-auto"
        }

        val modelName = when (state.model) {
            Model.DEFAULT -> null
            Model.CUSTOM -> state.customModel.trim().ifBlank { null }
            else -> state.model.cliName()
        }
        if (modelName != null) {
            parts += listOf("--model", modelName)
        }

        return parts.joinToString(" ")
    }
}
