package com.neeraj.fin.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neeraj.fin.data.db.Asset
import com.neeraj.fin.data.db.AssetType
import com.neeraj.fin.data.db.AssetValue
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.BarChart
import com.neeraj.fin.ui.components.BarEntry
import com.neeraj.fin.ui.components.ConfirmDialog
import com.neeraj.fin.ui.components.EmptyState
import com.neeraj.fin.ui.theme.expenseColor
import com.neeraj.fin.ui.theme.incomeColor
import com.neeraj.fin.util.Format
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun WealthScreen(vm: AppViewModel) {
    val assets by vm.assets.collectAsState()
    val values by vm.assetValues.collectAsState()
    val currency by vm.currencyCode.collectAsState()

    var editing by remember { mutableStateOf<Asset?>(null) }
    // null = not creating; false = new asset; true = new liability
    var creating by remember { mutableStateOf<Boolean?>(null) }

    // Latest value per asset
    val latest: Map<Long, Long> = remember(values) {
        values.groupBy { it.assetId }.mapValues { (_, vs) -> vs.maxByOrNull { it.timestamp }!!.valueMinor }
    }
    val holdings = assets.filter { !it.isLiability }
    val liabilities = assets.filter { it.isLiability }
    val totalAssets = holdings.sumOf { latest[it.id] ?: 0L }
    val totalLiabilities = liabilities.sumOf { latest[it.id] ?: 0L }
    val netWorth = totalAssets - totalLiabilities

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Text("Wealth", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Everything you own, minus everything you owe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Net worth", style = MaterialTheme.typography.labelLarge)
                        Text(
                            Format.money(netWorth, currency),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Assets", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    Format.money(totalAssets, currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = incomeColor()
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Liabilities", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    Format.money(totalLiabilities, currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = expenseColor()
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(onClick = { creating = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Asset")
                    }
                    FilledTonalButton(onClick = { creating = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Remove, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Liability")
                    }
                }
            }

            if (assets.isNotEmpty()) {
                val digest = buildWealthDigest(assets, values, netWorth, totalLiabilities, currency)
                if (digest.isNotEmpty()) {
                    item {
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("In short", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                digest.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }

            if (assets.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "💼",
                        title = "Track your net worth",
                        subtitle = "Add each investment or account — mutual funds, stocks, EPF, FDs, gold, property — and any loans. Update their values whenever you like; Kosh charts your net worth over time. Everything stays on this device."
                    )
                }
            } else {
                // Net worth trend from value history (last 6 months, carry-forward)
                val series = netWorthSeries(assets, values)
                if (series.count { it.value != 0L } > 1) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Net worth trend", style = MaterialTheme.typography.titleMedium)
                                BarChart(
                                    entries = series,
                                    primaryColor = MaterialTheme.colorScheme.primary,
                                    valueFormatter = { if (it == 0L) "" else Format.compact(it, currency) }
                                )
                            }
                        }
                    }
                }

                // MoM / QoQ / YoY comparison against carried-forward historical values
                item {
                    val now = System.currentTimeMillis()
                    val earliestSnapshot = values.minOfOrNull { it.timestamp } ?: now
                    val zone = java.time.ZoneId.systemDefault()
                    fun agoMillis(months: Long) =
                        java.time.LocalDate.now().minusMonths(months).atStartOfDay(zone).toInstant().toEpochMilli()
                    val rows = listOf(
                        Triple("MoM", "vs 1 month ago", agoMillis(1)),
                        Triple("QoQ", "vs 3 months ago", agoMillis(3)),
                        Triple("YoY", "vs 1 year ago", agoMillis(12))
                    ).filter { it.third >= earliestSnapshot }
                    if (rows.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Growth", style = MaterialTheme.typography.titleMedium)
                                rows.forEach { (tag, label, at) ->
                                    val then = netWorthAt(assets, values, at)
                                    val delta = netWorth - then
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(tag, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "$label · ${Format.compact(then, currency)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                (if (delta >= 0) "▲ " else "▼ ") + Format.compact(kotlin.math.abs(delta), currency),
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (delta >= 0) incomeColor() else expenseColor()
                                            )
                                            if (then != 0L) {
                                                Text(
                                                    "${if (delta >= 0) "+" else ""}${delta * 100 / kotlin.math.abs(then)}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    "Based on the values you've recorded — update holdings regularly for accurate trends.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (holdings.isNotEmpty()) {
                    item {
                        Text(
                            "INVESTMENTS & ACCOUNTS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    items(holdings.size, key = { holdings[it].id }) { i ->
                        AssetRow(holdings[i], latest[holdings[i].id] ?: 0L, currency) { editing = holdings[i] }
                    }
                }
                if (liabilities.isNotEmpty()) {
                    item {
                        Text(
                            "LIABILITIES",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    items(liabilities.size, key = { liabilities[it].id }) { i ->
                        AssetRow(liabilities[i], latest[liabilities[i].id] ?: 0L, currency) { editing = liabilities[i] }
                    }
                }
                item {
                    Text(
                        "Tap a holding to update its current value. Values are manual by design — Kosh never connects to your brokers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    creating?.let { asLiability ->
        AssetDialog(
            initial = null, initialValue = null, currency = currency,
            startLiability = asLiability,
            onSave = { asset, value -> vm.addAsset(asset, value); creating = null },
            onDelete = null,
            onDismiss = { creating = null }
        )
    }
    editing?.let { asset ->
        AssetDialog(
            initial = asset, initialValue = latest[asset.id], currency = currency,
            onSave = { updated, value ->
                vm.updateAsset(updated, value.takeIf { it != latest[asset.id] })
                editing = null
            },
            onDelete = { vm.deleteAsset(asset.id); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun AssetRow(asset: Asset, valueMinor: Long, currency: String, onClick: () -> Unit) {
    val meta = AssetType.meta(asset.type)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(meta.emoji, style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(asset.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(meta.label, asset.platform.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Format.money(valueMinor, currency),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (!asset.isLiability && asset.investedMinor > 0) {
                val gain = valueMinor - asset.investedMinor
                val pct = gain * 100 / asset.investedMinor
                Text(
                    "${if (gain >= 0) "+" else "−"}${Format.compact(kotlin.math.abs(gain), currency)} (${if (gain >= 0) "+" else ""}$pct%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (gain >= 0) incomeColor() else expenseColor()
                )
            }
        }
    }
}

/** Plain-language wealth summary vs one month ago, from recorded value history. */
private fun buildWealthDigest(
    assets: List<Asset>,
    values: List<AssetValue>,
    netWorth: Long,
    totalLiabilities: Long,
    currency: String
): List<String> {
    val lines = mutableListOf<String>()
    val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val hasHistory = values.any { it.timestamp <= monthAgo }
    if (hasHistory) {
        val then = netWorthAt(assets, values, monthAgo)
        val delta = netWorth - then
        if (then != 0L && delta != 0L) {
            val pct = delta * 100 / kotlin.math.abs(then)
            lines += "Net worth is ${if (delta > 0) "up" else "down"} " +
                "${Format.compact(kotlin.math.abs(delta), currency)} ($pct%) over the last month."
        }
        // Biggest mover among individual holdings in the same window.
        val byAsset = values.groupBy { it.assetId }
        assets.filter { !it.isLiability }.mapNotNull { a ->
            val vs = byAsset[a.id] ?: return@mapNotNull null
            val now = vs.maxByOrNull { it.timestamp }?.valueMinor ?: return@mapNotNull null
            val old = vs.filter { it.timestamp <= monthAgo }.maxByOrNull { it.timestamp }?.valueMinor
                ?: return@mapNotNull null
            a to (now - old)
        }.maxByOrNull { kotlin.math.abs(it.second) }?.takeIf { it.second != 0L }?.let { (a, d) ->
            lines += "Biggest mover: ${AssetType.meta(a.type).emoji} ${a.name} " +
                "(${if (d > 0) "+" else "−"}${Format.compact(kotlin.math.abs(d), currency)})."
        }
    }
    if (totalLiabilities > 0 && netWorth > 0) {
        lines += "Liabilities are ${totalLiabilities * 100 / (netWorth + totalLiabilities)}% of what you own."
    }
    return lines
}

/** Net worth at a point in time: latest recorded value per asset at or before [atMillis]. */
private fun netWorthAt(assets: List<Asset>, values: List<AssetValue>, atMillis: Long): Long {
    val sign = assets.associate { it.id to if (it.isLiability) -1L else 1L }
    return values.filter { it.timestamp <= atMillis }
        .groupBy { it.assetId }
        .entries.sumOf { (assetId, vs) ->
            (vs.maxByOrNull { it.timestamp }?.valueMinor ?: 0L) * (sign[assetId] ?: 1L)
        }
}

/** Month-end net worth for the last 6 months, carrying forward each asset's latest value. */
private fun netWorthSeries(assets: List<Asset>, values: List<AssetValue>): List<BarEntry> {
    val zone = ZoneId.systemDefault()
    val byAsset = values.groupBy { it.assetId }
    val sign = assets.associate { it.id to if (it.isLiability) -1L else 1L }
    return (5 downTo 0).map { back ->
        val month = LocalDate.now().minusMonths(back.toLong())
        val endOfMonth = month.withDayOfMonth(month.lengthOfMonth()).plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val total = byAsset.entries.sumOf { (assetId, vs) ->
            val v = vs.filter { it.timestamp < endOfMonth }.maxByOrNull { it.timestamp }?.valueMinor ?: 0L
            v * (sign[assetId] ?: 1L)
        }
        BarEntry(month.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, total.coerceAtLeast(0))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetDialog(
    initial: Asset?,
    initialValue: Long?,
    currency: String,
    startLiability: Boolean = false,
    onSave: (Asset, Long) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var liability by remember { mutableStateOf(initial?.isLiability ?: startLiability) }
    var type by remember {
        mutableStateOf(initial?.type ?: if (liability) AssetType.LOAN else AssetType.MUTUAL_FUND)
    }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var platform by remember { mutableStateOf(initial?.platform ?: "") }
    var investedText by remember {
        mutableStateOf(initial?.investedMinor?.takeIf { it > 0 }?.let { "%.0f".format(it / 100.0) } ?: "")
    }
    var valueText by remember {
        mutableStateOf(initialValue?.let { "%.0f".format(it / 100.0) } ?: "")
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val valueMinor = Format.parseAmount(valueText)
    val investedMinor = if (investedText.isBlank()) 0L else Format.parseAmount(investedText)
    val isLiability = liability

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    when {
                        initial != null -> if (isLiability) "Edit liability" else "Edit asset"
                        isLiability -> "Add liability"
                        else -> "Add asset"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !liability,
                        onClick = {
                            if (liability) {
                                liability = false
                                type = AssetType.all.first { !it.second.isLiability }.first
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Asset · you own") }
                    SegmentedButton(
                        selected = liability,
                        onClick = {
                            if (!liability) {
                                liability = true
                                type = AssetType.all.first { it.second.isLiability }.first
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Liability · you owe") }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssetType.all.filter { it.second.isLiability == liability }.forEach { (t, meta) ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text("${meta.emoji} ${meta.label}") }
                        )
                    }
                }

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(if (isLiability) "Loan name (e.g. Home loan)" else "Name (e.g. Nifty 50 Index Fund)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = platform, onValueChange = { platform = it },
                    label = { Text(if (isLiability) "Lender (optional)" else "Platform (Zerodha, Groww… optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = valueText, onValueChange = { valueText = it },
                    label = { Text(if (isLiability) "Outstanding amount" else "Current value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = valueText.isNotBlank() && valueMinor == null,
                    supportingText = if (Format.isExpression(valueText) && valueMinor != null) {
                        { Text("= ${Format.money(valueMinor, currency)}") }
                    } else null,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (!isLiability) {
                    OutlinedTextField(
                        value = investedText, onValueChange = { investedText = it },
                        label = { Text("Amount invested (optional, for returns)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = if (Format.isExpression(investedText) && investedMinor != null) {
                            { Text("= ${Format.money(investedMinor, currency)}") }
                        } else null,
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(Modifier.fillMaxWidth()) {
                    if (onDelete != null) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            onSave(
                                (initial ?: Asset(name = "", type = type)).copy(
                                    name = name.trim(),
                                    type = type,
                                    platform = platform.trim(),
                                    isLiability = isLiability,
                                    investedMinor = investedMinor ?: 0L
                                ),
                                valueMinor!!
                            )
                        },
                        enabled = name.isNotBlank() && valueMinor != null
                    ) { Text("Save") }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Remove \"${initial?.name}\"?",
            text = "Its value history will also be deleted.",
            confirmLabel = "Remove",
            onConfirm = { onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}
