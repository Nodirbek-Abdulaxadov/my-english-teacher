package uz.met.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import uz.met.core.Effort
import uz.met.core.PrepPack
import uz.met.core.Prompts
import uz.met.teacher.AppGraph

private enum class PrepStep(val labelUz: String) {
    PHRASES("Iboralar"),
    DIALOGUE("Namuna"),
    REPEAT("Qaytarish"),
    DRILLS("Mashq"),
    READY("Suhbat"),
}

/** Teaches what a role-play needs before asking for it: phrases, a model, repetition, short drills. */
@Composable
fun PrepareScreen(graph: AppGraph, nav: Navigator, screen: Screen.Prepare) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader {
        lessonContent(graph, "${screen.lessonId}.prep", PrepPack.serializer()) {
            val mistakes = graph.repo.dao.latestErrors().map { "\"${it.said}\" should be \"${it.better}\"" }
            val model = graph.settings.tutorModel
            val level = graph.sessionLevel(screen.lessonId)
            graph.claude {
                it.structured(
                    "prepare", model, Effort.MEDIUM, Prompts.prepareSystem,
                    Prompts.prepareUser(screen.brief, level, mistakes), PrepPack.serializer(),
                )
            }
        }
    }
    var step by remember { mutableStateOf(PrepStep.PHRASES) }
    var index by remember { mutableIntStateOf(0) }
    fun next(to: PrepStep) {
        step = to
        index = 0
    }
    fun startTalk(pack: PrepPack) {
        val brief = Prompts.briefWithPhrases(screen.brief, pack.phrases.map { it.term })
        nav.replace(Screen.Talk(screen.title, brief, screen.lessonId))
    }

    ScreenFrame(title = screen.title, onBack = nav::back) {
        ScrollArea {
            LoaderContent(loader, "Dars tayyorlanmoqda...") { pack ->
                Text(
                    "${step.ordinal + 1} / ${PrepStep.entries.size} · ${step.labelUz}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (step) {
                    PrepStep.PHRASES -> {
                        Text("Suhbatdan oldin shu iboralarni o'rganing. Har birini eshitib ko'ring.")
                        pack.phrases.forEach { phrase ->
                            MetCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(phrase.term, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(phrase.meaningUz, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    ListenButton(graph, phrase.term)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(phrase.example, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    ListenButton(graph, phrase.example)
                                }
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch { graph.repo.addCards(pack.phrases) }
                                next(PrepStep.DIALOGUE)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("O'rgandim, davom etish") }
                        TextButton(onClick = { startTalk(pack) }, modifier = Modifier.fillMaxWidth()) { Text("To'g'ridan-to'g'ri suhbatga o'tish") }
                    }
                    PrepStep.DIALOGUE -> DialoguePlayer(
                        graph, pack.dialogue,
                        intro = "Bu suhbat qanday o'tishi mumkinligini tinglang. B odam sizning o'rningizda gapiradi.",
                        nextLabel = "Davom etish",
                    ) { next(PrepStep.REPEAT) }
                    PrepStep.REPEAT -> {
                        val sentence = pack.repeat.getOrNull(index)
                        if (sentence == null) {
                            LaunchedEffect(Unit) { next(PrepStep.DRILLS) }
                        } else {
                            ShadowingStep(graph, sentence, "${index + 1} / ${pack.repeat.size}") { index++ }
                        }
                    }
                    PrepStep.DRILLS -> {
                        val drill = pack.drills.getOrNull(index)
                        if (drill == null) {
                            LaunchedEffect(Unit) { next(PrepStep.READY) }
                        } else {
                            DrillStep(graph, drill, "${index + 1} / ${pack.drills.size}", onResult = {}, onNext = { index++ })
                        }
                    }
                    PrepStep.READY -> {
                        MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Endi tayyorsiz!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Shu iboralarni suhbatda ishlatib ko'ring. Qiynalsangiz, \"Nima deyishni bilmayman\" tugmasi yordam beradi.")
                        }
                        MetCard {
                            pack.phrases.forEach { Text("• ${it.term}") }
                        }
                        Button(onClick = { startTalk(pack) }, modifier = Modifier.fillMaxWidth()) { Text("Suhbatni boshlash") }
                    }
                }
            }
        }
    }
}
