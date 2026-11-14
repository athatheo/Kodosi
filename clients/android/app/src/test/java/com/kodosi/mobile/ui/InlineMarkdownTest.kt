package com.kodosi.mobile.ui

import com.kodosi.mobile.ui.InlineMarkdown.Run
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMarkdownTest {
    @Test
    fun readsTheMarksThatAgentsWrite() {
        assertEquals(
            listOf(
                Run("checkout", strong = true),
                Run(" needs you: run "),
                Run("git push origin fix/checkout-total", code = true),
                Run(". ")
            ) +
                listOf(
                    Run("Not ", emphasis = true),
                    Run("now", strong = true, emphasis = true),
                    Run(".", emphasis = true)
                ) +
                listOf(Run(" "), Run("old", struck = true)),
            InlineMarkdown.runs(
                "**checkout** needs you: run `git push origin fix/checkout-total`. *Not **now**.* ~~old~~"
            )
        )
    }

    @Test
    fun leavesNamesNumbersAndOpenMarksAsTheyAre() {
        for (plain in listOf(
            "release_notes_v2.md and 2 * 3 * 4",
            "a list:\n* one\n* two",
            "**open and `tick and ~one~",
            "[no address](notes.md) [script](javascript:alert)"
        )) {
            assertEquals(listOf(Run(plain)), InlineMarkdown.runs(plain))
        }
        assertEquals(listOf(Run("*not* a_b")), InlineMarkdown.runs("""\*not\* a_b"""))
    }

    @Test
    fun namesALinkOnlyForAWebOrMailAddress() {
        assertEquals(
            listOf(
                Run("See "),
                Run("the ", link = "https://kodosi.com/guide"),
                Run("guide", strong = true, link = "https://kodosi.com/guide"),
                Run(" or write to "),
                Run("us", link = "mailto:hello@kodosi.com"),
                Run(".")
            ),
            InlineMarkdown.runs(
                "See [the **guide**](https://kodosi.com/guide) or write to [us](mailto:hello@kodosi.com)."
            )
        )
    }
}
