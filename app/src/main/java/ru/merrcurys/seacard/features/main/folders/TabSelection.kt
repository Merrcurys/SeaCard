package ru.merrcurys.seacard.features.main.folders

/** Выбранная вкладка главного экрана. */
sealed interface TabSelection {
    data object All : TabSelection
    data class Folder(val id: Long) : TabSelection

    fun toPrefValue(): String = when (this) {
        All -> VALUE_ALL
        is Folder -> "$VALUE_FOLDER_PREFIX$id"
    }

    companion object {
        const val VALUE_ALL = "all"
        const val VALUE_FOLDER_PREFIX = "folder:"

        fun fromPrefValue(value: String?): TabSelection = when {
            value != null && value.startsWith(VALUE_FOLDER_PREFIX) ->
                value.removePrefix(VALUE_FOLDER_PREFIX).toLongOrNull()
                    ?.let { Folder(it) }
                    ?: All
            else -> All
        }
    }
}

/** id вкладки для передачи в экран добавления карты: «Все» даёт null. */
fun TabSelection.folderIdOrNull(): Long? = (this as? TabSelection.Folder)?.id
