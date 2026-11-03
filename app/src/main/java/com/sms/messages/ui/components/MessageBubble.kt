package com.sms.messages.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.messages.R
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import com.sms.messages.util.TimeFormat

@Composable
fun MessageBubble(
    message: MessageEntity,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
    val shape = if (outgoing) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.86f)
                .clip(shape)
                .background(container)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column {
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
                        color = contentColor.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                    if (message.status == MessageStatus.PENDING) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.message_sending),
                            color = contentColor.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                        )
                    }
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
            Icons.Filled.Schedule, contentDescription = stringResource(R.string.message_queued),
            tint = tint.copy(alpha = 0.7f), modifier = Modifier.size(12.dp)
        )
        MessageStatus.SENT -> Icon(
            Icons.Filled.Done, contentDescription = stringResource(R.string.message_sent),
            tint = tint.copy(alpha = 0.8f), modifier = Modifier.size(14.dp)
        )
        MessageStatus.DELIVERED -> Icon(
            Icons.Filled.DoneAll, contentDescription = stringResource(R.string.message_delivered),
            tint = tint, modifier = Modifier.size(14.dp)
        )
        MessageStatus.FAILED -> Icon(
            Icons.Filled.ErrorOutline, contentDescription = stringResource(R.string.message_failed),
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp)
        )
        MessageStatus.NONE -> Unit
    }
}
