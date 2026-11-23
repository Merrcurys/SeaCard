package ru.merrcurys.seacard.features.main.folders

import androidx.compose.runtime.Composable
import ru.merrcurys.seacard.core.utils.FolderCreationResult
import ru.merrcurys.seacard.domain.entity.Folder
import ru.merrcurys.seacard.features.folders.FolderPickerSheet

/** Лист папок для выбранных карт: можно отметить несколько папок сразу. */
@Composable
fun CardsFoldersSheet(
    folders: List<Folder>,
    selectedFolderIds: Set<Long>,
    onToggleFolder: (Long) -> Unit,
    onCreateFolder: suspend (String) -> FolderCreationResult,
    onDismiss: () -> Unit
) {
    FolderPickerSheet(
        title = "Вкладки карт",
        folders = folders,
        selectedFolderIds = selectedFolderIds,
        onToggleFolder = onToggleFolder,
        onDismiss = onDismiss,
        onCreateFolder = onCreateFolder
    )
}
