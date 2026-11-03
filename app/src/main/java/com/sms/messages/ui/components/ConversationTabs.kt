package com.sms.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.sms.messages.R
import com.sms.messages.ui.viewmodel.TabItem

/**
 * The horizontal tab strip above the conversation list.
 *
 * The built-in tabs behave like filters: tapping one switches the list, and they cannot be
 * renamed or removed. Custom tabs can, so they respond to a long press with the edit sheet.
 * The trailing (+) always creates a new tab.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationTabs(
    tabs: List<TabItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (TabItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs, key = { it.key }) { tab ->
            TabChip(
                tab = tab,
                selected = tab.key == selectedKey,
                onClick = { onSelect(tab.key) },
                onLongClick = { if (tab.isCustom) onEdit(tab) },
            )
        }
        item {
            AddTabChip(onClick = onCreate)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(
    tab: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
            )
            if (tab.count > 0) {
                TabCount(count = tab.count, selected = selected)
            }
        }
    }
}

/** A small count next to the tab name; unselected tabs get a muted pill instead of a dot. */
@Composable
private fun TabCount(count: Int, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
                }
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** The (+) that opens the new-tab dialog. */
@Composable
private fun AddTabChip(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.tab_add),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
