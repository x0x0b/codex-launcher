package com.github.x0x0b.codexlauncher

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NotificationService(private val project: Project) {
    
    companion object {
        private const val NOTIFICATION_GROUP_ID = "Codex Launcher"
    }
    
    fun notifyRefreshReceived(message: String = "Refresh request received") {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            
        val notification = notificationGroup.createNotification(
            "Codex Launcher",
            message,
            NotificationType.INFORMATION
        )
        
        notification.notify(project)
    }
    
    fun notifyRefreshError(error: String) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            
        val notification = notificationGroup.createNotification(
            "Codex Launcher",
            "Error processing refresh request: $error",
            NotificationType.ERROR
        )
        
        notification.notify(project)
    }
}
