package com.github.x0x0b.geminilauncher.actions

import com.github.x0x0b.geminilauncher.http.HttpTriggerService
import com.github.x0x0b.geminilauncher.mcp.McpServer
import com.github.x0x0b.geminilauncher.settings.GeminiLauncherSettings
import com.github.x0x0b.geminilauncher.settings.options.WinShell
import com.github.x0x0b.geminilauncher.terminal.GeminiTerminalManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import javax.swing.Icon

class LaunchGeminiAction : AnAction(DEFAULT_TEXT, DEFAULT_DESCRIPTION, null), DumbAware {

    companion object {
        private const val GEMINI_COMMAND = "gemini"
        private const val NOTIFICATION_TITLE = "Gemini Launcher"
        private const val DEFAULT_TEXT = "Launch Gemini"
        private const val DEFAULT_DESCRIPTION = "Open a Gemini terminal"
        private const val ACTIVE_TEXT = "Insert File Path into Gemini"
        private const val ACTIVE_DESCRIPTION = "Send the current file path to the Gemini terminal"
        private val DEFAULT_ICON = IconLoader.getIcon("/icons/gemini.svg", LaunchGeminiAction::class.java)
        private val ACTIVE_ICON = IconLoader.getIcon("/icons/gemini_active.svg", LaunchGeminiAction::class.java)
    }

    private val logger = logger<LaunchGeminiAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            logger.warn("No project context available for Gemini launch")
            return
        }

        val terminalManager = project.service<GeminiTerminalManager>()
        if (terminalManager.isGeminiTerminalActive()) {
            performInsert(project, terminalManager)
            return
        }

        launchGemini(project, terminalManager)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val state = determineToolbarState(e.project)
        e.presentation.icon = state.icon
        e.presentation.text = state.text
        e.presentation.description = state.description
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private fun performInsert(project: Project, terminalManager: GeminiTerminalManager) {
        val insertText = resolveInsertText(project)
        if (insertText == null) {
            notify(project, "No active file to send to Gemini", NotificationType.INFORMATION)
            return
        }

        if (!terminalManager.typeIntoActiveGeminiTerminal(insertText)) {
            notify(project, "Failed to send file path to Gemini terminal", NotificationType.WARNING)
            return
        }

        logger.info("Sent active file path to Gemini terminal: $insertText")
    }

    private fun launchGemini(project: Project, terminalManager: GeminiTerminalManager) {
        val baseDir = project.basePath ?: System.getProperty("user.home")
        logger.info("Launching Gemini in directory: $baseDir")

        try {
            val httpService = ApplicationManager.getApplication().service<HttpTriggerService>()
            val port = httpService.getActualPort()
            // Note: port can be 0 if failed, handled gracefully by CLI usually or we warn
            
            val mcpServer = ApplicationManager.getApplication().service<McpServer>()
            val mcpPort = mcpServer.port

            val settings = service<GeminiLauncherSettings>()
            val args = settings.getArgs(port, baseDir)
            val command = buildCommand(args, mcpPort, settings.state.winShell)
            
            terminalManager.launch(baseDir, command)
            logger.info("Gemini command executed successfully: $command")
        } catch (t: Throwable) {
            logger.error("Failed to launch Gemini", t)
            notify(project, "Failed to launch Gemini: ${t.message}", NotificationType.ERROR)
        }
    }

    private fun buildCommand(args: String, mcpPort: Int, winShell: WinShell): String {
        val geminiCmd = buildString {
            append(GEMINI_COMMAND)
            if (args.isNotBlank()) {
                append(' ')
                append(args)
            }
        }
        
        if (mcpPort <= 0) {
            return geminiCmd
        }

        // Prepend environment variable for tie-breaking if possible
        return if (SystemInfo.isWindows) {
            if (winShell == WinShell.WSL) {
                 "export GEMINI_CLI_IDE_SERVER_PORT=$mcpPort && $geminiCmd"
            } else if (winShell == WinShell.POWERSHELL_73_PLUS || winShell == WinShell.POWERSHELL_LT_73) {
                 // PowerShell syntax: $env:VAR='val'; cmd
                 "\$env:GEMINI_CLI_IDE_SERVER_PORT='$mcpPort'; $geminiCmd"
            } else {
                // Default cmd syntax? IntelliJ terminal on Windows typically uses cmd.exe or PowerShell.
                // If it's cmd.exe: set VAR=val && cmd
                "set GEMINI_CLI_IDE_SERVER_PORT=$mcpPort && $geminiCmd"
            }
        } else {
            // Unix/Mac
            "export GEMINI_CLI_IDE_SERVER_PORT=$mcpPort && $geminiCmd"
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        runCatching {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("GeminiLauncher")
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

        val manager = project.service<GeminiTerminalManager>()
        return if (manager.isGeminiTerminalActive()) {
            ToolbarState(ACTIVE_ICON, ACTIVE_TEXT, ACTIVE_DESCRIPTION)
        } else {
            ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
        }
    }

    private data class InsertPayload(val relativePath: String, val lineRange: LineRange?)

    private data class LineRange(val start: Int, val end: Int?)

    private data class ToolbarState(val icon: Icon, val text: String, val description: String)
}
