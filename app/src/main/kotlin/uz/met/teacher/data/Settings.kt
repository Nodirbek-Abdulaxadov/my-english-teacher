package uz.met.teacher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.encodeToString
import uz.met.core.LearnerProfile
import uz.met.core.MetJson
import uz.met.core.TutorModel
import uz.met.teacher.speech.SttChoice
import uz.met.teacher.speech.TtsChoice

/** The learner's choices, readable as Compose state and kept across launches. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private class Saved<T>(read: () -> T, private val write: (T) -> Unit) {
        private var state by mutableStateOf(read())
        var value: T
            get() = state
            set(new) {
                state = new
                write(new)
            }
    }

    private fun <T> saved(read: () -> T, write: (T) -> Unit) = Saved(read, write)

    private val modelPref = saved({
        TutorModel.entries.firstOrNull { it.name == prefs.getString("model", null) } ?: TutorModel.OPUS
    }) { prefs.edit().putString("model", it.name).apply() }
    var tutorModel: TutorModel
        get() = modelPref.value
        set(value) { modelPref.value = value }

    private val sttPref = saved({
        SttChoice.entries.firstOrNull { it.name == prefs.getString("stt", null) } ?: SttChoice.BASE
    }) { prefs.edit().putString("stt", it.name).apply() }
    var stt: SttChoice
        get() = sttPref.value
        set(value) { sttPref.value = value }

    private val ttsPref = saved({
        TtsChoice.entries.firstOrNull { it.name == prefs.getString("tts", null) } ?: TtsChoice.SUPERTONIC
    }) { prefs.edit().putString("tts", it.name).apply() }
    var tts: TtsChoice
        get() = ttsPref.value
        set(value) { ttsPref.value = value }

    private val voicePref = saved({ prefs.getInt("voice", 0) }) { prefs.edit().putInt("voice", it).apply() }
    var voice: Int
        get() = voicePref.value
        set(value) { voicePref.value = value }

    private val speedPref = saved({ prefs.getFloat("speed", 1.0f) }) { prefs.edit().putFloat("speed", it).apply() }
    var speed: Float
        get() = speedPref.value
        set(value) { speedPref.value = value }

    private val goalPref = saved({ prefs.getInt("goal", 50) }) { prefs.edit().putInt("goal", it).apply() }
    var dailyGoal: Int
        get() = goalPref.value
        set(value) { goalPref.value = value }

    private val limitPref = saved({ prefs.getFloat("limit", 100f) }) { prefs.edit().putFloat("limit", it).apply() }
    var monthlyLimit: Float
        get() = limitPref.value
        set(value) { limitPref.value = value }

    private val reviewPref = saved({ prefs.getBoolean("review", false) }) { prefs.edit().putBoolean("review", it).apply() }
    /** Show what Whisper heard for editing before it goes to Claude. Slower, so off by default. */
    var reviewBeforeSend: Boolean
        get() = reviewPref.value
        set(value) { reviewPref.value = value }

    private val reminderPref = saved({ prefs.getInt("reminder", 20) }) { prefs.edit().putInt("reminder", it).apply() }
    /** Hour of the daily reminder, or -1 for none. */
    var reminderHour: Int
        get() = reminderPref.value
        set(value) { reminderPref.value = value }

    private val startUnitPref = saved({ prefs.getInt("startUnit", 0) }) { prefs.edit().putInt("startUnit", it).apply() }
    /** The unit the learning path starts from, set by the placement test or by hand. */
    var pathStartUnit: Int
        get() = startUnitPref.value
        set(value) { startUnitPref.value = value }

    private val setupPref = saved({ prefs.getBoolean("setup", false) }) { prefs.edit().putBoolean("setup", it).apply() }
    var setupDone: Boolean
        get() = setupPref.value
        set(value) { setupPref.value = value }

    private val profilePref = saved({
        prefs.getString("profile", null)?.let { runCatching { MetJson.decodeFromString<LearnerProfile>(it) }.getOrNull() } ?: LearnerProfile()
    }) { prefs.edit().putString("profile", MetJson.encodeToString(it)).apply() }
    var profile: LearnerProfile
        get() = profilePref.value
        set(value) { profilePref.value = value }

    /** The last date the daily goal was met, read by the reminder without opening the database. */
    var lastGoalDate: String?
        get() = prefs.getString("lastGoalDate", null)
        set(value) { prefs.edit().putString("lastGoalDate", value).apply() }
}
