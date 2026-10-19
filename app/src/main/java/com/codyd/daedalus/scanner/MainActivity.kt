package com.codyd.daedalus.scanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyd.daedalus.scanner.ui.AddScreen
import com.codyd.daedalus.scanner.ui.PairScreen
import com.codyd.daedalus.scanner.ui.ScanScreen
import com.codyd.daedalus.scanner.ui.ScannerTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) intent.pairingLink()?.let(vm::handleLink)
        setContent { ScannerTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.pairingLink()?.let(vm::handleLink)
    }

    private fun Intent.pairingLink(): String? = data?.takeIf { action == Intent.ACTION_VIEW }?.let(Uri::toString)
}

@Composable
private fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (val screen = state.screen) {
                Screen.Pair -> PairScreen(state, onQr = vm::onPairQr, onPair = vm::pair)
                Screen.Scan -> ScanScreen(
                    state,
                    onCandidates = vm::onFrame,
                    onPick = vm::startAdd,
                    onProfile = vm::selectProfile,
                    onRefresh = vm::refresh,
                    onUnpair = vm::unpair,
                )
                is Screen.Add -> {
                    BackHandler(onBack = vm::backToScan)
                    AddScreen(state, screen.candidate, onBack = vm::backToScan, onAdd = vm::add)
                }
            }
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            )
        }
    }

    state.pendingLink?.let { link ->
        val custom = !link.apiUrl.equals(BuildConfig.DEFAULT_API_URL, ignoreCase = true)
        AlertDialog(
            onDismissRequest = { vm.answerLink(false) },
            title = { Text(if (state.session != null) "Pair with a different computer?" else "Pair with this computer?") },
            text = {
                Text(
                    buildString {
                        append("Parts you scan will go into the inventory of the computer that showed this code.")
                        if (state.session != null) append(" This replaces the current pairing.")
                        if (custom) append("\n\nServer: ${link.apiUrl}")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { vm.answerLink(true) }) { Text("Pair") } },
            dismissButton = { TextButton(onClick = { vm.answerLink(false) }) { Text("Cancel") } },
        )
    }
}
