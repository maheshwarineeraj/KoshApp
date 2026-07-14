package com.neeraj.fin.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object TxnType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    // Money moved between the user's own accounts — excluded from all
    // income/expense statistics to avoid double counting.
    const val TRANSFER = "TRANSFER"
}

object TxnSource {
    const val MANUAL = "MANUAL"
    const val SMS = "SMS"
    const val RECURRING = "RECURRING"
    const val IMPORT = "IMPORT"
}

object PendingStatus {
    const val PENDING = "PENDING"
    const val REJECTED = "REJECTED"
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Long,
    val kind: String, // EXPENSE or INCOME
    val isDefault: Boolean = false
)

@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("categoryId"), Index("smsHash")]
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: String,
    val categoryId: Long?,
    val merchant: String,
    val note: String = "",
    val timestamp: Long,
    val source: String = TxnSource.MANUAL,
    val accountTail: String? = null,
    val smsHash: Long? = null,
    val eventBudgetId: Long? = null, // expense tagged to an event budget (trip, wedding…)
    val goalId: Long? = null         // income tagged to a savings goal
)

@Entity(tableName = "pending_sms", indices = [Index(value = ["smsHash"], unique = true)])
data class PendingSms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val amountMinor: Long,
    val type: String,
    val merchant: String,
    val accountTail: String?,
    val suggestedCategoryId: Long?,
    val smsHash: Long,
    val status: String = PendingStatus.PENDING
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val categoryId: Long,
    val monthlyLimitMinor: Long
)

object AssetType {
    const val BANK = "BANK"
    const val FD = "FD"
    const val MUTUAL_FUND = "MUTUAL_FUND"
    const val STOCKS = "STOCKS"
    const val EPF_PPF = "EPF_PPF"
    const val GOLD = "GOLD"
    const val CRYPTO = "CRYPTO"
    const val PROPERTY = "PROPERTY"
    const val CASH = "CASH"
    const val OTHER = "OTHER"
    const val LOAN = "LOAN"

    data class Meta(val emoji: String, val label: String, val isLiability: Boolean = false)

    val all: List<Pair<String, Meta>> = listOf(
        BANK to Meta("🏦", "Bank balance"),
        FD to Meta("📜", "FD / RD"),
        MUTUAL_FUND to Meta("📈", "Mutual funds"),
        STOCKS to Meta("💹", "Stocks"),
        EPF_PPF to Meta("🛡️", "EPF / PPF / NPS"),
        GOLD to Meta("🥇", "Gold"),
        CRYPTO to Meta("🪙", "Crypto"),
        PROPERTY to Meta("🏠", "Property"),
        CASH to Meta("💵", "Cash"),
        OTHER to Meta("📦", "Other"),
        LOAN to Meta("💳", "Loan / EMI", isLiability = true)
    )

    fun meta(type: String): Meta = all.firstOrNull { it.first == type }?.second ?: Meta("📦", "Other")
}

/** One holding: an investment, account, or (when isLiability) a loan. */
@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val platform: String = "",       // e.g. Zerodha, Groww, HDFC — free text
    val isLiability: Boolean = false,
    val investedMinor: Long = 0,     // total amount put in (0 = not tracked)
    val notes: String = ""
)

/** Point-in-time value of an asset; history of these gives the net-worth trend. */
@Entity(tableName = "asset_values", indices = [Index("assetId"), Index("timestamp")])
data class AssetValue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val valueMinor: Long,
    val timestamp: Long
)

/**
 * A monthly recurring transaction template (rent, SIP, subscription).
 * Applied automatically on [dayOfMonth] each month; [lastAppliedKey] is the
 * "yyyy-MM" of the last month it was posted for.
 */
@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: String,           // EXPENSE or INCOME
    val categoryId: Long?,
    val merchant: String,
    val note: String = "",
    val dayOfMonth: Int,        // 1..28
    val startMillis: Long = System.currentTimeMillis(),
    val lastAppliedKey: String? = null
)

/**
 * A spending envelope for an event ("Goa trip — ₹50k"). Expenses are tagged to it
 * from the transaction screen; the budget tracks planned vs actually spent.
 */
@Entity(tableName = "event_budgets")
data class EventBudget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val plannedMinor: Long,
    val createdAt: Long = System.currentTimeMillis()
)

/** A savings goal (trip, wedding, emergency fund) with a target amount. */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val targetMinor: Long,
    val deadlineMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "goal_contributions", indices = [Index("goalId")])
data class GoalContribution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountMinor: Long,
    val timestamp: Long,
    val note: String = ""
)
