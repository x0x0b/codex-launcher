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

class LaunchCodexAction : AnAction("Launch Codex", "Open a Codex terminal", null), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val baseDir = project.basePath ?: System.getProperty("user.home")

        try {
            val terminalView = org.jetbrains.plugins.terminal.TerminalToolWindowManager.getInstance(project)
            val widget = terminalView.createShellWidget(baseDir, "Codex", true, true)

            val settings = service<CodexLauncherSettings>()
            val args = settings.getArgs()
            
            // HttpTriggerServiceから実際のポート番号を取得
            val httpService = ApplicationManager.getApplication().service<HttpTriggerService>()
            val port = httpService.getActualPort()
            
            val command = buildString {
                append("codex")
                append(" -c 'notify=[\"curl\", \"-s\", \"-X\", \"POST\", \"http://localhost:$port/refresh\"]'")
                if (args.isNotBlank()) {
                    append(" ")
                    append(args)
                }
            }
            widget.sendCommandToExecute(command)
        } catch (t: Throwable) {
            notify(project, "Failed to launch Codex: ${t.message}", NotificationType.ERROR)
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup("CodexLauncher")
        group.createNotification(content, type).notify(project)
    }
}
