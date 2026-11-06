package uz.met.teacher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.met.core.ClaudeGateway
import uz.met.core.Example
import uz.met.core.SessionAnalysis
import uz.met.teacher.AppGraph
import uz.met.teacher.voice.ChatMessage
import uz.met.teacher.voice.ConversationController
import uz.met.teacher.voice.VoiceInputController
import uz.met.teacher.voice.analyzeSession
import uz.met.teacher.voice.storedAnalysis

@Composable
fun TalkScreen(graph: AppGraph, nav: Navigator, screen: Screen.Talk) {
    KeepScreenOn()
    val scope = rememberCoroutineScope()
    val controller = remember(screen) { ConversationController(graph, scope, screen.title, screen.brief, screen.lessonId) }
    LaunchedEffect(controller) { controller.start() }
    var ending by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    fun end() {
        if (ending) return
        ending = true
        scope.launch {
            val id = controller.finish()
            if (id != null) nav.replace(Screen.Summary(id)) else nav.back()
        }
    }
    BackHandler { end() }

    val listState = rememberLazyListState()
    LaunchedEffect(controller.messages.size, controller.messages.lastOrNull()?.text) {
        if (controller.messages.isNotEmpty()) listState.animateScrollToItem(controller.messages.lastIndex)
    }

    ScreenFrame(
        title = screen.title,
        onBack = ::end,
        actions = { TextButton(onClick = ::end, enabled = !ending) { Text("Tugatish") } },
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(controller.messages, key = { it.id }) { message ->
                MessageBubble(graph, message, onImprove = { controller.improve(message.id) })
            }
        }
        controller.error?.let { ErrorBanner(it) }
        controller.hints?.let { options -> HintsCard(graph, options) }
        controller.timing?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        when {
            ending -> LoadingBlock("Sessiya yakunlanmoqda...")
            controller.phase == ConversationController.Phase.REVIEW -> DraftEditor(controller)
            typing -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), placeholder = { Text("Inglizcha yozing") })
                IconButton(onClick = {
                    controller.sendTyped(typed)
                    typed = ""
                    typing = false
                }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Yuborish") }
            }
            else -> {
                val busy = controller.phase == ConversationController.Phase.STARTING || controller.phase == ConversationController.Phase.THINKING
                Box(Modifier.fillMaxWidth()) {
                    MicButton(
                        controller.voice,
                        enabled = !busy,
                        idleLabel = when {
                            busy -> "O'qituvchi o'ylayapti..."
                            controller.phase == ConversationController.Phase.SPEAKING -> "Bo'lib gapirish uchun bosing"
                            else -> "Gapirish uchun bosing"
                        },
                        onStart = controller::startListening,
                    )
                    if (controller.voice.state == VoiceInputController.State.IDLE) {
                        IconButton(onClick = { typing = true }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp)) {
                            Icon(Icons.Filled.Keyboard, contentDescription = "Yozib yuborish", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (controller.hints == null && controller.voice.state == VoiceInputController.State.IDLE) {
                    TextButton(onClick = controller::requestHints, enabled = !busy && !controller.hintsLoading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (controller.hintsLoading) "Tayyorlanmoqda..." else "Nima deyishni bilmayman")
                    }
                }
            }
        }
    }
}

@Composable
private fun HintsCard(graph: AppGraph, options: List<Example>) {
    MetCard(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text("Shunday deyishingiz mumkin:", style = MaterialTheme.typography.labelMedium)
        options.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(option.en, fontWeight = FontWeight.Medium)
                    Text(option.uz, style = MaterialTheme.typography.bodySmall)
                }
                ListenButton(graph, option.en)
            }
        }
    }
}

@Composable
private fun DraftEditor(controller: ConversationController) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Eshitilgan matn, kerak bo'lsa tuzating:", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(controller.draft, { controller.draft = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = controller::discardDraft, modifier = Modifier.weight(1f)) { Text("Bekor qilish") }
            Button(onClick = controller::confirmDraft, modifier = Modifier.weight(1f)) { Text("Yuborish") }
        }
    }
}

