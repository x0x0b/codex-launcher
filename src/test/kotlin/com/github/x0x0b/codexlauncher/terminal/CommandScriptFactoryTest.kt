package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class CommandScriptFactoryTest {

    companion object {
        private const val INLINE_THRESHOLD = 1024
    }

    @Test
    fun `inline commands under threshold are sent directly`() {
        val factory = CommandScriptFactory(dummyProject(), isWindows = false)
        val command = "a".repeat(INLINE_THRESHOLD - 1)

        val plan = factory.buildPlan(command) ?: error("Expected plan for command")

        assertEquals(command, plan.command)
    }

    @Test
    fun `posix script is created with trap and cleanup`() {
        val factory = CommandScriptFactory(dummyProject(), isWindows = false)
        val command = "echo posix-case"

        val plan = factory.buildPlan("a".repeat(INLINE_THRESHOLD + 1) + command) ?: error("Expected plan")
        val scriptPath = extractScriptPath(plan.command, prefix = "sh")
        val content = Files.readString(scriptPath, StandardCharsets.UTF_8)

        val expected = """
            trap 'rm -f "$0"' EXIT INT TERM
            set -e
            ${"a".repeat(INLINE_THRESHOLD + 1)}$command
        """.trimIndent() + "\n"

        assertEquals(expected, content)
        assertTrue(Files.exists(scriptPath))
        Files.deleteIfExists(scriptPath)
    }

    @Test
    fun `windows script is created with try-finally cleanup`() {
        val factory = CommandScriptFactory(dummyProject(), isWindows = true)
        val command = "Write-Output \"windows-case\""

        val plan = factory.buildPlan("x".repeat(INLINE_THRESHOLD + 2) + command) ?: error("Expected plan")
        val scriptPath = extractScriptPath(plan.command, prefix = "powershell")
        val content = Files.readString(scriptPath, StandardCharsets.UTF_8)

        val expected = """
            ${'$'}ErrorActionPreference = "Stop"
            try {
            ${"x".repeat(INLINE_THRESHOLD + 2)}$command
            }
            finally {
              Remove-Item -Force "${'$'}PSCommandPath"
            }
        """.trimIndent() + "\n"

        assertEquals(expected, content)
        assertTrue(Files.exists(scriptPath))
        Files.deleteIfExists(scriptPath)
    }

    private fun extractScriptPath(command: String, prefix: String): java.nio.file.Path {
        val parts = command.split(" ")
        val last = parts.lastOrNull() ?: error("Command missing script path")
        val trimmed = last.trim('\'', '"')
        return java.nio.file.Paths.get(trimmed)
    }

    private fun dummyProject(): Project {
        return Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "isDisposed" -> false
                "getName" -> "codex-test-project"
                else -> null
            }
        } as Project
    }
}
