package es.voghdev.chromecastsample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

private const val SAMPLE_VIDEO_URL =
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
private const val SAMPLE_VIDEO_TITLE = "Big Buck Bunny"

data class CastEvent(val timestamp: Long, val message: String)

class MainActivity : FragmentActivity() {

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var castSession: CastSession? = null

    private var connectionState by mutableStateOf(CastConnectionState.DISCONNECTED)
    private val events: SnapshotStateList<CastEvent> = mutableStateListOf()

    private fun log(message: String) {
        Log.d(TAG, message)
        events.add(0, CastEvent(System.currentTimeMillis(), message))
        while (events.size > MAX_EVENTS) events.removeAt(events.size - 1)
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = castSession?.remoteMediaClient?.mediaStatus
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
        }

        override fun onSessionEnding(session: CastSession) {
            log("SESSION ending")
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            log("SESSION ended: ${castStatusName(error)} ($error)")
            unregisterMediaCallback(session)
            castSession = null
            connectionState = CastConnectionState.DISCONNECTED
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            log("SESSION suspended: reason=$reason")
            unregisterMediaCallback(session)
            connectionState = CastConnectionState.DISCONNECTED
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
                    events = events,
                    onCastSampleClick = ::loadSampleMedia,
                    onClearLog = { events.clear() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        val current = sessionManager?.currentCastSession
        castSession = current
        connectionState = if (current?.isConnected == true) {
            registerMediaCallback(current)
            CastConnectionState.CONNECTED
        } else {
            CastConnectionState.DISCONNECTED
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
        val session = castSession
        if (session == null) {
            log("Load aborted: castSession=null")
            return
        }
        val remoteMediaClient = session.remoteMediaClient
        if (remoteMediaClient == null) {
            log("Load aborted: remoteMediaClient=null")
            return
        }

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
        request.setResultCallback { result ->
            val status = result.status
            if (status.isSuccess) {
                log("Load OK")
            } else {
                log(
                    "Load FAILED: ${castStatusName(status.statusCode)} (${status.statusCode})" +
                        " msg=${status.statusMessage ?: "<none>"}",
                )
            }
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
    events: List<CastEvent>,
    onCastSampleClick: () -> Unit,
    onClearLog: () -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Status: ${connectionState.name.lowercase()}")
            Button(
                onClick = onCastSampleClick,
                enabled = connectionState == CastConnectionState.CONNECTED,
            ) {
                Text("Cast sample video")
            }
            HorizontalDivider()
            Row(events.size, onClearLog)
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
private fun Row(eventCount: Int, onClearLog: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Events ($eventCount)",
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedButton(onClick = onClearLog) { Text("Clear") }
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
