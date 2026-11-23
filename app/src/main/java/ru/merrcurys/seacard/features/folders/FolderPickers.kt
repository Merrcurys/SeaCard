package ru.merrcurys.seacard.features.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import ru.merrcurys.seacard.core.utils.FolderCreationResult
import ru.merrcurys.seacard.core.utils.FolderNameValidator
import ru.merrcurys.seacard.domain.entity.Folder

/** Компактная строка «Вкладки: X» для формы добавления/редактирования карты. */
@Composable
fun FolderPickerRow(
    folderNames: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Вкладки",
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Text(
                text = folderNames,
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Нижний лист выбора вкладок с множественным выбором.
 * Изменения применяются сразу через [onToggleFolder], лист закрывается кнопкой «Готово».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    title: String,
    folders: List<Folder>,
    selectedFolderIds: Set<Long>,
    onToggleFolder: (Long) -> Unit,
    onDismiss: () -> Unit,
    onCreateFolder: suspend (String) -> FolderCreationResult
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Готово",
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = "Можно выбрать несколько вкладок",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (folders.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "Пока нет вкладок — создайте первую",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
                items(folders, key = { "folder_${it.id}" }) { folder ->
                    FolderOptionRow(
                        name = folder.name,
                        selected = folder.id in selectedFolderIds,
                        onClick = { onToggleFolder(folder.id) }
                    )
                }
                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    )
                }
                item(key = "create") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showCreateDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Создать вкладку…",
                            color = colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        FolderNameDialog(
            title = "Новая вкладка",
            existingNames = folders.map { it.name },
            onDismiss = { showCreateDialog = false },
            onSubmit = { name ->
                val result = onCreateFolder(name)
                if (result is FolderCreationResult.Success) {
                    onToggleFolder(result.folder.id)
                }
                result
            }
        )
    }
}

@Composable
private fun FolderOptionRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colorScheme.primary,
                uncheckedColor = Color.White.copy(alpha = 0.5f),
                checkmarkColor = colorScheme.onPrimary
            )
        )
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = name,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Диалог создания/переименования вкладки. */
@Composable
fun FolderNameDialog(
    title: String,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> FolderCreationResult,
    initialName: String = ""
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    fun submit() {
        if (submitting) return
        val localError = FolderNameValidator.validate(name, existingNames.map { null to it })?.message
        if (localError != null) {
            error = localError
            return
        }
        submitting = true
        error = null
        scope.launch {
            when (val result = onSubmit(FolderNameValidator.normalize(name))) {
                is FolderCreationResult.Success -> onDismiss()
                is FolderCreationResult.Failure -> error = result.message
            }
            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Название") },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error!!, color = colorScheme.error) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.5f),
                    focusedLabelColor = colorScheme.primary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !submitting) {
                Text(if (submitting) "Сохранение…" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("Отмена")
            }
        },
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.onSurface,
        textContentColor = colorScheme.onSurface
    )
}

/** Лист ручной сортировки вкладок: зажать строку и перетащить вверх/вниз. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderOrderSheet(
    folders: List<Folder>,
    onReorder: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val workingFolders = remember { mutableStateListOf<Folder>().apply { addAll(folders) } }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    val swapThresholdPx = with(density) { 52.dp.toPx() }

    LaunchedEffect(folders) {
        if (draggingId == null) {
            workingFolders.clear()
            workingFolders.addAll(folders)
        }
    }

    fun startDrag(folder: Folder) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        draggingId = folder.id
        dragOffset = 0f
        dragMoved = false
    }

    fun dragBy(amount: Float) {
        val id = draggingId ?: return
        dragOffset += amount
        val index = workingFolders.indexOfFirst { it.id == id }
        if (index < 0) return
        if (dragOffset > swapThresholdPx && index < workingFolders.lastIndex) {
            val item = workingFolders.removeAt(index)
            workingFolders.add(index + 1, item)
            dragOffset -= swapThresholdPx
            dragMoved = true
        } else if (dragOffset < -swapThresholdPx && index > 0) {
            val item = workingFolders.removeAt(index)
            workingFolders.add(index - 1, item)
            dragOffset += swapThresholdPx
            dragMoved = true
        }
    }

    fun endDrag() {
        val moved = dragMoved
        val newOrder = workingFolders.map { it.id }
        val oldOrder = folders.map { it.id }
        draggingId = null
        dragOffset = 0f
        dragMoved = false
        if (moved && newOrder != oldOrder) onReorder(newOrder)
    }

    fun cancelDrag() {
        draggingId = null
        dragOffset = 0f
        dragMoved = false
        workingFolders.clear()
        workingFolders.addAll(folders)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Порядок вкладок",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                text = "Зажмите вкладку и перетащите вверх или вниз",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (workingFolders.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "Вкладок пока нет",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
                itemsIndexed(workingFolders, key = { _, folder -> "order_${folder.id}" }) { _, folder ->
                    FolderOrderRow(
                        folder = folder,
                        dragging = draggingId == folder.id,
                        dragOffsetY = if (draggingId == folder.id) dragOffset else 0f,
                        onDragStart = { startDrag(folder) },
                        onDrag = { dragBy(it) },
                        onDragEnd = { endDrag() },
                        onDragCancel = { cancelDrag() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderOrderRow(
    folder: Folder,
    dragging: Boolean,
    dragOffsetY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Row(
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (dragging) Color(0xFF1E1E22) else Color.Transparent)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentOnDrag(amount.y)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = folder.name,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Перетащить",
            tint = Color.White.copy(alpha = 0.5f)
        )
    }
}

/** Действия с пользовательской вкладкой (долгое нажатие). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionsSheet(
    folderName: String,
    cardCount: Int?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = folderName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cardCount != null) {
                    Text(
                        text = "Карт: $cardCount",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            FolderActionRow(
                text = "Переименовать",
                icon = Icons.Filled.Edit,
                onClick = onRename
            )
            FolderActionRow(
                text = "Удалить вкладку",
                icon = Icons.Filled.Delete,
                tint = Color(0xFFFF6B6B),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun FolderActionRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.White.copy(alpha = 0.75f)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            color = tint,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FolderDeleteConfirmDialog(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить вкладку?") },
        text = {
            Text("Вкладка «$folderName» будет удалена. Карты не удалятся — они останутся в других вкладках или без вкладки.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить", color = Color(0xFFFF6B6B))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.onSurface,
        textContentColor = colorScheme.onSurface
    )
}
