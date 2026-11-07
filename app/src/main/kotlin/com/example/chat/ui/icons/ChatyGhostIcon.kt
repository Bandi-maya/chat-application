package com.example.chat.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One canonical semantic role uses global/per-role pack selection, no generic fallback. */
@Composable
fun ChatyGhostIcon(color: Color) { ChatyIcon(ChatyIconId.ghost, color = color, size = 20.dp) }
