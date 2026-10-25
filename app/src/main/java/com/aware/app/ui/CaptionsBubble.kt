package com.aware.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignLanguage
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aware.app.R
import com.aware.app.ui.theme.AwareAccent

/** The single entry point for live captions with the signer above them. */
@Composable
fun CaptionsBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(72.dp),
        shape = CircleShape,
        containerColor = AwareAccent,
        contentColor = Color.Black,
    ) {
        Icon(Icons.Default.SignLanguage, stringResource(R.string.live_captions), Modifier.size(40.dp))
    }
}
