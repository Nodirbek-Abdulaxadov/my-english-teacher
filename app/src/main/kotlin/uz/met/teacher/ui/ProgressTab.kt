package uz.met.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import uz.met.teacher.AppGraph

private val DAY_LABEL = DateTimeFormatter.ofPattern("d.MM")

private fun shortDate(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY_LABEL)

@Composable
fun ProgressTab(graph: AppGraph, nav: Navigator) {
    val days by graph.repo.dao.days().collectAsStateWithLifecycle(emptyList())
    val sessions by graph.repo.dao.sessions().collectAsStateWithLifecycle(emptyList())
    val tests by graph.repo.dao.tests().collectAsStateWithLifecycle(emptyList())
    val pending by graph.repo.dao.pendingSessions().collectAsStateWithLifecycle(emptyList())
    val costFlow = remember { graph.repo.dao.costSinceFlow(graph.repo.monthStartMs()) }
    val spent by costFlow.collectAsStateWithLifecycle(0.0)
    val cards by graph.repo.dao.cardCount().collectAsStateWithLifecycle(0)
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val lastTest = tests.firstOrNull()
        MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Text("Taxminiy daraja: ${lastTest?.cefr ?: graph.settings.profile.level}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Maqsad: B2. Bu baho rasmiy emas.", style = MaterialTheme.typography.bodySmall)
            val daysSince = lastTest?.let { ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate(), today) }
            Button(onClick = { nav.go(Screen.SpeakingTest(tests.size)) }) {
                Text(
                    when {
                        daysSince == null -> "Daraja testini topshirish"
                        daysSince >= 30 -> "Oylik testni topshirish"
                        else -> "Testni qayta topshirish ($daysSince kun oldin)"
                    },
                )
            }
        }

        pending.firstOrNull()?.let { session ->
            ActionCard(Icons.Filled.Warning, "Tahlil qilinmagan sessiya", session.title) { nav.go(Screen.Summary(session.id)) }
        }

        StreakCalendar(
            goalDays = days.filter { it.goalMet }.map { LocalDate.parse(it.date) }.toSet(),
            practiceDays = days.filter { it.xp > 0 }.map { LocalDate.parse(it.date) }.toSet(),
            today = today,
        )

        val byDate = days.associateBy { it.date }
        BarChart(
            "Gapirish (14 kun)",
            (13 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                ChartPoint(day.format(DAY_LABEL), ((byDate[day.toString()]?.speakingSeconds ?: 0.0) / 60).toFloat())
            },
            "daq",
        )

        val spoken = sessions.filter { it.speakingSeconds >= 30 }.take(20).reversed()
        LineChart("Gapirish tezligi", spoken.map { ChartPoint(shortDate(it.startedAt), (it.words / (it.speakingSeconds / 60)).toFloat()) }, "so'z/daq")
        val analyzed = spoken.filter { it.analysisJson != null && it.words > 0 }
        LineChart("Har 100 so'zga xatolar", analyzed.map { ChartPoint(shortDate(it.startedAt), it.errorCount * 100f / it.words) }, "ta")
        LineChart("Pauzalar (daqiqasiga)", spoken.map { ChartPoint(shortDate(it.startedAt), (it.pauses / (it.speakingSeconds / 60)).toFloat()) }, "ta")
        LineChart("B2 so'z va iboralar", analyzed.map { ChartPoint(shortDate(it.startedAt), it.b2Words.toFloat()) }, "ta")

        MetCard {
            Text("Lug'atingiz: $cards ta so'z", style = MaterialTheme.typography.titleSmall)
        }

        if (tests.isNotEmpty()) {
            SectionTitle("Test natijalari")
            tests.take(6).forEach { test ->
                MetCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(test.cefr, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(shortDate(test.at), style = MaterialTheme.typography.labelMedium)
                    }
                    Text("Ravonlik ${test.fluency}/5 · Grammatika ${test.grammar}/5 · So'zlar ${test.vocabulary}/5 · Izchillik ${test.coherence}/5", style = MaterialTheme.typography.bodySmall)
                    Text(test.feedbackUz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val limit = graph.settings.monthlyLimit
        val share = (spent / limit).toFloat().coerceIn(0f, 1f)
        MetCard {
            Text("Bu oygi xarajat: \$${"%.2f".format(spent)} / \$${limit.toInt()}", style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(
                progress = { share },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (share >= 0.8f) Amber else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (share >= 0.8f) {
                Text("Limitga yaqinlashdingiz. Limit tugasa, suhbat to'xtaydi.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
