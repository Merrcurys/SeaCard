package ru.merrcurys.seacard.features.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.SharedPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.content.edit
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.design.BerlinAzure
import ru.merrcurys.seacard.core.design.GradientColorOption
import ru.merrcurys.seacard.core.utils.CardSortUtil
import ru.merrcurys.seacard.core.utils.CoverBitmapStorage
import ru.merrcurys.seacard.core.utils.SortType
import androidx.compose.ui.graphics.Color
import ru.merrcurys.seacard.domain.entity.Card as CardModel

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DatabaseProvider.get(application).cardDao()
    private val prefs = application.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val _cardsFromDbReady = MutableStateFlow(false)

    private val cardsFromDb = dao.getAllFlow()
        .map { list -> list.map { it.toCard() } }
        .onEach { _cardsFromDbReady.value = true }

    /** Первый эмит из Room получен — можно показывать пустой список или сетку (до этого не путать с «нет карт»). */
    val cardsFromDbReady: StateFlow<Boolean> = _cardsFromDbReady.asStateFlow()

    val sortType = MutableStateFlow(loadSortTypePref())
    val gridColumns = MutableStateFlow(loadGridColumnsPref())
    val gradientColor = MutableStateFlow(loadGradientColorPref())
    val showCoverPicker = MutableStateFlow(false)

    val cards: StateFlow<List<CardModel>> = combine(cardsFromDb, sortType) { list, sort ->
        CardSortUtil.sorted(list, sort.name)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun loadSortTypePref(): SortType =
        CardSortUtil.parseSortType(prefs.getString("sort_type", null))

    private fun loadGridColumnsPref(): Int =
        prefs.getInt("grid_columns", 2).coerceIn(1, 4)

    private fun loadGradientColorPref(): Color {
        val colorValue = prefs.getInt("gradient_color", BerlinAzure.hashCode())
        return GradientColorOption.entries.find { it.color.hashCode() == colorValue }?.color ?: BerlinAzure
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "grid_columns" -> gridColumns.value = loadGridColumnsPref()
            "gradient_color" -> gradientColor.value = loadGradientColorPref()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun setSortType(type: SortType) {
        prefs.edit { putString("sort_type", type.name) }
        sortType.value = type
        ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(getApplication())
    }

    fun setGridColumns(columns: Int) {
        val fixed = columns.coerceIn(1, 4)
        prefs.edit { putInt("grid_columns", fixed) }
        gridColumns.value = fixed
    }

    fun setShowCoverPicker(show: Boolean) {
        showCoverPicker.value = show
    }

    /**
     * Сохраняет пользовательский порядок карт и переключает сортировку на «Своя сортировка».
     * Позиции пишутся как 0..n-1 по порядку отображаемого списка.
     */
    fun reorderCards(orderedCards: List<CardModel>) {
        if (orderedCards.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.get(getApplication())
            db.withTransaction {
                orderedCards.forEachIndexed { index, card ->
                    dao.updateSortOrder(card.id, index.toLong())
                }
            }
            prefs.edit { putString("sort_type", SortType.CUSTOM.name) }
            sortType.value = SortType.CUSTOM
            ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(getApplication())
        }
    }

    fun deleteCards(cardsToDelete: List<CardModel>) {
        viewModelScope.launch(Dispatchers.IO) {
            cardsToDelete.forEach { card ->
                // Обложка принадлежит конкретной карте — при удалении чистим и её файлы.
                CoverBitmapStorage.deleteLocalCover(card.frontCoverPath)
                CoverBitmapStorage.deleteLocalCover(card.backCoverPath)
                dao.deleteById(card.id)
            }
            ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(getApplication())
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onCleared()
    }
}
