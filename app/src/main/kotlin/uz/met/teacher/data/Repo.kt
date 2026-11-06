package uz.met.teacher.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import uz.met.core.ClaudeFailure
import uz.met.core.CardMemory
import uz.met.core.ErrorItem
import uz.met.core.Fsrs
import uz.met.core.LearningPath
import uz.met.core.MetJson
import uz.met.core.Rating
import uz.met.core.SessionAnalysis
import uz.met.core.SpeakingTestResult
import uz.met.core.Streak
import uz.met.core.StreakInfo
import uz.met.core.UsageRecord
import uz.met.core.WordItem
import uz.met.core.Xp
import java.time.LocalDate
import java.time.ZoneId

const val MIN_WORDS_FOR_ANALYSIS = 15

/** All writes to the learner's data, so rules like XP and the daily goal live in one place. */
class Repo(val dao: MetDao, private val settings: Settings, private val scope: CoroutineScope) {
    private fun today(): LocalDate = LocalDate.now()

    val streak: Flow<StreakInfo> = dao.days().map { days ->
        Streak.compute(days.filter { it.goalMet }.map { LocalDate.parse(it.date) }.toSet(), today())
    }

    val todayXp: Flow<Int> = dao.days().map { days -> days.firstOrNull { it.date == today().toString() }?.xp ?: 0 }

    suspend fun addXp(amount: Int, speakingSeconds: Double = 0.0) {
        val date = today().toString()
        val day = dao.day(date) ?: DayEntity(date)
        val xp = day.xp + amount
        val met = day.goalMet || xp >= settings.dailyGoal
        dao.putDay(day.copy(xp = xp, speakingSeconds = day.speakingSeconds + speakingSeconds, goalMet = met))
        if (met) settings.lastGoalDate = date
    }

    /** Counts speech towards today's XP, and towards the session when there is one. */
    suspend fun addSpeaking(sessionId: Long?, seconds: Double, words: Int, pauses: Int) {
        addXp(Xp.forSpeaking(seconds), seconds)
        val session = sessionId?.let { dao.session(it) } ?: return
        val totalWords = session.words + words
        dao.updateSession(
            session.copy(
                speakingSeconds = session.speakingSeconds + seconds,
                words = totalWords,
                pauses = session.pauses + pauses,
                // Set as the words come in, so a session survives the app being killed halfway.
                analysisPending = session.analysisJson == null && totalWords >= MIN_WORDS_FOR_ANALYSIS,
            ),
        )
    }

    fun recordUsage(record: UsageRecord) {
        scope.launch {
            dao.insertUsage(
                UsageEntity(
                    at = System.currentTimeMillis(),
                    kind = record.kind,
                    model = record.model.id,
                    input = record.usage.inputTokens,
                    output = record.usage.outputTokens,
                    cacheRead = record.usage.cacheReadTokens,
                    cacheWrite = record.usage.cacheWriteTokens,
                    costUsd = record.costUsd,
                ),
            )
        }
    }

    fun monthStartMs(): Long =
        today().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Stops new Claude requests once the learner's own monthly limit is used up. */
    suspend fun ensureBudget() {
        val spent = dao.costSince(monthStartMs())
        if (spent >= settings.monthlyLimit) {
            throw ClaudeFailure(
                "Bu oy uchun belgilangan \$${settings.monthlyLimit.toInt()} limit tugadi. Sozlamada oshirishingiz mumkin.",
            )
        }
    }

    suspend fun applyAnalysis(sessionId: Long, analysis: SessionAnalysis) {
        val session = checkNotNull(dao.session(sessionId))
        val now = System.currentTimeMillis()
        dao.insertErrors(analysis.errors.map { it.toEntity(sessionId, now) })
        addCards(analysis.newWords)
        // One session says little, so only speaking tests or the learner's own choice change the level.
        settings.profile = settings.profile.merged(analysis.profile)
        dao.updateSession(
            session.copy(
                analysisPending = false,
                analysisJson = MetJson.encodeToString(analysis),
                b2Words = analysis.b2PlusWords,
                errorCount = analysis.errors.size,
            ),
        )
        addXp(Xp.SESSION_DONE)
    }

    private fun ErrorItem.toEntity(sessionId: Long, at: Long) =
        ErrorEntity(sessionId = sessionId, category = category, said = said, better = better, explanationUz = explanationUz, at = at)

    /** Adds words the learner doesn't have yet. They are due straight away. */
    suspend fun addCards(words: List<WordItem>) {
        val now = System.currentTimeMillis()
        words.forEach { dao.insertCard(CardEntity(term = it.term.trim(), meaningUz = it.meaningUz, example = it.example, dueMs = now)) }
    }

    suspend fun review(card: CardEntity, rating: Rating) {
        val next = Fsrs.review(
            CardMemory(card.stability, card.difficulty, card.reps, card.lapses, card.lastReviewMs, card.dueMs),
            rating,
            System.currentTimeMillis(),
        )
        dao.updateCard(
            card.copy(
                stability = next.stability, difficulty = next.difficulty, reps = next.reps,
                lapses = next.lapses, lastReviewMs = next.lastReviewMs, dueMs = next.dueMs,
            ),
        )
        addXp(Xp.WORD_REVIEWED)
    }

    suspend fun markLessonDone(lessonId: String) {
        val lesson = dao.lesson(lessonId) ?: LessonEntity(lessonId)
        if (!lesson.done) {
            dao.putLesson(lesson.copy(done = true, doneAt = System.currentTimeMillis()))
            addXp(Xp.LESSON_DONE)
        }
    }

    suspend fun saveLessonContent(lessonId: String, json: String) {
        dao.putLesson((dao.lesson(lessonId) ?: LessonEntity(lessonId)).copy(contentJson = json))
    }

    suspend fun saveTest(result: SpeakingTestResult, wordsPerMinute: Int, pauses: Int) {
        // Only the first test places the learner on the path. Later ones must not move them around.
        if (dao.testCount() == 0) settings.pathStartUnit = LearningPath.startUnitFor(result.cefr)
        dao.insertTest(
            TestEntity(
                at = System.currentTimeMillis(),
                cefr = result.cefr,
                fluency = result.fluency,
                grammar = result.grammar,
                vocabulary = result.vocabulary,
                coherence = result.coherence,
                feedbackUz = result.feedbackUz,
                nextStepsJson = MetJson.encodeToString(result.nextStepsUz),
                wordsPerMinute = wordsPerMinute,
                pauses = pauses,
            ),
        )
        settings.profile = settings.profile.copy(level = result.cefr)
        addXp(Xp.TEST_DONE)
    }
}
