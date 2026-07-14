package com.neeraj.fin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.neeraj.fin.data.db.Category
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.CategoryDot
import com.neeraj.fin.ui.components.ConfirmDialog

private val palette = listOf(
    0xFFEF6C00, 0xFF7CB342, 0xFFFDD835, 0xFFEC407A, 0xFF29B6F6, 0xFFAB47BC,
    0xFFEF5350, 0xFF26A69A, 0xFF5C6BC0, 0xFF8D6E63, 0xFF78909C, 0xFF66BB6A,
    0xFFFF8A65, 0xFF43A047, 0xFF00897B, 0xFFFFB300, 0xFF26C6DA, 0xFFD81B60
)

private val emojiChoices = listOf(
    "🍔", "🛒", "🚕", "🛍️", "💡", "🎬", "💊", "✈️", "📚", "🏠", "🏦", "📈",
    "💇", "🧾", "💼", "🏢", "🪙", "💸", "🎁", "➕", "☕", "🍺", "🐶", "👶",
    "🎮", "⚽", "🎵", "📱", "🚗", "⛽", "🧘", "💍", "🌱", "🎓", "🩺", "🏋️"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: AppViewModel, nav: NavController) {
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            val expense = categories.filter { it.kind == TxnType.EXPENSE }
            val income = categories.filter { it.kind == TxnType.INCOME }

            item {
                Text(
                    "EXPENSE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(expense.size, key = { expense[it].id }) { i ->
                CategoryRow(expense[i]) { editing = expense[i] }
            }
            item {
                Text(
                    "INCOME",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(income.size, key = { income[it].id }) { i ->
                CategoryRow(income[i]) { editing = income[i] }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (creating) {
        CategoryDialog(
            initial = null,
            onSave = { vm.saveCategory(it); creating = false },
            onDelete = null,
            onDismiss = { creating = false }
        )
    }
    editing?.let { cat ->
        CategoryDialog(
            initial = cat,
            onSave = { vm.saveCategory(it); editing = null },
            onDelete = { vm.deleteCategory(cat); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryDot(category)
        Text(
            category.name,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        if (category.isDefault) {
            Text("Default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    initial: Category?,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🧾") }
    var color by remember { mutableStateOf(initial?.color ?: palette.first()) }
    var kind by remember { mutableStateOf(initial?.kind ?: TxnType.EXPENSE) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (initial == null) "New category" else "Edit category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = kind == TxnType.EXPENSE,
                        onClick = { kind = TxnType.EXPENSE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Expense") }
                    SegmentedButton(
                        selected = kind == TxnType.INCOME,
                        onClick = { kind = TxnType.INCOME },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Income") }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojiChoices.forEach { e ->
                        Text(
                            e,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (emoji == e) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = e }
                                .padding(6.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { c ->
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .size(if (color == c) 34.dp else 28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (onDelete != null && initial?.isDefault == false) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            onSave(
                                (initial ?: Category(name = "", emoji = "", color = 0, kind = kind))
                                    .copy(name = name.trim(), emoji = emoji, color = color, kind = kind)
                            )
                        },
                        enabled = name.isNotBlank()
                    ) { Text("Save") }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete \"${initial?.name}\"?",
            text = "Transactions in this category will become uncategorized.",
            confirmLabel = "Delete",
            onConfirm = { onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}
