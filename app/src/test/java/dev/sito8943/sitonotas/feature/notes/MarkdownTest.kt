package dev.sito8943.sitonotas.feature.notes

import dev.sito8943.sitonotas.feature.notes.markdown.MarkdownBlocks
import dev.sito8943.sitonotas.feature.notes.markdown.MarkdownInline
import dev.sito8943.sitonotas.feature.notes.markdown.MdBlock
import dev.sito8943.sitonotas.feature.notes.markdown.MdSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {

    @Test
    fun `blocks are recognized and unknown text stays as paragraphs`() {
        val blocks = MarkdownBlocks.parse(
            """
            # Lista
            - pan
              - integral
            - [x] leche
            1. uno
            > cita
            ---
            ```
            code *raw*
            ```
            texto
            seguido
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MdBlock.Heading(1, "Lista"),
                MdBlock.Bullet("pan", 0),
                MdBlock.Bullet("integral", 1),
                MdBlock.Task(true, "leche", 0),
                MdBlock.Numbered("1", "uno", 0),
                MdBlock.Quote("cita"),
                MdBlock.Rule,
                MdBlock.Code("code *raw*"),
                MdBlock.Paragraph("texto\nseguido"),
            ),
            blocks,
        )
    }

    @Test
    fun `inline formats`() {
        assertEquals(
            listOf(
                MdSpan("a "),
                MdSpan("b", bold = true),
                MdSpan(" "),
                MdSpan("c", italic = true),
                MdSpan(" "),
                MdSpan("d", code = true),
                MdSpan(" "),
                MdSpan("e", link = "https://x.y"),
                MdSpan(" snake_case_word"),
            ),
            MarkdownInline.parse("a **b** *c* `d` [e](https://x.y) snake_case_word"),
        )
    }
}
