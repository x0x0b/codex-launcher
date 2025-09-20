package com.github.x0x0b.codexlauncher.cli

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.settings.options.Model
import com.github.x0x0b.codexlauncher.settings.options.Mode
import com.github.x0x0b.codexlauncher.settings.options.ModelReasoningEffort
import com.intellij.testFramework.LightPlatformTestCase
import com.github.x0x0b.codexlauncher.settings.options.WinShell

/**
 * Test OS provider for mocking Windows/non-Windows behavior
 */
class TestOsProvider(override val isWindows: Boolean) : OsProvider

/**
 * Windows-specific and PowerShell 7.3+ specific tests for CodexArgsBuilder.
 * These tests verify the OS-specific formatting behavior.
 */
class CodexArgsBuilderTest : LightPlatformTestCase() {

    private lateinit var state: CodexLauncherSettings.State
    private val mcpNonWindows = """
        {
          "type": "stdio",
          "env": {
            "IJ_MCP_SERVER_PORT": "64342"
          },
          "command": "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java",
          "args": [
            "-classpath",
            "/Applications/IntelliJ IDEA.app/Contents/plugins/mcpserver/lib/mcpserver-frontend.jar:/Applications/IntelliJ IDEA.app/Contents/lib/util-8.jar",
            "com.intellij.mcpserver.stdio.McpStdioRunnerKt"
          ]
        }
        """.trimIndent()
    private val mcpWindows = """
        {
          "type": "stdio",
          "env": {
            "IJ_MCP_SERVER_PORT": "64342"
          },
          "command": "C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2025.2.1\\jbr\\bin\\java",
          "args": [
            "-classpath",
            "C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2025.2.1\\plugins\\mcpserver\\lib\\mcpserver-frontend.jar;C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2025.2.1\\lib\\util-8.jar",
            "com.intellij.mcpserver.stdio.McpStdioRunnerKt"
          ]
        }
        """.trimIndent()

    override fun setUp() {
        super.setUp()
        state = CodexLauncherSettings.State()
        state.mode = Mode.FULL_AUTO
        state.model = Model.CUSTOM
        state.customModel = "gpt-4o"
        state.modelReasoningEffort = ModelReasoningEffort.HIGH
    }

    // === Complex arguments formatting tests ===

    fun testComplexArgsFormattingOnNonWindows() {
        // Test non-Windows formatting
        val osProvider = TestOsProvider(isWindows = false)
        state.mcpConfigInput = mcpNonWindows

        val result = CodexArgsBuilder.build(state, 11111, osProvider = osProvider)

        // Verify that complex arguments are properly formatted for non-Windows
        assertEquals(13, result.size)
        assertEquals("""--full-auto""", result[0])
        assertEquals("""--model""", result[1])
        assertEquals("""'gpt-4o'""", result[2])
        assertEquals("""-c""", result[3])
        assertEquals("""'model_reasoning_effort=high'""", result[4])
        assertEquals("""-c""", result[5])
        assertEquals("""'notify=["curl", "-s", "-X", "POST", "http://localhost:11111/refresh", "-d"]'""", result[6])
        assertEquals("""-c""", result[7])
        assertEquals(
            """'mcp_servers.intellij.command=/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java'""",
            result[8]
        )
        assertEquals("""-c""", result[9])
        assertEquals(
            """'mcp_servers.intellij.args=["-classpath", "/Applications/IntelliJ IDEA.app/Contents/plugins/mcpserver/lib/mcpserver-frontend.jar:/Applications/IntelliJ IDEA.app/Contents/lib/util-8.jar", "com.intellij.mcpserver.stdio.McpStdioRunnerKt"]'""",
            result[10]
        )
        assertEquals("""-c""", result[11])
        assertEquals("""'mcp_servers.intellij.env={"IJ_MCP_SERVER_PORT"="64342"}'""", result[12])
    }

