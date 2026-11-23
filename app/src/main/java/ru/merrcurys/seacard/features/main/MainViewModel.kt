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
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import ru.merrcurys.seacard.core.db.CardFolderRefEntity
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.db.createWithValidation
import ru.merrcurys.seacard.core.design.BerlinAzure
import ru.merrcurys.seacard.core.prefs.SettingsKeys
import ru.merrcurys.seacard.core.utils.CardReorderUtil
import ru.merrcurys.seacard.core.utils.CardSortUtil
import ru.merrcurys.seacard.core.utils.CoverBitmapStorage
import ru.merrcurys.seacard.core.utils.FolderCreationResult
import ru.merrcurys.seacard.core.utils.FolderNameValidator
import ru.merrcurys.seacard.core.utils.SortType
import androidx.compose.ui.graphics.Color
import ru.merrcurys.seacard.core.design.GradientColorOption
import ru.merrcurys.seacard.domain.entity.Folder
import ru.merrcurys.seacard.features.main.folders.TabSelection
import ru.merrcurys.seacard.domain.entity.Card as CardModel

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.get(application)
    private val dao = db.cardDao()
    private val folderDao = db.cardFolderDao()
    private val refDao = db.cardFolderRefDao()
    private val prefs = application.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val _cardsFromDbReady = MutableStateFlow(false)
    private val _foldersReady = MutableStateFlow(false)

    private val cardsFromDb = combine(dao.getAllFlow(), refDao.getAllFlow()) { entities, refs ->
        val folderIdsByCard = refs.groupBy({ it.cardId }, { it.folderId })
        entities.map { entity ->
            entity.toCard().copy(folderIds = folderIdsByCard[entity.id].orEmpty().toSet())
        }
    }.onEach { _cardsFromDbReady.value = true }

    private val foldersFromDb = folderDao.getAllFlow()
        .map { list -> list.map { it.toFolder() } }
        .onEach { _foldersReady.value = true }

    /** Первый эмит из Room получен — можно показывать пустой список или сетку (до этого не путать с «нет карт»). */
    val cardsFromDbReady: StateFlow<Boolean> = _cardsFromDbReady.asStateFlow()

    val sortType = MutableStateFlow(loadSortTypePref())
    val gridColumns = MutableStateFlow(loadGridColumnsPref())
    val gradientColor = MutableStateFlow(loadGradientColorPref())
    val foldersEnabled = MutableStateFlow(loadFoldersEnabledPref())
    val showCoverPicker = MutableStateFlow(false)

    val folders: StateFlow<List<Folder>> = foldersFromDb.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalCardCount: StateFlow<Int> = cardsFromDb
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val folderCardCounts: StateFlow<Map<Long?, Int>> = cardsFromDb
        .map { cards ->
            val counts = mutableMapOf<Long?, Int>()
            cards.forEach { card ->
                if (card.folderIds.isEmpty()) {
                    counts[null] = (counts[null] ?: 0) + 1
                } else {
                    card.folderIds.forEach { folderId ->
                        counts[folderId] = (counts[folderId] ?: 0) + 1
                    }
                }
            }
            counts
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    private val _selectedTab = MutableStateFlow(loadSelectedTabPref())
    val selectedTab: StateFlow<TabSelection> = _selectedTab.asStateFlow()

    val cards: StateFlow<List<CardModel>> =
        combine(cardsFromDb, sortType, selectedTab, foldersEnabled) { list, sort, tab, enabled ->
            val filtered = if (!enabled) {
                list
            } else {
                when (tab) {
                    TabSelection.All -> list
                    is TabSelection.Folder -> list.filter { tab.id in it.folderIds }
                }
            }
            CardSortUtil.sorted(filtered, sort.name)
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

    private fun loadFoldersEnabledPref(): Boolean =
        prefs.getBoolean(SettingsKeys.KEY_FOLDERS_ENABLED, false)

    private fun loadSelectedTabPref(): TabSelection =
        TabSelection.fromPrefValue(prefs.getString(KEY_SELECTED_TAB, null))

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "grid_columns" -> gridColumns.value = loadGridColumnsPref()
            "gradient_color" -> gradientColor.value = loadGradientColorPref()
            SettingsKeys.KEY_FOLDERS_ENABLED -> foldersEnabled.value = loadFoldersEnabledPref()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        // Выбранная вкладка могла исчезнуть — возвращаем «Все».
        viewModelScope.launch {
            combine(folders, foldersEnabled, _foldersReady) { f, enabled, ready -> Triple(f, enabled, ready) }
                .collect { (folderList, enabled, foldersReady) ->
                    if (!enabled || !foldersReady || !_cardsFromDbReady.value) return@collect
                    val current = _selectedTab.value
                    val valid = when (current) {
                        TabSelection.All -> true
                        is TabSelection.Folder -> folderList.any { it.id == current.id }
                    }
                    if (!valid) selectTab(TabSelection.All)
                }
        }
    }

    fun selectTab(tab: TabSelection) {
        _selectedTab.value = tab
        prefs.edit { putString(KEY_SELECTED_TAB, tab.toPrefValue()) }
    }

    suspend fun createFolder(rawName: String): FolderCreationResult = withContext(Dispatchers.IO) {
        val result = folderDao.createWithValidation(rawName)
        if (result is FolderCreationResult.Success) {
            selectTab(TabSelection.Folder(result.folder.id))
        }
        result
    }

    suspend fun renameFolder(id: Long, rawName: String): FolderCreationResult = withContext(Dispatchers.IO) {
        val existing = folderDao.getAll()
        val folder = existing.firstOrNull { it.id == id }
            ?: return@withContext FolderCreationResult.Failure("Вкладка не найдена")
        val normalized = FolderNameValidator.normalize(rawName)
        FolderNameValidator.validate(
            raw = normalized,
            existing = existing.map { it.id to it.name },
            selfId = id
        )?.let { return@withContext FolderCreationResult.Failure(it.message) }

        val updated = folder.copy(name = normalized)
        folderDao.update(updated)
        FolderCreationResult.Success(updated.toFolder())
    }

    /** Сохраняет порядок вкладок после перетаскивания. */
    fun reorderFolders(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                val byId = folderDao.getAll().associateBy { it.id }
                orderedIds.forEachIndexed { index, id ->
                    val folder = byId[id] ?: return@forEachIndexed
                    if (folder.sortOrder != index.toLong()) {
                        folderDao.update(folder.copy(sortOrder = index.toLong()))
                    }
                }
            }
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                refDao.deleteByFolder(id)
                folderDao.deleteById(id)
            }
            if ((_selectedTab.value as? TabSelection.Folder)?.id == id) {
                selectTab(TabSelection.All)
            }
        }
    }

    /** Добавляет карты в папку или убирает из неё, не затрагивая другие папки. */
    fun setCardsInFolder(cardIds: Collection<Long>, folderId: Long, inFolder: Boolean) {
        if (cardIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                if (inFolder) {
                    refDao.insertAll(cardIds.map { CardFolderRefEntity(it, folderId) })
                } else {
                    cardIds.forEach { id -> refDao.deleteRef(id, folderId) }
                }
            }
        }
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
     * Сохраняет пользовательский порядок видимых карт (вкладка или весь список).
     * Видимые карты получают те же глобальные слоты, поэтому остальные не съезжают.
     */
    fun reorderCards(orderedCards: List<CardModel>) {
        if (orderedCards.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val all = dao.getAll().sortedWith(
                compareBy<ru.merrcurys.seacard.core.db.CardEntity> { it.sortOrder }
                    .thenByDescending { it.addTime }
            )
            val slots = CardReorderUtil.assignSlots(
                allInCustomOrder = all.map { it.id },
                visibleNewOrder = orderedCards.map { it.id }
            )
            db.withTransaction {
                slots.forEach { (id, slot) -> dao.updateSortOrder(id, slot) }
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
                refDao.deleteByCard(card.id)
                dao.deleteById(card.id)
            }
            ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(getApplication())
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onCleared()
    }

    private companion object {
        const val KEY_SELECTED_TAB = "selected_folder"
    }
}
