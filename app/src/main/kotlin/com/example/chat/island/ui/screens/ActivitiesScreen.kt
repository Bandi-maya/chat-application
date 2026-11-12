package com.example.chat.island.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.chat.R
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.Trigger
import com.example.chat.island.ui.components.hint
import com.example.chat.island.ui.components.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen() {
    val selected by IslandSettings.triggers.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_activities)) }) }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "intro") {
                Text(stringResource(R.string.act_title), Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium)
            }
            items(Trigger.entries, key = { it.key }) { trigger ->
                val enabled = selected.contains(trigger.key)
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(trigger.label)) },
                        supportingContent = { Text(stringResource(trigger.hint())) },
                        leadingContent = { Icon(trigger.icon(), contentDescription = null) },
                        trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = enabled,
                            role = Role.Switch,
                            onValueChange = { IslandSettings.setTrigger(trigger, it) }
                        )
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
            item(key = "footer") {
                Text(stringResource(R.string.act_footer), Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
