package uz.met.teacher.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.met.teacher.AppGraph
import uz.met.teacher.voice.VoiceInputController

/** The standard screen: a title bar with a back arrow, then the content. */
@Composable
fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga") }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), content = content)
    }
}

/** Scrolling content for screens that are mostly reading. */
@Composable
fun ColumnScope.ScrollArea(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun MetCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = color, shadowElevation = 1.dp) {
        Column(clickable.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun ActionCard(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    MetCard(onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null) {
    MetCard(color = MaterialTheme.colorScheme.errorContainer) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Qayta urinish") }
    }
}

@Composable
fun LoadingBlock(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Says [text] aloud when tapped, and stops if tapped again. */
@Composable
fun ListenButton(graph: AppGraph, text: String, voice: Int? = null, speed: Float? = null) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    IconButton(onClick = {
        val running = job
        if (running != null && running.isActive) {
            running.cancel()
        } else {
            job = scope.launch { runCatching { graph.speech.say(text, voice, speed) } }
        }
    }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Eshitish", tint = MaterialTheme.colorScheme.primary) }
}

/** The big round microphone button, with its permission request and listening feedback. */
@Composable
fun MicButton(
    controller: VoiceInputController,
    enabled: Boolean = true,
    idleLabel: String = "Gapirish uchun bosing",
    onStart: () -> Unit = { controller.start() },
) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart()
    }
    // Leaving the screen must not leave the microphone open.
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    val recording = controller.state == VoiceInputController.State.RECORDING
    val transcribing = controller.state == VoiceInputController.State.TRANSCRIBING
    val pulse by animateFloatAsState(if (recording) 1f + controller.level * 0.6f else 1f, label = "mic level")
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            if (recording) {
                Box(Modifier.size(80.dp).scale(pulse).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)))
            }
            FilledIconButton(
                onClick = {
                    when {
                        recording -> controller.stop()
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> onStart()
                        else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = enabled && !transcribing,
                modifier = Modifier.size(76.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (recording) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (transcribing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    Icon(
                        if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (recording) "To'xtatish" else "Gapirish",
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Text(
            when {
                recording -> "Tinglayapman, ${formatSeconds(controller.seconds)}. Tugatish uchun bosing"
                transcribing -> "Tanilmoqda..."
                else -> idleLabel
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun formatSeconds(total: Int): String = "%d:%02d".format(total / 60, total % 60)

/** Keeps the screen awake while it is shown, because talking doesn't touch the display. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
