package uz.met.teacher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.met.core.LearningPath
import uz.met.core.LessonKind
import uz.met.core.PathLesson
import uz.met.core.Scenarios
import uz.met.core.StreakInfo
import uz.met.teacher.AppGraph

private enum class Tab(val label: String, val icon: ImageVector) {
    PATH("Yo'l", Icons.Filled.Route),
    TALK("Suhbat", Icons.Filled.RecordVoiceOver),
    PRACTICE("Mashq", Icons.Filled.FitnessCenter),
    PROGRESS("Natija", Icons.Filled.Insights),
}

@Composable
fun HomeScreen(graph: AppGraph, nav: Navigator) {
    val tab = Tab.entries[nav.homeTab]
    LaunchedEffect(Unit) { graph.warmUp() }
    val streak by graph.repo.streak.collectAsStateWithLifecycle(StreakInfo(0, 0, 0, false))
    val xp by graph.repo.todayXp.collectAsStateWithLifecycle(0)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HomeHeader(graph, nav, streak, xp) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { nav.homeTab = it.ordinal },
                        icon = { Icon(it.icon, contentDescription = null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            when (tab) {
                Tab.PATH -> PathTab(graph, nav)
                Tab.TALK -> TalkTab(nav)
                Tab.PRACTICE -> PracticeTab(graph, nav)
                Tab.PROGRESS -> ProgressTab(graph, nav)
            }
        }
    }
}

