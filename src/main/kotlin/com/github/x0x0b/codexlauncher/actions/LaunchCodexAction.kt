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
import javax.swing.Icon

class LaunchCodexAction : AnAction(DEFAULT_TEXT, DEFAULT_DESCRIPTION, null), DumbAware {

    companion object {
        private const val CODEX_COMMAND = "codex"
        private const val NOTIFICATION_TITLE = "Codex Launcher"
        private const val DEFAULT_TEXT = "Launch Codex"
        private const val DEFAULT_DESCRIPTION = "Open a Codex terminal"
        private const val ACTIVE_TEXT = "Insert File Path into Codex"
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
            performInsert(project, terminalManager)
            return
        }

        launchCodex(project, terminalManager)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val state = determineToolbarState(e.project)
        e.presentation.icon = state.icon
        e.presentation.text = state.text
        e.presentation.description = state.description
    }

    private fun performInsert(project: Project, terminalManager: CodexTerminalManager) {
        val insertText = resolveInsertText(project)
        if (insertText == null) {
            notify(project, "No active file to send to Codex", NotificationType.INFORMATION)
            return
        }

        if (!terminalManager.typeIntoActiveCodexTerminal(insertText)) {
            notify(project, "Failed to send file path to Codex terminal", NotificationType.WARNING)
            return
        }

        logger.info("Sent active file path to Codex terminal: $insertText")
    }

    private fun launchCodex(project: Project, terminalManager: CodexTerminalManager) {
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
            val command = buildCommand(settings.getArgs(port, baseDir))
            terminalManager.launch(baseDir, command)
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
                append(' ')
                append(args)
            }
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        runCatching {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("CodexLauncher")
            group.createNotification(NOTIFICATION_TITLE, content, type).notify(project)
        }.onFailure { error ->
            logger.error("Failed to show notification: $content", error)
        }
    }

    private fun resolveInsertText(project: Project): String? {
        val payload = resolveInsertPayload(project) ?: return null
        return buildString {
            append(payload.relativePath)
            payload.lineRange?.let { range ->
                append(':')
                append(range.start)
                range.end?.takeIf { it != range.start }?.let { end ->
                    append('-')
                    append(end)
                }
            }
            append(' ')
        }
    }

    private fun resolveInsertPayload(project: Project): InsertPayload? {
        val editorManager = FileEditorManager.getInstance(project)
        val file = editorManager.selectedFiles.firstOrNull() ?: return null
        val rawPath = file.canonicalPath ?: file.presentableUrl ?: file.path
        if (rawPath.isNullOrBlank()) {
            return null
        }

        val relativePath = project.basePath?.let { basePath ->
            runCatching {
                val base = Path.of(basePath).normalize()
                val target = Path.of(rawPath).normalize()
                if (target.startsWith(base)) base.relativize(target).toString() else rawPath
            }.getOrElse { rawPath }
        } ?: rawPath

        val lineRange = resolveSelectedLineRange(editorManager)
        return InsertPayload(relativePath, lineRange)
    }

    private fun resolveSelectedLineRange(editorManager: FileEditorManager): LineRange? {
        val editor = editorManager.selectedTextEditor ?: return null
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            return null
        }
        if (selectionModel.selectedText.isNullOrEmpty()) {
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
            val adjustedEnd = when {
                endOffset <= startOffset -> startOffset
                endOffset == document.textLength -> endOffset
                else -> endOffset - 1
            }
            document.getLineNumber(adjustedEnd.coerceAtLeast(startOffset))
        }.getOrElse {
            logger.warn("Failed to resolve end line number", it)
            startLine
        }

        val start = startLine + 1
        val end = (endLine + 1).takeIf { it > start }
        return LineRange(start, end)
    }

    private fun determineToolbarState(project: Project?): ToolbarState {
        if (project == null) {
            return ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
        }

        val manager = project.service<CodexTerminalManager>()
        return if (manager.isCodexTerminalActive()) {
            ToolbarState(ACTIVE_ICON, ACTIVE_TEXT, ACTIVE_DESCRIPTION)
        } else {
            ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
        }
    }

    private data class InsertPayload(val relativePath: String, val lineRange: LineRange?)

    private data class LineRange(val start: Int, val end: Int?)

    private data class ToolbarState(val icon: Icon, val text: String, val description: String)
}
