package com.codyd.daedalus.scanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codyd.daedalus.scanner.BuildConfig
import com.codyd.daedalus.scanner.UiState

@Composable
fun PairScreen(
    state: UiState,
    onQr: (String) -> Unit,
    onPair: (apiUrl: String, code: String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var apiUrl by rememberSaveable { mutableStateOf(BuildConfig.DEFAULT_API_URL) }
    var showServer by rememberSaveable { mutableStateOf(false) }
    val scanner = rememberBarcodeScanner()
    val detectors = remember(scanner) { listOf(scanner) }
    val submit = { if (code.isNotBlank()) onPair(apiUrl.trim(), code.trim()) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Pair with Daedalus", style = MaterialTheme.typography.headlineSmall)
        Text(
            "On your computer, open Parts Inventory and click Pair Phone… then point this camera at the QR code. " +
                "Parts you scan go straight into that computer's inventory.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            WithCameraPermission(Modifier.fillMaxSize()) {
                CameraView(
                    detectors = detectors,
                    onResult = { r -> r.getValue(scanner)?.mapNotNull { it.rawValue }?.firstOrNull { it.startsWith("daedalus:") }?.let(onQr) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(48.dp))
        }

        Text("Or type the code shown under the QR:", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(12) },
                label = { Text("Pairing code") },
                placeholder = { Text("XXXX-XXXX") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = submit, enabled = code.isNotBlank() && !state.busy) { Text("Pair") }
        }

        if (showServer) {
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("Server") },
                supportingText = { Text("Only needed for a typed code when the computer uses its own Daedalus API.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { showServer = true }) { Text("Server settings") }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
