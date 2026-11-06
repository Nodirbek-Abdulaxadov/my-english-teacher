package uz.met.teacher.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.met.core.ClaudeGateway
import uz.met.teacher.AppGraph

/** First launch: name, API key, voice models and permissions, then the placement test. */
@Composable
fun SetupScreen(graph: AppGraph, nav: Navigator) {
    // Only a checked key is kept, so a saved one means setup got past that step and can resume there.
    var step by remember { mutableIntStateOf(if (graph.keys.load() != null) 2 else 0) }
    ScreenFrame(title = "My English Teacher", onBack = null) {
        ScrollArea {
            when (step) {
                0 -> WelcomeStep(graph) { step = 1 }
                1 -> KeyStep(graph) { step = 2 }
                2 -> ModelsStep(graph) { step = 3 }
                else -> PermissionsStep(
                    onPlacement = {
                        graph.settings.setupDone = true
                        nav.go(Screen.SpeakingTest(0))
                    },
                    onSkip = { graph.settings.setupDone = true },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(graph: AppGraph, onNext: () -> Unit) {
    var name by remember { mutableStateOf(graph.settings.profile.name) }
    Text("Xush kelibsiz!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("Bu ilova sizni A2/B1 dan B2 gacha, asosan gapirish orqali olib boradi: suhbat, rol o'yinlari, grammatika, so'zlar va tinglash.")
    MetCard {
        Text("Maxfiylik", fontWeight = FontWeight.SemiBold)
        Text("Ovozingiz telefondan chiqmaydi: u shu yerda matnga aylanadi. Faqat matn sizning API kalitingiz bilan Anthropic'ga yuboriladi.", style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedTextField(name, { name = it }, label = { Text("Ismingiz (o'qituvchi shunday murojaat qiladi)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = {
            graph.settings.profile = graph.settings.profile.copy(name = name.trim())
            onNext()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Davom etish") }
}

@Composable
private fun KeyStep(graph: AppGraph, onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Text("Claude API kaliti", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Suhbat uchun o'zingizning Claude API kalitingiz kerak. Kalit telefoningizda shifrlangan holda saqlanadi va faqat Anthropic'ga yuboriladi.")
    OutlinedButton(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Kalitni Anthropic Console'dan olish") }
    OutlinedTextField(
        key, { key = it }, label = { Text("API kalit") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    error?.let { ErrorBanner(it) }
    Button(
        enabled = key.isNotBlank() && !checking,
        onClick = {
            checking = true
            error = null
            scope.launch {
                try {
                    graph.keys.save(key.trim())
                    graph.claude { it.verifyKey() }
                    onNext()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    graph.keys.clear()
                    error = ClaudeGateway.describe(e)
                }
                checking = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (checking) "Tekshirilmoqda..." else "Tekshirish va davom etish") }
}

@Composable
private fun ModelsStep(graph: AppGraph, onNext: () -> Unit) {
    val downloads = graph.downloads
    val packs = remember { listOf(graph.settings.stt.pack, graph.settings.tts.pack) }
    val total = remember { packs.sumOf { it.sizeBytes } }
    val ready = packs.all(graph.models::isReady)
    LaunchedEffect(ready) { if (ready) onNext() }
    Text("Ovoz modellari", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Nutqni tanish va ovoz telefonda ishlaydi, shuning uchun bir marta ${total / 1_000_000} MB yuklab olinadi. Wi-Fi tavsiya qilinadi. Uzilib qolsa, to'xtagan joyidan davom etadi.")
    if (downloads.running) {
        KeepScreenOn()
        val progress = if (downloads.totalBytes > 0) downloads.doneBytes.toFloat() / downloads.totalBytes else 0f
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("${downloads.doneBytes / 1_000_000} / ${downloads.totalBytes / 1_000_000} MB")
    } else {
        downloads.error?.let { ErrorBanner(it) }
        Button(onClick = { downloads.start(packs, onNext) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (downloads.error != null) "Qayta urinish" else "Yuklab olish")
        }
    }
}

@Composable
private fun PermissionsStep(onPlacement: () -> Unit, onSkip: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(wanted.toTypedArray())
    }
    Text("Hammasi tayyor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Mikrofon gapirish uchun, bildirishnoma esa kunlik eslatma uchun kerak.")
    MetCard {
        Text("Darajangizni aniqlaymiz", fontWeight = FontWeight.SemiBold)
        Text("3 ta savolga og'zaki javob berasiz, taxminan 5 daqiqa. Natijaga qarab o'qituvchi sizga moslashadi.", style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = onPlacement, modifier = Modifier.fillMaxWidth()) { Text("Testni boshlash") }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Keyinroq") }
}
