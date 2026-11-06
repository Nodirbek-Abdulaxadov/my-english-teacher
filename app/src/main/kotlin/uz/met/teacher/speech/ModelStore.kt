package uz.met.teacher.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Downloads speech models into private storage, resuming after a lost connection. */
class ModelStore(context: Context) {
    private val root = File(context.filesDir, "models")

    fun dir(pack: ModelPack) = File(root, pack.folder)

    /** Complete files have their final name, so a size check is enough here; hashes are checked on download. */
    fun isReady(pack: ModelPack): Boolean =
        pack.files.all { File(dir(pack), it.name).length() == it.sizeBytes }

    /** [onProgress] receives bytes done across the whole pack. Throws if it can't finish. */
    suspend fun download(pack: ModelPack, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        dir(pack).mkdirs()
        var finished = 0L
        for (file in pack.files) {
            val target = File(dir(pack), file.name)
            if (target.length() != file.sizeBytes) fetchWithRetries(file, target) { onProgress(finished + it) }
            finished += file.sizeBytes
            onProgress(finished)
        }
    }

    fun delete(pack: ModelPack) {
        dir(pack).deleteRecursively()
    }

    private suspend fun fetchWithRetries(file: ModelFile, target: File, onBytes: (Long) -> Unit) {
        val part = File(target.path + ".part")
        var failures = 0
        while (true) {
            val before = part.length()
            try {
                fetch(file, part, onBytes)
                if (sha256(part) != file.sha256) {
                    part.delete()
                    throw IOException("${file.name}: fayl buzilgan keldi")
                }
                check(part.renameTo(target)) { "${file.name} saqlanmadi" }
                return
            } catch (e: IOException) {
                // Progress on a bad connection isn't failure, so only attempts that got nowhere count.
                failures = if (part.length() > before) 0 else failures + 1
                if (failures >= MAX_FAILURES) throw e
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun fetch(file: ModelFile, part: File, onBytes: (Long) -> Unit) {
        val connection = URL(file.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val existing = part.length()
            if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("${file.name}: server $code qaytardi")
            }
            // A server that ignores Range sends the whole file again.
            val resume = code == HttpURLConnection.HTTP_PARTIAL
            RandomAccessFile(part, "rw").use { out ->
                if (resume) out.seek(existing) else out.setLength(0)
                var done = if (resume) existing else 0L
                val buffer = ByteArray(1 shl 16)
                connection.inputStream.use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        onBytes(done)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_FAILURES = 5
        const val RETRY_DELAY_MS = 3_000L
        const val TIMEOUT_MS = 30_000
    }
}
