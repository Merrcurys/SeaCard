package ru.merrcurys.seacard.features.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.core.backup.BackupManager
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.design.BerlinAzure
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.SeaCardTheme
import ru.merrcurys.seacard.core.design.applySeaCardSystemBarColors

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySeaCardSystemBarColors()
        var exportCards: (() -> Unit)? = null
        var importCards: (() -> Unit)? = null
        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
            if (uri != null) {
                try {
                    runBlocking(Dispatchers.IO) {
                        val dao = DatabaseProvider.get(this@SettingsActivity).cardDao()
                        val cards = dao.getAll()
                        contentResolver.openOutputStream(uri)?.use { out ->
                            BackupManager.exportToZip(this@SettingsActivity, cards, out)
                        }
                    }
                    Toast.makeText(this, "Бэкап экспортирован (карточки и обложки)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка экспорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val result = runBlocking(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                                BackupManager.importFromZip(this@SettingsActivity, bytes.inputStream())
                            } else {
                                importLegacyTxt(this@SettingsActivity, String(bytes, StandardCharsets.UTF_8))
                            }
                        } ?: Pair(0, listOf("Не удалось открыть файл"))
                    }
                    val (imported, errors) = result
                    if (errors.isEmpty()) {
                        Toast.makeText(this, "Импортировано карт: $imported", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Импортировано: $imported. Ошибки: ${errors.take(3).joinToString()}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка импорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        exportCards = { exportLauncher.launch("seacard_backup.zip") }
        importCards = {
            importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "text/plain"))
        }
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val gradientColor by viewModel.gradientColor.collectAsState(initial = BerlinAzure)
            val gridColumns by viewModel.gridColumns.collectAsState(initial = 2)
            SeaCardTheme {
                GradientBackground(gradientColor = gradientColor) {
                    SettingsScreen(
                        gradientColor = gradientColor,
                        onGradientColorChange = { viewModel.setGradientColor(it) },
                        gridColumns = gridColumns,
                        onGridColumnsChange = { viewModel.setGridColumns(it) },
                        onBack = { finish() },
                        topBarContainerColor = Color.Transparent,
                        onExport = { exportCards() },
                        onImport = { importCards() }
                    )
                }
            }
        }
    }
}
