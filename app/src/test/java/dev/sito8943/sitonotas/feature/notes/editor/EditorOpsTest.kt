package dev.sito8943.sitonotas.feature.notes.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorOpsTest {

    private val ids = IdSource(1_000)

    private fun blocks(markdown: String) = MarkdownDocument.parse(markdown)

    private fun md(blocks: List<EditorBlock>) = MarkdownDocument.serialize(blocks)

    /** Escribe [newText] en el bloque [index] con el cursor al final. */
    private fun type(markdown: String, index: Int, newText: String, caret: Int = newText.length) =
        EditorOps.editText(blocks(markdown), index, newText, caret, ids)

    @Test
    fun `editing one line leaves the others byte for byte`() {
        val source = "*  odd  spacing*\n-   [X]   done\n\ttabbed line\nedit me"
        val result = type(source, 3, "edited")
        assertEquals("*  odd  spacing*\n-   [X]   done\n\ttabbed line\nedited", md(result.blocks))
    }

    @Test
    fun `enter continues bullets tasks and numbers`() {
        assertEquals("- milk\n- ", md(type("- milk", 0, "milk\n").blocks))
        assertEquals("  - [x] done\n  - [ ] ", md(type("  - [x] done", 0, "done\n").blocks))
        assertEquals("9. nine\n10. ", md(type("9. nine", 0, "nine\n").blocks))
        assertEquals("> quote\n> ", md(type("> quote", 0, "quote\n").blocks))
    }

    @Test
    fun `enter in the middle splits the text`() {
        val result = type("- buy bread", 0, "buy\n bread", caret = 4)
        assertEquals("- buy\n-  bread", md(result.blocks))
        assertEquals(0, result.caret)
    }

    @Test
    fun `enter on an empty item leaves the list`() {
        val result = type("- a\n- ", 1, "\n", caret = 1)
        assertEquals("- a\n", md(result.blocks))
        assertEquals(BlockType.PARAGRAPH, result.blocks[1].type)
    }

    @Test
    fun `enter after a heading starts a paragraph`() {
        assertEquals("# Title\n", md(type("# Title", 0, "Title\n").blocks))
    }

    @Test
    fun `typing a fence and enter opens a code block`() {
        val typed = type("``", 0, "```")
        assertEquals(BlockType.PARAGRAPH, typed.blocks.single().type)
        val result = EditorOps.editText(typed.blocks, 0, "```\n", 4, ids)
        assertEquals(BlockType.CODE, result.blocks.single().type)
        assertEquals("```\n\n```", md(result.blocks))
    }

    @Test
    fun `markdown shortcuts convert a paragraph like the web`() {
        assertEquals(BlockType.BULLET, type("", 0, "- ").blocks.single().type)
        assertEquals(BlockType.ORDERED, type("", 0, "1. ").blocks.single().type)
        assertEquals(BlockType.HEADING, type("", 0, "## ").blocks.single().type)
        assertEquals(BlockType.QUOTE, type("", 0, "> ").blocks.single().type)
        val task = type("", 0, "[ ] ").blocks.single()
        assertEquals(BlockType.TASK, task.type)
        assertEquals("- [ ] ", task.source)
        assertEquals(BlockType.TASK, type("- ", 0, "[ ] ").blocks.single().type)
    }

    @Test
    fun `a marker without its space does not convert yet`() {
        assertEquals(BlockType.PARAGRAPH, type("", 0, "-").blocks.single().type)
        assertEquals(BlockType.PARAGRAPH, type("", 0, "-5 degrees").blocks.single().type)
        assertEquals(BlockType.PARAGRAPH, type("", 0, "#hashtag").blocks.single().type)
    }

    @Test
    fun `backspace at the start removes block formatting then merges`() {
        val lifted = EditorOps.backspaceAtStart(blocks("text\n- [ ] item"), 1)
        assertEquals("text\nitem", md(lifted.blocks))
        val merged = EditorOps.backspaceAtStart(lifted.blocks, 1)
        assertEquals("textitem", md(merged.blocks))
        assertEquals(4, merged.caret)
    }

    @Test
    fun `backspace on a nested item outdents it`() {
        val result = EditorOps.backspaceAtStart(blocks("- a\n    - b"), 1)
        assertEquals("- a\n  - b", md(result.blocks))
    }

    @Test
    fun `backspace after a rule deletes the rule`() {
        val result = EditorOps.backspaceAtStart(blocks("a\n\n---\nb"), 3)
        assertEquals("a\n\nb", md(result.blocks))
    }

    @Test
    fun `toggling a task only flips its box`() {
        val once = EditorOps.toggleChecked(blocks("  -  [ ]  x [ ] y"), 0)
        assertEquals("  -  [x]  x [ ] y", md(once))
        assertEquals("  -  [ ]  x [ ] y", md(EditorOps.toggleChecked(once, 0)))
        assertEquals("- [ ] upper", md(EditorOps.toggleChecked(blocks("- [X] upper"), 0)))
    }

    @Test
    fun `toolbar block types toggle`() {
        assertEquals("- [ ] buy", md(EditorOps.setBlockType(blocks("buy"), 0, BlockType.TASK)))
        assertEquals("buy", md(EditorOps.setBlockType(blocks("- [ ] buy"), 0, BlockType.TASK)))
        assertEquals("1. a\n2. b", md(EditorOps.setBlockType(blocks("1. a\nb"), 1, BlockType.ORDERED)))
        assertEquals("## t", md(EditorOps.setBlockType(blocks("t"), 0, BlockType.HEADING, 2)))
        assertEquals("  - x", md(EditorOps.setBlockType(blocks("  1. x"), 0, BlockType.BULLET)))
    }

    @Test
    fun `inline marks wrap and unwrap the selection`() {
        val bold = EditorOps.toggleMark("make this bold", 10, 14, MarkType.BOLD)
        assertEquals("make this **bold**", bold.text)
        assertEquals(12, bold.selectionStart)
        val leave = EditorOps.toggleMark(bold.text, 16, 16, MarkType.BOLD)
        assertEquals(bold.text, leave.text)
        assertEquals(18, leave.selectionStart)
        assertEquals(18, EditorOps.toggleMark(bold.text, 13, 13, MarkType.BOLD).selectionStart)
        val plain = EditorOps.toggleMark(bold.text, 12, 16, MarkType.BOLD)
        assertEquals("make this bold", plain.text)
        assertEquals(10, plain.selectionStart)
        assertEquals(14, plain.selectionEnd)
        assertEquals("a `b` c", EditorOps.toggleMark("a b c", 2, 3, MarkType.CODE).text)
        assertEquals("~~x~~", EditorOps.toggleMark("x", 0, 1, MarkType.STRIKE).text)
        assertEquals("*x*", EditorOps.toggleMark("x", 0, 1, MarkType.ITALIC).text)
        assertEquals("see [docs](https://e.com)", EditorOps.insertLink("see docs", 4, 8, "https://e.com").text)
    }

    @Test
    fun `inline parser finds marks with positions`() {
        val marks = InlineMarks.parse("**b** *i* ~~s~~ `c` [l](https://x.y) snake_case_name")
        assertEquals(
            listOf(MarkType.BOLD, MarkType.ITALIC, MarkType.STRIKE, MarkType.CODE, MarkType.LINK),
            marks.map { it.type },
        )
        assertEquals("https://x.y", marks.last().url)
        assertTrue(InlineMarks.parse("2 * 3 * 4").isEmpty())
        assertEquals(listOf(MarkType.BOLD, MarkType.ITALIC), InlineMarks.parse("**a *b* c**").map { it.type })
    }

    @Test
    fun `pasting several lines parses them as blocks`() {
        val result = type("x", 0, "x- [ ] one\n- [x] two", caret = 20)
        assertEquals("x- [ ] one\n- [x] two", md(result.blocks))
        assertEquals(BlockType.TASK, result.blocks[1].type)
    }

    // --- Estado del editor: solo se avisa cuando el Markdown cambia ---

    @Test
    fun `opening moving the cursor and retyping the same text never reports a change`() {
        val changes = mutableListOf<String>()
        val source = "# Title\n- [ ] task with **bold**\n\ntext"
        val state = RichEditorState(source) { changes += it }
        val task = state.blocks[1]
        state.onValueChange(task.id, TextFieldValue(RichEditorState.SENTINEL + task.text, TextRange(3)))
        state.onValueChange(task.id, TextFieldValue(RichEditorState.SENTINEL + task.text, TextRange(8)))
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `typing checking and undoing a check report the new markdown`() {
        val changes = mutableListOf<String>()
        val state = RichEditorState("- [ ] task") { changes += it }
        val task = state.blocks.single()
        state.onValueChange(task.id, TextFieldValue(RichEditorState.SENTINEL + "tasks", TextRange(6)))
        state.toggleChecked(task.id)
        state.toggleChecked(task.id)
        assertEquals(listOf("- [ ] tasks", "- [x] tasks", "- [ ] tasks"), changes)
    }

    @Test
    fun `deleting the hidden first character merges with the previous line`() {
        val changes = mutableListOf<String>()
        val state = RichEditorState("one\ntwo") { changes += it }
        val second = state.blocks[1]
        state.onValueChange(second.id, TextFieldValue("two", TextRange(0)))
        assertEquals(listOf("onetwo"), changes)
    }
}
