package com.github.x0x0b.codexlauncher

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger

class LaunchCodexAction : AnAction("Launch Codex", "Open a Codex terminal", null), DumbAware {
    
    companion object {
        private const val CODEX_COMMAND = "codex"
        private const val NOTIFICATION_TITLE = "Codex Launcher"
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

        try {
            val terminalView = org.jetbrains.plugins.terminal.TerminalToolWindowManager.getInstance(project)
            val widget = terminalView.createShellWidget(baseDir, "Codex", true, true)

            val settings = service<CodexLauncherSettings>()
            val args = settings.getArgs()
            
            val httpService = ApplicationManager.getApplication().service<HttpTriggerService>()
            val port = httpService.getActualPort()
            
            if (port == 0) {
                logger.warn("HTTP service port is not available")
                notify(project, "HTTP service is not properly initialized", NotificationType.WARNING)
                return
            }
            
            val command = buildCommand(port, args)
            widget.sendCommandToExecute(command)
            
            logger.info("Codex command executed successfully: $command")
            
        } catch (t: Throwable) {
            logger.error("Failed to launch Codex", t)
            notify(project, "Failed to launch Codex: ${t.message}", NotificationType.ERROR)
        }
    }
    
    private fun buildCommand(port: Int, args: String): String {
        return buildString {
            append(CODEX_COMMAND)
            append(" -c 'notify=[\"curl\", \"-s\", \"-X\", \"POST\", \"http://localhost:$port/refresh\"]'")
            if (args.isNotBlank()) {
                append(" ")
                append(args)
            }
        }
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
