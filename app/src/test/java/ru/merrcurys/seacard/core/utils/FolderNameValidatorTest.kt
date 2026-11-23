package ru.merrcurys.seacard.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderNameValidatorTest {

    @Test
    fun `blank name is rejected`() {
        assertEquals(FolderNameError.EMPTY, FolderNameValidator.validate("   ", emptyList()))
    }

    @Test
    fun `name longer than max is rejected`() {
        val name = "a".repeat(FolderNameValidator.MAX_LENGTH + 1)
        assertEquals(FolderNameError.TOO_LONG, FolderNameValidator.validate(name, emptyList()))
    }

    @Test
    fun `duplicate is case insensitive`() {
        val existing = listOf(1L to "Продукты")
        assertEquals(
            FolderNameError.DUPLICATE,
            FolderNameValidator.validate("  продукты ", existing)
        )
    }

    @Test
    fun `rename to own name is allowed`() {
        val existing = listOf(1L to "Продукты")
        assertNull(FolderNameValidator.validate("Продукты", existing, selfId = 1L))
    }

    @Test
    fun `name is trimmed and inner spaces collapse`() {
        assertEquals("Мои карты", FolderNameValidator.normalize("  Мои   карты  "))
    }

    @Test
    fun `valid unique name passes`() {
        assertNull(FolderNameValidator.validate("Аптека", listOf(1L to "Продукты")))
    }
}
