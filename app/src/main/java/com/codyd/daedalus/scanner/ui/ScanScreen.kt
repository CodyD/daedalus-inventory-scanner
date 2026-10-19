package com.codyd.daedalus.scanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codyd.daedalus.scanner.UiState
import com.codyd.daedalus.scanner.data.Library
import com.codyd.daedalus.scanner.scan.Candidate
import com.codyd.daedalus.scanner.scan.CandidateSource
import com.codyd.daedalus.scanner.scan.FrameReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    state: UiState,
    onCandidates: (List<Candidate>) -> Unit,
    onPick: (Candidate?) -> Unit,
    onProfile: (String) -> Unit,
    onRefresh: () -> Unit,
    onUnpair: () -> Unit,
) {
    var torch by rememberSaveable { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val recognizer = rememberTextRecognizer()
    val scanner = rememberBarcodeScanner()
    val detectors = remember(recognizer, scanner) { listOf(recognizer, scanner) }
    val library = state.library

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = {
                    Box {
                        TextButton(onClick = { profileMenu = true }) {
                            Text("Into: ${state.profileName}", style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose profile")
                        }
                        DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                            for (p in state.profiles) {
                                DropdownMenuItem(
                                    text = { Text("${p.name}  (${p.items} lines)") },
                                    onClick = { profileMenu = false; onProfile(p.id) },
                                )
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { torch = !torch }) { Text(if (torch) "Light on" else "Light") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; onRefresh() })
                            DropdownMenuItem(text = { Text("Unpair this phone") }, onClick = { menu = false; confirmUnpair = true })
                        }
                    }
                },
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                WithCameraPermission(Modifier.fillMaxSize()) {
                    CameraView(
                        detectors = detectors,
                        onResult = { r -> onCandidates(FrameReader.read(r.getValue(recognizer), r.getValue(scanner), library)) },
                        modifier = Modifier.fillMaxSize(),
                        torch = torch,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    if (state.candidates.isEmpty()) {
                        item {
                            Text(
                                "Point at the chip's markings or a bag label. Pinch to zoom, tap to focus; turn on the light for small print.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    } else {
                        items(state.candidates, key = { it.partNumber }) { c ->
                            CandidateRow(c, library, state, onClick = { onPick(c) }, modifier = Modifier.animateItem())
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onPick(null) }) { Text("Type it in instead") }
                }
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair this phone?") },
            text = { Text("It will stop adding parts to the computer's inventory. You can pair again from Parts Inventory on the computer.") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; onUnpair() }) { Text("Unpair") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CandidateRow(c: Candidate, library: Library, state: UiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val part = c.libraryId?.let { id -> library.parts.firstOrNull { it.id == id } }
    val details = listOfNotNull(
        part?.name ?: "Not in the library",
        c.packageGuess,
        if (c.source == CandidateSource.BARCODE) "bag label" + (c.qty?.let { " · qty $it" } ?: "") else null,
        state.onHand(c.partNumber, null, c.packageGuess).takeIf { it > 0 }?.let { "$it on hand" },
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(c.partNumber, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(details) },
        trailingContent = { FilledTonalButton(onClick = onClick) { Text("Add") } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.background(Color.Transparent),
    )
}
