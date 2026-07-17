package com.neeraj.fin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neeraj.fin.data.db.Category
import com.neeraj.fin.data.db.Txn
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.theme.expenseColor
import com.neeraj.fin.ui.theme.incomeColor
import com.neeraj.fin.util.Format

@Composable
fun CategoryDot(category: Category?, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                color = category?.let { Color(it.color).copy(alpha = 0.25f) }
                    ?: MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(category?.emoji ?: "❓", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun TxnRow(
    txn: Txn,
    category: Category?,
    currencyCode: String,
    onClick: () -> Unit
) {
    val isTransfer = txn.type == TxnType.TRANSFER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isTransfer) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🔁", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            CategoryDot(category)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                txn.merchant.ifBlank { if (isTransfer) "Transfer" else category?.name ?: "Transaction" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(if (isTransfer) "Transfer" else category?.name ?: "Uncategorized")
                    if (txn.note.isNotBlank()) append(" · ${txn.note}")
                    else if (txn.accountTail != null) append(" · a/c ··${txn.accountTail}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (isTransfer) Format.money(txn.amountMinor, currencyCode)
            else Format.signedMoney(txn.amountMinor, txn.type == TxnType.EXPENSE, currencyCode),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = when (txn.type) {
                TxnType.EXPENSE -> expenseColor()
                TxnType.INCOME -> incomeColor()
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


/** Sentinel-based pocket filter: -1 = all pockets, 0 = Personal (null), else pocket id. */
fun com.neeraj.fin.data.db.Txn.inPocket(sel: Long): Boolean = when (sel) {
    -1L -> true
    0L -> pocketId == null
    else -> pocketId == sel
}

@Composable
fun PocketFilterRow(
    pockets: List<com.neeraj.fin.data.db.Pocket>,
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pockets.isEmpty()) return
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.FilterChip(
            selected = selected == -1L, onClick = { onSelect(-1L) },
            label = { Text("All pockets") }
        )
        androidx.compose.material3.FilterChip(
            selected = selected == 0L, onClick = { onSelect(0L) },
            label = { Text("👤 Personal") }
        )
        pockets.forEach { p ->
            androidx.compose.material3.FilterChip(
                selected = selected == p.id, onClick = { onSelect(p.id) },
                label = { Text("${p.emoji} ${p.name}") }
            )
        }
    }
}


fun txnInPocket(t: com.neeraj.fin.data.db.Txn, sel: Long): Boolean = t.inPocket(sel)
