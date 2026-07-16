package com.neeraj.fin.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, name")
    fun all(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun allOnce(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun all(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun allOnce(): List<Txn>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun between(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to")
    suspend fun betweenOnce(from: Long, to: Long): List<Txn>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsHash = :hash)")
    suspend fun existsBySmsHash(hash: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE source = 'SMS' AND amountMinor = :amountMinor AND type = :type AND timestamp BETWEEN :from AND :to)")
    suspend fun existsSimilarSms(amountMinor: Long, type: String, from: Long, to: Long): Boolean

    @Query("SELECT categoryId FROM transactions WHERE categoryId IS NOT NULL AND LOWER(merchant) = LOWER(:merchant) ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastCategoryForMerchant(merchant: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(txn: Txn): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txns: List<Txn>)

    @Update
    suspend fun update(txn: Txn)

    @Delete
    suspend fun delete(txn: Txn)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("UPDATE transactions SET eventBudgetId = NULL WHERE eventBudgetId = :eventBudgetId")
    suspend fun clearEventBudgetTag(eventBudgetId: Long)

    @Query("UPDATE transactions SET goalId = NULL WHERE goalId = :goalId")
    suspend fun clearGoalTag(goalId: Long)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface PendingSmsDao {
    @Query("SELECT * FROM pending_sms WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun pending(): Flow<List<PendingSms>>

    @Query("SELECT COUNT(*) FROM pending_sms WHERE status = 'PENDING'")
    fun pendingCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_sms WHERE smsHash = :hash)")
    suspend fun existsByHash(hash: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM pending_sms WHERE amountMinor = :amountMinor AND type = :type AND timestamp BETWEEN :from AND :to)")
    suspend fun existsSimilar(amountMinor: Long, type: String, from: Long, to: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: PendingSms): Long

    @Query("UPDATE pending_sms SET status = 'REJECTED' WHERE id = :id")
    suspend fun reject(id: Long)

    @Query("DELETE FROM pending_sms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_sms WHERE status = 'REJECTED' AND timestamp < :olderThan")
    suspend fun pruneRejected(olderThan: Long)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY isLiability, type, name")
    fun all(): Flow<List<Asset>>

    @Query("SELECT * FROM assets")
    suspend fun allOnce(): List<Asset>

    @Query("SELECT * FROM asset_values ORDER BY timestamp")
    fun values(): Flow<List<AssetValue>>

    @Query("SELECT * FROM asset_values")
    suspend fun valuesOnce(): List<AssetValue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: Asset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<Asset>)

    @Update
    suspend fun update(asset: Asset)

    @Query("DELETE FROM assets WHERE id = :assetId")
    suspend fun delete(assetId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValue(value: AssetValue): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(values: List<AssetValue>)

    @Query("DELETE FROM asset_values WHERE assetId = :assetId")
    suspend fun deleteValues(assetId: Long)

    @Query("DELETE FROM assets")
    suspend fun clear()

    @Query("DELETE FROM asset_values")
    suspend fun clearValues()
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY dayOfMonth")
    fun all(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules")
    suspend fun allOnce(): List<RecurringRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RecurringRule>)

    @Update
    suspend fun update(rule: RecurringRule)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recurring_rules")
    suspend fun clear()
}

@Dao
interface EventBudgetDao {
    @Query("SELECT * FROM event_budgets ORDER BY createdAt")
    fun all(): Flow<List<EventBudget>>

    @Query("SELECT * FROM event_budgets")
    suspend fun allOnce(): List<EventBudget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: EventBudget): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<EventBudget>)

    @Update
    suspend fun update(budget: EventBudget)

    @Query("DELETE FROM event_budgets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM event_budgets")
    suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt")
    fun all(): Flow<List<Goal>>

    @Query("SELECT * FROM goals")
    suspend fun allOnce(): List<Goal>

    @Query("SELECT * FROM goal_contributions ORDER BY timestamp DESC")
    fun contributions(): Flow<List<GoalContribution>>

    @Query("SELECT * FROM goal_contributions")
    suspend fun contributionsOnce(): List<GoalContribution>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: Goal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<Goal>)

    @Update
    suspend fun update(goal: Goal)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun delete(goalId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(c: GoalContribution): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributions(cs: List<GoalContribution>)

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId")
    suspend fun deleteContributions(goalId: Long)

    @Query("DELETE FROM goals")
    suspend fun clear()

    @Query("DELETE FROM goal_contributions")
    suspend fun clearContributions()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun all(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun allOnce(): List<Budget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: Budget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<Budget>)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)

    @Query("DELETE FROM budgets")
    suspend fun clear()
}


@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY enabled DESC, dayOfMonth ASC")
    fun all(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun allOnce(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: Reminder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Reminder>)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun clear()
}
