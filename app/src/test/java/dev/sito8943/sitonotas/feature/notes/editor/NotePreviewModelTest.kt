package dev.sito8943.sitonotas.feature.notes.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePreviewModelTest {

    @Test
    fun `long notes are cut at a line break within 600 characters like the web`() {
        val markdown = "**bold** text\n".repeat(100)
        val cut = NotePreviewModel.truncate(markdown)
        assertTrue(cut.length <= NotePreviewModel.PREVIEW_CONTENT_LENGTH)
        assertTrue(cut.endsWith("text"))
    }

    @Test
    fun `short notes and notes without breaks are kept or cut flat`() {
        assertEquals("short", NotePreviewModel.truncate("short"))
        val oneLine = "x".repeat(700)
        assertEquals(600, NotePreviewModel.truncate(oneLine).length)
    }

    @Test
    fun `blocks render like the web card`() {
        val items = NotePreviewModel.build(
            """
            # Plan
            first line
            second line

            - [ ] open task
            - [x] done task
              - nested bullet
            3. third
            > quoted
            > more
            ---
            ```
            code *raw*
            ```
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                PreviewKind.HEADING, PreviewKind.PARAGRAPH, PreviewKind.TASK, PreviewKind.TASK,
                PreviewKind.BULLET, PreviewKind.ORDERED, PreviewKind.QUOTE, PreviewKind.RULE, PreviewKind.CODE,
            ),
            items.map { it.kind },
        )
        assertEquals("first line second line", items[1].text)
        assertEquals(true, items[3].checked)
        assertEquals(1, items[4].depth)
        assertEquals("3.", items[5].number)
        assertEquals("quoted more", items[6].text)
        assertEquals("code *raw*", items[8].text)
    }

    @Test
    fun `images vanish html keeps its text and setext underlines make headings`() {
        val items = NotePreviewModel.build(
            "Title\n===\n![pic](https://x.y/a.png)\n<b>bold</b> words\n[ref]: https://x.y\n| a | b |\n|---|---|\n| 1 | 2 |",
        )
        assertEquals(PreviewKind.HEADING, items[0].kind)
        assertEquals("Title", items[0].text)
        assertEquals("bold words", items[1].text)
        assertEquals(listOf("a  b", "1  2"), items.drop(2).map { it.text })
    }

    @Test
    fun `empty or image only notes have no preview`() {
        assertTrue(NotePreviewModel.build("").isEmpty())
        assertTrue(NotePreviewModel.build("\n\n![x](https://x.y/a.png)\n").isEmpty())
    }

    @Test
    fun `html entities show as their character`() {
        assertEquals("a -> b & c 'd' &unknown;", NotePreviewModel.build("a -&gt; b &amp; c &#39;d&#x27; &unknown;").single().text)
    }
}
