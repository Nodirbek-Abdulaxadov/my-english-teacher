package uz.met.teacher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data class Talk(val title: String, val brief: String?, val lessonId: String?) : Screen
    data class Prepare(val lessonId: String, val title: String, val brief: String) : Screen
    data class Summary(val sessionId: Long) : Screen
    data class Grammar(val lessonId: String?, val topic: String, val category: String?) : Screen
    data class WordsLearn(val lessonId: String?, val theme: String) : Screen
    data object WordsReview : Screen
    data class Listening(val lessonId: String?, val theme: String) : Screen
    data object ImportAudio : Screen
    data class Fluency(val lessonId: String?, val topic: String) : Screen
    data class SpeakingTest(val testNumber: Int) : Screen
    data object Settings : Screen
    data object Diagnostics : Screen
    data object WhisperCheck : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    /** Lives here so that coming back from a lesson returns to the tab it was opened from. */
    var homeTab by mutableIntStateOf(0)
    val canGoBack: Boolean get() = stack.size > 1

    fun go(screen: Screen) {
        stack += screen
    }

    fun back() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }

    /** Moves on without leaving the current screen in the history. */
    fun replace(screen: Screen) {
        stack[stack.lastIndex] = screen
    }
}
