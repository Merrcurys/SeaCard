package ru.merrcurys.seacard.features.detail

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.merrcurys.seacard.core.design.DynamicGradientBackground
import ru.merrcurys.seacard.core.design.SeaCardTheme
import ru.merrcurys.seacard.core.design.applySeaCardSystemBarColors

class CardDetailActivity : ComponentActivity() {
    private var originalBrightness: Float = 0f
    private var hasBrightnessPermission: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySeaCardSystemBarColors()

        val cardId = intent.getLongExtra("card_id", -1L)
        if (cardId < 0) {
            finish()
            return
        }

        // Сохранение текущей яркости и увеличение ее.
        originalBrightness = window.attributes.screenBrightness
        if (originalBrightness == -1f) {
            originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        }
        setBrightness(1.0f) // Максимальная яркость

        setContent {
            val viewModel: CardDetailViewModel = viewModel(factory = CardDetailViewModelFactory(application, cardId))
            val card by viewModel.card.collectAsState()
            var frontImageUri by remember { mutableStateOf<Uri?>(null) }
            var hasCameraPermission by remember {
                mutableStateOf(ContextCompat.checkSelfPermission(this@CardDetailActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                hasCameraPermission = granted
            }

            // Обновляем проверку разрешения при изменении состояния
            LaunchedEffect(Unit) {
                if (Settings.System.canWrite(this@CardDetailActivity)) {
                    hasBrightnessPermission = true
                    originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                    setBrightness(1.0f)
                }
            }

            val cardNameState = card?.name ?: ""
            val cardCodeState = card?.code ?: ""
            val codeTypeState = card?.type ?: "barcode"
            val cardColorState = card?.color ?: 0xFFFFFFFF.toInt()
            val frontCoverPath = card?.frontCoverPath

            SeaCardTheme {
                val baseColor = Color(cardColorState)
                val backgroundColor = MaterialTheme.colorScheme.background
                val gradientColors = listOf(
                    baseColor,
                    backgroundColor
                )
                // Вычисляем контрастный цвет для текста на основе базового цвета градиента
                val contrastTextColor = getContrastTextColor(baseColor)

                if (card == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    DynamicGradientBackground(colors = gradientColors) {
                        CardDetailScreen(
                            viewModel = viewModel,
                            cardName = cardNameState,
                            cardCode = cardCodeState,
                            codeType = codeTypeState,
                            cardColor = cardColorState,
                            coverAsset = card?.coverAsset,
                            frontCoverPath = frontCoverPath,
                            frontImageUri = frontImageUri,
                            note = card?.note ?: "",
                            backCoverPath = card?.backCoverPath,
                            hasCameraPermission = hasCameraPermission,
                            permissionLauncher = permissionLauncher,
                            onBack = { finish() },
                            onDelete = { viewModel.deleteCard { setResult(RESULT_OK); finish() } },
                            topBarContainerColor = Color.Transparent,
                            topBarTextColor = contrastTextColor
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        setBrightness(originalBrightness)
    }

    private fun setBrightness(brightness: Float) {
        try {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
            window.attributes = layoutParams
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadThemePref(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("dark_theme", true)
    }
}
