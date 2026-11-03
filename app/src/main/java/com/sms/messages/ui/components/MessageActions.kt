package com.sms.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.messages.R
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import com.sms.messages.data.ReactionEntity
import com.sms.messages.util.TimeFormat

/**
 * The reaction row Messages puts above a long-pressed bubble. The list is the ten the app
 * ships with; picking one replaces any earlier reaction of ours, the way Messages behaves.
 */
val DEFAULT_REACTIONS = listOf("👍", "👎", "❤️", "😂", "😮", "😢", "🎉", "🔥", "🙏", "👏")

/**
 * The sheet shown after a long press: reactions on top, then the actions Messages offers.
 * Only [onCopy] receives the message body; every other entry works on the row id.
 */
@Composable
fun MessageActionSheet(
    message: MessageEntity,
    reactions: List<ReactionEntity>,
    hasSelection: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
    onSelectMore: () -> Unit,
    onInfo: () -> Unit,
    onCopyCode: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column {
                ReactionStrip(
                    reactions = reactions,
                    onReact = onReact,
                )
                Spacer(Modifier.height(12.dp))
                if (message.body.isNotBlank()) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                ActionRow(Icons.AutoMirrored.Filled.Reply, R.string.action_reply, onReply)
                ActionRow(Icons.AutoMirrored.Filled.Forward, R.string.action_forward, onForward)
                ActionRow(Icons.Filled.ContentCopy, R.string.action_copy, onCopy)
                if (onCopyCode != null) {
                    ActionRow(Icons.Filled.LibraryAdd, R.string.action_copy_code, onCopyCode)
                }
                ActionRow(Icons.Filled.Share, R.string.action_share, onShare)
                ActionRow(
                    icon = if (message.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                    labelRes = if (message.starred) R.string.action_unstar else R.string.action_star,
                    onClick = onToggleStar,
                )
                if (!hasSelection) {
                    ActionRow(Icons.Filled.LibraryAdd, R.string.action_select_more, onSelectMore)
                }
                ActionRow(Icons.Filled.Info, R.string.action_info, onInfo)
                ActionRow(Icons.Filled.Delete, R.string.action_delete, onDelete, destructive = true)
            }
        },
        confirmButton = {},
    )
}

/** The emoji picker that sits at the top of the action sheet. */
@Composable
private fun ReactionStrip(
    reactions: List<ReactionEntity>,
    onReact: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(DEFAULT_REACTIONS) { emoji ->
            val mine = reactions.firstOrNull { it.emoji == emoji }?.mine == true
            Surface(
                onClick = { onReact(emoji) },
                shape = CircleShape,
                color = if (mine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = emoji, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The message details Messages shows under "Info": times, direction, type and status. */
@Composable
fun MessageDetailsDialog(
    message: MessageEntity,
    conversationTitle: String,
    onDismiss: () -> Unit,
) {
    val outgoing = message.type != MessageType.INBOX
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_details)) },
        text = {
            Column {
                if (message.body.isNotBlank()) {
                    Text(message.body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                }
                DetailRow(stringResource(R.string.label_type), stringResource(R.string.label_type_sms))
                DetailRow(
                    stringResource(if (outgoing) R.string.label_to else R.string.label_from),
                    if (outgoing) conversationTitle else conversationTitle,
                )
                DetailRow(
                    stringResource(if (outgoing) R.string.label_sent else R.string.label_received),
                    TimeFormat.full(message.timestamp),
                )
                DetailRow(stringResource(R.string.label_status), statusLabel(message))
                if (message.subscriptionId >= 0) {
                    DetailRow(
                        stringResource(R.string.label_sim),
                        stringResource(R.string.label_sim_default),
                    )
                }
                if (message.starred) {
                    DetailRow(stringResource(R.string.label_starred), "★")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun statusLabel(message: MessageEntity): String = when {
    message.type == MessageType.INBOX -> stringResource(R.string.label_received)
    message.status == MessageStatus.PENDING -> stringResource(R.string.message_queued)
    message.status == MessageStatus.SENT -> stringResource(R.string.message_sent)
    message.status == MessageStatus.DELIVERED -> stringResource(R.string.message_delivered)
    message.status == MessageStatus.FAILED -> stringResource(R.string.message_failed)
    else -> stringResource(R.string.message_sent)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The small chips drawn under a bubble showing which emoji it collected. */
@Composable
fun ReactionChips(
    reactions: List<ReactionEntity>,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.forEach { reaction ->
            Surface(
                shape = CircleShape,
                color = if (reaction.mine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = reaction.emoji, fontSize = 12.sp)
                    if (reaction.count > 1) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = reaction.count.toString(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
