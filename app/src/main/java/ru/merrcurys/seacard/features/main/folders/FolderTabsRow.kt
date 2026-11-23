package ru.merrcurys.seacard.features.main.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.merrcurys.seacard.domain.entity.Folder

/** Горизонтально прокручиваемая полоса вкладок. */
@Composable
fun FolderTabsRow(
    folders: List<Folder>,
    selectedTab: TabSelection,
    onSelect: (TabSelection) -> Unit,
    onCreateFolder: () -> Unit,
    onOrderClick: () -> Unit,
    onFolderLongPress: (Folder) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val selectedIndex = remember(selectedTab, folders) {
        when (selectedTab) {
            TabSelection.All -> 0
            is TabSelection.Folder -> {
                val folderIndex = folders.indexOfFirst { it.id == selectedTab.id }
                if (folderIndex < 0) -1 else 1 + folderIndex
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "all") {
            FolderTabChip(
                text = "Все",
                selected = selectedTab == TabSelection.All,
                contentDescription = "Вкладка «Все»",
                onClick = { onSelect(TabSelection.All) }
            )
        }
        items(folders, key = { "folder_${it.id}" }) { folder ->
            FolderTabChip(
                text = folder.name,
                selected = selectedTab == TabSelection.Folder(folder.id),
                contentDescription = "Вкладка «${folder.name}»",
                onClick = { onSelect(TabSelection.Folder(folder.id)) },
                onLongClick = { onFolderLongPress(folder) }
            )
        }
        item(key = "add") {
            TabActionChip(
                icon = Icons.Filled.Add,
                contentDescription = "Создать вкладку",
                onClick = onCreateFolder
            )
        }
        if (folders.isNotEmpty()) {
            item(key = "order") {
                TabActionChip(
                    icon = Icons.Filled.Reorder,
                    contentDescription = "Порядок вкладок",
                    onClick = onOrderClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTabChip(
    text: String,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val background = if (selected) {
        colorScheme.primary.copy(alpha = 0.16f)
    } else {
        colorScheme.surface.copy(alpha = 0.35f)
    }
    val textColor = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(1.dp, colorScheme.primary.copy(alpha = 0.4f), shape)
                } else {
                    Modifier
                }
            )
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TabActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colorScheme.surface.copy(alpha = 0.35f))
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
    }
}
