package com.streamlivex.android.tv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object TvDesignTokens {
    val Background = Color(0xFF020A10)
    val Sidebar = Color(0xFF03141F)
    val Card = Color(0xFF101A22)
    val CardBorder = Color(0x1FFFFFFF)
    val Primary = Color(0xFF008FFF)
    val Focus = Color(0xFF00A4FF)
    val TextPrimary = Color(0xFFF5F7F9)
    val TextSecondary = Color(0xFFAEB7BF)
    val TextTertiary = Color(0xFF7E8992)
    val HomeSidebarWidth = 230.dp
    val CollapsedSidebarWidth = 68.dp
    val HeroHeight = 190.dp
    val LiveCardWidth = 142.dp
    val LiveCardHeight = 112.dp
}

object TvPerformanceTrace {
    @Volatile var profileSelectedAtMs: Long = 0L
}