@Composable
private fun HomeHeader(graph: AppGraph, nav: Navigator, streak: StreakInfo, xp: Int) {
    val goal = graph.settings.dailyGoal
    Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocalFireDepartment, contentDescription = "Ketma-ket kunlar",
                tint = if (streak.goalMetToday) Ember else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(" ${streak.current} kun", fontWeight = FontWeight.Bold)
            if (streak.freezes > 0) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.AcUnit, contentDescription = "Himoya kunlari", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(" ${streak.freezes}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.weight(1f))
            Text("$xp / $goal XP", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { nav.go(Screen.Settings) }) { Icon(Icons.Filled.Settings, contentDescription = "Sozlama") }
        }
        LinearProgressIndicator(
            progress = { (xp.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp).height(8.dp).clip(CircleShape),
            color = if (xp >= goal) Leaf else Ember,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

private fun kindIcon(kind: LessonKind): ImageVector = when (kind) {
    LessonKind.SPEAK -> Icons.Filled.RecordVoiceOver
    LessonKind.GRAMMAR -> Icons.AutoMirrored.Filled.MenuBook
    LessonKind.WORDS -> Icons.Filled.Translate
    LessonKind.LISTEN -> Icons.Filled.Headphones
    LessonKind.FLUENCY -> Icons.Filled.Timer
}

fun openLesson(nav: Navigator, lesson: PathLesson) {
    nav.go(
        when (lesson.kind) {
            LessonKind.SPEAK -> Screen.Prepare(lesson.id, lesson.titleUz, lesson.brief)
            LessonKind.GRAMMAR -> Screen.Grammar(lesson.id, lesson.brief, null)
            LessonKind.WORDS -> Screen.WordsLearn(lesson.id, lesson.brief)
            LessonKind.LISTEN -> Screen.Listening(lesson.id, lesson.brief)
            LessonKind.FLUENCY -> Screen.Fluency(lesson.id, lesson.brief)
        },
    )
}

@Composable
private fun PathTab(graph: AppGraph, nav: Navigator) {
    val done by graph.repo.dao.doneLessons().collectAsStateWithLifecycle(emptyList())
    val now = remember(nav.stack.size) { System.currentTimeMillis() }
    val dueFlow = remember(now) { graph.repo.dao.dueCount(now) }
    val due by dueFlow.collectAsStateWithLifecycle(0)
    val doneSet = done.toSet()
    val startUnit = graph.settings.pathStartUnit
    val next = LearningPath.nextLesson(doneSet, startUnit)

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                if (due > 0) {
                    ActionCard(Icons.Filled.Translate, "So'z takrori", "Bugun $due ta so'z kutmoqda") { nav.go(Screen.WordsReview) }
                }
                if (next != null) {
                    MetCard(color = MaterialTheme.colorScheme.primary, onClick = { openLesson(nav, next) }) {
                        Text("Keyingi dars", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                        Text(next.titleUz, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        Text(next.kind.labelUz, color = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    MetCard { Text("Butun yo'lni bosib o'tdingiz. Endi erkin suhbat va og'zaki test bilan davom eting!") }
                }
            }
        }
        LearningPath.units.forEachIndexed { index, unit ->
            item(key = "unit${unit.id}") {
                Row(Modifier.padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}. ${unit.titleUz}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(unit.level, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(unit.lessons, key = { it.id }) { lesson ->
                val isDone = lesson.id in doneSet
                val isNext = lesson.id == next?.id
                val isLast = lesson == unit.lessons.last()
                PathNode(lesson, isDone, isNext, open = isDone || isNext || unit.id < startUnit, isLast) { openLesson(nav, lesson) }
            }
        }
    }
}

/** One stop on the trail. Lessons ahead of the next one stay locked, so the path is walked in order. */
@Composable
private fun PathNode(lesson: PathLesson, done: Boolean, current: Boolean, open: Boolean, last: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val trail = if (done) Leaf else colors.outline
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).then(if (open) Modifier.clickable(onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (!last) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(trail, Offset(size.width / 2, size.height / 2), Offset(size.width / 2, size.height), strokeWidth = 3.dp.toPx())
                    drawLine(trail, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height / 2), strokeWidth = 3.dp.toPx())
                }
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(
                    when {
                        done -> Leaf
                        current -> Ember
                        open -> colors.primary
                        else -> colors.surfaceVariant
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        done -> Icons.Filled.Check
                        open -> kindIcon(lesson.kind)
                        else -> Icons.Filled.Lock
                    },
                    contentDescription = null,
                    tint = if (open) androidx.compose.ui.graphics.Color.White else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp)) {
            Text(lesson.titleUz, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal, color = if (open) colors.onBackground else colors.onSurfaceVariant)
            Text(lesson.kind.labelUz, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun TalkTab(nav: Navigator) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Scenarios.all, key = { it.id }) { scenario ->
            ActionCard(Icons.Filled.RecordVoiceOver, scenario.titleUz, scenario.descriptionUz) {
                nav.go(Screen.Talk(scenario.titleUz, scenario.brief, null))
            }
        }
    }
}

private val CATEGORY_NAMES = mapOf(
    "articles" to "Artikllar (a, an, the)",
    "tenses" to "Zamonlar",
    "third person -s" to "Uchinchi shaxs -s",
    "he/she" to "He va she",
    "prepositions" to "Predloglar",
    "word order" to "So'z tartibi",
    "plurals" to "Ko'plik",
    "word choice" to "So'z tanlash",
    "question forms" to "So'roq gaplar",
    "other" to "Boshqa xatolar",
)

@Composable
private fun PracticeTab(graph: AppGraph, nav: Navigator) {
    val now = remember(nav.stack.size) { System.currentTimeMillis() }
    val dueFlow = remember(now) { graph.repo.dao.dueCount(now) }
    val due by dueFlow.collectAsStateWithLifecycle(0)
    val categoriesFlow = remember(now) { graph.repo.dao.errorCategories(now - 30L * 86_400_000L) }
    val categories by categoriesFlow.collectAsStateWithLifecycle(emptyList())
    val done by graph.repo.dao.doneLessons().collectAsStateWithLifecycle(emptyList())
    val unit = LearningPath.nextLesson(done.toSet(), graph.settings.pathStartUnit)?.let { LearningPath.unitOf(it.id) } ?: LearningPath.units.last()
    val listenTheme = unit.lessons.first { it.kind == LessonKind.LISTEN }.brief
    val wordsTheme = unit.lessons.first { it.kind == LessonKind.WORDS }.brief
    val fluencyTopic = unit.lessons.first { it.kind == LessonKind.FLUENCY }.brief

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionCard(Icons.Filled.Translate, "So'z takrori", if (due > 0) "$due ta so'z kutmoqda" else "Hozircha takrorlanadigan so'z yo'q", enabled = due > 0) {
                nav.go(Screen.WordsReview)
            }
        }
        item { ActionCard(Icons.Filled.Add, "Yangi so'zlar", "Mavzu: ${unit.titleUz}") { nav.go(Screen.WordsLearn(null, wordsTheme)) } }
        item { ActionCard(Icons.Filled.Headphones, "Tinglash", "Mavzu: ${unit.titleUz}") { nav.go(Screen.Listening(null, listenTheme)) } }
        item { ActionCard(Icons.Filled.AudioFile, "O'z audiongiz", "Podcast yoki yozuvdan mashq tuzish") { nav.go(Screen.ImportAudio) } }
        item { ActionCard(Icons.Filled.Timer, "Ravonlik mashqi", "Bitta hikoyani uch marta, har safar qisqaroq vaqtda aytish") { nav.go(Screen.Fluency(null, fluencyTopic)) } }
        if (categories.isNotEmpty()) {
            item { SectionTitle("Zaif joylaringiz (30 kun)") }
            items(categories, key = { it.category }) { item ->
                ActionCard(Icons.AutoMirrored.Filled.MenuBook, CATEGORY_NAMES[item.category] ?: item.category, "${item.count} ta xato") {
                    nav.go(Screen.Grammar(null, "Common mistakes with: ${item.category}", item.category))
                }
            }
        }
    }
}
