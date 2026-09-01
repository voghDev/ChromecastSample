package es.voghdev.chromecastsample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import es.voghdev.chromecastsample.ui.theme.ChromecastSampleTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ChromecastSample"
private const val MAX_EVENTS = 200
private const val SEEK_DELTA_MS = 10_000L

private const val SAMPLE_VIDEO_URL =
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
private const val SAMPLE_VIDEO_TITLE = "Big Buck Bunny"

data class CastEvent(val timestamp: Long, val message: String)

class MainActivity : FragmentActivity() {

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var castSession: CastSession? = null

    private var connectionState by mutableStateOf(CastConnectionState.DISCONNECTED)
    private var playerState by mutableStateOf(MediaStatus.PLAYER_STATE_UNKNOWN)
    private val events: SnapshotStateList<CastEvent> = mutableStateListOf()

    private fun log(message: String) {
        Log.d(TAG, message)
        events.add(0, CastEvent(System.currentTimeMillis(), message))
        while (events.size > MAX_EVENTS) events.removeAt(events.size - 1)
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = castSession?.remoteMediaClient?.mediaStatus
            playerState = status?.playerState ?: MediaStatus.PLAYER_STATE_UNKNOWN
            if (status == null) {
                log("MEDIA onStatusUpdated: mediaStatus=null")
                return
            }
            log(
                "MEDIA onStatusUpdated: playerState=${playerStateName(status.playerState)}" +
                    " idleReason=${idleReasonName(status.idleReason)}",
            )
        }

        override fun onMediaError(mediaError: MediaError) {
            log(
                "MEDIA onMediaError: type=${mediaError.type}" +
                    " reason=${mediaError.reason}" +
                    " detailedErrorCode=${mediaError.detailedErrorCode}",
            )
        }

        override fun onMetadataUpdated() {
            log("MEDIA onMetadataUpdated")
        }

        override fun onQueueStatusUpdated() {
            log("MEDIA onQueueStatusUpdated")
        }

        override fun onPreloadStatusUpdated() {
            log("MEDIA onPreloadStatusUpdated")
        }

        override fun onSendingRemoteMediaRequest() {
            log("MEDIA onSendingRemoteMediaRequest")
        }

        override fun onAdBreakStatusUpdated() {
            log("MEDIA onAdBreakStatusUpdated")
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            log("SESSION starting → ${session.castDevice?.friendlyName ?: "?"}")
            connectionState = CastConnectionState.CONNECTING
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            log("SESSION started (sessionId=$sessionId)")
            castSession = session
            registerMediaCallback(session)
            connectionState = CastConnectionState.CONNECTED
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            log("SESSION start FAILED: ${castStatusName(error)} ($error)")
            castSession = null
            connectionState = CastConnectionState.DISCONNECTED
            playerState = MediaStatus.PLAYER_STATE_UNKNOWN
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            log("SESSION resuming (sessionId=$sessionId)")
            connectionState = CastConnectionState.CONNECTING
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            log("SESSION resumed (wasSuspended=$wasSuspended)")
            castSession = session
            registerMediaCallback(session)
            connectionState = CastConnectionState.CONNECTED
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            log("SESSION resume FAILED: ${castStatusName(error)} ($error)")
            castSession = null
            connectionState = CastConnectionState.DISCONNECTED
            playerState = MediaStatus.PLAYER_STATE_UNKNOWN
        }

