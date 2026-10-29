package app.leestn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Deep ink-violet canvas, soft lifted cards, one vivid violet accent. Mint, lilac and sky are the colour blocks for stats.
// Names Red/RedDark/RedSoft are kept so older code keeps working: they now mean the accent colour. Use Err for errors.
object LColors {
    val Bg = Color(0xFF121117)
    val Surface = Color(0xFF1C1A24)
    val Surface2 = Color(0xFF26232F)
    val Surface3 = Color(0xFF353141)
    val Line = Color(0xFF2D2A38)
    val Text = Color(0xFFF5F4FA)
    val Text2 = Color(0xFFBDBACB)
    val Text3 = Color(0xFF8F8C9E)
    val Accent = Color(0xFF7B5CF5)
    val AccentDark = Color(0xFF5B3FD0)
    val AccentSoft = Color(0x337B5CF5)
    val Red = Accent
    val RedDark = AccentDark
    val RedSoft = AccentSoft
    val Err = Color(0xFFFF6B6B)
    val Mint = Color(0xFF4DEBC4)
    val Lilac = Color(0xFFC59BFF)
    val Sky = Color(0xFF4F8DFF)
    val Side = Mint
    val SideSoft = Color(0x334DEBC4)
    val Good = Color(0xFF4FD39A)
    val Chart1 = Color(0xFF3987E5)
    val Chart2 = Color(0xFFD95926)
    val Chart3 = Color(0xFF199E70)
    val Chart4 = Color(0xFFC98500)
    val ChartOther = Color(0xFF5A5A5A)
}

private val Scheme = darkColorScheme(
    primary = LColors.Accent,
    onPrimary = Color.White,
    secondary = LColors.Mint,
    background = LColors.Bg,
    onBackground = LColors.Text,
    surface = LColors.Surface,
    onSurface = LColors.Text,
    surfaceVariant = LColors.Surface2,
    onSurfaceVariant = LColors.Text2,
    outline = LColors.Line,
    outlineVariant = LColors.Line,
)

private val Sans = FontFamily.SansSerif

private val LTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.6.sp),
)

private val LShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun LeestnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = LTypography, shapes = LShapes, content = content)
}
