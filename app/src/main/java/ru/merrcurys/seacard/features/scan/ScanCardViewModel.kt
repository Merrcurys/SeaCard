package ru.merrcurys.seacard.features.scan

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import ru.merrcurys.seacard.core.db.CardEntity
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.ColorCoverGenerator
import ru.merrcurys.seacard.core.utils.CoverBitmapStorage
import ru.merrcurys.seacard.core.utils.CoverNames
import java.io.File

class ScanCardViewModel(application: Application, val coverAsset: String?) : AndroidViewModel(application) {

    private val app = application
    private val dao = DatabaseProvider.get(application).cardDao()

    val initialCardName: String = coverAsset?.let {
        val fileName = it.substringAfterLast('/')
        CoverNames.coverNameMap[fileName] ?: fileName.substringBeforeLast('.')
    } ?: ""

    val cardName = MutableStateFlow(initialCardName)
    val cardCode = MutableStateFlow("")
    val selectedColor = MutableStateFlow(0xFFFFFFFF.toInt())
    val scanned = MutableStateFlow(false)
    val scanSuccess = MutableStateFlow(false)
    val codeTypeState = MutableStateFlow("")
    val cardSaved = MutableStateFlow(false)

    val frontCoverUri = MutableStateFlow<Uri?>(null)
    val backCoverUri = MutableStateFlow<Uri?>(null)
    val showFrontCropDialog = MutableStateFlow(false)
    val showBackCropDialog = MutableStateFlow(false)
    val frontCropImageUri = MutableStateFlow<Uri?>(null)
    val backCropImageUri = MutableStateFlow<Uri?>(null)

    fun setCardName(name: String) { cardName.value = name }
    fun setCardCode(code: String) { cardCode.value = code }
    fun setSelectedColor(color: Int) { selectedColor.value = color }
    fun setScanned(value: Boolean) { scanned.value = value }
    fun setScanSuccess(value: Boolean) { scanSuccess.value = value }
    fun setCodeType(type: String) { codeTypeState.value = type }

    fun onScanResult(code: String, type: String) {
        cardCode.value = code
        codeTypeState.value = type
        scanSuccess.value = true
        scanned.value = true
    }

    fun showFrontCrop(uri: Uri) {
        frontCropImageUri.value = uri
        showFrontCropDialog.value = true
    }

    fun showBackCrop(uri: Uri) {
        backCropImageUri.value = uri
        showBackCropDialog.value = true
    }

    fun onFrontCropResult(bitmap: Bitmap) {
        val file = File.createTempFile("front_crop_", ".webp", app.cacheDir)
        CoverBitmapStorage.saveBitmapAsWebpFile(file, bitmap)
        frontCoverUri.value = Uri.fromFile(file)
        showFrontCropDialog.value = false
        frontCropImageUri.value = null
    }

    fun onBackCropResult(bitmap: Bitmap) {
        val file = File.createTempFile("back_crop_", ".webp", app.cacheDir)
        CoverBitmapStorage.saveBitmapAsWebpFile(file, bitmap)
        backCoverUri.value = Uri.fromFile(file)
        showBackCropDialog.value = false
        backCropImageUri.value = null
    }

    fun dismissFrontCrop() {
        showFrontCropDialog.value = false
        frontCropImageUri.value = null
    }

    fun dismissBackCrop() {
        showBackCropDialog.value = false
        backCropImageUri.value = null
    }

    fun setFrontCoverUri(uri: Uri?) { frontCoverUri.value = uri }
    fun setBackCoverUri(uri: Uri?) { backCoverUri.value = uri }

    private fun saveBitmapAsWebp(bitmap: Bitmap, fileName: String): String? =
        CoverBitmapStorage.saveBitmapAsWebpToCovers(app.filesDir, bitmap, fileName)

    suspend fun saveCardWithCover(
        name: String,
        code: String,
        codeType: String,
        color: Int,
        frontPath: String?,
        backPath: String?
    ) = withContext(Dispatchers.IO) {
        dao.insert(CardEntity(
            name = name,
            code = code,
            type = codeType,
            addTime = System.currentTimeMillis(),
            usageCount = 0,
            color = color,
            frontCoverPath = frontPath,
            backCoverPath = backPath,
            note = null
        ))
        ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(app)
    }

    /** Сохраняет карту с обложками из Uri (конвертирует в файлы). Если обложка не выбрана — генерирует из цвета и названия. */
    suspend fun saveCardWithCoverUris(
        name: String,
        code: String,
        codeType: String,
        color: Int
    ): Boolean = withContext(Dispatchers.IO) {
        var frontPath: String? = null
        var backPath: String? = null
        val timestamp = System.currentTimeMillis()
        frontCoverUri.value?.let { uri ->
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    if (bmp != null) frontPath = saveBitmapAsWebp(bmp, "front_${name}_$timestamp.webp")
                }
            } catch (_: Exception) { }
        }
        if (frontPath == null && coverAsset == null) {
            val safeName = name.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9\\-_]"), "_").take(50).ifBlank { "card" }
            frontPath = ColorCoverGenerator.generateAndSaveAsWebp(app, name, color, "front_${safeName}_$timestamp.webp")
        }
        backCoverUri.value?.let { uri ->
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    if (bmp != null) backPath = saveBitmapAsWebp(bmp, "back_${name}_$timestamp.webp")
                }
            } catch (_: Exception) { }
        }
        saveCardWithCover(name, code, codeType, color, frontPath, backPath)
        cardSaved.value = true
        true
    }

    /** Для сценария с coverAsset: сохранить и вернуть true один раз. */
    suspend fun saveIfCoverAssetReady(): Boolean {
        val asset = coverAsset ?: return false
        val name = cardName.value
        val code = cardCode.value
        val codeType = codeTypeState.value.ifBlank { "barcode" }
        val color = selectedColor.value
        if (name.isBlank() || code.isBlank() || cardSaved.value) return false
        saveCardWithCover(name, code, codeType, color, asset, null)
        cardSaved.value = true
        return true
    }

    companion object {
        fun detectCodeType(code: String): String = when {
            code.all { it.isDigit() } && code.length == 13 -> "ean13"
            code.all { it.isDigit() } && code.length == 12 -> "upca"
            code.all { it.isDigit() } && code.length == 8 -> "ean8"
            code.all { it.isDigit() } -> "code128"
            else -> "qr"
        }
    }
}
