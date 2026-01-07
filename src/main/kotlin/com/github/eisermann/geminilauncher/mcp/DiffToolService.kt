package com.github.eisermann.geminilauncher.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.diff.DiffContentFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

@Service(Service.Level.APP)
class DiffToolService {
    private val logger = logger<DiffToolService>()

    fun openDiff(filePath: String, newContent: String): JsonObject {
        val result = JsonObject()
        val contentArray = JsonArray()
        result.add("content", contentArray)
        
        // Find the file and project
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (virtualFile == null) {
            result.addProperty("isError", true)
            val errorContent = JsonObject()
            errorContent.addProperty("type", "text")
            errorContent.addProperty("text", "File not found: $filePath")
            contentArray.add(errorContent)
            return result
        }

        val project = ProjectManager.getInstance().openProjects.find { 
            filePath.startsWith(it.basePath ?: "") 
        }
        
        if (project == null) {
            result.addProperty("isError", true)
            val errorContent = JsonObject()
            errorContent.addProperty("type", "text")
            errorContent.addProperty("text", "No open project found for file: $filePath")
            contentArray.add(errorContent)
            return result
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                // Use custom tool window with Accept/Reject buttons
                val diffToolWindow = project.service<DiffReviewToolWindow>()
                diffToolWindow.showDiff(filePath, newContent)
            } catch (e: Exception) {
                logger.error("Failed to open diff", e)
            }
        }

        result.addProperty("isError", false)
        return result
    }

    fun closeDiff(filePath: String): JsonObject {
        // closing diff programmatically in IntelliJ is not straightforward if it's a modal dialog.
        // If it's a tool window tab, we can close it.
        // For now, we return success but log that it's not fully implemented.
        
        val result = JsonObject()
        val contentArray = JsonArray()
        val textContent = JsonObject()
        textContent.addProperty("type", "text")
        
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        val currentContent = if (virtualFile != null) {
            ReadAction.compute<String, Throwable> {
                FileDocumentManager.getInstance().getDocument(virtualFile)?.text ?: File(filePath).readText()
            }
        } else {
            ""
        }
        
        textContent.addProperty("text", currentContent)
        contentArray.add(textContent)
        result.add("content", contentArray)
        result.addProperty("isError", false)
        
        return result
    }
}