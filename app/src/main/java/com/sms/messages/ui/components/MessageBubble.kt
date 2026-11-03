package com.sms.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.messages.R
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import com.sms.messages.data.ReactionEntity
import com.sms.messages.util.TimeFormat

/**
 * A single message bubble. Outgoing bubbles are filled with the brand colour and tuck the
 * corner nearest the sender; incoming bubbles use the surface colour. The timestamp sits
 * inside the bubble on the trailing edge, the way Messages does it.
 *
 * In selection mode a circle replaces the long-press menu and the whole row toggles.
 */
@Composable
fun MessageBubble(
    message: MessageEntity,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTail: Boolean = true,
    reactions: List<ReactionEntity> = emptyList(),
    quoted: MessageEntity? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
    val outgoing = message.type != MessageType.INBOX
    val container = if (outgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (outgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // The tail corner is only cut on the last bubble of a run, which is how Messages
    // visually groups consecutive messages from the same sender.
    val shape = when {
        outgoing && showTail -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
        outgoing -> RoundedCornerShape(20.dp)
        showTail -> RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
        else -> RoundedCornerShape(20.dp)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        if (selectionMode && !outgoing) {
            SelectionDot(selected)
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Surface(
                onClick = { if (selectionMode) onToggleSelect() else onLongPress() },
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(0.86f),
                shape = shape,
                color = container,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    if (quoted != null) {
                        QuotedStrip(quoted = quoted, contentColor = contentColor)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (message.body.isNotBlank()) {
                        Text(
                            text = message.body,
                            color = contentColor,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        )
                    }
                    Spacer(Modifier.size(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (message.starred) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(R.string.cd_starred),
                                tint = contentColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = TimeFormat.clock(message.timestamp),
                            color = contentColor.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                        )
                        if (outgoing) {
                            Spacer(Modifier.width(4.dp))
                            StatusIcon(message.status, contentColor, onRetry)
                        }
                    }
                }
            }

            if (reactions.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                ReactionChips(reactions = reactions, outgoing = outgoing)
            }
        }

        if (selectionMode && outgoing) {
            Spacer(Modifier.width(8.dp))
            SelectionDot(selected)
        }
    }
}

/** The quoted original shown inside a reply bubble. */
@Composable
private fun QuotedStrip(quoted: MessageEntity, contentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(contentColor.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(26.dp)
                .background(contentColor.copy(alpha = 0.65f), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = quoted.body.ifBlank { stringResource(R.string.cd_reply_quoted) },
            color = contentColor.copy(alpha = 0.9f),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The circle shown beside each bubble while multi-select is active. */
@Composable
private fun SelectionDot(selected: Boolean) {
    Icon(
        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
        contentDescription = stringResource(
            if (selected) R.string.cd_selected else R.string.cd_not_selected
        ),
        tint = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun StatusIcon(status: MessageStatus, tint: Color, onRetry: () -> Unit) {
    when (status) {
        MessageStatus.PENDING -> Icon(
            Icons.Filled.Schedule,
            contentDescription = stringResource(R.string.message_queued),
            tint = tint.copy(alpha = 0.75f),
            modifier = Modifier.size(13.dp),
        )
        MessageStatus.SENT -> Icon(
            Icons.Filled.Done,
            contentDescription = stringResource(R.string.message_sent),
            tint = tint.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp),
        )
        MessageStatus.DELIVERED -> Icon(
            Icons.Filled.DoneAll,
            contentDescription = stringResource(R.string.message_delivered),
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        MessageStatus.FAILED -> Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = stringResource(R.string.message_failed),
            tint = MaterialTheme.colorScheme.error,
            // Only the failure glyph retries, so a long press elsewhere still opens the menu.
            modifier = Modifier
                .size(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRetry,
                ),
        )
        MessageStatus.NONE -> Unit
    }
}

/** The centred date chip that separates message groups. */
@Composable
fun DayDivider(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}
