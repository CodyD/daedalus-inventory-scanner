package com.codyd.daedalus.scanner

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codyd.daedalus.scanner.data.ApiClient
import com.codyd.daedalus.scanner.data.ApiException
import com.codyd.daedalus.scanner.data.InventoryItem
import com.codyd.daedalus.scanner.data.Library
import com.codyd.daedalus.scanner.data.PairingLink
import com.codyd.daedalus.scanner.data.ProfileSummary
import com.codyd.daedalus.scanner.data.Session
import com.codyd.daedalus.scanner.data.SessionStore
import com.codyd.daedalus.scanner.scan.Candidate
import com.codyd.daedalus.scanner.scan.CandidateTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface Screen {
    data object Pair : Screen
    data object Scan : Screen
    /** Reviewing a part before adding it; [candidate] null = typed in by hand. */
    data class Add(val candidate: Candidate?) : Screen
}

data class UiState(
    val library: Library,
    val session: Session? = null,
    val screen: Screen = Screen.Pair,
    val profiles: List<ProfileSummary> = emptyList(),
    val profileId: String = DEFAULT_PROFILE_ID,
    /** The selected profile's inventory, to show what is already on hand. */
    val inventory: List<InventoryItem> = emptyList(),
    val candidates: List<Candidate> = emptyList(),
    /** A pairing link opened from outside the app, waiting for the user to confirm it. */
    val pendingLink: PairingLink? = null,
    val busy: Boolean = false,
    /** Shown in a snackbar. */
    val message: String? = null,
    /** Shown inline on the pair and add screens. */
    val error: String? = null,
    val lastLocation: String? = null,
) {
    val profileName get() = profiles.firstOrNull { it.id == profileId }?.name ?: "Default"

    /** How many of this part (same name, value and package, as the API merges them) are on hand. */
    fun onHand(name: String, value: String?, pkg: String?): Int = inventory
        .filter {
            it.name.trim().equals(name.trim(), true) &&
                it.value.orEmpty().equals(value.orEmpty(), true) &&
                it.pkg.orEmpty().equals(pkg.orEmpty(), true)
        }
        .sumOf { it.qty }
}

