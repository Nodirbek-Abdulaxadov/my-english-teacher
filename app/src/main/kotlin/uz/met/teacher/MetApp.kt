package uz.met.teacher

import android.app.Application
import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.met.core.ClaudeFailure
import uz.met.core.ClaudeGateway
import uz.met.core.LearningPath
import uz.met.core.Level
import uz.met.teacher.data.ApiKeyStore
import uz.met.teacher.data.MetDatabase
import uz.met.teacher.data.Repo
import uz.met.teacher.data.Settings
import uz.met.teacher.speech.MicRecorder
import uz.met.teacher.speech.ModelDownloads
import uz.met.teacher.speech.ModelStore
import uz.met.teacher.speech.SpeechEngines
import uz.met.teacher.speech.SpeechPlayer

class MetApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        graph = AppGraph(this)
        Reminders.schedule(this, graph.settings.reminderHour)
    }
}

/** The app's long-lived objects, made once at startup. */
class AppGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(context)
    val keys = ApiKeyStore(context)
    val repo = Repo(MetDatabase.open(context).dao(), settings, scope)
    val models = ModelStore(context)
    val engines = SpeechEngines(models, settings)
    val speech = SpeechPlayer(engines, settings)
    val downloads = ModelDownloads(models, scope)

    private var gateway: Pair<String, ClaudeGateway>? = null
    private var warmedUp = false

    /** Loads the speech engines and API client ahead of time, so the first turn isn't the slow one. */
    fun warmUp() {
        if (warmedUp) return
        warmedUp = true
        scope.launch {
            runCatching {
                if (models.isReady(settings.stt.pack)) engines.withStt {}
                if (models.isReady(settings.tts.pack)) engines.withTts {}
                gateway()
            }
        }
    }

    /**
     * The level to pitch a session at. A path lesson uses its unit's level, and everything else
     * rises with the learner's place on the path, so the tutor gets harder gradually.
     */
    suspend fun sessionLevel(lessonId: String?): String {
        lessonId?.let { id -> LearningPath.unitOf(id)?.let { return it.level } }
        val profileLevel = settings.profile.level
        val done = repo.dao.doneLessons().first().toSet()
        val current = LearningPath.nextLesson(done, settings.pathStartUnit)?.let { LearningPath.unitOf(it.id) }
        return current?.let { Level.higher(profileLevel, it.level) } ?: Level.normalize(profileLevel)
    }

    /** Test builds can use a recording instead of the microphone, and a stand-in for the API. */
    private fun testFile(name: String): File? =
        if (BuildConfig.TEST_HOOKS) context.getExternalFilesDir(null)?.let { File(it, name) }?.takeIf { it.exists() } else null

    fun newRecorder() = MicRecorder(testFile("test_mic.wav"))

    private fun gateway(): ClaudeGateway {
        val key = keys.load() ?: throw ClaudeFailure("Avval Sozlamada API kalitni kiriting.")
        gateway?.takeIf { it.first == key }?.let { return it.second }
        val baseUrl = testFile("test_base_url.txt")?.readText()?.trim()
        return ClaudeGateway(key, baseUrl, repo::recordUsage).also { gateway = key to it }
    }

    /** Runs a Claude call off the main thread, within the budget, with errors the learner can read. */
    suspend fun <T> claude(block: (ClaudeGateway) -> T): T = withContext(Dispatchers.IO) {
        repo.ensureBudget()
        try {
            block(gateway())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw ClaudeFailure(ClaudeGateway.describe(e), e)
        }
    }
}

/** Saves a crash to a file so the next launch can show it and the learner can share it. */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(context.filesDir, FILE).writeText(error.stackTraceToString()) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
