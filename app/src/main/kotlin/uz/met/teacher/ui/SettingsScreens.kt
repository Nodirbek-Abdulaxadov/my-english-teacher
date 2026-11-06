package uz.met.teacher.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import uz.met.core.ClaudeGateway
import uz.met.core.LearningPath
import uz.met.core.Level
import uz.met.core.ProbeOutcome
import uz.met.core.TutorModel
import uz.met.core.WhisperProbes
import uz.met.teacher.AppGraph
import uz.met.teacher.CrashLog
import uz.met.teacher.Reminders
import uz.met.teacher.BuildConfig
import uz.met.teacher.data.TurnEntity
import uz.met.teacher.speech.SttChoice
import uz.met.teacher.speech.TtsChoice
import uz.met.teacher.voice.VoiceInputController

@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
fun SettingsScreen(graph: AppGraph, nav: Navigator) {
    val settings = graph.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyDraft by remember { mutableStateOf("") }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(settings.profile.name) }

    ScreenFrame(title = "Sozlama", onBack = nav::back) {
        ScrollArea {
            SectionTitle("Ism")
            OutlinedTextField(
                name,
                {
                    name = it
                    settings.profile = settings.profile.copy(name = it.trim())
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Darajam")
            ChoiceChips(Level.choices, Level.normalize(settings.profile.level), { it }) {
                settings.profile = settings.profile.copy(level = it)
                settings.pathStartUnit = LearningPath.startUnitFor(it)
            }
            Text("Yo'l va o'qituvchi shunga moslashadi. Oldingi bo'limlarga qaytish mumkin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Claude API kaliti")
            OutlinedTextField(
                keyDraft, { keyDraft = it },
                label = { Text("Kalit saqlangan. Almashtirish uchun yangisini kiriting") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = keyDraft.isNotBlank(),
                onClick = {
                    scope.launch {
                        keyStatus = "Tekshirilmoqda..."
                        graph.keys.save(keyDraft.trim())
                        keyDraft = ""
                        keyStatus = try {
                            graph.claude { it.verifyKey() }
                            "Kalit ishlayapti"
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            ClaudeGateway.describe(e)
                        }
                    }
                },
            ) { Text("Saqlash va tekshirish") }
            keyStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            SectionTitle("Claude modeli")
            ChoiceChips(TutorModel.entries, settings.tutorModel, { it.label }) { settings.tutorModel = it }
            Text(settings.tutorModel.noteUz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Nutqni tanish")
            ChoiceChips(SttChoice.entries, settings.stt, { it.label }) { choice ->
                if (graph.models.isReady(choice.pack)) settings.stt = choice else graph.downloads.start(listOf(choice.pack)) { settings.stt = choice }
            }
            Text(settings.stt.noteUz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("O'qituvchi ovozi")
            ChoiceChips(TtsChoice.entries, settings.tts, { it.label }) { choice ->
                val apply = {
                    settings.voice = 0
                    settings.tts = choice
                }
                if (graph.models.isReady(choice.pack)) apply() else graph.downloads.start(listOf(choice.pack), apply)
            }
            Text(settings.tts.noteUz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DownloadStatus(graph)
            ChoiceChips((0 until settings.tts.voiceCount).toList(), settings.voice, { "Ovoz ${it + 1}" }) { settings.voice = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Eshitib ko'rish")
                ListenButton(graph, "Hi! How was your day? Tell me something interesting that happened.")
            }
            Text("Tezlik", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f), settings.speed, { "$it x" }) { settings.speed = it }

            SectionTitle("Kunlik maqsad")
            ChoiceChips(listOf(30, 50, 100, 150), settings.dailyGoal, { "$it XP" }) { settings.dailyGoal = it }
            Text("Bir daqiqa gapirish 6 XP beradi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Eslatma")
            ChoiceChips(listOf(-1, 8, 12, 18, 20, 21), settings.reminderHour, { if (it < 0) "Yo'q" else "$it:00" }) {
                settings.reminderHour = it
                Reminders.schedule(context, it)
            }

            SectionTitle("Oylik xarajat limiti")
            ChoiceChips(listOf(10f, 25f, 50f, 100f, 200f), settings.monthlyLimit, { "\$${it.toInt()}" }) { settings.monthlyLimit = it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Yuborishdan oldin matnni ko'rsatish", fontWeight = FontWeight.Medium)
                    Text("Eshitilgan gapni tuzatish mumkin, lekin suhbat sekinlashadi.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(settings.reviewBeforeSend, { settings.reviewBeforeSend = it })
            }

            SectionTitle("O'qituvchi siz haqingizda biladi")
            val profile = settings.profile
            MetCard {
                if (profile.facts.isEmpty() && profile.interests.isEmpty()) Text("Hozircha hech narsa.")
                profile.facts.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                profile.interests.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedButton(onClick = { settings.profile = settings.profile.copy(facts = emptyList(), interests = emptyList()) }) { Text("Tozalash") }

            OutlinedButton(onClick = { nav.go(Screen.Diagnostics) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) { Text("Diagnostika") }
        }
    }
}

@Composable
private fun DownloadStatus(graph: AppGraph) {
    val downloads = graph.downloads
    if (downloads.running) {
        val progress = if (downloads.totalBytes > 0) downloads.doneBytes.toFloat() / downloads.totalBytes else 0f
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("Yuklanmoqda: ${downloads.doneBytes / 1_000_000} / ${downloads.totalBytes / 1_000_000} MB", style = MaterialTheme.typography.bodySmall)
    }
    downloads.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

private fun median(values: List<Long>): Long? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

/** Timings per setup, the numbers that decide which model and voice to keep. */
private fun timingReport(turns: List<TurnEntity>): String = buildString {
    turns.groupBy { it.config ?: "?" }.forEach { (config, group) ->
        val spoken = group.filter { it.sttMs != null }
        appendLine("$config (${group.size} navbat):")
        appendLine("  Whisper: ${median(spoken.mapNotNull { it.sttMs })} ms")
        appendLine("  Claude birinchi so'z: ${median(spoken.mapNotNull { it.firstTokenMs })} ms")
        appendLine("  Birinchi ovoz: ${median(spoken.mapNotNull { it.firstAudioMs })} ms")
        appendLine("  Ovoz yaratish tezligi: ${group.mapNotNull { it.ttsFactor }.average().let { "%.2f".format(it) }} (1 dan kichik bo'lsa yaxshi)")
        appendLine("  Keshdan o'qilgan (oxirgi): ${group.firstOrNull()?.cacheRead}")
    }
}.ifBlank { "Hali o'lchov yo'q. Avval suhbat qiling.\n" }

@Composable
fun DiagnosticsScreen(graph: AppGraph, nav: Navigator) {
    val context = LocalContext.current
    var timing by remember { mutableStateOf("Yuklanmoqda...") }
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    val whisper = remember { File(context.filesDir, WHISPER_RESULT_FILE).takeIf { it.exists() }?.readText() }
    LaunchedEffect(Unit) { timing = timingReport(graph.repo.dao.timedTurns()) }

    fun report() = buildString {
        appendLine("My English Teacher ${BuildConfig.VERSION_NAME}")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        appendLine("Model: ${graph.settings.tutorModel.name}, STT: ${graph.settings.stt.name}, TTS: ${graph.settings.tts.name}")
        appendLine()
        appendLine(timing)
        whisper?.let {
            appendLine("Whisper testi:")
            appendLine(it)
        }
        crash?.let {
            appendLine("Oxirgi nosozlik:")
            appendLine(it)
        }
    }

    ScreenFrame(title = "Diagnostika", onBack = nav::back) {
        ScrollArea {
            Text("Bu hisobotda API kalit va suhbatlaringiz yo'q.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report())
                    context.startActivity(Intent.createChooser(send, "Hisobotni ulashish"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Hisobotni ulashish") }
            SectionTitle("Kechikish")
            MetCard { Text(timing, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { nav.go(Screen.WhisperCheck) }, modifier = Modifier.fillMaxWidth()) {
                Text("Whisper testi: xatolar saqlanadimi?")
            }
            whisper?.let { MetCard { Text(it, style = MaterialTheme.typography.bodySmall) } }
            crash?.let { text ->
                SectionTitle("Oxirgi nosozlik")
                MetCard { Text(text.take(1500), style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = {
                    CrashLog.clear(context)
                    crash = null
                }) { Text("Tozalash") }
            }
        }
    }
}

private const val WHISPER_RESULT_FILE = "whisper_check.txt"

/** Reads learner-style mistakes aloud to see whether Whisper keeps them. If it fixes them, Claude never sees them. */
@Composable
fun WhisperCheckScreen(graph: AppGraph, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val outcomes = remember { mutableStateListOf<Pair<ProbeOutcome, String>>() }
    var error by remember { mutableStateOf<String?>(null) }
    val index = outcomes.size
    val probe = WhisperProbes.all.getOrNull(index)

    val voice = remember(index) {
        VoiceInputController(graph, scope, maxSeconds = 20, onFailure = { error = it }) { captured ->
            val current = WhisperProbes.all.getOrNull(outcomes.size) ?: return@VoiceInputController
            outcomes += current.classify(captured.text) to captured.text
            if (outcomes.size == WhisperProbes.all.size) {
                val kept = outcomes.count { it.first == ProbeOutcome.KEPT }
                val detail = outcomes.mapIndexed { i, (outcome, heard) -> "${outcome.name}: ${WhisperProbes.all[i].spoken} -> $heard" }
                File(context.filesDir, WHISPER_RESULT_FILE).writeText("${graph.settings.stt.name}: $kept/${outcomes.size} saqlandi\n" + detail.joinToString("\n"))
            }
        }
    }

    ScreenFrame(title = "Whisper testi", onBack = nav::back) {
        ScrollArea {
            Text("${outcomes.size} / ${WhisperProbes.all.size} · Xato saqlandi: ${outcomes.count { it.first == ProbeOutcome.KEPT }}", style = MaterialTheme.typography.labelLarge)
            error?.let { ErrorBanner(it) }
            if (probe != null) {
                MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Xuddi shunday, xatosi bilan o'qing:", style = MaterialTheme.typography.labelMedium)
                    Text(probe.spoken, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                MicButton(voice)
            } else {
                MetCard { Text("Test tugadi. Natija Diagnostika hisobotiga qo'shildi.", fontWeight = FontWeight.SemiBold) }
                Button(onClick = nav::back, modifier = Modifier.fillMaxWidth()) { Text("Orqaga") }
            }
            outcomes.asReversed().forEach { (outcome, heard) ->
                Text("${outcome.name}: $heard", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