const val DEFAULT_PROFILE_ID = "default"

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val builtin = Library.fromJson(app.assets.open("library.json").bufferedReader().use { it.readText() })
    private val tracker = CandidateTracker()
    /** True from the moment a pairing code is sent, so repeated QR frames do not redeem it twice. */
    private var pairing = false

    private val _state = MutableStateFlow(UiState(library = builtin, lastLocation = store.lastLocation))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        store.load()?.let { s ->
            _state.update { it.copy(session = s, screen = Screen.Scan, profileId = store.profileId ?: DEFAULT_PROFILE_ID) }
            refresh()
        }
    }

    private fun api(): ApiClient? = state.value.session?.let { ApiClient(it.apiUrl, it.token) }

    /** Runs [block] against the API, turning failures into messages; a revoked token goes back to pairing. */
    private fun launchApi(onError: (String) -> Unit = ::showMessage, block: suspend (ApiClient) -> Unit) {
        val api = api() ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block(api)
            } catch (e: ApiException) {
                if (e.status == 401) signedOut(error = "This phone was unpaired from the computer. Pair it again to keep scanning.")
                else onError(e.message ?: "Daedalus refused that (${e.status}).")
            } catch (e: IOException) {
                onError("Can't reach Daedalus: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    // --- pairing --------------------------------------------------------------

    fun pair(apiUrl: String, code: String) {
        if (pairing) return
        if (!PairingLink.isAllowedApi(apiUrl, BuildConfig.DEBUG)) {
            _state.update { it.copy(error = "The server address must start with https://") }
            return
        }
        pairing = true
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val r = ApiClient(apiUrl).pair(code, deviceLabel())
                val session = Session(apiUrl.trimEnd('/'), r.deviceId, r.linkedId, r.token)
                store.save(session)
                tracker.clear()
                _state.update {
                    it.copy(session = session, screen = Screen.Scan, profileId = DEFAULT_PROFILE_ID, message = "Paired. Point the camera at a part.")
                }
                refresh()
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: IOException) {
                _state.update { it.copy(error = "Can't reach ${apiUrl}: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                pairing = false
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** A QR read by the pair screen's camera: the user is already pairing, so go ahead. */
    fun onPairQr(raw: String) {
        val link = PairingLink.parse(raw, BuildConfig.DEFAULT_API_URL, BuildConfig.DEBUG) ?: return
        pair(link.apiUrl, link.code)
    }

    /** A daedalus://pair link opened from another app (a camera app's QR result): ask first. */
    fun handleLink(raw: String) {
        val link = PairingLink.parse(raw, BuildConfig.DEFAULT_API_URL, BuildConfig.DEBUG)
        if (link == null) {
            showMessage("That pairing link isn't valid.")
            return
        }
        _state.update { it.copy(pendingLink = link) }
    }

    fun answerLink(accept: Boolean) {
        val link = state.value.pendingLink ?: return
        _state.update { it.copy(pendingLink = null) }
        if (accept) pair(link.apiUrl, link.code)
    }

    fun unpair() {
        val s = state.value.session ?: return
        // Best effort: revoke the token on the server, but forget it here either way.
        viewModelScope.launch { runCatching { ApiClient(s.apiUrl, s.token).unpair(s.linkedId) } }
        signedOut(message = "Unpaired.")
    }

    /** Forgets the session and goes back to pairing, with an inline [error] or a snackbar [message]. */
    private fun signedOut(error: String? = null, message: String? = null) {
        store.clear()
        tracker.clear()
        _state.update { UiState(library = builtin, lastLocation = it.lastLocation, error = error, message = message) }
    }

    // --- inventory ------------------------------------------------------------

    /** Profiles, the computer's imported parts, and the selected profile's inventory. */
    fun refresh() = launchApi { api ->
        val profiles = api.profiles()
        val profileId = state.value.profileId.takeIf { id -> profiles.any { it.id == id } } ?: DEFAULT_PROFILE_ID
        val library = builtin + api.parts()
        val inventory = api.inventory(profileId)
        _state.update { it.copy(profiles = profiles, profileId = profileId, library = library, inventory = inventory) }
    }

    fun selectProfile(id: String) {
        store.profileId = id
        _state.update { it.copy(profileId = id, inventory = emptyList()) }
        refresh()
    }

    fun add(item: InventoryItem, merge: Boolean) {
        _state.update { it.copy(error = null) }
        launchApi(onError = { msg -> _state.update { it.copy(error = msg) } }) { api ->
            val profileId = state.value.profileId
            val r = api.addItem(profileId, item, merge)
            store.lastLocation = item.location
            val inventory = runCatching { api.inventory(profileId) }.getOrDefault(state.value.inventory)
            val total = if (r.merged) " (now ${r.item.qty})" else ""
            tracker.clear()
            _state.update {
                it.copy(
                    screen = Screen.Scan,
                    candidates = emptyList(),
                    inventory = inventory,
                    lastLocation = item.location,
                    message = "Added ${item.qty} × ${item.name} to ${it.profileName}$total",
                )
            }
        }
    }

    // --- scanning -------------------------------------------------------------

    fun onFrame(candidates: List<Candidate>) {
        if (state.value.screen != Screen.Scan) return
        _state.update { it.copy(candidates = tracker.update(candidates)) }
    }

    fun startAdd(candidate: Candidate?) = _state.update { it.copy(screen = Screen.Add(candidate), error = null) }

    fun backToScan() {
        tracker.clear()
        _state.update { it.copy(screen = Screen.Scan, candidates = emptyList(), error = null) }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun deviceLabel(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return if (Build.MODEL.startsWith(maker, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
    }
}
