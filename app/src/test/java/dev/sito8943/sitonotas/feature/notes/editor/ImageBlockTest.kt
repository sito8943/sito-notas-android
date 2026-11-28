package dev.sito8943.sitonotas.feature.notes.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Imagenes como bloque propio: se pintan, pero el Markdown no cambia ni un byte. */
class ImageBlockTest {

    private fun roundTrip(markdown: String) =
        assertEquals(markdown, MarkdownDocument.serialize(MarkdownDocument.parse(markdown)))

    @Test
    fun `an image alone on its line is an image block`() {
        val url = "https://api.example.com/files/note-images/12_0f8fad5b-d9cb-469f-a165-70867728950e.png"
        val block = MarkdownDocument.parseLine("![]($url)", 1)
        assertEquals(BlockType.IMAGE, block.type)
        assertEquals(url, block.imageUrl)
        assertEquals("", block.imageAlt)
        assertEquals("![]($url)", block.source)
    }

    @Test
    fun `alt text title angle brackets and spacing are read and kept verbatim`() {
        val lines = listOf(
            "![A cat](https://x.y/cat.jpg)",
            "![](https://x.y/a.png \"Title\")",
            "![](<https://x.y/a.png>)",
            "   ![x](https://x.y/a.png)  ",
            "![](https://x.y/a.png)\t",
        )
        lines.forEach { line ->
            val block = MarkdownDocument.parseLine(line, 1)
            assertEquals(line, BlockType.IMAGE, block.type)
            assertEquals(line, block.source)
        }
        assertEquals("A cat", MarkdownDocument.parseLine(lines[0], 1).imageAlt)
        assertEquals("https://x.y/a.png", MarkdownDocument.parseLine(lines[2], 1).imageUrl)
    }

    @Test
    fun `images mixed with text stay raw`() {
        listOf(
            "Look ![x](https://x.y/a.png)",
            "![a](https://x.y/a.png) ![b](https://x.y/b.png)",
            "- ![x](https://x.y/a.png)",
            "    ![x](https://x.y/a.png)",
        ).forEach { line ->
            val type = MarkdownDocument.parseLine(line, 1).type
            assertFalse(line, type == BlockType.IMAGE)
        }
    }

    @Test
    fun `notes with images round trip byte for byte`() {
        roundTrip("# Trip\n![](https://x.y/files/note-images/1_a.png)\ntext after\n")
        roundTrip("![](https://x.y/a.png)\n![](https://x.y/b.png)")
        roundTrip("before\r\n![alt](https://x.y/a.png \"t\")\r\n\r\nafter")
        roundTrip("```\n![](https://x.y/in-code.png)\n```\n![](https://x.y/a.png)")
        roundTrip("  ![](https://x.y/a.png)   \n\n")
    }

    @Test
    fun `image inside a code block is code not an image`() {
        val blocks = MarkdownDocument.parse("```\n![](https://x.y/a.png)\n```")
        assertEquals(listOf(BlockType.CODE), blocks.map { it.type })
    }

    @Test
    fun `notes with only images are not raw and open in the rich editor`() {
        val blocks = MarkdownDocument.parse("text\n![](https://x.y/a.png)")
        assertFalse(MarkdownDocument.hasRawContent(blocks))
    }

    @Test
    fun `inserting splits the paragraph at the caret like the web`() {
        val ids = IdSource(100)
        val blocks = MarkdownDocument.parse("hello world\nnext")
        val result = EditorOps.insertImages(blocks, 0, 5, listOf("https://x.y/a.png"), ids)
        assertEquals("hello\n![](https://x.y/a.png)\n world\nnext", MarkdownDocument.serialize(result.blocks))
        assertEquals(result.blocks[2].id, result.focusId)
    }

    @Test
    fun `inserting at the end of a block goes below it and keeps the rest untouched`() {
        val ids = IdSource(100)
        val blocks = MarkdownDocument.parse("- [ ] task\nnext")
        val result = EditorOps.insertImages(blocks, 0, 99, listOf("https://x.y/a.png", "https://x.y/b.png"), ids)
        assertEquals(
            "- [ ] task\n![](https://x.y/a.png)\n![](https://x.y/b.png)\nnext",
            MarkdownDocument.serialize(result.blocks),
        )
    }

    @Test
    fun `inserting into an empty paragraph replaces it and without focus appends`() {
        val ids = IdSource(100)
        val empty = MarkdownDocument.parse("a\n\nb")
        val replaced = EditorOps.insertImages(empty, 1, 0, listOf("https://x.y/a.png"), ids)
        assertEquals("a\n![](https://x.y/a.png)\nb", MarkdownDocument.serialize(replaced.blocks))

        val appended = EditorOps.insertImages(MarkdownDocument.parse("a"), null, 0, listOf("https://x.y/a.png"), ids)
        assertEquals("a\n![](https://x.y/a.png)", MarkdownDocument.serialize(appended.blocks))
    }

    @Test
    fun `typing an image by hand stays editable text and backspace does not merge into an image`() {
        val ids = IdSource(100)
        val blocks = MarkdownDocument.parse("")
        val typed = EditorOps.editText(blocks, 0, "![](https://x.y/a.png)", 22, ids)
        assertEquals(BlockType.RAW, typed.blocks[0].type)
        assertEquals("![](https://x.y/a.png)", MarkdownDocument.serialize(typed.blocks))

        val withImage = MarkdownDocument.parse("![](https://x.y/a.png)\ntext")
        val result = EditorOps.backspaceAtStart(withImage, 1)
        assertEquals("![](https://x.y/a.png)\ntext", MarkdownDocument.serialize(result.blocks))
    }

    @Test
    fun `removing an image block drops only its line`() {
        val blocks = MarkdownDocument.parse("a\n![](https://x.y/a.png)\nb")
        assertEquals("a\nb", MarkdownDocument.serialize(EditorOps.removeBlock(blocks, blocks[1].id)))
    }

    @Test
    fun `card preview drops images like the web`() {
        assertEquals(
            listOf("before after"),
            NotePreviewModel.build("before\n![](https://x.y/a.png)\nafter").map { it.text },
        )
    }
}
