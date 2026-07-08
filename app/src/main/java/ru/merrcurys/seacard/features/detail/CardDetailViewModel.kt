package ru.merrcurys.seacard.features.detail

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.ColorCoverGenerator
import ru.merrcurys.seacard.core.utils.CoverBitmapStorage
import ru.merrcurys.seacard.domain.entity.Card as CardModel
import java.io.File

class CardDetailViewModel(application: Application, val cardId: Long) : AndroidViewModel(application) {

    private val app = application
    private val dao = DatabaseProvider.get(application).cardDao()

    private val _card = MutableStateFlow<CardModel?>(null)
    val card: StateFlow<CardModel?> = _card.asStateFlow()

    init {
        loadCard()
    }

    fun loadCard() {
        viewModelScope.launch(Dispatchers.IO) {
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun updateCard(updated: CardModel?) {
        _card.value = updated
    }

    suspend fun getCard(): CardModel? = withContext(Dispatchers.IO) {
        dao.getById(cardId)?.toCard()
    }

    fun updateNote(note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateNote(cardId, note)
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun updateFrontCover(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateFrontCover(cardId, path)
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun updateBackCover(path: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateBackCover(cardId, path)
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun updateCardFields(name: String, code: String, type: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getById(cardId) ?: return@launch
            dao.update(entity.copy(name = name, code = code, type = type, color = color))
            _card.value = dao.getById(cardId)?.toCard()
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
        backDirty: Boolean
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
                    frontUri != null -> saveCoverFromUri(frontUri, "front_${normalizedName}_$timestamp.webp")
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
                    backUri != null -> saveCoverFromUri(backUri, "back_${normalizedName}_$timestamp.webp")
                    else -> backPath
                }
            }

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
            ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(app)
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun deleteCard(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
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
        val safeName = name.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9\\-_]"), "_").take(50).ifBlank { "card" }
        return ColorCoverGenerator.generateAndSaveAsWebp(
            app,
            name,
            color,
            "front_${safeName}_$timestamp.webp"
        )
    }
}

class CardDetailViewModelFactory(private val application: Application, private val cardId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = CardDetailViewModel(application, cardId) as T
}
