package ru.merrcurys.seacard.features.detail

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import ru.merrcurys.seacard.core.db.CardFolderRefEntity
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.db.createWithValidation
import ru.merrcurys.seacard.core.utils.ColorCoverGenerator
import ru.merrcurys.seacard.core.utils.CoverBitmapStorage
import ru.merrcurys.seacard.core.utils.FolderCreationResult
import ru.merrcurys.seacard.core.utils.SortType
import ru.merrcurys.seacard.domain.entity.Folder
import ru.merrcurys.seacard.domain.entity.Card as CardModel
import ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider
import java.io.File

class CardDetailViewModel(application: Application, initialCardId: Long) : AndroidViewModel(application) {

    private val app = application
    private val db = DatabaseProvider.get(application)
    private val dao = db.cardDao()
    private val folderDao = db.cardFolderDao()
    private val refDao = db.cardFolderRefDao()

    private val _card = MutableStateFlow<CardModel?>(null)
    val card: StateFlow<CardModel?> = _card.asStateFlow()

    val folders: StateFlow<List<Folder>> = folderDao.getAllFlow()
        .map { list -> list.map { it.toFolder() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    var cardId: Long = initialCardId
        private set

    init {
        openCard(initialCardId)
    }

    /**
     * Открывает карточку: переключает экран и учитывает использование.
     * Вызывается при первом открытии и при повторном тапе по виджету (onNewIntent).
     */
    fun openCard(id: Long) {
        if (id < 0) return
        cardId = id
        _card.value = null
        loadCard()
        // Учитываем использование при каждом открытии карточки — в т.ч. из виджета.
        viewModelScope.launch(Dispatchers.IO) {
            dao.incrementUsage(id)
            // При сортировке по частоте порядок карт в виджете зависит от usageCount,
            // поэтому просим виджет перечитать данные (иначе он остаётся в старом порядке).
            val sortType = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("sort_type", null)
            if (sortType == SortType.USAGE_FREQ.name) {
                SeaCardAppWidgetProvider.notifyDataChanged(app)
            }
        }
    }

    private suspend fun loadCardModel(): CardModel? {
        val entity = dao.getById(cardId) ?: return null
        return entity.toCard().copy(folderIds = refDao.getFolderIds(cardId).toSet())
    }

    fun loadCard() {
        viewModelScope.launch(Dispatchers.IO) {
            _card.value = loadCardModel()
        }
    }

    fun updateCard(updated: CardModel?) {
        _card.value = updated
    }

    suspend fun getCard(): CardModel? = withContext(Dispatchers.IO) {
        loadCardModel()
    }

    suspend fun createFolder(rawName: String): FolderCreationResult = withContext(Dispatchers.IO) {
        folderDao.createWithValidation(rawName)
    }

    fun updateNote(note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateNote(cardId, note)
            _card.value = loadCardModel()
        }
    }

    fun updateFrontCover(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateFrontCover(cardId, path)
            _card.value = loadCardModel()
        }
    }

    fun updateBackCover(path: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateBackCover(cardId, path)
            _card.value = loadCardModel()
        }
    }

    fun updateCardFields(name: String, code: String, type: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getById(cardId) ?: return@launch
            dao.update(entity.copy(name = name, code = code, type = type, color = color))
            _card.value = loadCardModel()
        }
    }

    fun updateCardFromEdit(
        name: String,
        code: String,
        type: String,
        color: Int,
        frontUri: Uri?,
        backUri: Uri?,
        frontRemoved: Boolean,
        backRemoved: Boolean,
        frontDirty: Boolean,
        backDirty: Boolean,
        folderIds: Set<Long>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getById(cardId) ?: return@launch
            val normalizedName = normalizeCardName(name)
            var frontPath = entity.frontCoverPath
            var backPath = entity.backCoverPath
            val timestamp = System.currentTimeMillis()

            if (frontDirty) {
                deleteCoverFileIfLocal(frontPath)
                frontPath = when {
                    frontRemoved -> generateFrontCoverFromColor(normalizedName, color, timestamp)
                    frontUri != null -> saveCoverFromUri(
                        frontUri,
                        "front_${CoverBitmapStorage.safeFileNamePart(normalizedName)}_$timestamp.webp"
                    )
                    else -> frontPath
                }
            } else if (
                (color != entity.color || normalizedName != entity.name) &&
                frontPath != null &&
                ColorCoverGenerator.isGeneratedColorCover(frontPath, entity.color)
            ) {
                deleteCoverFileIfLocal(frontPath)
                frontPath = generateFrontCoverFromColor(normalizedName, color, timestamp)
            }

            if (backDirty) {
                deleteCoverFileIfLocal(backPath)
                backPath = when {
                    backRemoved -> null
                    backUri != null -> saveCoverFromUri(
                        backUri,
                        "back_${CoverBitmapStorage.safeFileNamePart(normalizedName)}_$timestamp.webp"
                    )
                    else -> backPath
                }
            }

            db.withTransaction {
                dao.update(
                    entity.copy(
                        name = normalizedName,
                        code = code,
                        type = type,
                        color = color,
                        frontCoverPath = frontPath,
                        backCoverPath = backPath
                    )
                )
                refDao.deleteByCard(cardId)
                if (folderIds.isNotEmpty()) {
                    refDao.insertAll(folderIds.map { CardFolderRefEntity(cardId, it) })
                }
            }
            ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(app)
            _card.value = loadCardModel()
        }
    }

    fun deleteCard(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Обложка принадлежит конкретной карте — при удалении чистим и её файлы.
            val entity = dao.getById(cardId)
            deleteCoverFileIfLocal(entity?.frontCoverPath)
            deleteCoverFileIfLocal(entity?.backCoverPath)
            refDao.deleteByCard(cardId)
            dao.deleteById(cardId)
            ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(getApplication())
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private fun normalizeCardName(name: String): String =
        name
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    private fun deleteCoverFileIfLocal(path: String?) {
        if (path.isNullOrBlank() || path.startsWith("cards/")) return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }

    private fun saveCoverFromUri(uri: Uri, fileName: String): String? {
        return try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                if (bmp != null) {
                    CoverBitmapStorage.saveBitmapAsWebpToCovers(app.filesDir, bmp, fileName)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateFrontCoverFromColor(name: String, color: Int, timestamp: Long): String? {
        val safeName = CoverBitmapStorage.safeFileNamePart(name)
        return ColorCoverGenerator.generateAndSaveAsWebp(
            app,
            name,
            color,
            "front_${safeName}_$timestamp.webp"
        )
    }
}

class CardDetailViewModelFactory(private val application: Application, private val cardId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = CardDetailViewModel(application, cardId) as T
}
