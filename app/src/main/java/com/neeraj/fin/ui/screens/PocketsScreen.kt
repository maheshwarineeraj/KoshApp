package com.neeraj.fin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.neeraj.fin.data.db.Pocket
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.EmptyState

/**
 * Manage pockets — isolated money streams (side business, family, rental).
 * "Personal" is the built-in default and everything belongs to it unless
 * routed elsewhere. Max 10 pockets including Personal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketsScreen(vm: AppViewModel, nav: NavController) {
    val pockets by vm.pockets.collectAsState()
    val txns by vm.transactions.collectAsState()
    var editing by remember { mutableStateOf<Pocket?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pockets") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (pockets.size < 9) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New pocket") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)) {
                    Row(Modifier.padding(16.dp)) {
                        Text("👤", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Personal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Default — ${txns.count { it.pocketId == null }} transactions. " +
                                    "Everything lands here unless routed to a pocket.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (pockets.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "👛",
                        title = "Isolate a money stream",
                        subtitle = "Create a pocket for a side business, family spending, or a rental property. Claim account tails (e.g. 5740) and detections from that card route there automatically."
                    )
                }
            }
            items(pockets.size, key = { pockets[it].id }) { i ->
                val p = pockets[i]
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clickable { editing = p }
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Text(p.emoji, style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${txns.count { it.pocketId == p.id }} transactions" +
                                    (p.accountTails.takeIf { it.isNotBlank() }?.let { " · routes a/c $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        PocketDialog(
            initial = editing,
            onSave = { vm.savePocket(it); creating = false; editing = null },
            onDelete = editing?.let { p -> { vm.deletePocket(p.id); editing = null } },
            onDismiss = { creating = false; editing = null }
        )
    }
}

@Composable
private fun PocketDialog(
    initial: Pocket?,
    onSave: (Pocket) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "👛") }
    var tails by remember { mutableStateOf(initial?.accountTails ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New pocket" else "Edit pocket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji, onValueChange = { emoji = it.take(4) },
                        label = { Text("Emoji") }, modifier = Modifier.padding(end = 4.dp).fillMaxWidth(0.3f)
                    )
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Name (e.g. Business)") }, singleLine = true
                    )
                }
                OutlinedTextField(
                    value = tails, onValueChange = { tails = it },
                    label = { Text("Account tails (optional)") },
                    supportingText = { Text("Comma-separated, e.g. 5740, 8694 — SMS detections from those accounts route here automatically.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(
                    onClick = {
                        onSave(
                            (initial ?: Pocket(name = "")).copy(
                                name = name.trim(),
                                emoji = emoji.ifBlank { "👛" },
                                accountTails = tails.trim()
                            )
                        )
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
