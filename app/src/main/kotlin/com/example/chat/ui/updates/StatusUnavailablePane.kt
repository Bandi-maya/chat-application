package com.example.chat.ui.updates

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.data.services.StatusIdentityUnboundException

/** Shown instead of the Updates feed while status is not bound to the signed-in account.
 *  Pure presentation: no service, session or network access. */
@Composable
fun StatusUnavailablePane(modifier: Modifier = Modifier) {
    Text(StatusIdentityUnboundException().message.orEmpty(), modifier = modifier.padding(24.dp))
}
