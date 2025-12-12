package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.project.Project
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@RunWith(Parameterized::class)
class CommandScriptFactoryTest(private val isWindows: Boolean) {

    companion object {
        private const val INLINE_THRESHOLD = 1024

        @JvmStatic
        @Parameterized.Parameters(name = "isWindows={0}")
        fun parameters() = listOf(true, false)
    }

    private val createdScripts = mutableListOf<Path>()

    @After
    fun tearDown() {
        createdScripts.forEach { Files.deleteIfExists(it) }
        createdScripts.clear()
    }

    @Test
    fun `inline commands under threshold are sent directly`() {
        val factory = CommandScriptFactory(dummyProject(), isWindows = isWindows)
        val command = "a".repeat(INLINE_THRESHOLD - 1)

        val plan = factory.buildPlan(command) ?: error("Expected plan for command")

        assertEquals(command, plan.command)
    }

    @Test
    fun `inline commands at threshold are sent directly`() {
        val factory = CommandScriptFactory(dummyProject(), isWindows = isWindows)
        val command = "a".repeat(INLINE_THRESHOLD)

        val plan = factory.buildPlan(command) ?: error("Expected plan for command")

        assertEquals(command, plan.command)
    }

    @Test
    fun `inline commands for windows are always sent directly`() {
        val factory = CommandScriptFactory(dummyProject(), isWindows = true)
        val command = "a".repeat(INLINE_THRESHOLD + 100)

        val plan = factory.buildPlan(command) ?: error("Expected plan for command")

        assertEquals(command, plan.command)
    }

    @Test
    fun `script is created with trap and cleanup`() {
        val factory = CommandScriptFactory(dummyProject(), isWindows = false)
        val command = "echo test-command"

        val plan = factory.buildPlan("a".repeat(INLINE_THRESHOLD + 1) + command) ?: error("Expected plan")
        val scriptPath = extractScriptPath(plan.command)
        createdScripts.add(scriptPath)
        val content = Files.readString(scriptPath, StandardCharsets.UTF_8)

        val expected = """
            trap 'rm -f "$0"' EXIT INT TERM
            set -e
            ${"a".repeat(INLINE_THRESHOLD + 1)}$command
        """.trimIndent() + "\n"

        assertEquals(expected, content)
        assertTrue(Files.exists(scriptPath))
        val expectedQuotedPath = "'${scriptPath.toAbsolutePath().toString().replace("'", "'\"'\"'")}'"
        assertEquals("sh $expectedQuotedPath", plan.command)

        plan.cleanupOnFailure()
        assertFalse(scriptPath.exists())
    }

    @Test
    fun `script is registered for cleanup service`() {
        val cleanupService = TempScriptCleanupService()
        val factory = CommandScriptFactory(dummyProject(), isWindows = false, cleanupService = cleanupService)
        val plan = factory.buildPlan("a".repeat(INLINE_THRESHOLD + 10)) ?: error("Expected plan")
        val scriptPath = extractScriptPath(plan.command)
        createdScripts.add(scriptPath)

        assertTrue(registeredPaths(cleanupService).contains(scriptPath))
    }

    private fun extractScriptPath(command: String): java.nio.file.Path {
        val parts = command.split(" ")
        val last = parts.lastOrNull() ?: error("Command missing script path")
        val trimmed = last.trim('\'', '"')
        return java.nio.file.Paths.get(trimmed)
    }

    private fun registeredPaths(service: TempScriptCleanupService): Set<Path> {
        val field = TempScriptCleanupService::class.java.getDeclaredField("scriptPaths")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(service) as Set<Path>).toSet()
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
