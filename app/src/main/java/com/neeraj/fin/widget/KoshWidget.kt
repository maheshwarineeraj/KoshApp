package com.neeraj.fin.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.neeraj.fin.FinApp
import com.neeraj.fin.MainActivity
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.util.Format
import com.neeraj.fin.util.PeriodKind
import com.neeraj.fin.util.Periods
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Home-screen widget: this month's net, income, and expenses; tap to open Kosh. */
class KoshWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as FinApp
        val month = Periods.rangeFor(PeriodKind.MONTH, LocalDate.now())
        val txns = runCatching { app.repository.txnsBetweenOnce(month.startMillis, month.endMillis) }
            .getOrDefault(emptyList())
        val currency = runCatching { app.settings.currencyCode.first() }.getOrDefault("INR")
        val income = txns.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinor }
        val expense = txns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinor }

        provideContent {
            val bg = ColorProvider(Color(0xFFDCF5EE), Color(0xFF1C2B27))
            val fg = ColorProvider(Color(0xFF00201C), Color(0xFFDEE4E1))
            val sub = ColorProvider(Color(0xFF3F4946), Color(0xFFBFC9C4))
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(bg)
                    .cornerRadius(20.dp)
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Text("Kosh · ${month.label}", style = TextStyle(color = sub, fontSize = 12.sp))
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    Format.money(income - expense, currency),
                    style = TextStyle(color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "↑ ${Format.compact(income, currency)}",
                        style = TextStyle(color = ColorProvider(Color(0xFF2E7D32), Color(0xFF81C784)), fontSize = 13.sp)
                    )
                    Spacer(GlanceModifier.height(1.dp).defaultWeight())
                    Text(
                        "↓ ${Format.compact(expense, currency)}",
                        style = TextStyle(color = ColorProvider(Color(0xFFC62828), Color(0xFFE57373)), fontSize = 13.sp)
                    )
                }
            }
        }
    }
}

class KoshWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KoshWidget()
}

suspend fun updateKoshWidget(context: Context) {
    runCatching { KoshWidget().updateAll(context) }
}
