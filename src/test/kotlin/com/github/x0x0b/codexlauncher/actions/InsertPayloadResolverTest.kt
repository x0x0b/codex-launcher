package com.github.x0x0b.codexlauncher.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InsertPayloadResolverTest : BasePlatformTestCase() {

    fun testSelectionLineRangeIsResolved() {
        val fileText = """
            class Foo {
                fun bar() {
                    <selection>val x = 1
                    val y = 2</selection>
                }
            }
        """.trimIndent()

        myFixture.configureByText("Foo.kt", fileText)

        val project = project
        val editor = myFixture.editor
        val virtualFile = myFixture.file.virtualFile

        val payload = InsertPayloadResolver.resolve(
            project = project,
            includeCaretClass = true,
            editor = editor,
            file = virtualFile
        )

        assertNotNull("Payload should be resolved for a selected range", payload)
        payload!!

        assertTrue("Relative path should end with file name", payload.relativePath.endsWith("Foo.kt"))

        val range = payload.lineRange
        assertNotNull("Line range must be present when selection exists", range)
        range!!

        // Selection spans the two val-lines inside bar()
        assertEquals(3, range.start)
        assertEquals(4, range.end)
    }

    fun testCaretClassRangeWhenNoSelection() {
        val fileText = """
            class Foo {
                fun bar() {
                    val x = 1
                    val y = 2
                }
            }
        """.trimIndent()

        myFixture.configureByText("Foo.kt", fileText)

        // Place caret inside the method body, no explicit selection
        val offset = myFixture.file.text.indexOf("val x")
        myFixture.editor.caretModel.moveToOffset(offset)

        val project = project
        val editor = myFixture.editor
        val virtualFile = myFixture.file.virtualFile

        val payload = InsertPayloadResolver.resolve(
            project = project,
            includeCaretClass = true,
            editor = editor,
            file = virtualFile
        )

        assertNotNull("Payload should be resolved when caret is inside a class", payload)
        payload!!

        val range = payload.lineRange
        assertNotNull("Line range must be present when caret fallback is used", range)
        range!!

        // Whole class span (class Foo { ... })
        assertEquals(1, range.start)
        assertEquals(6, range.end)
    }
}
