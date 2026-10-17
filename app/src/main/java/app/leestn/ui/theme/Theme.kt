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

// Trakt-inspired palette: near-black canvas, slightly lifted cards, one strong red.
object LColors {
    val Bg = Color(0xFF101010)
    val Surface = Color(0xFF1B1B1B)
    val Surface2 = Color(0xFF262626)
    val Surface3 = Color(0xFF313131)
    val Line = Color(0xFF2C2C2C)
    val Text = Color(0xFFF2F2F2)
    val Text2 = Color(0xFFBDBDBD)
    val Text3 = Color(0xFF8A8A8A)
    val Red = Color(0xFFED1C24)
    val RedDark = Color(0xFFB5161C)
    val RedSoft = Color(0x33ED1C24)
    val Side = Color(0xFF9B8CFF)
    val SideSoft = Color(0x339B8CFF)
    val Good = Color(0xFF4FD39A)
    val Chart1 = Color(0xFF3987E5)
    val Chart2 = Color(0xFFD95926)
    val Chart3 = Color(0xFF199E70)
    val Chart4 = Color(0xFFC98500)
    val ChartOther = Color(0xFF5A5A5A)
}

private val Scheme = darkColorScheme(
    primary = LColors.Red,
    onPrimary = Color.White,
    secondary = LColors.Side,
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
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun LeestnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = LTypography, shapes = LShapes, content = content)
}
