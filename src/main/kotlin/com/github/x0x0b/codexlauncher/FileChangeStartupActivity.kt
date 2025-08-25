package com.github.x0x0b.codexlauncher

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class FileChangeStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        // サービスを明示的に初期化
        project.getService(FileChangeService::class.java)
    }
}