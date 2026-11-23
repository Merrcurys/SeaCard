package ru.merrcurys.seacard.core.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.merrcurys.seacard.core.db.CardEntity
import ru.merrcurys.seacard.core.db.CardFolderEntity
import ru.merrcurys.seacard.core.db.CardFolderRefEntity
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.CardColorResolver
import ru.merrcurys.seacard.core.utils.FolderNameValidator
import java.io.File
import java.util.Locale
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
    private const val FOLDERS_JSON = "folders.json"
    private const val COVERS_DIR = "covers"

    /**
     * Экспортирует все карточки, папки, связи и обложки в ZIP в outputStream.
     * Обложки: файлы с диска или из assets (cards/...) копируются в папку covers/ внутри ZIP.
     * @param cards список карточек (получить через dao.getAll() на IO)
     * @param folders список папок (получить через folderDao.getAll() на IO)
     * @param refs связи карта ↔ папка (получить через refDao.getAll() на IO)
     */
    fun exportToZip(
        context: Context,
        cards: List<CardEntity>,
        folders: List<CardFolderEntity>,
        refs: List<CardFolderRefEntity>,
        outputStream: OutputStream
    ) {
        val folderNames = folders.associate { it.id to it.name }
        val folderIdsByCard = refs.groupBy({ it.cardId }, { it.folderId })
        val zip = ZipOutputStream(outputStream)
        zip.use {
            val jsonArray = JSONArray()
            cards.forEachIndexed { index, card ->
                val frontEntry = "${COVERS_DIR}/${index}_front.webp"
                val backEntry = "${COVERS_DIR}/${index}_back.webp"

                val frontPath = card.frontCoverPath
                val backPath = card.backCoverPath

                // Копируем обложку и ссылаемся в JSON только на ту, что реально попала в архив:
                // ассет (cards/...) мог быть удалён из приложения в новой версии, и это
                // не должно ломать экспорт остальных карточек.
                val frontInZip = frontPath != null && copyCoverToZip(context, frontPath, frontEntry, zip)
                val backInZip = backPath != null &&
                    !backPath.startsWith("cards/") &&
                    copyFileToZip(File(backPath), backEntry, zip)

                val obj = JSONObject().apply {
                    put("name", card.name)
                    put("code", card.code)
                    put("type", card.type)
                    put("addTime", card.addTime)
                    put("usageCount", card.usageCount)
                    put("sortOrder", card.sortOrder)
                    put("color", card.color)
                    put("note", card.note ?: "")
                    val cardFolderNames = folderIdsByCard[card.id].orEmpty()
                        .mapNotNull { id -> folderNames[id] }
                        .distinct()
                    if (cardFolderNames.isNotEmpty()) {
                        // "folder" оставляем для совместимости со старыми импортёрами.
                        put("folder", cardFolderNames.first())
                        put("folders", JSONArray(cardFolderNames))
                    }
                    if (frontInZip) put("frontCoverFile", frontEntry)
                    if (backInZip) put("backCoverFile", backEntry)
                }
                jsonArray.put(obj)
            }
            val foldersArray = JSONArray()
            folders.forEach { folder ->
                foldersArray.put(JSONObject().apply {
                    put("name", folder.name)
                    put("sortOrder", folder.sortOrder)
                    put("createdAt", folder.createdAt)
                })
            }
            it.putNextEntry(ZipEntry(FOLDERS_JSON))
            it.write(foldersArray.toString(2).toByteArray(Charsets.UTF_8))
            it.closeEntry()

            it.putNextEntry(ZipEntry(CARDS_JSON))
            it.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
            it.closeEntry()
        }
    }

    /** @return true, если обложка действительно записана в архив. */
    private fun copyCoverToZip(context: Context, sourcePath: String, entryName: String, zip: ZipOutputStream): Boolean {
        if (sourcePath.startsWith("cards/")) {
            return try {
                context.assets.open(sourcePath).use { input ->
                    writeZipEntry(zip, entryName) { out -> input.copyTo(out) }
                }
                true
            } catch (_: Exception) {
                // Обложка-ассет удалена из приложения — пропускаем её, экспорт продолжается.
                false
            }
        }
        return copyFileToZip(File(sourcePath), entryName, zip)
    }

    /** @return true, если файл существует и записан в архив. */
    private fun copyFileToZip(file: File, entryName: String, zip: ZipOutputStream): Boolean {
        if (!file.exists()) return false
        return try {
            file.inputStream().use { input ->
                writeZipEntry(zip, entryName) { out -> input.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, entryName: String, writeBody: (ZipOutputStream) -> Unit) {
        zip.putNextEntry(ZipEntry(entryName))
        try {
            writeBody(zip)
        } finally {
            zip.closeEntry()
        }
    }

    /**
     * Импортирует карточки и обложки из ZIP.
     * Существующие карточки (по name+code+type) пропускаются.
     * Возвращает пару (импортировано, ошибки). Вызывать из корутины (например runBlocking(IO)).
     */
    suspend fun importFromZip(context: Context, inputStream: InputStream): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val dao = db.cardDao()
        val folderDao = db.cardFolderDao()
        val refDao = db.cardFolderRefDao()
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
            // Папки из нового бэкапа: создаём отсутствующие и запоминаем id по имени
            // (без учёта регистра). Старые архивы без folders.json просто дают пустую карту.
            val folderIdByName = mutableMapOf<String, Long>()
            entriesByPath[FOLDERS_JSON]?.let { foldersJson ->
                try {
                    val foldersArray = JSONArray(String(foldersJson, Charsets.UTF_8))
                    val byName = folderDao.getAll()
                        .associateBy { it.name.lowercase(Locale.ROOT) }
                        .toMutableMap()
                    for (i in 0 until foldersArray.length()) {
                        val obj = foldersArray.getJSONObject(i)
                        val name = FolderNameValidator.normalize(obj.optString("name", ""))
                        if (name.isBlank()) continue
                        val key = name.lowercase(Locale.ROOT)
                        val sortOrder = obj.optLong("sortOrder", i.toLong())
                        val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        val id = byName[key]?.id ?: run {
                            val entity = CardFolderEntity(
                                name = name,
                                sortOrder = sortOrder,
                                createdAt = createdAt
                            )
                            val newId = folderDao.insert(entity)
                            byName[key] = entity.copy(id = newId)
                            newId
                        }
                        folderIdByName[key] = id
                    }
                } catch (e: Exception) {
                    errors.add("Папки: ${e.message}")
                }
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
                    val addTime = obj.optLong("addTime", System.currentTimeMillis())
                    val cardFolderNames = mutableListOf<String>()
                    obj.optJSONArray("folders")?.let { foldersArray ->
                        for (j in 0 until foldersArray.length()) {
                            val folderName = foldersArray.optString(j, "")
                            if (folderName.isNotBlank()) cardFolderNames.add(folderName)
                        }
                    }
                    if (cardFolderNames.isEmpty()) {
                        obj.optString("folder", "")
                            .takeIf { it.isNotBlank() }
                            ?.let { cardFolderNames.add(it) }
                    }
                    val cardId = dao.insert(CardEntity(
                        name = name,
                        code = code,
                        type = type,
                        addTime = addTime,
                        usageCount = obj.optInt("usageCount", 0),
                        color = resolvedColor,
                        frontCoverPath = frontPath,
                        backCoverPath = backPath,
                        note = obj.optString("note", "").takeIf { it.isNotBlank() },
                        sortOrder = obj.optLong("sortOrder", -addTime)
                    ))
                    val cardFolderIds = cardFolderNames
                        .mapNotNull { name ->
                            folderIdByName[FolderNameValidator.normalize(name).lowercase(Locale.ROOT)]
                        }
                        .distinct()
                    if (cardFolderIds.isNotEmpty()) {
                        refDao.insertAll(cardFolderIds.map { CardFolderRefEntity(cardId, it) })
                    }
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
