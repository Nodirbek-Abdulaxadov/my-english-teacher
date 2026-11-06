package uz.met.teacher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Ember = Color(0xFFE8593A)
val Leaf = Color(0xFF17866F)
val Amber = Color(0xFFB7791F)

private val Light = lightColorScheme(
    primary = Color(0xFF2A2F7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E4F8),
    onPrimaryContainer = Color(0xFF15184D),
    secondary = Ember,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE5DE),
    onSecondaryContainer = Color(0xFF5A1C0C),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF211F1C),
    surface = Color.White,
    onSurface = Color(0xFF211F1C),
    surfaceVariant = Color(0xFFF1EADF),
    onSurfaceVariant = Color(0xFF5E5850),
    outline = Color(0xFFCDC5B8),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF5F120D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB9BDF5),
    onPrimary = Color(0xFF15184D),
    primaryContainer = Color(0xFF2F3372),
    onPrimaryContainer = Color(0xFFE3E4F8),
    secondary = Color(0xFFFF9B80),
    onSecondary = Color(0xFF4C1405),
    secondaryContainer = Color(0xFF5E2A1B),
    onSecondaryContainer = Color(0xFFFDE5DE),
    background = Color(0xFF17161A),
    onBackground = Color(0xFFEDE8E0),
    surface = Color(0xFF211F25),
    onSurface = Color(0xFFEDE8E0),
    surfaceVariant = Color(0xFF312E36),
    onSurfaceVariant = Color(0xFFC9C3B9),
    outline = Color(0xFF5B5760),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF6B1D16),
    onErrorContainer = Color(0xFFFBE4E1),
)

private val MetShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun MetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, shapes = MetShapes, content = content)
}
