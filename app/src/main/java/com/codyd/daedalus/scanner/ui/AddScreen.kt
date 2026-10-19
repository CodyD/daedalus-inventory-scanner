package com.codyd.daedalus.scanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codyd.daedalus.scanner.UiState
import com.codyd.daedalus.scanner.data.InventoryItem
import com.codyd.daedalus.scanner.scan.Candidate

/** Review what was scanned (or type a part in), then add it to the selected profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    state: UiState,
    candidate: Candidate?,
    onBack: () -> Unit,
    onAdd: (InventoryItem, merge: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(candidate?.partNumber.orEmpty()) }
    var defId by rememberSaveable { mutableStateOf(candidate?.libraryId) }
    var value by rememberSaveable { mutableStateOf("") }
    var pkg by rememberSaveable { mutableStateOf(candidate?.packageGuess.orEmpty()) }
    var qty by rememberSaveable { mutableStateOf((candidate?.qty ?: 1).toString()) }
    var location by rememberSaveable { mutableStateOf(state.lastLocation.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var merge by rememberSaveable { mutableStateOf(true) }
    var libraryMenu by rememberSaveable { mutableStateOf(false) }

    val count = qty.toIntOrNull()
    val valid = name.isNotBlank() && count != null && count >= 1
    val onHand = state.onHand(name, value, pkg)
    val part = defId?.let { id -> state.library.parts.firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add to ${state.profileName}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to scanning") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Part number or name") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Library part", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedButton(onClick = { libraryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(part?.name ?: "Not in the library")
                    }
                    DropdownMenu(expanded = libraryMenu, onDismissRequest = { libraryMenu = false }) {
                        DropdownMenuItem(text = { Text("Not in the library") }, onClick = { defId = null; libraryMenu = false })
                        HorizontalDivider()
                        for (p in state.library.parts.sortedBy { it.name }) {
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { defId = p.id; libraryMenu = false })
                        }
                    }
                }
                Text(
                    "Linking a library part lets the design assistant place it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value") }, placeholder = { Text("10k") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("Package") }, placeholder = { Text("DIP-8") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { qty = ((count ?: 1) - 1).coerceAtLeast(1).toString() }) { Text("−") }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter(Char::isDigit).take(6) },
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
                OutlinedButton(onClick = { qty = ((count ?: 0) + 1).toString() }) { Text("+") }
            }

            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") },
                placeholder = { Text("drawer 3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add to the existing line", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (onHand > 0) "$onHand already on hand with this name, value and package." else "If one has this name, value and package.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = merge, onCheckedChange = { merge = it })
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    onAdd(
                        InventoryItem(
                            defId = defId,
                            name = name.trim(),
                            value = value.trim().ifEmpty { null },
                            pkg = pkg.trim().ifEmpty { null },
                            qty = count!!,
                            location = location.trim().ifEmpty { null },
                            notes = notes.trim().ifEmpty { null },
                        ),
                        merge,
                    )
                },
                enabled = valid && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "Adding…" else "Add ${count ?: ""} to inventory")
            }
        }
    }
}
