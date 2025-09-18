package com.github.x0x0b.codexlauncher

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

class LaunchCodexAction : AnAction("Launch Codex", "Open a Codex terminal", null), DumbAware {
    
    companion object {
        private const val CODEX_COMMAND = "codex"
        private const val NOTIFICATION_TITLE = "Codex Launcher"
        private val CODEX_TERMINAL_KEY = Key.create<Boolean>("codex.launcher.codexTerminal")
    }
    
    private val logger = logger<LaunchCodexAction>()
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            logger.warn("No project context available for Codex launch")
            return
        }
        
        val baseDir = project.basePath ?: System.getProperty("user.home")
        logger.info("Launching Codex in directory: $baseDir")

        val terminalManager = TerminalToolWindowManager.getInstance(project)

        findRunningCodexWidget(terminalManager)?.let { existingWidget ->
            logger.info("Found running Codex terminal; focusing existing tab instead of launching a new one")
            focusExistingCodexTerminal(project, terminalManager, existingWidget)
            return
        }

        try {
            val httpService = ApplicationManager.getApplication().service<HttpTriggerService>()
            val port = httpService.getActualPort()
            
            if (port == 0) {
                logger.warn("HTTP service port is not available")
                notify(project, "HTTP service is not properly initialized", NotificationType.WARNING)
                return
            }

            val settings = service<CodexLauncherSettings>()
            val args = settings.getArgs(port)
            
            val command = buildCommand(args)
            var widget: TerminalWidget? = null
            try {
                widget = terminalManager.createShellWidget(baseDir, "Codex", true, true)
                markCodexTerminal(terminalManager, widget)
                widget.sendCommandToExecute(command)
            } catch (sendError: Throwable) {
                widget?.let { clearCodexMetadata(terminalManager, it) }
                throw sendError
            }
            
            logger.info("Codex command executed successfully: $command")
            
        } catch (t: Throwable) {
            logger.error("Failed to launch Codex", t)
            notify(project, "Failed to launch Codex: ${t.message}", NotificationType.ERROR)
        }
    }

    private fun buildCommand(args: String): String {
        return buildString {
            append(CODEX_COMMAND)
            if (args.isNotBlank()) {
                append(" ")
                append(args)
            }
        }
    }

    private fun findRunningCodexWidget(manager: TerminalToolWindowManager): TerminalWidget? {
        return try {
            manager.terminalWidgets.firstOrNull { widget ->
                val content = manager.getContainer(widget)?.content
                if (content?.getUserData(CODEX_TERMINAL_KEY) != true) {
                    return@firstOrNull false
                }

                if (!widget.isCommandRunning()) {
                    content.putUserData(CODEX_TERMINAL_KEY, null)
                    return@firstOrNull false
                }

                true
            }
        } catch (t: Throwable) {
            logger.warn("Failed to inspect existing terminal widgets", t)
            null
        }
    }

    private fun focusExistingCodexTerminal(
        project: Project,
        manager: TerminalToolWindowManager,
        widget: TerminalWidget
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            try {
                val container = manager.getContainer(widget) ?: return@invokeLater
                val content = container.content ?: return@invokeLater
                val toolWindow = manager.getToolWindow()
                    ?: ToolWindowManager.getInstance(project)
                        .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
                if (toolWindow == null) {
                    logger.warn("Terminal tool window is not available for focusing Codex")
                    return@invokeLater
                }

                val contentManager = toolWindow.contentManager
                if (contentManager.selectedContent != content) {
                    contentManager.setSelectedContent(content, true)
                }

                toolWindow.activate({
                    try {
                        widget.requestFocus()
                    } catch (focusError: Throwable) {
                        logger.warn("Failed to request focus for Codex terminal", focusError)
                    }
                }, true)
            } catch (focusError: Throwable) {
                logger.warn("Failed to focus existing Codex terminal", focusError)
            }
        }
    }

    private fun markCodexTerminal(
        manager: TerminalToolWindowManager,
        widget: TerminalWidget
    ) {
        try {
            val content = manager.getContainer(widget)?.content ?: return
            content.putUserData(CODEX_TERMINAL_KEY, true)
        } catch (t: Throwable) {
            logger.warn("Failed to tag Codex terminal metadata", t)
        }
    }

    private fun clearCodexMetadata(manager: TerminalToolWindowManager, widget: TerminalWidget) {
        try {
            val content = manager.getContainer(widget)?.content
            if (content != null) {
                clearCodexMetadata(content)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to clear Codex terminal metadata", t)
        }
    }

    private fun clearCodexMetadata(content: Content) {
        content.putUserData(CODEX_TERMINAL_KEY, null)
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        try {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("CodexLauncher")
            group.createNotification(NOTIFICATION_TITLE, content, type).notify(project)
        } catch (e: Exception) {
            logger.error("Failed to show notification: $content", e)
        }
    }
}
