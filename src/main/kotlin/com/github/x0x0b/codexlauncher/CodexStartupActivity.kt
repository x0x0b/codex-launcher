package com.github.x0x0b.codexlauncher

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class CodexStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        // サービスを明示的に初期化
        project.getService(FileOpenService::class.java)
        project.getService(HttpTriggerService::class.java)
    }
}
