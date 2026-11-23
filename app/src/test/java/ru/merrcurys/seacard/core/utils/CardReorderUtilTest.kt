package ru.merrcurys.seacard.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardReorderUtilTest {

    @Test
    fun `full list reorder assigns sequential slots`() {
        val all = listOf(10L, 20L, 30L, 40L)
        val newOrder = listOf(30L, 10L, 40L, 20L)

        val slots = CardReorderUtil.assignSlots(all, newOrder)

        assertEquals(mapOf(30L to 0L, 10L to 1L, 40L to 2L, 20L to 3L), slots)
    }

    @Test
    fun `subset reorder keeps slots of other cards`() {
        val all = listOf(1L, 2L, 3L, 4L, 5L)
        val visible = listOf(2L, 4L, 5L)
        val newVisibleOrder = listOf(5L, 2L, 4L)

        val slots = CardReorderUtil.assignSlots(all, newVisibleOrder)

        // Видимые карты занимали глобальные позиции 1, 3, 4 — их и получают.
        assertEquals(mapOf(5L to 1L, 2L to 3L, 4L to 4L), slots)
        assertTrue(slots.keys.none { it == 1L || it == 3L })
    }

    @Test
    fun `empty input returns empty map`() {
        assertTrue(CardReorderUtil.assignSlots(emptyList(), listOf(1L)).isEmpty())
        assertTrue(CardReorderUtil.assignSlots(listOf(1L), emptyList()).isEmpty())
    }

    @Test
    fun `unknown ids are ignored`() {
        val slots = CardReorderUtil.assignSlots(listOf(1L, 2L), listOf(99L, 2L, 1L))

        assertEquals(mapOf(2L to 0L, 1L to 1L), slots)
    }
}
