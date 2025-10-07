package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Project-level service responsible for managing Codex terminals.
 * Encapsulates lookup, reuse, focus, and command execution logic so actions stay thin.
 */
@Service(Service.Level.PROJECT)
class CodexTerminalManager(private val project: Project) {

    companion object {
        private val CODEX_TERMINAL_KEY = Key.create<Boolean>("codex.launcher.codexTerminal")
        private val CODEX_TERMINAL_RUNNING_KEY = Key.create<Boolean>("codex.launcher.codexTerminal.running")
        private val CODEX_TERMINAL_CALLBACK_KEY = Key.create<Boolean>("codex.launcher.codexTerminal.callbackRegistered")
        private val CODEX_TERMINAL_WATCHER_KEY = Key.create<ScheduledFuture<*>>("codex.launcher.codexTerminal.commandWatcher")
        private const val COMMAND_WATCH_INITIAL_DELAY_MS = 1000L
        private const val COMMAND_WATCH_INTERVAL_MS = 1000L
    }

    private val logger = logger<CodexTerminalManager>()

    private data class CodexTerminal(val widget: TerminalWidget, val content: Content)

    /**
     * Launches or reuses the Codex terminal for the given command.
     * @throws Throwable when terminal creation or command execution fails.
     */
    fun launch(baseDir: String, command: String) {
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        var existingTerminal = locateCodexTerminal(terminalManager)

        existingTerminal?.let { terminal ->
            ensureTerminationCallback(terminal.widget, terminal.content)
            if (isCodexRunning(terminal)) {
                logger.info("Focusing active Codex terminal")
                focusCodexTerminal(terminalManager, terminal)
                return
            }

            if (reuseCodexTerminal(terminal, command)) {
                logger.info("Reused existing Codex terminal for new Codex run")
                focusCodexTerminal(terminalManager, terminal)
                return
            } else {
                clearCodexMetadata(terminalManager, terminal.widget)
                existingTerminal = null
            }
        }

        var widget: TerminalWidget? = null
        try {
            widget = terminalManager.createShellWidget(baseDir, "Codex", true, true)
            val content = markCodexTerminal(terminalManager, widget)
            if (!sendCommandToTerminal(widget, content, command)) {
                throw IllegalStateException("Failed to execute Codex command")
            }
            if (content != null) {
                focusCodexTerminal(terminalManager, CodexTerminal(widget, content))
            }
        } catch (sendError: Throwable) {
            widget?.let { clearCodexMetadata(terminalManager, it) }
            throw sendError
        }
    }

