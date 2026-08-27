package ru.merrcurys.seacard.core.utils

import ru.merrcurys.seacard.domain.entity.Card
import java.text.Collator
import java.util.Locale

enum class SortType(val displayName: String) {
    ADD_TIME("По времени добавления"),
    NAME_ASC("По названию (А-Я)"),
    NAME_DESC("По названию (Я-А)"),
    NAME_ASC_LATIN("По названию (A-Z)"),
    NAME_DESC_LATIN("По названию (Z-A)"),
    USAGE_FREQ("По частоте использования")
}

object CardSortUtil {
    private val sortComparator: (SortType) -> Comparator<Card> = { sortType ->
        when (sortType) {
            SortType.ADD_TIME -> compareByDescending { it.addTime }
            SortType.NAME_ASC -> compareBy(Collator.getInstance(Locale.forLanguageTag("ru"))) { it.name }
            SortType.NAME_DESC -> compareByDescending(Collator.getInstance(Locale.forLanguageTag("ru"))) { it.name }
            SortType.NAME_ASC_LATIN -> compareBy(Collator.getInstance(Locale.ENGLISH)) { it.name }
            SortType.NAME_DESC_LATIN -> compareByDescending(Collator.getInstance(Locale.ENGLISH)) { it.name }
            SortType.USAGE_FREQ -> compareByDescending { it.usageCount }
        }
    }

    fun sorted(cards: List<Card>, sortTypeName: String?): List<Card> {
        val sortType = parseSortType(sortTypeName)
        return cards.sortedWith(sortComparator(sortType))
    }

    fun parseSortType(name: String?): SortType =
        try {
            SortType.valueOf(name ?: SortType.ADD_TIME.name)
        } catch (e: IllegalArgumentException) {
            SortType.ADD_TIME
        }
}
