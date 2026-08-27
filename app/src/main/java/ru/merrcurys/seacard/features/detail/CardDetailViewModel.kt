package ru.merrcurys.seacard.features.detail

import android.app.Application
import android.graphics.Bitmap
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

    // Диалоги
    private val _showMenu = MutableStateFlow(false)
    val showMenu: StateFlow<Boolean> = _showMenu.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _showNoteDialog = MutableStateFlow(false)
    val showNoteDialog: StateFlow<Boolean> = _showNoteDialog.asStateFlow()

    private val _showCoverDialog = MutableStateFlow(false)
    val showCoverDialog: StateFlow<Boolean> = _showCoverDialog.asStateFlow()

    // Заметка
    private val _noteDraft = MutableStateFlow("")
    val noteDraft: StateFlow<String> = _noteDraft.asStateFlow()

    private val _noteError = MutableStateFlow("")
    val noteError: StateFlow<String> = _noteError.asStateFlow()

    // Черновик редактирования
    private val _editName = MutableStateFlow("")
    val editName: StateFlow<String> = _editName.asStateFlow()

    private val _editCode = MutableStateFlow("")
    val editCode: StateFlow<String> = _editCode.asStateFlow()

    private val _editType = MutableStateFlow("")
    val editType: StateFlow<String> = _editType.asStateFlow()

    private val _editColor = MutableStateFlow(0xFFFFFFFF.toInt())
    val editColor: StateFlow<Int> = _editColor.asStateFlow()

    private val _editError = MutableStateFlow("")
    val editError: StateFlow<String> = _editError.asStateFlow()

    private val _editFrontCoverUri = MutableStateFlow<Uri?>(null)
    val editFrontCoverUri: StateFlow<Uri?> = _editFrontCoverUri.asStateFlow()

    private val _editBackCoverUri = MutableStateFlow<Uri?>(null)
    val editBackCoverUri: StateFlow<Uri?> = _editBackCoverUri.asStateFlow()

    private val _editFrontCoverRemoved = MutableStateFlow(false)
    val editFrontCoverRemoved: StateFlow<Boolean> = _editFrontCoverRemoved.asStateFlow()

    private val _editBackCoverRemoved = MutableStateFlow(false)
    val editBackCoverRemoved: StateFlow<Boolean> = _editBackCoverRemoved.asStateFlow()

    private val _initialEditFrontUri = MutableStateFlow<Uri?>(null)
    val initialEditFrontUri: StateFlow<Uri?> = _initialEditFrontUri.asStateFlow()

    private val _initialEditBackUri = MutableStateFlow<Uri?>(null)
    val initialEditBackUri: StateFlow<Uri?> = _initialEditBackUri.asStateFlow()

    // Кадрирование обложек
    private val _showEditFrontCrop = MutableStateFlow(false)
    val showEditFrontCrop: StateFlow<Boolean> = _showEditFrontCrop.asStateFlow()

    private val _showEditBackCrop = MutableStateFlow(false)
    val showEditBackCrop: StateFlow<Boolean> = _showEditBackCrop.asStateFlow()

    private val _editFrontCropUri = MutableStateFlow<Uri?>(null)
    val editFrontCropUri: StateFlow<Uri?> = _editFrontCropUri.asStateFlow()

    private val _editBackCropUri = MutableStateFlow<Uri?>(null)
    val editBackCropUri: StateFlow<Uri?> = _editBackCropUri.asStateFlow()

    // Полноэкранный просмотр обложки
    private val _showFullScreenImage = MutableStateFlow<Pair<Boolean, Uri?>>(false to null)
    val showFullScreenImage: StateFlow<Pair<Boolean, Uri?>> = _showFullScreenImage.asStateFlow()

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

    fun setShowMenu(show: Boolean) {
        _showMenu.value = show
    }

    fun setShowDeleteDialog(show: Boolean) {
        _showDeleteDialog.value = show
    }

    fun setShowNoteDialog(show: Boolean) {
        if (show) _noteDraft.value = _card.value?.note ?: ""
        _showNoteDialog.value = show
    }

    fun setShowCoverDialog(show: Boolean) {
        _showCoverDialog.value = show
    }

    fun setShowFullScreenImage(value: Pair<Boolean, Uri?>) {
        _showFullScreenImage.value = value
    }

    fun openEditDialog() {
        resetEditDraft()
        _showEditDialog.value = true
    }

    fun closeEditDialog() {
        resetEditDraft()
        _showEditDialog.value = false
    }

    fun saveEdit() {
        val normalizedName = normalizeCardName(_editName.value)
        if (normalizedName.isBlank()) {
            _editError.value = "Заполните имя карты"
            return
        }
        val type = _editType.value.ifBlank { if (_editCode.value.isBlank()) "none" else "code128" }
        val code = _editCode.value
        val frontDirty = _editFrontCoverRemoved.value || _editFrontCoverUri.value != _initialEditFrontUri.value
        val backDirty = _editBackCoverRemoved.value || _editBackCoverUri.value != _initialEditBackUri.value
        _showEditDialog.value = false
        _editError.value = ""
        updateCardFromEdit(
            normalizedName,
            code,
            type,
            _editColor.value,
            _editFrontCoverUri.value,
            _editBackCoverUri.value,
            _editFrontCoverRemoved.value,
            _editBackCoverRemoved.value,
            frontDirty,
            backDirty
        )
    }

    fun setEditName(value: String) {
        _editName.value = value
    }

    fun setEditCode(value: String) {
        _editCode.value = value
    }

    fun setEditType(value: String) {
        _editType.value = value
    }

    fun setEditColor(value: Int) {
        _editColor.value = value
    }

    fun removeEditFrontCover() {
        _editFrontCoverUri.value = null
        _editFrontCoverRemoved.value = true
    }

    fun removeEditBackCover() {
        _editBackCoverUri.value = null
        _editBackCoverRemoved.value = true
    }

    fun setNoteDraft(value: String) {
        _noteDraft.value = value
        _noteError.value = ""
    }

    fun saveNote() {
        if (_noteDraft.value.length <= 100) {
            updateNote(_noteDraft.value)
            _showNoteDialog.value = false
        } else {
            _noteError.value = "Максимум 100 символов"
        }
    }

    fun onEditFrontPicked(uri: Uri) {
        _editFrontCropUri.value = uri
        _showEditFrontCrop.value = true
    }

    fun onEditBackPicked(uri: Uri) {
        _editBackCropUri.value = uri
        _showEditBackCrop.value = true
    }

    fun dismissEditFrontCrop() {
        _showEditFrontCrop.value = false
        _editFrontCropUri.value = null
    }

    fun dismissEditBackCrop() {
        _showEditBackCrop.value = false
        _editBackCropUri.value = null
    }

    fun onEditFrontCrop(bitmap: Bitmap) {
        val file = File.createTempFile("front_crop_", ".webp", app.cacheDir)
        CoverBitmapStorage.saveBitmapAsWebpFile(file, bitmap)
        _editFrontCoverUri.value = Uri.fromFile(file)
        _editFrontCoverRemoved.value = false
        _showEditFrontCrop.value = false
        _editFrontCropUri.value = null
    }

    fun onEditBackCrop(bitmap: Bitmap) {
        val file = File.createTempFile("back_crop_", ".webp", app.cacheDir)
        CoverBitmapStorage.saveBitmapAsWebpFile(file, bitmap)
        _editBackCoverUri.value = Uri.fromFile(file)
        _editBackCoverRemoved.value = false
        _showEditBackCrop.value = false
        _editBackCropUri.value = null
    }

    private fun resetEditDraft() {
        val card = _card.value
        val frontCoverUri = card?.frontCoverPath?.takeIf { !it.startsWith("cards/") }?.let { Uri.fromFile(File(it)) }
        val backCoverUri = card?.backCoverPath?.let { Uri.fromFile(File(it)) }
        val displayCodeType = when (val type = card?.type ?: "") {
            "barcode", "" -> "code128"
            else -> type
        }
        _editName.value = card?.name ?: ""
        _editCode.value = card?.code ?: ""
        _editType.value = displayCodeType
        _editColor.value = card?.color ?: 0xFFFFFFFF.toInt()
        _editError.value = ""
        _editFrontCoverUri.value = frontCoverUri
        _editBackCoverUri.value = backCoverUri
        _editFrontCoverRemoved.value = false
        _editBackCoverRemoved.value = false
        _initialEditFrontUri.value = frontCoverUri
        _initialEditBackUri.value = backCoverUri
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
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = CardDetailViewModel(application, cardId) as T
}
