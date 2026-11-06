package uz.met.teacher.speech

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tracks model downloads outside any one screen, so leaving a screen doesn't stop them. */
class ModelDownloads(private val store: ModelStore, private val scope: CoroutineScope) {
    var running by mutableStateOf(false)
        private set
    var doneBytes by mutableLongStateOf(0L)
        private set
    var totalBytes by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun start(packs: List<ModelPack>, onFinished: () -> Unit = {}) {
        if (running) return
        val missing = packs.filterNot(store::isReady)
        if (missing.isEmpty()) {
            onFinished()
            return
        }
        running = true
        error = null
        totalBytes = missing.sumOf { it.sizeBytes }
        doneBytes = 0
        scope.launch {
            try {
                var finished = 0L
                for (pack in missing) {
                    store.download(pack) { doneBytes = finished + it }
                    finished += pack.sizeBytes
                }
                onFinished()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = "Yuklab bo'lmadi: ${e.message}. Internetni tekshirib, qayta urinib ko'ring."
            } finally {
                running = false
            }
        }
    }
}
