package com.github.x0x0b.codexlauncher.actions

import com.github.x0x0b.codexlauncher.http.HttpTriggerService
import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.terminal.CodexTerminalManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import java.nio.file.Path

class LaunchCodexAction : AnAction("Launch Codex", "Open a Codex terminal", null), DumbAware {

    companion object {
        private const val CODEX_COMMAND = "codex"
        private const val NOTIFICATION_TITLE = "Codex Launcher"
        private const val DEFAULT_TEXT = "Launch Codex"
        private const val DEFAULT_DESCRIPTION = "Open a Codex terminal"
        private const val ACTIVE_TEXT = "Insert File Path"
        private const val ACTIVE_DESCRIPTION = "Send the current file path to the Codex terminal"
        private val DEFAULT_ICON = IconLoader.getIcon("/icons/codex.svg", LaunchCodexAction::class.java)
        private val ACTIVE_ICON = IconLoader.getIcon("/icons/codex_active.svg", LaunchCodexAction::class.java)
    }

    private val logger = logger<LaunchCodexAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            logger.warn("No project context available for Codex launch")
            return
        }

        val terminalManager = project.service<CodexTerminalManager>()
        if (terminalManager.isCodexTerminalActive()) {
            val filePath = resolveSelectedFilePath(project)
            if (filePath == null) {
                notify(project, "No active file to send to Codex", NotificationType.INFORMATION)
                return
            }

            val sent = terminalManager.typeIntoActiveCodexTerminal(filePath)
            if (!sent) {
                notify(project, "Failed to send file path to Codex terminal", NotificationType.WARNING)
            } else {
                logger.info("Sent active file path to Codex terminal: $filePath")
            }
            return
        }

        val baseDir = project.basePath ?: System.getProperty("user.home")
        logger.info("Launching Codex in directory: $baseDir")

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
            terminalManager.launch(baseDir, command)

            logger.info("Codex command executed successfully: $command")

        } catch (t: Throwable) {
            logger.error("Failed to launch Codex", t)
            notify(project, "Failed to launch Codex: ${t.message}", NotificationType.ERROR)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        val project = e.project
        if (project == null) {
            e.presentation.icon = DEFAULT_ICON
            e.presentation.text = DEFAULT_TEXT
            e.presentation.description = DEFAULT_DESCRIPTION
            return
        }

        val manager = project.service<CodexTerminalManager>()
        val isActive = manager.isCodexTerminalActive()
        if (isActive) {
            e.presentation.icon = ACTIVE_ICON
            e.presentation.text = ACTIVE_TEXT
            e.presentation.description = ACTIVE_DESCRIPTION
        } else {
            e.presentation.icon = DEFAULT_ICON
            e.presentation.text = DEFAULT_TEXT
            e.presentation.description = DEFAULT_DESCRIPTION
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

    private fun notify(project: Project, content: String, type: NotificationType) {
        try {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("CodexLauncher")
            group.createNotification(NOTIFICATION_TITLE, content, type).notify(project)
        } catch (e: Exception) {
            logger.error("Failed to show notification: $content", e)
        }
    }

    private fun resolveSelectedFilePath(project: Project): String? {
        val editorManager = FileEditorManager.getInstance(project)
        val file = editorManager.selectedFiles.firstOrNull() ?: return null
        val rawPath = file.canonicalPath ?: file.presentableUrl ?: file.path
        if (rawPath.isNullOrBlank()) {
            return null
        }
        val projectBase = project.basePath
        val resolvedPath = if (!projectBase.isNullOrBlank()) {
            runCatching {
                val base = Path.of(projectBase).normalize()
                val target = Path.of(rawPath).normalize()
                if (target.startsWith(base)) base.relativize(target).toString() else rawPath
            }.getOrElse { rawPath }
        } else {
            rawPath
        }

        val lineRange = resolveSelectedLineRange(editorManager)

        return buildString {
            append(resolvedPath)
            if (lineRange != null) {
                append(':')
                append(lineRange.start)
                if (lineRange.end != null && lineRange.end != lineRange.start) {
                    append('-')
                    append(lineRange.end)
                }
            }
        }
    }

    private fun resolveSelectedLineRange(editorManager: FileEditorManager): LineRange? {
        val editor = editorManager.selectedTextEditor ?: return null
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            return null
        }
        val selectedText = selectionModel.selectedText
        if (selectedText.isNullOrEmpty()) {
            return null
        }

        val document = editor.document
        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd
        val startLine = runCatching { document.getLineNumber(startOffset) }.getOrElse {
            logger.warn("Failed to resolve start line number", it)
            return null
        }
        if (startLine < 0) {
            return null
        }
        val endLine = runCatching {
            val adjustedEnd = if (endOffset > 0 && endOffset > startOffset && endOffset == document.textLength) endOffset else endOffset - 1
            document.getLineNumber(adjustedEnd.coerceAtLeast(startOffset))
        }.getOrElse {
            logger.warn("Failed to resolve end line number", it)
            startLine
        }
        if (endLine < 0) {
            return LineRange(startLine + 1, null)
        }

        val start = startLine + 1
        val end = endLine + 1
        return if (end > start) LineRange(start, end) else LineRange(start, null)
    }

    private data class LineRange(val start: Int, val end: Int?)
}
