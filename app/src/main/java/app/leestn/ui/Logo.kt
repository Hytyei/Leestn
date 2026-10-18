package app.leestn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.leestn.R

/** The Leestn logo (mark plus wordmark). Drawn at the given height; the width follows the picture. */
@Composable
fun LeestnLogo(modifier: Modifier = Modifier, height: Dp = 34.dp) {
    Image(
        painter = painterResource(R.drawable.logo_wordmark),
        contentDescription = "Leestn",
        modifier = modifier.height(height),
    )
}
