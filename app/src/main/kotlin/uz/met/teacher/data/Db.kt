package uz.met.teacher.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val lessonId: String?,
    val startedAt: Long,
    val speakingSeconds: Double = 0.0,
    val words: Int = 0,
    val pauses: Int = 0,
    /** True until a session long enough to analyze has been analyzed. */
    val analysisPending: Boolean = false,
    val analysisJson: String? = null,
    val b2Words: Int = 0,
    val errorCount: Int = 0,
)

@Entity(tableName = "turns", indices = [Index("sessionId")])
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val fromLearner: Boolean,
    val text: String,
    val at: Long,
    /** Measured from the moment the learner stopped speaking. Empty for typed turns. */
    val sttMs: Long? = null,
    val firstTokenMs: Long? = null,
    val firstAudioMs: Long? = null,
    val ttsFactor: Double? = null,
    val cacheRead: Long? = null,
    val config: String? = null,
)

@Entity(tableName = "errors", indices = [Index("sessionId")])
data class ErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val category: String,
    val said: String,
    val better: String,
    val explanationUz: String,
    val at: Long,
)

@Entity(tableName = "cards", indices = [Index(value = ["term"], unique = true)])
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    val meaningUz: String,
    val example: String,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewMs: Long = 0,
    val dueMs: Long,
)

@Entity(tableName = "days")
data class DayEntity(
    /** ISO date in the phone's time zone. */
    @PrimaryKey val date: String,
    val xp: Int = 0,
    val speakingSeconds: Double = 0.0,
    val goalMet: Boolean = false,
)

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val lessonId: String,
    val done: Boolean = false,
    val doneAt: Long? = null,
    /** Generated once and kept, so repeating a lesson costs nothing. */
    val contentJson: String? = null,
)

@Entity(tableName = "usage")
data class UsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: String,
    val model: String,
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    val cacheWrite: Long,
    val costUsd: Double,
)

@Entity(tableName = "tests")
data class TestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val cefr: String,
    val fluency: Int,
    val grammar: Int,
    val vocabulary: Int,
    val coherence: Int,
    val feedbackUz: String,
    val nextStepsJson: String,
    val wordsPerMinute: Int,
    val pauses: Int,
)

data class CategoryCount(val category: String, val count: Int)

@Dao
interface MetDao {
    @Insert suspend fun insertSession(session: SessionEntity): Long
    @Update suspend fun updateSession(session: SessionEntity)
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: Long): SessionEntity?
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 200") fun sessions(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE analysisPending = 1 ORDER BY startedAt DESC") fun pendingSessions(): Flow<List<SessionEntity>>

    @Insert suspend fun insertTurn(turn: TurnEntity): Long
    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY at, id") suspend fun turns(sessionId: Long): List<TurnEntity>
    @Query("SELECT * FROM turns WHERE firstAudioMs IS NOT NULL ORDER BY at DESC LIMIT 200") suspend fun timedTurns(): List<TurnEntity>

    @Insert suspend fun insertErrors(errors: List<ErrorEntity>)
    @Query("SELECT * FROM errors WHERE sessionId = :sessionId") suspend fun errorsOf(sessionId: Long): List<ErrorEntity>
    @Query("SELECT * FROM errors WHERE category = :category ORDER BY at DESC LIMIT 6") suspend fun recentErrors(category: String): List<ErrorEntity>
    @Query("SELECT * FROM errors ORDER BY at DESC LIMIT 6") suspend fun latestErrors(): List<ErrorEntity>
    @Query("SELECT category, COUNT(*) AS count FROM errors WHERE at >= :since GROUP BY category ORDER BY count DESC LIMIT 8")
    fun errorCategories(since: Long): Flow<List<CategoryCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCard(card: CardEntity): Long
    @Update suspend fun updateCard(card: CardEntity)
    @Query("SELECT * FROM cards WHERE dueMs <= :now ORDER BY dueMs LIMIT :limit") suspend fun dueCards(now: Long, limit: Int): List<CardEntity>
    @Query("SELECT COUNT(*) FROM cards WHERE dueMs <= :now") fun dueCount(now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM cards") fun cardCount(): Flow<Int>
    @Query("SELECT term FROM cards ORDER BY id DESC LIMIT 150") suspend fun recentTerms(): List<String>

    @Query("SELECT * FROM days WHERE date = :date") suspend fun day(date: String): DayEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDay(day: DayEntity)
    @Query("SELECT * FROM days ORDER BY date") fun days(): Flow<List<DayEntity>>

    @Query("SELECT * FROM lessons WHERE lessonId = :id") suspend fun lesson(id: String): LessonEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLesson(lesson: LessonEntity)
    @Query("SELECT lessonId FROM lessons WHERE done = 1") fun doneLessons(): Flow<List<String>>

    @Insert suspend fun insertUsage(usage: UsageEntity)
    @Query("SELECT COALESCE(SUM(costUsd), 0) FROM usage WHERE at >= :since") suspend fun costSince(since: Long): Double
    @Query("SELECT COALESCE(SUM(costUsd), 0) FROM usage WHERE at >= :since") fun costSinceFlow(since: Long): Flow<Double>

    @Insert suspend fun insertTest(test: TestEntity)
    @Query("SELECT COUNT(*) FROM tests") suspend fun testCount(): Int
    @Query("SELECT * FROM tests ORDER BY at DESC") fun tests(): Flow<List<TestEntity>>
}

@Database(
    entities = [
        SessionEntity::class, TurnEntity::class, ErrorEntity::class, CardEntity::class,
        DayEntity::class, LessonEntity::class, UsageEntity::class, TestEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MetDatabase : RoomDatabase() {
    abstract fun dao(): MetDao

    companion object {
        fun open(context: Context): MetDatabase =
            Room.databaseBuilder(context, MetDatabase::class.java, "met.db").build()
    }
}
