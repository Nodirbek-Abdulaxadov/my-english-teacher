package uz.met.teacher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import uz.met.teacher.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as MetApp).graph
        setContent {
            MetTheme {
                val nav = remember { Navigator() }
                if (!graph.settings.setupDone) {
                    SetupScreen(graph, nav)
                } else {
                    BackHandler(enabled = nav.canGoBack) { nav.back() }
                    when (val screen = nav.current) {
                        Screen.Home -> HomeScreen(graph, nav)
                        is Screen.Prepare -> PrepareScreen(graph, nav, screen)
                        is Screen.Talk -> TalkScreen(graph, nav, screen)
                        is Screen.Summary -> SummaryScreen(graph, nav, screen.sessionId)
                        is Screen.Grammar -> GrammarScreen(graph, nav, screen)
                        is Screen.WordsLearn -> WordsLearnScreen(graph, nav, screen)
                        Screen.WordsReview -> WordsReviewScreen(graph, nav)
                        is Screen.Listening -> ListeningScreen(graph, nav, screen)
                        Screen.ImportAudio -> ImportAudioScreen(graph, nav)
                        is Screen.Fluency -> FluencyScreen(graph, nav, screen)
                        is Screen.SpeakingTest -> SpeakingTestScreen(graph, nav, screen.testNumber)
                        Screen.Settings -> SettingsScreen(graph, nav)
                        Screen.Diagnostics -> DiagnosticsScreen(graph, nav)
                        Screen.WhisperCheck -> WhisperCheckScreen(graph, nav)
                    }
                }
            }
        }
    }
}
