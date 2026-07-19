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
    val goalId: Long? = null,        // income tagged to a savings goal
    val pocketId: Long? = null       // stream bucket; null = the default "Personal" pocket
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
    val status: String = PendingStatus.PENDING,
    // ISO code when the SMS reported a foreign-currency spend; amountMinor is then
    // the foreign value and the user should correct it to INR when approving.
    val foreignCurrency: String? = null,
    // Purpose text parsed from the message ("For: Gas Cylinder Booking").
    val note: String = "",
    val pocketId: Long? = null
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
    const val CREDIT_CARD = "CREDIT_CARD"
    const val OTHER_LIABILITY = "OTHER_LIABILITY"

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
        LOAN to Meta("🏦", "Loan / EMI", isLiability = true),
        CREDIT_CARD to Meta("💳", "Credit card due", isLiability = true),
        OTHER_LIABILITY to Meta("📋", "Other dues", isLiability = true)
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
    val createdAt: Long = System.currentTimeMillis(),
    // Optional trip window: expenses dated inside it get auto-suggested for tagging.
    val startMillis: Long? = null,
    val endMillis: Long? = null
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


object ReminderRecurrence {
    const val ONCE = "ONCE"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
}

object ReminderSource {
    const val MANUAL = "MANUAL"
    const val PATTERN = "PATTERN"   // suggested from repeated payments
    const val SMS = "SMS"           // suggested from a bill-due message
}

/**
 * A payment the user must perform (rent transfer, manual SIP, insurance
 * premium) — unlike RecurringRule, nothing is recorded automatically.
 * Suggested reminders arrive with enabled=false until accepted.
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountMinor: Long = 0,          // 0 = amount not fixed
    val recurrence: String = ReminderRecurrence.MONTHLY,
    val dayOfMonth: Int = 1,            // MONTHLY/YEARLY: due day (1..28)
    val monthOfYear: Int = 1,           // YEARLY only
    val dueMillis: Long? = null,        // ONCE only
    val merchant: String = "",
    val categoryId: Long? = null,
    val lastDoneKey: String? = null,    // "2026-07" monthly · "2026" yearly · "done" once
    val enabled: Boolean = true,
    val source: String = ReminderSource.MANUAL,
    // Full original message for SMS-suggested reminders, so the user can verify.
    val sourceBody: String = ""
)


/**
 * A pocket isolates a stream of money (side business, family, rental) from
 * the default "Personal" stream. Transactions with pocketId = null belong to
 * Personal. At most 9 custom pockets (10 including the default).
 * [accountTails] is a comma-separated list of account/card tails this pocket
 * claims — detections from those accounts route here automatically.
 */
@Entity(tableName = "pockets")
data class Pocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "👛",
    val accountTails: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