    fun testComplexArgsFormattingOnWindows() {
        // Test Windows formatting
        val osProvider = TestOsProvider(isWindows = true)
        state.winShell = WinShell.POWERSHELL_LT_73
        state.mcpConfigInput = mcpWindows

        val result = CodexArgsBuilder.build(state, 22222, osProvider = osProvider)

        // Verify that complex arguments are properly formatted for Windows
        assertEquals(13, result.size)
        assertEquals("""--full-auto""", result[0])
        assertEquals("""--model""", result[1])
        assertEquals("""'gpt-4o'""", result[2])
        assertEquals("""-c""", result[3])
        assertEquals("""model_reasoning_effort='high'""", result[4])
        assertEquals("""-c""", result[5])
        assertEquals("""notify='[\"curl\", \"-s\", \"-X\", \"POST\", \"http://localhost:22222/refresh\", \"-d\"]'""", result[6])
        assertEquals("""-c""", result[7])
        assertEquals(
            """mcp_servers.intellij.command='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java'""",
            result[8]
        )
        assertEquals("""-c""", result[9])
        assertEquals(
            """mcp_servers.intellij.args='[\"-classpath\", \"C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2025.2.1\\plugins\\mcpserver\\lib\\mcpserver-frontend.jar;C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2025.2.1\\lib\\util-8.jar\", \"com.intellij.mcpserver.stdio.McpStdioRunnerKt\"]'""",
            result[10]
        )
        assertEquals("""-c""", result[11])
        assertEquals(
            """mcp_servers.intellij.env='{\"IJ_MCP_SERVER_PORT\"=\"64342\",\"SystemRoot\"=\"C:\\Windows\"}'""",
            result[12]
        )
    }

    fun testComplexArgsFormattingOnWindowsWithPowerShell73OrOver() {
        // Test Windows formatting with PowerShell 7.3+
        val osProvider = TestOsProvider(isWindows = true)
        state.winShell = WinShell.POWERSHELL_73_PLUS
        state.mcpConfigInput = mcpWindows

        val result = CodexArgsBuilder.build(state, 33333, osProvider = osProvider)

        // Verify that complex arguments are properly formatted for Windows with PowerShell 7.3+
        assertEquals(13, result.size)
        assertEquals("""--full-auto""", result[0])
        assertEquals("""--model""", result[1])
        assertEquals("""'gpt-4o'""", result[2])
        assertEquals("""-c""", result[3])
        assertEquals("""model_reasoning_effort='high'""", result[4])
        assertEquals("""-c""", result[5])
        assertEquals("""notify='["curl", "-s", "-X", "POST", "http://localhost:33333/refresh", "-d"]'""", result[6])
        assertEquals("""-c""", result[7])
        assertEquals(
            """mcp_servers.intellij.command='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java'""",
            result[8]
        )
        assertEquals("""-c""", result[9])
        assertEquals(
            """mcp_servers.intellij.args='["-classpath", "C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2025.2.1\\plugins\\mcpserver\\lib\\mcpserver-frontend.jar;C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2025.2.1\\lib\\util-8.jar", "com.intellij.mcpserver.stdio.McpStdioRunnerKt"]'""",
            result[10]
        )
        assertEquals("""-c""", result[11])
        assertEquals(
            """mcp_servers.intellij.env='{"IJ_MCP_SERVER_PORT"="64342","SystemRoot"="C:\\Windows"}'""",
            result[12]
        )
    }

    fun testMinimalArgs() {
        // Test minimal args on non-Windows
        val osProvider = TestOsProvider(isWindows = false)
        state.mode = Mode.DEFAULT
        state.model = Model.DEFAULT
        state.modelReasoningEffort = ModelReasoningEffort.DEFAULT
        state.isPowerShell73OrOver = false
        state.mcpConfigInput = ""

        val result = CodexArgsBuilder.build(state, null, osProvider = osProvider)

        // Verify that only necessary arguments are included
        assertEquals(0, result.size)
    }

    fun testComplexArgsFormattingOnWindowsWithWSL() {
        // Test Windows host but WSL selected; should format like non-Windows
        val osProvider = TestOsProvider(isWindows = true)
        state.winShell = WinShell.WSL
        state.mcpConfigInput = mcpWindows

        val result = CodexArgsBuilder.build(state, 44444, osProvider = osProvider)

        // Verify non-Windows style quoting and no SystemRoot
        assertEquals(5, result.size)
        assertEquals("""--full-auto""", result[0])
        assertEquals("""--model""", result[1])
        assertEquals("""'gpt-4o'""", result[2])
        assertEquals("""-c""", result[3])
        assertEquals("""'model_reasoning_effort=high'""", result[4])
    }
}
