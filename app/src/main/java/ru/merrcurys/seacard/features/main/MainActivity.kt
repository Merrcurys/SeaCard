package ru.merrcurys.seacard.features.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.SeaCardTheme
import ru.merrcurys.seacard.core.design.applySeaCardSystemBarColors
import ru.merrcurys.seacard.core.play.PlayInAppUpdateController
import ru.merrcurys.seacard.core.play.PlayReviewHelper
import ru.merrcurys.seacard.core.rustore.RuStoreInAppUpdateController
import ru.merrcurys.seacard.core.rustore.RuStoreReviewHelper
import ru.merrcurys.seacard.core.store.AppInstallSource
import ru.merrcurys.seacard.features.detail.CardDetailActivity
import ru.merrcurys.seacard.features.scan.ScanCardActivity
import ru.merrcurys.seacard.features.settings.SettingsActivity

private const val IN_APP_REVIEW_LOG_TAG = "InAppReview"

class MainActivity : ComponentActivity() {

    private var playUpdateController: PlayInAppUpdateController? = null
    private var ruStoreUpdateController: RuStoreInAppUpdateController? = null
    private val installSource: AppInstallSource by lazy { AppInstallSource.detect(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySeaCardSystemBarColors()

        when (installSource) {
            AppInstallSource.GOOGLE_PLAY ->
                playUpdateController = PlayInAppUpdateController(this).also { it.checkOnLaunch() }
            AppInstallSource.RU_STORE ->
                ruStoreUpdateController = RuStoreInAppUpdateController(this).also { it.checkOnLaunch() }
            AppInstallSource.UNKNOWN -> Unit
        }

        setContent {
            val viewModel: MainViewModel = viewModel()
            val cards by viewModel.cards.collectAsStateWithLifecycle(initialValue = emptyList())
            val cardsFromDbReady by viewModel.cardsFromDbReady.collectAsStateWithLifecycle(initialValue = false)
            val currentSortType by viewModel.sortType.collectAsStateWithLifecycle()
            val showCoverPicker by viewModel.showCoverPicker.collectAsStateWithLifecycle()
            val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
            val gradientColor by viewModel.gradientColor.collectAsStateWithLifecycle()
            val showSearch by viewModel.showSearch.collectAsStateWithLifecycle()
            val showFilterMenu by viewModel.showFilterMenu.collectAsStateWithLifecycle()
            val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
            val selectedCardIds by viewModel.selectedCardIds.collectAsStateWithLifecycle()

            val scanCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val cardDetailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val context = this@MainActivity

            LaunchedEffect(installSource) {
                if (installSource == AppInstallSource.UNKNOWN) return@LaunchedEffect

                Log.i(
                    IN_APP_REVIEW_LOG_TAG,
                    "Ожидание: главный экран (!picker) и карт >= 5, магазин=$installSource, сейчас карт=${cards.size}",
                )
                snapshotFlow { !showCoverPicker && cards.size >= 5 }
                    .first { it }
                Log.i(IN_APP_REVIEW_LOG_TAG, "Условие выполнено, запрос отзыва ($installSource)")
                when (installSource) {
                    AppInstallSource.GOOGLE_PLAY -> PlayReviewHelper.tryLaunchReview(this@MainActivity)
                    AppInstallSource.RU_STORE -> RuStoreReviewHelper.tryLaunchReview(this@MainActivity)
                    AppInstallSource.UNKNOWN -> Unit
                }
            }

            SeaCardTheme {
                GradientBackground(gradientColor = gradientColor) {
                    if (showCoverPicker) {
                        CardCoverPickerScreen(
                            onCoverSelected = { coverAsset: String? ->
                                viewModel.setShowCoverPicker(false)
                                val intent = Intent(context, ScanCardActivity::class.java)
                                if (coverAsset != null) intent.putExtra("cover_asset", coverAsset)
                                scanCardLauncher.launch(intent)
                            },
                            onBack = { viewModel.setShowCoverPicker(false) }
                        )
                    } else {
                        MainScreen(
                            cards = cards,
                            cardsFromDbReady = cardsFromDbReady,
                            currentSortType = currentSortType,
                            gridColumns = gridColumns,
                            gradientColor = gradientColor,
                            showSearch = showSearch,
                            showFilterMenu = showFilterMenu,
                            selectionMode = selectionMode,
                            selectedCardIds = selectedCardIds,
                            onAddCard = { viewModel.setShowCoverPicker(true) },
                            onCardClick = { card ->
                                viewModel.updateCardUsage(card.id)
                                cardDetailLauncher.launch(Intent(context, CardDetailActivity::class.java).apply { putExtra("card_id", card.id) })
                            },
                            onSettingsClick = { settingsLauncher.launch(Intent(context, SettingsActivity::class.java)) },
                            onSortTypeChange = { viewModel.setSortType(it) },
                            onShowSearchChange = viewModel::setShowSearch,
                            onShowFilterMenuChange = viewModel::setShowFilterMenu,
                            onSelectionModeChange = viewModel::setSelectionMode,
                            onStartSelection = viewModel::startSelection,
                            onToggleCardSelection = viewModel::toggleCardSelection,
                            onDeleteSelectedCards = viewModel::deleteSelectedCards
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        playUpdateController?.dispose()
        ruStoreUpdateController?.dispose()
        super.onDestroy()
    }
}
