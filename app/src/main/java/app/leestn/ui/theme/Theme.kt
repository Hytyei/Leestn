package app.leestn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Graphite canvas, quiet raised surfaces and the one brand red. Mint, lilac and sky are kept for the few stat blocks that lead a screen.
// Names Red/RedDark/RedSoft are kept so older code keeps working: they now mean the accent colour. Use Err for errors.
object LColors {
    val Bg = Color(0xFF121214)
    val Surface = Color(0xFF1B1B1F)
    val Surface2 = Color(0xFF252529)
    val Surface3 = Color(0xFF343439)
    val Line = Color(0xFF2B2B30)
    val Text = Color(0xFFF4F4F5)
    val Text2 = Color(0xFFBDBDC4)
    val Text3 = Color(0xFF908F98)
    val Accent = Color(0xFFF41717)
    val AccentDark = Color(0xFFB80F0F)
    val AccentSoft = Color(0x33F41717)
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
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

private val LShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun LeestnTheme(content: @Composable () -> Unit) {
    // without this, text outside a Surface (the onboarding screen) falls back to black
    MaterialTheme(colorScheme = Scheme, typography = LTypography, shapes = LShapes) {
        CompositionLocalProvider(LocalContentColor provides LColors.Text, content = content)
    }
}
