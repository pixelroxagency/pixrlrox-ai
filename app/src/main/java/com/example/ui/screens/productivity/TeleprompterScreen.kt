package com.example.ui.screens.productivity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun TeleprompterScreen(viewModel: TeleprompterViewModel, onBack: () -> Unit) {
    val script by viewModel.script.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(isPlaying, speed) {
        if (isPlaying) {
            while (isActive) {
                scrollState.animateScrollTo(scrollState.value + speed.toInt())
                delay(16)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = script,
            onValueChange = { viewModel.setScript(it) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = { Text("Script") }
        )
        Text(text = script, fontSize = fontSize.sp, modifier = Modifier.verticalScroll(scrollState).weight(1f))
        Row {
            Button(onClick = { if (isPlaying) viewModel.pause() else viewModel.play() }) {
                Text(if (isPlaying) "Pause" else "Play")
            }
        }
    }
}
