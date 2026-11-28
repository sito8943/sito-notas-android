package dev.sito8943.sitonotas.feature.notes.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Abrir una nota en el editor y volver a escribirla no puede cambiar ni un
 * caracter: si cambiara, abrirla bastaria para mandar un PATCH al servidor.
 */
class MarkdownRoundTripTest {

    private fun roundTrip(markdown: String) =
        assertEquals(markdown, MarkdownDocument.serialize(MarkdownDocument.parse(markdown)))

    @Test
    fun `checklists like the ones written on the web survive untouched`() = roundTrip(
        """
        - [ ] Plan the week
        - [ ] Calendar view with weeks, months and days
        - [x] Sticky header while scrolling
        - [X] Upper-case check also counts
          - [ ] Nested task
        """.trimIndent(),
    )

    @Test
    fun `headings lists quotes and inline marks survive untouched`() = roundTrip(
        """
        # Trip
        ## Things to pack
        - **charger** and *cables*
        * other bullet marker
        + and another
          - nested two spaces
        	- nested with a tab
        1. first
        2) second with parenthesis
        10. ten

        > Quote with a [link](https://example.com) and `code`
        Text with ~~strike~~, __bold__ and _italic_.
        ***
        """.trimIndent(),
    )

    @Test
    fun `code blocks blank lines and trailing newline survive untouched`() {
        roundTrip("```kotlin\nval x = 1\n\n// *not* markdown\n```\n\n\ntext\n")
        roundTrip("```\n```")
        roundTrip("~~~\nunterminated fence\nkeeps going")
        roundTrip("")
        roundTrip("\n")
        roundTrip("line with windows ending\r\nnext\r\n")
    }

    @Test
    fun `unsupported content is kept verbatim and flagged`() {
        val markdown = """
            Before
            ![photo](https://example.com/a.png)
            | a | b |
            |---|---|
            <div>html</div>
            Setext title
            ===
            [ref]: https://example.com
        """.trimIndent()
        roundTrip(markdown)
        val blocks = MarkdownDocument.parse(markdown)
        assertTrue(MarkdownDocument.hasRawContent(blocks))
        assertEquals(5, blocks.count { it.type == BlockType.RAW })
        assertEquals(1, blocks.count { it.type == BlockType.IMAGE })
    }

    @Test
    fun `plain notes are not flagged as raw`() {
        val blocks = MarkdownDocument.parse("# Title\n- [ ] task\ntext\n\n---\n```\ncode\n```")
        assertFalse(MarkdownDocument.hasRawContent(blocks))
    }

    @Test
    fun `lines are classified like the web editor`() {
        val types = MarkdownDocument.parse(
            "# h\n- b\n1. o\n- [ ] t\n> q\n---\ntext\n#tag\n-5 degrees",
        ).map { it.type }
        assertEquals(
            listOf(
                BlockType.HEADING, BlockType.BULLET, BlockType.ORDERED, BlockType.TASK,
                BlockType.QUOTE, BlockType.RULE, BlockType.PARAGRAPH, BlockType.PARAGRAPH, BlockType.PARAGRAPH,
            ),
            types,
        )
    }
}
