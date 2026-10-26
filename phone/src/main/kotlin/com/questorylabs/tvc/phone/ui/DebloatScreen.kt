package com.questorylabs.tvc.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.questorylabs.tvc.phone.DeviceUi
import com.questorylabs.tvc.phone.MainViewModel
import com.questorylabs.tvc.shared.DebloatEntry
import com.questorylabs.tvc.shared.DebloatTier

@Composable
fun DebloatScreen(vm: MainViewModel, state: DeviceUi, onBack: () -> Unit) {
    val host = state.device.host
    var tier by remember { mutableStateOf<DebloatTier?>(null) }
    var confirm by remember { mutableStateOf<Pair<DebloatEntry, Boolean>?>(null) }
    var confirmBulk by remember { mutableStateOf<Boolean?>(null) }
    val disabled = state.disabledPackages.orEmpty()
    val entries = vm.catalog.packages.filter { tier == null || it.tier == tier }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                Text("Debloat: ${state.info?.model ?: state.device.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refreshDisabled(host) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            }
        }
        item {
            Text(
                "Disabling keeps the package on the device; Restore re-enables it. Catalog: ${vm.catalog.device}. Packages missing on this device fail harmlessly.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tier == null, onClick = { tier = null }, label = { Text("All") })
                DebloatTier.entries.forEach { t ->
                    FilterChip(selected = tier == t, onClick = { tier = t }, label = { Text(t.name.lowercase().replaceFirstChar(Char::uppercase)) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmBulk = true }) { Text("Disable all safe") }
                OutlinedButton(onClick = { confirmBulk = false }) { Text("Restore all") }
            }
        }
        items(entries, key = { it.pkg }) { entry ->
            val isDisabled = entry.pkg in disabled
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.label, style = MaterialTheme.typography.titleSmall)
                        Text(entry.pkg, style = MaterialTheme.typography.bodySmall)
                        Text("${entry.tier.name.lowercase()}: ${entry.why}", style = MaterialTheme.typography.bodySmall, color = if (entry.tier == DebloatTier.RISKY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isDisabled,
                        onCheckedChange = { wantDisabled ->
                            if (entry.tier == DebloatTier.RISKY && wantDisabled) confirm = entry to true else vm.setDebloat(host, entry.pkg, wantDisabled)
                        },
                    )
                }
            }
        }
    }

    confirm?.let { (entry, disable) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Disable ${entry.label}?") },
            text = { Text(entry.why) },
            confirmButton = { TextButton(onClick = { vm.setDebloat(host, entry.pkg, disable); confirm = null }) { Text("Disable") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    confirmBulk?.let { disableAll ->
        val targets = if (disableAll) vm.catalog.packages.filter { it.tier == DebloatTier.SAFE && it.pkg !in disabled }
        else vm.catalog.packages.filter { it.pkg in disabled }
        AlertDialog(
            onDismissRequest = { confirmBulk = null },
            title = { Text(if (disableAll) "Disable ${targets.size} safe packages?" else "Restore ${targets.size} packages?") },
            text = { Text(targets.joinToString("\n") { it.label }) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDebloatBulk(host, targets.map { it.pkg }, disableAll)
                    confirmBulk = null
                }) { Text(if (disableAll) "Disable" else "Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmBulk = null }) { Text("Cancel") } },
        )
    }
}
