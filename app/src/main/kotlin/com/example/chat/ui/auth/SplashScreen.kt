package com.example.chat.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chat.ui.theme.LocalThemeConfig

/** Port of lib/features/auth/splash_screen.dart: instant (~120ms) startup
 *  handoff with a light fade-in. Never blocks the first frame on heavy work.
 *  The routing decision (authenticated?) is injected by AppRoot, which owns
 *  the backend/session seams. */
@Composable
fun SplashScreen(routeToHome: suspend () -> Boolean, onRoute: (Boolean) -> Unit) {
    val theme = LocalThemeConfig.current
    var started by remember { mutableStateOf(false) }
    val fade by animateFloatAsState(
        targetValue = if (started) 1.0f else 0.8f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "splashFade",
    )

    LaunchedEffect(Unit) {
        started = true
        onRoute(routeToHome())
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .alpha(fade)
                    .size(80.dp)
                    .clip(RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // ChatyBrandIcon placeholder: the brand-asset slice swaps in the
                // real icon; shape/scale/radius already match the Dart layout.
                Icon(
                    Icons.Rounded.Forum,
                    contentDescription = "Chaty",
                    tint = Color(theme.accentColor),
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}
