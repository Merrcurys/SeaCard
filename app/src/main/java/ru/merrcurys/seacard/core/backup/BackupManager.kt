package ru.merrcurys.seacard.core.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.merrcurys.seacard.core.db.CardEntity
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.CardColorResolver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Экспорт и импорт бэкапа: ZIP с карточками (JSON) и файлами обложек.
 * Структура ZIP: cards.json, covers/0_front.webp, covers/0_back.webp, ...
 */
object BackupManager {

    private const val CARDS_JSON = "cards.json"
    private const val COVERS_DIR = "covers"

    /**
     * Экспортирует все карточки и их обложки в ZIP в outputStream.
     * Обложки: файлы с диска или из assets (cards/...) копируются в папку covers/ внутри ZIP.
     * @param cards список карточек (получить через dao.getAll() на IO)
     */
    fun exportToZip(context: Context, cards: List<CardEntity>, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)
        zip.use {
            val jsonArray = JSONArray()
            cards.forEachIndexed { index, card ->
                val frontInZip = if (card.frontCoverPath != null) "${COVERS_DIR}/${index}_front.webp" else null
                val backInZip = if (card.backCoverPath != null) "${COVERS_DIR}/${index}_back.webp" else null
                val obj = JSONObject().apply {
                    put("name", card.name)
                    put("code", card.code)
                    put("type", card.type)
                    put("addTime", card.addTime)
                    put("usageCount", card.usageCount)
                    put("color", card.color)
                    put("note", card.note ?: "")
                    if (frontInZip != null) put("frontCoverFile", frontInZip)
                    if (backInZip != null) put("backCoverFile", backInZip)
                }
                jsonArray.put(obj)
                card.frontCoverPath?.let { path ->
                    copyCoverToZip(context, path, "${COVERS_DIR}/${index}_front.webp", zip)
                }
                card.backCoverPath?.let { path ->
                    if (path.startsWith("cards/")) return@let
                    copyFileToZip(File(path), "${COVERS_DIR}/${index}_back.webp", zip)
                }
            }
            it.putNextEntry(ZipEntry(CARDS_JSON))
            it.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
            it.closeEntry()
        }
    }

    private fun copyCoverToZip(context: Context, sourcePath: String, entryName: String, zip: ZipOutputStream) {
        if (sourcePath.startsWith("cards/")) {
            context.assets.open(sourcePath).use { input ->
                zip.putNextEntry(ZipEntry(entryName))
                input.copyTo(zip)
                zip.closeEntry()
            }
        } else {
            copyFileToZip(File(sourcePath), entryName, zip)
        }
    }

    private fun copyFileToZip(file: File, entryName: String, zip: ZipOutputStream) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Импортирует карточки и обложки из ZIP.
     * Существующие карточки (по name+code+type) пропускаются.
     * Возвращает пару (импортировано, ошибки). Вызывать из корутины (например runBlocking(IO)).
     */
    suspend fun importFromZip(context: Context, inputStream: InputStream): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        val dao = DatabaseProvider.get(context).cardDao()
        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()
        val errors = mutableListOf<String>()
        var imported = 0
        ZipInputStream(inputStream).use { zis ->
            val entriesByPath = mutableMapOf<String, ByteArray>()
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entriesByPath[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            val cardsJson = entriesByPath[CARDS_JSON] ?: return@withContext Pair(0, listOf("В архиве нет cards.json"))
            val array = JSONArray(String(cardsJson, Charsets.UTF_8))
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val name = obj.optString("name", "")
                    val code = obj.optString("code", "")
                    val type = obj.optString("type", "barcode")
                    if (name.isBlank() || code.isBlank()) {
                        errors.add("Карта $i: пустое имя или код")
                        continue
                    }
                    if (dao.getByNameCodeType(name, code, type) != null) continue
                    val frontCoverFile = obj.optString("frontCoverFile", "").takeIf { it.isNotBlank() }
                    val backCoverFile = obj.optString("backCoverFile", "").takeIf { it.isNotBlank() }
                    var frontPath: String? = null
                    var backPath: String? = null
                    frontCoverFile?.let { path ->
                        entriesByPath[path]?.let { bytes ->
                            val outFile = File(coversDir, "front_${name}_${System.currentTimeMillis()}_$i.webp")
                            outFile.writeBytes(bytes)
                            frontPath = outFile.absolutePath
                        }
                    }
                    backCoverFile?.let { path ->
                        entriesByPath[path]?.let { bytes ->
                            val outFile = File(coversDir, "back_${name}_${System.currentTimeMillis()}_$i.webp")
                            outFile.writeBytes(bytes)
                            backPath = outFile.absolutePath
                        }
                    }
                    val storedColor = obj.optInt("color", CardColorResolver.DEFAULT_CARD_COLOR)
                    val resolvedColor = CardColorResolver.resolveColor(context, storedColor, frontPath)
                    dao.insert(CardEntity(
                        name = name,
                        code = code,
                        type = type,
                        addTime = obj.optLong("addTime", System.currentTimeMillis()),
                        usageCount = obj.optInt("usageCount", 0),
                        color = resolvedColor,
                        frontCoverPath = frontPath,
                        backCoverPath = backPath,
                        note = obj.optString("note", "").takeIf { it.isNotBlank() }
                    ))
                    imported++
                } catch (e: Exception) {
                    errors.add("Карта $i: ${e.message}")
                }
            }
        }
        if (imported > 0) ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(context)
        Pair(imported, errors)
    }
}
