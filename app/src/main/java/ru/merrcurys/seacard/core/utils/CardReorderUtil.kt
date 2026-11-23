package ru.merrcurys.seacard.core.utils

/**
 * Перестановка карт внутри отфильтрованного списка (например, внутри вкладки-папки).
 * Видимые карты получают те же слоты sortOrder, которые занимали в глобальном
 * CUSTOM-порядке, поэтому карты из других папок не съезжают.
 */
object CardReorderUtil {

    /**
     * @param allInCustomOrder id всех карт в текущем CUSTOM-порядке
     *        (sortOrder, затем addTime DESC).
     * @param visibleNewOrder новый порядок видимых карт после drag.
     * @return id -> новый sortOrder для видимых карт.
     */
    fun assignSlots(
        allInCustomOrder: List<Long>,
        visibleNewOrder: List<Long>
    ): Map<Long, Long> {
        if (allInCustomOrder.isEmpty() || visibleNewOrder.isEmpty()) return emptyMap()
        val positionById = allInCustomOrder.withIndex().associate { (index, id) -> id to index.toLong() }
        val slots = visibleNewOrder.mapNotNull { positionById[it] }.sorted()
        return visibleNewOrder
            .filter { positionById.containsKey(it) }
            .mapIndexed { index, id -> id to slots[index] }
            .toMap()
    }
}