@Composable
private fun MessageBubble(graph: AppGraph, message: ChatMessage, onImprove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.fromLearner) Alignment.End else Alignment.Start) {
        Box(
            Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (message.fromLearner) colors.primary else colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (message.text.isEmpty()) {
                Text("...", color = colors.onSurfaceVariant)
            } else {
                Text(message.text, color = if (message.fromLearner) colors.onPrimary else colors.onSurface)
            }
        }
        if (message.fromLearner) {
            val better = message.better
            when {
                better != null -> MetCard(Modifier.padding(top = 4.dp), color = colors.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(better.better, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        ListenButton(graph, better.better)
                    }
                    Text(better.noteUz, style = MaterialTheme.typography.bodySmall)
                }
                message.betterLoading -> Text("Tayyorlanmoqda...", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
                else -> TextButton(onClick = onImprove, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Yaxshiroq ayt", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** What came out of a session. Runs the analysis itself if it hasn't happened yet. */
@Composable
fun SummaryScreen(graph: AppGraph, nav: Navigator, sessionId: Long) {
    var analysis by remember { mutableStateOf<SessionAnalysis?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tooShort by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(sessionId, attempt) {
        loading = true
        error = null
        try {
            val session = graph.repo.dao.session(sessionId)
            analysis = storedAnalysis(session?.analysisJson)
            if (analysis == null) {
                if (session?.analysisPending == true) analysis = analyzeSession(graph, sessionId) else tooShort = true
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = ClaudeGateway.describe(e)
        }
        loading = false
    }

    ScreenFrame(title = "Sessiya yakuni", onBack = nav::back) {
        ScrollArea {
            if (loading) LoadingBlock("Suhbatingiz tahlil qilinmoqda...")
            error?.let { ErrorBanner("$it\nSuhbatingiz saqlangan, tahlilni keyinroq ham qilish mumkin.") { attempt++ } }
            if (tooShort) {
                MetCard { Text("Tahlil uchun suhbat juda qisqa bo'ldi. Keyingi safar ko'proq gapiring!") }
            }
            analysis?.let { AnalysisContent(it, nav) }
        }
        Button(onClick = nav::back, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Davom etish") }
    }
}

@Composable
private fun AnalysisContent(a: SessionAnalysis, nav: Navigator) {
    val colors = MaterialTheme.colorScheme
    MetCard(color = colors.primaryContainer) {
        Text(a.summaryUz, color = colors.onPrimaryContainer)
        Text("Bugungi taxminiy daraja: ${a.cefrEstimate}", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
    }
    if (a.strengthsUz.isNotEmpty()) {
        SectionTitle("Yaxshi chiqdi")
        MetCard { a.strengthsUz.forEach { Text("• $it") } }
    }
    if (a.errors.isNotEmpty()) {
        SectionTitle("Tuzatish kerak")
        a.errors.forEach { error ->
            MetCard {
                Text(error.said, color = colors.error)
                Text(error.better, color = Leaf, fontWeight = FontWeight.Medium)
                Text(error.explanationUz, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        OutlinedButton(
            onClick = {
                val top = a.errors.first().category
                nav.go(Screen.Grammar(null, "Common mistakes with: $top", top))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Shu xatolar bo'yicha mashq qilish") }
    }
    if (a.upgrades.isNotEmpty()) {
        SectionTitle("B2 darajasida shunday deyiladi")
        a.upgrades.forEach { upgrade ->
            MetCard {
                Text(upgrade.said, color = colors.onSurfaceVariant)
                Text(upgrade.b2, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (a.newWords.isNotEmpty()) {
        SectionTitle("Lug'atga qo'shildi")
        MetCard {
            a.newWords.forEach { word ->
                Text(word.term, fontWeight = FontWeight.SemiBold)
                Text(word.meaningUz, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
