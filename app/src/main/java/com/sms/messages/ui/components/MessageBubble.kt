package com.sms.messages.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.messages.R
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import com.sms.messages.util.TimeFormat

/**
 * A single message bubble. Outgoing bubbles are filled with the brand colour and tuck the
 * corner nearest the sender; incoming bubbles use the surface colour. The timestamp sits
 * inside the bubble on the trailing edge, the way Messages does it.
 */
@Composable
fun MessageBubble(
    message: MessageEntity,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTail: Boolean = true,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            onClick = onLongPress,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.86f),
            shape = shape,
            color = container,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
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
    }
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
