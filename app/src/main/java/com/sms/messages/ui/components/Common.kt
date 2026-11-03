package com.sms.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AvatarPalette = listOf(
    Color(0xFF1B6EF3),
    Color(0xFF00A67D),
    Color(0xFFF2A33C),
    Color(0xFFE5484D),
    Color(0xFF8E4EC6),
    Color(0xFF0C8CE9),
)

@Composable
fun InitialAvatar(
    label: String,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val color = AvatarPalette[((colorIndex % AvatarPalette.size) + AvatarPalette.size) % AvatarPalette.size]
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value / 2.4f).sp,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