        override fun onSessionEnding(session: CastSession) {
            log("SESSION ending")
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            log("SESSION ended: ${castStatusName(error)} ($error)")
            unregisterMediaCallback(session)
            castSession = null
            connectionState = CastConnectionState.DISCONNECTED
            playerState = MediaStatus.PLAYER_STATE_UNKNOWN
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            log("SESSION suspended: reason=$reason")
            unregisterMediaCallback(session)
            connectionState = CastConnectionState.DISCONNECTED
            playerState = MediaStatus.PLAYER_STATE_UNKNOWN
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC

        log("Activity onCreate")

        try {
            val context = CastContext.getSharedInstance(this)
            castContext = context
            sessionManager = context.sessionManager
            log("CastContext initialized")
        } catch (e: Exception) {
            log("CastContext INIT FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }

        setContent {
            ChromecastSampleTheme {
                CastSampleScreen(
                    connectionState = connectionState,
                    playerState = playerState,
                    events = events,
                    onStartCast = ::loadSampleMedia,
                    onPlay = ::playMedia,
                    onPause = ::pauseMedia,
                    onStop = ::stopMedia,
                    onRewind = { seekBy(-SEEK_DELTA_MS) },
                    onForward = { seekBy(+SEEK_DELTA_MS) },
                    onClearLog = { events.clear() },
                    onSaveLog = ::saveLogTo,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        val current = sessionManager?.currentCastSession
        castSession = current
        if (current?.isConnected == true) {
            registerMediaCallback(current)
            connectionState = CastConnectionState.CONNECTED
            playerState = current.remoteMediaClient?.mediaStatus?.playerState
                ?: MediaStatus.PLAYER_STATE_UNKNOWN
        } else {
            connectionState = CastConnectionState.DISCONNECTED
            playerState = MediaStatus.PLAYER_STATE_UNKNOWN
        }
    }

    override fun onPause() {
        super.onPause()
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        castSession?.let { unregisterMediaCallback(it) }
    }

    private fun registerMediaCallback(session: CastSession) {
        val client = session.remoteMediaClient
        if (client == null) {
            log("registerMediaCallback: remoteMediaClient=null")
            return
        }
        client.registerCallback(remoteMediaClientCallback)
    }

    private fun unregisterMediaCallback(session: CastSession) {
        session.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
    }

    private fun loadSampleMedia() {
        val remoteMediaClient = requireClient("Load") ?: return

        log("Requesting load: $SAMPLE_VIDEO_URL")

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, SAMPLE_VIDEO_TITLE)
        }
        val mediaInfo = MediaInfo.Builder(SAMPLE_VIDEO_URL)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(metadata)
            .build()

        val request = remoteMediaClient.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build(),
        )
        request.setResultCallback { logMediaResult("Load", it) }
    }

    private fun playMedia() {
        val client = requireClient("Play") ?: return
        log("Play requested")
        client.play().setResultCallback { logMediaResult("Play", it) }
    }

    private fun pauseMedia() {
        val client = requireClient("Pause") ?: return
        log("Pause requested")
        client.pause().setResultCallback { logMediaResult("Pause", it) }
    }

    private fun stopMedia() {
        val client = requireClient("Stop") ?: return
        log("Stop requested")
        client.stop().setResultCallback { logMediaResult("Stop", it) }
    }

    private fun seekBy(deltaMs: Long) {
        val client = requireClient("Seek") ?: return
        val current = client.approximateStreamPosition
        val target = (current + deltaMs).coerceAtLeast(0L)
        log("Seek requested: from=${current}ms delta=${deltaMs}ms → target=${target}ms")
        val options = MediaSeekOptions.Builder().setPosition(target).build()
        client.seek(options).setResultCallback { logMediaResult("Seek", it) }
    }

    private fun requireClient(op: String): RemoteMediaClient? {
        val session = castSession
        if (session == null) {
            log("$op aborted: castSession=null")
            return null
        }
        val client = session.remoteMediaClient
        if (client == null) {
            log("$op aborted: remoteMediaClient=null")
            return null
        }
        return client
    }

    private fun logMediaResult(op: String, result: RemoteMediaClient.MediaChannelResult) {
        val status = result.status
        if (status.isSuccess) {
            log("$op OK")
        } else {
            log(
                "$op FAILED: ${castStatusName(status.statusCode)} (${status.statusCode})" +
                    " msg=${status.statusMessage ?: "<none>"}",
            )
        }
    }

    private fun saveLogTo(uri: Uri) {
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val snapshot = events.toList().asReversed()
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter().use { writer ->
                    snapshot.forEach { event ->
                        writer.appendLine(
                            "${formatter.format(Date(event.timestamp))}  ${event.message}",
                        )
                    }
                }
            } ?: run {
                log("Log save FAILED: openOutputStream returned null for $uri")
                return
            }
            log("Log saved (${snapshot.size} entries) → $uri")
        } catch (e: Exception) {
            log("Log save FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

enum class CastConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

private fun castStatusName(code: Int): String =
    runCatching { CastStatusCodes.getStatusCodeString(code) }.getOrDefault("UNKNOWN($code)")

private fun playerStateName(state: Int): String = when (state) {
    MediaStatus.PLAYER_STATE_UNKNOWN -> "UNKNOWN"
    MediaStatus.PLAYER_STATE_IDLE -> "IDLE"
    MediaStatus.PLAYER_STATE_PLAYING -> "PLAYING"
    MediaStatus.PLAYER_STATE_PAUSED -> "PAUSED"
    MediaStatus.PLAYER_STATE_BUFFERING -> "BUFFERING"
    MediaStatus.PLAYER_STATE_LOADING -> "LOADING"
    else -> "state=$state"
}

private fun idleReasonName(reason: Int): String = when (reason) {
    MediaStatus.IDLE_REASON_NONE -> "NONE"
    MediaStatus.IDLE_REASON_FINISHED -> "FINISHED"
    MediaStatus.IDLE_REASON_CANCELED -> "CANCELED"
    MediaStatus.IDLE_REASON_INTERRUPTED -> "INTERRUPTED"
    MediaStatus.IDLE_REASON_ERROR -> "ERROR"
    else -> "reason=$reason"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastSampleScreen(
    connectionState: CastConnectionState,
    playerState: Int,
    events: List<CastEvent>,
    onStartCast: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onClearLog: () -> Unit,
    onSaveLog: (Uri) -> Unit,
) {
    var showLog by remember { mutableStateOf(false) }
    val connected = connectionState == CastConnectionState.CONNECTED
    val canControl = connected && playerState != MediaStatus.PLAYER_STATE_UNKNOWN

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Chromecast Sample") },
                actions = { CastButton() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = buildString {
                    append("Status: ${connectionState.name.lowercase()}")
                    if (playerState != MediaStatus.PLAYER_STATE_UNKNOWN) {
                        append(" · ")
                        append(playerStateName(playerState).lowercase())
                    }
                },
            )
            AnimatedButton(
                onClick = onStartCast,
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start cast")
            }
            PlaybackControls(
                enabled = canControl,
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
                onRewind = onRewind,
                onForward = onForward,
            )
            HorizontalDivider()
            AnimatedOutlinedButton(
                onClick = { showLog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log (${events.size})")
            }
        }
    }

    if (showLog) {
        LogBottomSheet(
            events = events,
            onDismiss = { showLog = false },
            onClear = onClearLog,
            onSaveLog = onSaveLog,
        )
    }
}

@Composable
private fun PlaybackControls(
    enabled: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        AnimatedOutlinedButton(onClick = onRewind, enabled = enabled) { Text("-10s") }
        AnimatedOutlinedButton(onClick = onPlay, enabled = enabled) { Text("Play") }
        AnimatedOutlinedButton(onClick = onPause, enabled = enabled) { Text("Pause") }
        AnimatedOutlinedButton(onClick = onStop, enabled = enabled) { Text("Stop") }
        AnimatedOutlinedButton(onClick = onForward, enabled = enabled) { Text("+10s") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogBottomSheet(
    events: List<CastEvent>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSaveLog: (Uri) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultFileName = remember {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        "chromecast-log-$ts.txt"
    }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let(onSaveLog) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Events (${events.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedOutlinedButton(
                        onClick = { saveLauncher.launch(defaultFileName) },
                        enabled = events.isNotEmpty(),
                    ) { Text("Save to file") }
                    AnimatedOutlinedButton(
                        onClick = onClear,
                        enabled = events.isNotEmpty(),
                    ) { Text("Clear") }
                }
            }
            Spacer(Modifier.height(12.dp))
            EventLog(
                events = events,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun EventLog(events: List<CastEvent>, modifier: Modifier = Modifier) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val listState = rememberLazyListState()

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.scrollToItem(0)
    }

    LazyColumn(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(8.dp),
        state = listState,
    ) {
        items(events) { event ->
            val timestamp = formatter.format(Date(event.timestamp))
            Text(
                text = "$timestamp  ${event.message}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private const val PRESSED_SCALE = 0.92f

@Composable
private fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        label = "buttonScale",
    )
    Button(
        onClick = onClick,
        modifier = modifier.scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun AnimatedOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        label = "outlinedButtonScale",
    )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CastButton(modifier: Modifier = Modifier) {
    var buttonRef by remember { mutableStateOf<MediaRouteButton?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // Open the chooser regardless of grant/deny — Cast can still discover on
        // most networks without NEARBY_WIFI_DEVICES; the permission just improves
        // discovery reliability on API 33+.
        buttonRef?.performClick()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val themed = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)
            MediaRouteButton(themed).also { button ->
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, button)
                buttonRef = button
                button.setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (needsNearbyWifiDevicesPermission(view.context)) {
                            permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                        } else {
                            button.performClick()
                        }
                    }
                    true
                }
            }
        },
    )
}

private fun needsNearbyWifiDevicesPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        ) != PackageManager.PERMISSION_GRANTED