    /**
     * Returns true when the Codex terminal tab is currently selected in the terminal tool window.
     */
    fun isCodexTerminalActive(): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            val terminal = findDisplayedCodexTerminal(terminalManager) ?: return false
            isCodexRunning(terminal)
        } catch (t: Throwable) {
            logger.warn("Failed to inspect Codex terminal active state", t)
            false
        }
    }

    fun typeIntoActiveCodexTerminal(text: String): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            val terminal = findDisplayedCodexTerminal(terminalManager) ?: return false
            typeText(terminal.widget, text)
        } catch (t: Throwable) {
            logger.warn("Failed to type into Codex terminal", t)
            false
        }
    }

    private fun locateCodexTerminal(manager: TerminalToolWindowManager): CodexTerminal? = try {
        manager.terminalWidgets.asSequence().mapNotNull { widget ->
            val content = manager.getContainer(widget)?.content ?: return@mapNotNull null
            val isCodex = content.getUserData(CODEX_TERMINAL_KEY) == true || content.displayName == "Codex"
            if (!isCodex) {
                return@mapNotNull null
            }
            CodexTerminal(widget, content)
        }.firstOrNull()
    } catch (t: Throwable) {
        logger.warn("Failed to inspect existing terminal widgets", t)
        null
    }

    private fun findDisplayedCodexTerminal(
        manager: TerminalToolWindowManager
    ): CodexTerminal? {
        val terminal = locateCodexTerminal(manager) ?: return null
        val toolWindow = resolveTerminalToolWindow(manager) ?: return null
        val selectedContent = toolWindow.contentManager.selectedContent ?: return null
        if (selectedContent != terminal.content) {
            return null
        }

        val isDisplayed = toolWindow.isVisible
        if (!isDisplayed) {
            return null
        }

        return terminal
    }

    private fun focusCodexTerminal(
        manager: TerminalToolWindowManager,
        terminal: CodexTerminal
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            try {
                val toolWindow = resolveTerminalToolWindow(manager)
                if (toolWindow == null) {
                    logger.warn("Terminal tool window is not available for focusing Codex")
                    return@invokeLater
                }

                val contentManager = toolWindow.contentManager
                if (contentManager.selectedContent != terminal.content) {
                    contentManager.setSelectedContent(terminal.content, true)
                }

                toolWindow.activate({
                    try {
                        terminal.widget.requestFocus()
                    } catch (focusError: Throwable) {
                        logger.warn("Failed to request focus for Codex terminal", focusError)
                    }
                }, true)
            } catch (focusError: Throwable) {
                logger.warn("Failed to focus existing Codex terminal", focusError)
            }
        }
    }

    private fun resolveTerminalToolWindow(
        manager: TerminalToolWindowManager
    ) = manager.getToolWindow()
        ?: ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

    private fun markCodexTerminal(manager: TerminalToolWindowManager, widget: TerminalWidget): Content? {
        return try {
            manager.getContainer(widget)?.content?.also { content ->
                content.putUserData(CODEX_TERMINAL_KEY, true)
                setCodexRunning(content, false)
                ensureTerminationCallback(widget, content)
                content.displayName = "Codex"
            }
        } catch (t: Throwable) {
            logger.warn("Failed to tag Codex terminal metadata", t)
            null
        }
    }

    private fun clearCodexMetadata(manager: TerminalToolWindowManager, widget: TerminalWidget) {
        try {
            manager.getContainer(widget)?.content?.let { content ->
                clearCodexMetadata(content)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to clear Codex terminal metadata", t)
        }
    }

    private fun clearCodexMetadata(content: Content) {
        stopCommandWatcher(content)
        content.putUserData(CODEX_TERMINAL_KEY, null)
        content.putUserData(CODEX_TERMINAL_RUNNING_KEY, null)
        content.putUserData(CODEX_TERMINAL_CALLBACK_KEY, null)
    }

    private fun reuseCodexTerminal(
        terminal: CodexTerminal,
        command: String
    ): Boolean {
        ensureTerminationCallback(terminal.widget, terminal.content)
        return sendCommandToTerminal(terminal.widget, terminal.content, command)
    }

    private fun sendCommandToTerminal(
        widget: TerminalWidget,
        content: Content?,
        command: String
    ): Boolean {
        return try {
            widget.sendCommandToExecute(command)
            setCodexRunning(content, true)
            startCommandWatcher(widget, content)
            true
        } catch (t: Throwable) {
            logger.warn("Failed to execute Codex command", t)
            setCodexRunning(content, false)
            false
        }
    }

    private fun isCodexRunning(terminal: CodexTerminal): Boolean {
        val liveState = invokeIsCommandRunning(terminal.widget)
        if (liveState != null) {
            setCodexRunning(terminal.content, liveState)
            return liveState
        }
        return terminal.content.getUserData(CODEX_TERMINAL_RUNNING_KEY) ?: false
    }

    private fun setCodexRunning(content: Content?, running: Boolean) {
        content?.putUserData(CODEX_TERMINAL_RUNNING_KEY, running)
        if (!running) {
            stopCommandWatcher(content)
        }
    }

    private fun ensureTerminationCallback(widget: TerminalWidget, content: Content?) {
        if (content == null) return
        if (content.getUserData(CODEX_TERMINAL_CALLBACK_KEY) == true) return
        try {
            widget.addTerminationCallback({ setCodexRunning(content, false) }, content)
            content.putUserData(CODEX_TERMINAL_CALLBACK_KEY, true)
        } catch (t: Throwable) {
            logger.warn("Failed to register termination callback", t)
        }
    }

    private fun invokeIsCommandRunning(widget: TerminalWidget): Boolean? {
        if (SystemInfoRt.isWindows && ApplicationManager.getApplication().isDispatchThread) {
            // Windows implementation falls back to a blocking wait that freezes the EDT.
            return null
        }
        return runCatching {
            val method = widget.javaClass.methods.firstOrNull { it.name == "isCommandRunning" && it.parameterCount == 0 }
            method?.apply { isAccessible = true }?.invoke(widget) as? Boolean
        }.getOrNull()
    }

    private fun startCommandWatcher(widget: TerminalWidget, content: Content?) {
        if (content == null) return
        stopCommandWatcher(content)
        val future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
            if (project.isDisposed) {
                stopCommandWatcher(content)
                return@scheduleWithFixedDelay
            }
            val running = invokeIsCommandRunning(widget)
            if (running == null || running) {
                return@scheduleWithFixedDelay
            }
            stopCommandWatcher(content)
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    setCodexRunning(content, false)
                }
            }
        }, COMMAND_WATCH_INITIAL_DELAY_MS, COMMAND_WATCH_INTERVAL_MS, TimeUnit.MILLISECONDS)
        content.putUserData(CODEX_TERMINAL_WATCHER_KEY, future)
    }

    private fun stopCommandWatcher(content: Content?) {
        if (content == null) return
        val future = content.getUserData(CODEX_TERMINAL_WATCHER_KEY) ?: return
        future.cancel(false)
        content.putUserData(CODEX_TERMINAL_WATCHER_KEY, null)
    }

    private fun typeText(widget: TerminalWidget, text: String): Boolean {
        val connector = runCatching { widget.ttyConnector }.getOrNull()
        if (connector != null) {
            return runCatching {
                connector.write(text)
                true
            }.getOrElse {
                logger.warn("Failed to write to Codex terminal connector", it)
                false
            }
        }

        val methods = widget.javaClass.methods
        val typeMethod = methods.firstOrNull { it.name == "typeText" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (typeMethod != null) {
            return runCatching {
                typeMethod.isAccessible = true
                typeMethod.invoke(widget, text)
                true
            }.getOrElse {
                logger.warn("Failed to invoke typeText on Codex terminal", it)
                false
            }
        }

        val pasteMethod = methods.firstOrNull { it.name == "pasteText" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (pasteMethod != null) {
            return runCatching {
                pasteMethod.isAccessible = true
                pasteMethod.invoke(widget, text)
                true
            }.getOrElse {
                logger.warn("Failed to invoke pasteText on Codex terminal", it)
                false
            }
        }

        return false
    }
}
