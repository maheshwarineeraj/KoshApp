package com.neeraj.fin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Category::class, Txn::class, PendingSms::class, Budget::class,
        Asset::class, AssetValue::class, Goal::class, GoalContribution::class,
        EventBudget::class, RecurringRule::class, Reminder::class, Pocket::class, CreditCard::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun pendingSmsDao(): PendingSmsDao
    abstract fun budgetDao(): BudgetDao
    abstract fun assetDao(): AssetDao
    abstract fun goalDao(): GoalDao
    abstract fun eventBudgetDao(): EventBudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun reminderDao(): ReminderDao
    abstract fun pocketDao(): PocketDao
    abstract fun cardDao(): CardDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `platform` TEXT NOT NULL, `isLiability` INTEGER NOT NULL, `investedMinor` INTEGER NOT NULL, `notes` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `asset_values` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `assetId` INTEGER NOT NULL, `valueMinor` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_values_assetId` ON `asset_values` (`assetId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_values_timestamp` ON `asset_values` (`timestamp`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, `deadlineMillis` INTEGER, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `eventBudgetId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `goalId` INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `event_budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `plannedMinor` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountMinor` INTEGER NOT NULL, `type` TEXT NOT NULL, `categoryId` INTEGER, `merchant` TEXT NOT NULL, `note` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `startMillis` INTEGER NOT NULL, `lastAppliedKey` TEXT)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_sms` ADD COLUMN `foreignCurrency` TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_sms` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `event_budgets` ADD COLUMN `startMillis` INTEGER")
                db.execSQL("ALTER TABLE `event_budgets` ADD COLUMN `endMillis` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, `recurrence` TEXT NOT NULL, " +
                        "`dayOfMonth` INTEGER NOT NULL, `monthOfYear` INTEGER NOT NULL, " +
                        "`dueMillis` INTEGER, `merchant` TEXT NOT NULL, `categoryId` INTEGER, " +
                        "`lastDoneKey` TEXT, `enabled` INTEGER NOT NULL, `source` TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `pocketId` INTEGER")
                db.execSQL("ALTER TABLE `pending_sms` ADD COLUMN `pocketId` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pockets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `accountTails` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `sourceBody` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cards` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bankName` TEXT NOT NULL, " +
                        "`holderName` TEXT NOT NULL, `last4` TEXT NOT NULL, `network` TEXT NOT NULL, " +
                        "`encNumber` TEXT NOT NULL, `encCvv` TEXT NOT NULL, `expiryMonth` INTEGER NOT NULL, " +
                        "`expiryYear` INTEGER NOT NULL, `colorIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `cvvShifted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `cardId` INTEGER")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fin.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build().also { instance = it }
            }
    }
}

object DefaultCategories {
    fun expense(): List<Category> = listOf(
        Category(name = "Food & Dining", emoji = "🍔", color = 0xFFEF6C00, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Groceries", emoji = "🛒", color = 0xFF7CB342, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Transport", emoji = "🚕", color = 0xFFFDD835, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Shopping", emoji = "🛍️", color = 0xFFEC407A, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Bills & Utilities", emoji = "💡", color = 0xFF29B6F6, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Entertainment", emoji = "🎬", color = 0xFFAB47BC, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Health", emoji = "💊", color = 0xFFEF5350, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Travel", emoji = "✈️", color = 0xFF26A69A, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Education", emoji = "📚", color = 0xFF5C6BC0, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Rent & Home", emoji = "🏠", color = 0xFF8D6E63, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "EMI & Loans", emoji = "🏦", color = 0xFF78909C, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Investments", emoji = "📈", color = 0xFF66BB6A, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Personal Care", emoji = "💇", color = 0xFFFF8A65, kind = TxnType.EXPENSE, isDefault = true),
        Category(name = "Other", emoji = "🧾", color = 0xFFBDBDBD, kind = TxnType.EXPENSE, isDefault = true)
    )

    fun income(): List<Category> = listOf(
        Category(name = "Salary", emoji = "💼", color = 0xFF43A047, kind = TxnType.INCOME, isDefault = true),
        Category(name = "Business", emoji = "🏢", color = 0xFF00897B, kind = TxnType.INCOME, isDefault = true),
        Category(name = "Interest & Dividends", emoji = "🪙", color = 0xFFFFB300, kind = TxnType.INCOME, isDefault = true),
        Category(name = "Refunds & Cashback", emoji = "💸", color = 0xFF26C6DA, kind = TxnType.INCOME, isDefault = true),
        Category(name = "Gifts", emoji = "🎁", color = 0xFFD81B60, kind = TxnType.INCOME, isDefault = true),
        Category(name = "Other Income", emoji = "➕", color = 0xFF9E9E9E, kind = TxnType.INCOME, isDefault = true)
    )
}
