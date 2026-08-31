package es.voghdev.chromecastsample

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import es.voghdev.chromecastsample.ui.theme.ChromecastSampleTheme
import java.util.concurrent.Executors

private const val SAMPLE_VIDEO_URL =
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
private const val SAMPLE_VIDEO_TITLE = "Big Buck Bunny"

class MainActivity : FragmentActivity() {

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var castSession: CastSession? = null

    private var connectionState by mutableStateOf(CastConnectionState.DISCONNECTED)

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            connectionState = CastConnectionState.CONNECTING
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            connectionState = CastConnectionState.CONNECTED
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            castSession = null
            connectionState = CastConnectionState.DISCONNECTED
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            connectionState = CastConnectionState.CONNECTING
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            castSession = session
            connectionState = CastConnectionState.CONNECTED
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            castSession = null
            connectionState = CastConnectionState.DISCONNECTED
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            castSession = null
            connectionState = CastConnectionState.DISCONNECTED
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            connectionState = CastConnectionState.DISCONNECTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC

        CastContext.getSharedInstance(this, Executors.newSingleThreadExecutor())
            .addOnSuccessListener { context ->
                castContext = context
                sessionManager = context.sessionManager
                castSession = sessionManager?.currentCastSession
                if (castSession?.isConnected == true) {
                    connectionState = CastConnectionState.CONNECTED
                }
            }

        setContent {
            ChromecastSampleTheme {
                CastSampleScreen(
                    connectionState = connectionState,
                    onCastSampleClick = ::loadSampleMedia,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        castSession = sessionManager?.currentCastSession
        connectionState = if (castSession?.isConnected == true) {
            CastConnectionState.CONNECTED
        } else {
            CastConnectionState.DISCONNECTED
        }
    }

    override fun onPause() {
        super.onPause()
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }

    private fun loadSampleMedia() {
        val remoteMediaClient: RemoteMediaClient = castSession?.remoteMediaClient ?: return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, SAMPLE_VIDEO_TITLE)
        }
        val mediaInfo = MediaInfo.Builder(SAMPLE_VIDEO_URL)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(metadata)
            .build()

        val request: PendingResult<RemoteMediaClient.MediaChannelResult> =
            remoteMediaClient.load(
                com.google.android.gms.cast.MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .build()
            )
        // Fire-and-forget: request completion errors surface in the Cast framework logs.
        request.setResultCallback { }
    }
}

enum class CastConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastSampleScreen(
    connectionState: CastConnectionState,
    onCastSampleClick: () -> Unit,
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Status: ${connectionState.name.lowercase()}")
            Button(
                onClick = onCastSampleClick,
                enabled = connectionState == CastConnectionState.CONNECTED,
            ) {
                Text("Cast sample video")
            }
        }
    }
}

@Composable
private fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val themed = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)
            MediaRouteButton(themed).also { button ->
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, button)
            }
        },
    )
}
