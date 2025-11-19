package com.github.x0x0b.geminilauncher.mcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.Alarm
import com.intellij.util.messages.MessageBusConnection

@Service(Service.Level.APP)
class IdeContextTracker : Disposable {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val connection: MessageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)

    init {
        // Listen for file focus changes (Application level listener might capture all projects)
        // Actually FileEditorManagerListener is usually project level. 
        // We need to listen to all open projects.
        
        // Listen for editor creation to attach caret listeners
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                editor.caretModel.addCaretListener(object : CaretListener {
                    override fun caretPositionChanged(e: CaretEvent) {
                        scheduleUpdate()
                    }
                }, this@IdeContextTracker)
                
                editor.selectionModel.addSelectionListener(object : SelectionListener {
                    override fun selectionChanged(e: SelectionEvent) {
                        scheduleUpdate()
                    }
                }, this@IdeContextTracker)
            }
        }, this)
        
        // We also need to listen to FileEditorManager events. 
        // Since projects can open/close, we should dynamically attach listeners.
        // For simplicity in this app-level service, we can iterate open projects on each update 
        // or use a ProjectManagerListener to attach FileEditorManagerListener.
        // Let's rely on the fact that caret/focus changes trigger most things. 
        // But switching tabs needs FileEditorManagerListener.
    }
    
    // Helper to attach project listeners - call this from StartupActivity
    fun attachProjectListeners(project: Project) {
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                scheduleUpdate()
            }

            override fun fileOpened(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
                scheduleUpdate()
            }

            override fun fileClosed(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
                scheduleUpdate()
            }
        })
    }

    private fun scheduleUpdate() {
        alarm.cancelAllRequests()
        alarm.addRequest({ sendContextUpdate() }, 50)
    }

    private fun sendContextUpdate() {
        ApplicationManager.getApplication().runReadAction {
            val openProjects = ProjectManager.getInstance().openProjects
            // We aggregate context from the active project mostly.
            // How to determine active project? 
            // IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project
            
            val context = buildContext()
            service<McpServer>().sendNotification("ide/contextUpdate", context)
        }
    }

    private fun buildContext(): Map<String, Any> {
        // Simplified context building logic
        // Iterate through all editors in all projects to find the "active" one?
        // The spec says: "The CLI considers only the most recent file... to be the 'active' file."
        
        val openFilesList = mutableListOf<Map<String, Any>>()
        val projects = ProjectManager.getInstance().openProjects
        
        // We need a global view of "focused" file.
        // IntelliJ doesn't maintain a single global "last focused file timestamp" easily accessible across projects 
        // without our own tracking. 
        // But typically user is in one project.
        
        for (project in projects) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val openFiles = fileEditorManager.openFiles
            val selectedFile = fileEditorManager.selectedFiles.firstOrNull()
            val editor = fileEditorManager.selectedTextEditor

            for (file in openFiles) {
                if (file.path == null) continue
                
                val isActive = (file == selectedFile) && (project.isOpen) // simplistic active check
                
                val fileData = mutableMapOf<String, Any>(
                    "path" to file.path,
                    "timestamp" to System.currentTimeMillis() // We should track real timestamps
                )
                
                if (isActive) {
                    fileData["isActive"] = true
                    if (editor != null) {
                        val caret = editor.caretModel.primaryCaret
                        val logicalPos = caret.logicalPosition
                        fileData["cursor"] = mapOf(
                            "line" to logicalPos.line + 1,
                            "character" to logicalPos.column + 1
                        )
                        
                        val selection = editor.selectionModel.selectedText
                        if (selection != null) {
                            fileData["selectedText"] = selection
                        }
                    }
                }
                openFilesList.add(fileData)
            }
        }

        return mapOf(
            "workspaceState" to mapOf(
                "openFiles" to openFilesList,
                "isTrusted" to true
            )
        )
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }
}
