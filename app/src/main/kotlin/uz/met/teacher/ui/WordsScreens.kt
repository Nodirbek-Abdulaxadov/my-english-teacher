package uz.met.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.met.core.Effort
import uz.met.core.Prompts
import uz.met.core.Rating
import uz.met.core.WordPack
import uz.met.core.containsPhrase
import uz.met.core.wordsOf
import uz.met.teacher.AppGraph
import uz.met.teacher.data.CardEntity
import uz.met.teacher.voice.VoiceInputController

@Composable
fun WordsLearnScreen(graph: AppGraph, nav: Navigator, screen: Screen.WordsLearn) {
    val loader = rememberLoader {
        lessonContent(graph, screen.lessonId, WordPack.serializer()) {
            val known = graph.repo.dao.recentTerms()
            val model = graph.settings.tutorModel
            val level = graph.sessionLevel(screen.lessonId)
            graph.claude {
                it.structured(
                    "words", model, Effort.MEDIUM, Prompts.wordPackSystem,
                    Prompts.wordPackUser(screen.theme, level, known), WordPack.serializer(),
                )
            }
        }
    }
    val scope = rememberCoroutineScope()
    ScreenFrame(title = "Yangi so'zlar", onBack = nav::back) {
        ScrollArea {
            LoaderContent(loader, "So'zlar tanlanmoqda...") { pack ->
                pack.words.forEach { word ->
                    MetCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(word.term, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(word.meaningUz, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ListenButton(graph, word.term)
                        }
                        Text(word.example, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            graph.repo.addCards(pack.words)
                            screen.lessonId?.let { graph.repo.markLessonDone(it) }
                            nav.replace(Screen.WordsReview)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text("Lug'atga qo'shib, mashq qilish") }
            }
        }
    }
}

/** Recall out loud: the Uzbek meaning is shown and the learner says the English. */
@Composable
fun WordsReviewScreen(graph: AppGraph, nav: Navigator) {
    val scope = rememberCoroutineScope()
    val queue = remember { mutableStateListOf<CardEntity>() }
    var loaded by remember { mutableStateOf(false) }
    var reviewed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        queue += graph.repo.dao.dueCards(System.currentTimeMillis(), SESSION_SIZE)
        loaded = true
    }
    ScreenFrame(title = "So'z takrori", onBack = nav::back) {
        ScrollArea {
            val card = queue.firstOrNull()
            when {
                !loaded -> LoadingBlock("Yuklanmoqda...")
                card == null -> {
                    MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(if (reviewed > 0) "Barakalla!" else "Hozircha takrorlanadigan so'z yo'q", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (reviewed > 0) Text("$reviewed ta takrorlash bajarildi.")
                    }
                    Button(onClick = nav::back, modifier = Modifier.fillMaxWidth()) { Text("Davom etish") }
                }
                else -> ReviewCard(graph, card, remaining = queue.size) { rating ->
                    scope.launch {
                        graph.repo.review(card, rating)
                        reviewed++
                        queue.removeAt(0)
                        // A forgotten word comes back once more in this same sitting.
                        if (rating == Rating.AGAIN) graph.repo.dao.dueCards(Long.MAX_VALUE, SESSION_SIZE * 10).firstOrNull { it.id == card.id }?.let { queue += it }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(graph: AppGraph, card: CardEntity, remaining: Int, onRated: (Rating) -> Unit) {
    val scope = rememberCoroutineScope()
    var heard by remember(card.id, card.reps) { mutableStateOf<String?>(null) }
    var revealed by remember(card.id, card.reps) { mutableStateOf(false) }
    var error by remember(card.id, card.reps) { mutableStateOf<String?>(null) }
    val voice = remember(card.id, card.reps) {
        VoiceInputController(graph, scope, onFailure = { error = it }) { captured ->
            scope.launch { graph.repo.addSpeaking(null, captured.speechSeconds, wordsOf(captured.text).size, captured.pauses) }
            heard = captured.text
            revealed = true
        }
    }
    val correct = heard?.let { containsPhrase(it, card.term) } == true
    LaunchedEffect(revealed) { if (revealed) runCatching { graph.speech.say(card.term) } }

    Text("Qoldi: $remaining", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Text("Inglizcha qanday deyiladi?", style = MaterialTheme.typography.labelMedium)
        Text(card.meaningUz, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        // An inflected form ("was late" for "be late") can't be blanked, and showing it would give the answer away.
        if (!revealed && card.example.contains(card.term, ignoreCase = true)) {
            Text(card.example.replace(card.term, "_____", ignoreCase = true), style = MaterialTheme.typography.bodyMedium)
        }
    }
    error?.let { ErrorBanner(it) }
    if (!revealed) {
        MicButton(voice)
        TextButton(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Bilmayman, ko'rsating") }
    } else {
        heard?.let {
            MetCard(color = if (correct) Leaf.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer) {
                Text(if (correct) "To'g'ri!" else "Eshitildi: $it", fontWeight = FontWeight.Medium)
            }
        }
        MetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.term, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                ListenButton(graph, card.term)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.example, modifier = Modifier.weight(1f))
                ListenButton(graph, card.example)
            }
        }
        Text("Qanchalik oson esladingiz?", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRated(Rating.AGAIN) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Yana") }
            OutlinedButton(onClick = { onRated(Rating.HARD) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Qiyin") }
            Button(onClick = { onRated(Rating.GOOD) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Yaxshi") }
            OutlinedButton(onClick = { onRated(Rating.EASY) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Oson") }
        }
    }
}

private const val SESSION_SIZE = 30
