package com.example.ui.session

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.example.R
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaType
import com.example.ui.components.CircularWhiteButton
import com.example.ui.components.GridHomeIcon
import com.example.ui.components.QuestionCardItem
import com.example.ui.components.StreetModeWindowEffect
import com.example.ui.components.StudioBackground
import com.example.ui.components.ViewerLockButton
import com.example.ui.components.YouTubeBadgeButton
import com.example.ui.components.YouTubePlaySymbol
import com.example.ui.components.performSubtleHaptic
import com.example.ui.theme.BlushWashLight
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.DisabledContent
import com.example.ui.theme.HairlineDivider
import com.example.ui.theme.InkDark
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PillInactiveGray
import com.example.ui.theme.RosePillBackground
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.util.Locale

private class VideoPauseHandle {
    private var activeToken: Any? = null
    private var pauseAction: (() -> Long)? = null

    fun register(token: Any, action: () -> Long) {
        activeToken = token
        pauseAction = action
    }

    fun unregister(token: Any) {
        if (activeToken === token) {
            activeToken = null
            pauseAction = null
        }
    }

    fun pauseAndGetPosition(): Long? = pauseAction?.invoke()
}

@Composable
fun InterviewSessionScreen(
    viewModel: InterviewSessionViewModel,
    onExitToHome: () -> Unit,
    onOpenEditor: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val volumeDownShortcutHost = LocalVolumeDownShortcutHost.current

    val lockedHint = stringResource(R.string.viewer_locked_hint)
    val unlockedHint = stringResource(R.string.viewer_unlocked_hint)
    val immersiveHint = stringResource(R.string.viewer_immersive_hint)

    var statusBannerText by remember { mutableStateOf<String?>(null) }
    var statusBannerNonce by remember { mutableIntStateOf(0) }

    fun showDiscreteBanner(message: String) {
        statusBannerText = message
        statusBannerNonce++
    }

    LaunchedEffect(statusBannerNonce) {
        if (statusBannerText != null) {
            delay(2_000L)
            statusBannerText = null
        }
    }

    // Fallback safely to Home if a restored interview no longer exists in Room
    LaunchedEffect(uiState.interviewNotFound, uiState.isLoading) {
        if (uiState.interviewNotFound && !uiState.isLoading) {
            viewModel.clearPersistedSession()
            onExitToHome()
        }
    }

    // Street Mode: keeps screen awake & applies temporary sun brightness strictly in foreground
    StreetModeWindowEffect(
        keepScreenOn = uiState.streetMode.enabled,
        boostBrightness = uiState.streetMode.boostBrightness
    )

    // Non-reactive snapshot of current video position & pause handle for instant mode toggles
    var liveVideoPosMs by remember(uiState.activeInterview?.id) {
        mutableLongStateOf(uiState.savedVideoPositionMs)
    }
    val videoPauseHandle = remember { VideoPauseHandle() }

    fun toggleSessionMode(bypassViewerLock: Boolean): Boolean {
        if (uiState.mode == SessionMode.VIEWER && uiState.isLocked && !bypassViewerLock) {
            return false
        }
        val currentVideoPos = videoPauseHandle.pauseAndGetPosition() ?: liveVideoPosMs
        val toggled = viewModel.toggleMode(
            currentVideoPositionMs = currentVideoPos,
            bypassViewerLock = bypassViewerLock
        )
        if (toggled) {
            performSubtleHaptic(view)
        }
        return toggled
    }

    val shortcutToken = remember { Any() }
    val latestCanHandleShortcut by rememberUpdatedState {
        uiState.activeInterview != null && !uiState.interviewNotFound
    }
    val latestOnVolumeDownToggle by rememberUpdatedState {
        toggleSessionMode(bypassViewerLock = true)
    }

    DisposableEffect(lifecycleOwner, volumeDownShortcutHost, shortcutToken) {
        fun registerShortcut() {
            volumeDownShortcutHost.register(
                token = shortcutToken,
                isActive = { latestCanHandleShortcut() },
                onToggle = { latestOnVolumeDownToggle() }
            )
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerShortcut()
        }

        val shortcutObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerShortcut()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> volumeDownShortcutHost.unregister(shortcutToken)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(shortcutObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(shortcutObserver)
            volumeDownShortcutHost.unregister(shortcutToken)
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.persistSessionSnapshot(overrideVideoPositionMs = liveVideoPosMs)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Consistent system Back behavior across Locked / Interview / Immersive / Viewer states
    BackHandler {
        when {
            uiState.mode == SessionMode.VIEWER && uiState.isLocked -> showDiscreteBanner(lockedHint)
            uiState.mode == SessionMode.INTERVIEW -> {
                toggleSessionMode(bypassViewerLock = false)
            }
            uiState.isImmersive -> viewModel.setImmersive(false)
            else -> {
                viewModel.clearPersistedSession()
                onExitToHome()
            }
        }
    }

    // Controlled adjacent photo preloading with bounded size for 2 GB RAM devices
    LaunchedEffect(uiState.adjacentImagePaths) {
        uiState.adjacentImagePaths.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                val req = ImageRequest.Builder(context)
                    .data(file)
                    .size(720, 720)
                    .scale(Scale.FIT)
                    .allowHardware(true)
                    .build()
                context.imageLoader.enqueue(req)
            }
        }
    }

    StudioBackground(showTopRightWave = true, showBottomWave = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = uiState.mode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(70)) togetherWith fadeOut(animationSpec = tween(50))
                },
                label = "sessionModeTransition"
            ) { mode ->
                when (mode) {
                    SessionMode.VIEWER -> {
                        ViewerPane(
                            uiState = uiState,
                            isViewerActive = uiState.mode == SessionMode.VIEWER,
                            videoPauseHandle = videoPauseHandle,
                            onBackToHome = {
                                viewModel.clearPersistedSession()
                                onExitToHome()
                            },
                            onEnterInterviewMode = {
                                toggleSessionMode(bypassViewerLock = false)
                            },
                            onSaveVideoPosition = { id, pos ->
                                liveVideoPosMs = pos
                                viewModel.saveVideoPosition(id, pos)
                            },
                            onSelectMediaType = viewModel::selectMediaType,
                            onPrevious = viewModel::goToPrevious,
                            onNext = viewModel::goToNext,
                            onOpenEditor = onOpenEditor,
                            onLock = {
                                performSubtleHaptic(view)
                                viewModel.lockViewer()
                                showDiscreteBanner(lockedHint)
                            },
                            onUnlock = {
                                performSubtleHaptic(view)
                                viewModel.unlockViewer()
                                showDiscreteBanner(unlockedHint)
                            },
                            onShortTapWhileLocked = {
                                showDiscreteBanner(lockedHint)
                            },
                            onToggleImmersive = {
                                val nextImmersive = !uiState.isImmersive
                                viewModel.setImmersive(nextImmersive)
                                if (nextImmersive) {
                                    showDiscreteBanner(immersiveHint)
                                } else {
                                    statusBannerText = null
                                }
                            },
                            onSetImmersive = { immersive ->
                                viewModel.setImmersive(immersive)
                                if (immersive) {
                                    showDiscreteBanner(immersiveHint)
                                } else {
                                    statusBannerText = null
                                }
                            }
                        )
                    }

                    SessionMode.INTERVIEW -> {
                        InterviewModePane(
                            uiState = uiState,
                            getInitialScroll = viewModel::getQuestionScrollPosition,
                            onSaveScroll = viewModel::saveQuestionScrollPosition,
                            onPersistScrollOnBackground = { idx, offset ->
                                viewModel.persistSessionSnapshot(
                                    overrideQuestionIndex = idx,
                                    overrideQuestionOffset = offset
                                )
                            },
                            onReturnToViewer = {
                                toggleSessionMode(bypassViewerLock = false)
                            },
                            onGoHome = {
                                viewModel.clearPersistedSession()
                                onExitToHome()
                            },
                            onPrevious = viewModel::goToPrevious,
                            onNext = viewModel::goToNext,
                            onOpenEditor = onOpenEditor
                        )
                    }
                }
            }

            // Discrete auto-hiding status pill (Lock / Unlock / Immersive hint)
            AnimatedVisibility(
                visible = statusBannerText != null && uiState.mode == SessionMode.VIEWER,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(220)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Surface(
                    modifier = Modifier.testTag("viewer_status_pill"),
                    shape = RoundedCornerShape(50),
                    color = InkDark.copy(alpha = 0.88f),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = statusBannerText.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerPane(
    uiState: InterviewSessionUiState,
    isViewerActive: Boolean,
    videoPauseHandle: VideoPauseHandle,
    onBackToHome: () -> Unit,
    onEnterInterviewMode: () -> Unit,
    onSaveVideoPosition: (Long, Long) -> Unit,
    onSelectMediaType: (MediaType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenEditor: (Long) -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onShortTapWhileLocked: () -> Unit,
    onToggleImmersive: () -> Unit,
    onSetImmersive: (Boolean) -> Unit
) {
    val outerHorizontalPadding by animateDpAsState(
        targetValue = if (uiState.isImmersive) 6.dp else 14.dp,
        animationSpec = tween(160),
        label = "viewerHPadding"
    )
    val outerVerticalPadding by animateDpAsState(
        targetValue = if (uiState.isImmersive) 6.dp else 10.dp,
        animationSpec = tween(160),
        label = "viewerVPadding"
    )

    val backgroundTapModifier = if (uiState.isImmersive && !uiState.isLocked) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            onSetImmersive(false)
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .then(backgroundTapModifier)
            .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: Left secondary controls (Back + Lock) hide smoothly in immersive mode;
        // Top-right YouTube button stays ALWAYS accessible even in immersive mode.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (uiState.isImmersive) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = !uiState.isImmersive || uiState.isLocked,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(120))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularWhiteButton(
                        onClick = onBackToHome,
                        enabled = !uiState.isLocked,
                        contentDescription = stringResource(R.string.cd_back),
                        modifier = Modifier.testTag("viewer_back_button")
                    )
                    ViewerLockButton(
                        isLocked = uiState.isLocked,
                        onLock = onLock,
                        onUnlock = onUnlock,
                        onShortTapWhileLocked = onShortTapWhileLocked,
                        modifier = Modifier.testTag("viewer_lock_button")
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            YouTubeBadgeButton(
                onClick = onEnterInterviewMode,
                enabled = !uiState.isLocked,
                contentDescription = stringResource(R.string.cd_enter_interview_mode),
                modifier = Modifier.testTag("enter_interview_mode_button")
            )
        }

        AnimatedVisibility(
            visible = !uiState.isImmersive && uiState.totalCount > 0,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
        ) {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                CounterIndicator(
                    current = uiState.activeIndex + 1,
                    total = uiState.totalCount,
                    modifier = Modifier.testTag("viewer_counter")
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (uiState.isImmersive) {
            Spacer(modifier = Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val interview = uiState.activeInterview
            if (interview != null) {
                when {
                    uiState.selectedMediaType == MediaType.VIDEO && interview.videoMedia != null -> {
                        LocalVideoProtagonist(
                            media = interview.videoMedia,
                            initialPositionMs = uiState.savedVideoPositionMs,
                            isImmersive = uiState.isImmersive,
                            isLocked = uiState.isLocked,
                            isViewerActive = isViewerActive,
                            videoPauseHandle = videoPauseHandle,
                            onPositionUpdated = { pos ->
                                onSaveVideoPosition(interview.id, pos)
                            },
                            onToggleImmersive = onToggleImmersive,
                            onEditClick = { onOpenEditor(interview.id) }
                        )
                    }

                    interview.imageMedia != null -> {
                        LocalImageProtagonist(
                            media = interview.imageMedia,
                            isLocked = uiState.isLocked,
                            onTapMedia = onToggleImmersive,
                            onEditClick = { onOpenEditor(interview.id) }
                        )
                    }

                    interview.videoMedia != null -> {
                        LocalVideoProtagonist(
                            media = interview.videoMedia,
                            initialPositionMs = uiState.savedVideoPositionMs,
                            isImmersive = uiState.isImmersive,
                            isLocked = uiState.isLocked,
                            isViewerActive = isViewerActive,
                            videoPauseHandle = videoPauseHandle,
                            onPositionUpdated = { pos ->
                                onSaveVideoPosition(interview.id, pos)
                            },
                            onToggleImmersive = onToggleImmersive,
                            onEditClick = { onOpenEditor(interview.id) }
                        )
                    }

                    else -> {
                        EmptyMediaState(
                            editEnabled = !uiState.isLocked,
                            onAddMediaClick = { onOpenEditor(interview.id) }
                        )
                    }
                }
            }

            // Shield over media area while viewer lock is active: prevents accidental pauses,
            // seeks, immersive toggles, or editor jumps while keeping content fully visible.
            if (uiState.isLocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .testTag("viewer_locked_media_shield")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onShortTapWhileLocked()
                        }
                )
            }
        }

        AnimatedVisibility(
            visible = !uiState.isImmersive,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                val activeInterview = uiState.activeInterview
                val hasImage = activeInterview?.imageMedia != null
                val hasVideo = activeInterview?.videoMedia != null

                MediaTypeSwitcherPill(
                    selectedType = uiState.selectedMediaType,
                    hasImage = hasImage,
                    hasVideo = hasVideo,
                    enabled = !uiState.isLocked,
                    onSelectType = onSelectMediaType
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DiscreteNavPillButton(
                        text = stringResource(R.string.nav_previous),
                        enabled = !uiState.isLocked && uiState.hasPrevious,
                        isLeadingIcon = true,
                        onClick = onPrevious,
                        modifier = Modifier.testTag("viewer_prev_button")
                    )

                    DiscreteNavPillButton(
                        text = stringResource(R.string.nav_next),
                        enabled = !uiState.isLocked && uiState.hasNext,
                        isLeadingIcon = false,
                        onClick = onNext,
                        modifier = Modifier.testTag("viewer_next_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun InterviewModePane(
    uiState: InterviewSessionUiState,
    getInitialScroll: (Long) -> Pair<Int, Int>,
    onSaveScroll: (Long, Int, Int) -> Unit,
    onPersistScrollOnBackground: (Int, Int) -> Unit,
    onReturnToViewer: () -> Unit,
    onGoHome: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenEditor: (Long) -> Unit
) {
    val activeInterview = uiState.activeInterview
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Top Bar: Back to Viewer, Interview Title, Red YouTube button (returns to Viewer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularWhiteButton(
                    onClick = onReturnToViewer,
                    contentDescription = stringResource(R.string.cd_return_to_viewer),
                    modifier = Modifier.testTag("interview_mode_back_button")
                )

                val rawTitle = activeInterview?.title.orEmpty()
                Text(
                    text = formatStyledTitle(rawTitle),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("interview_mode_title"),
                    textAlign = TextAlign.Center,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                YouTubeBadgeButton(
                    onClick = onReturnToViewer,
                    contentDescription = stringResource(R.string.cd_return_to_viewer),
                    modifier = Modifier.testTag("interview_mode_youtube_button")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Clickable "Modo entrevista" pill header: tapping returns to Viewer as well
            InterviewModePillHeader(
                onClick = onReturnToViewer
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeInterview != null) {
                    val questions = activeInterview.questions
                    if (questions.isEmpty()) {
                        EmptyQuestionsState(
                            onAddQuestionsClick = { onOpenEditor(activeInterview.id) },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val (initialIdx, initialOffset) = remember(activeInterview.id) {
                            getInitialScroll(activeInterview.id)
                        }
                        val listState = rememberLazyListState(
                            initialFirstVisibleItemIndex = initialIdx,
                            initialFirstVisibleItemScrollOffset = initialOffset
                        )

                        LaunchedEffect(activeInterview.id, listState) {
                            snapshotFlow {
                                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                            }
                                .distinctUntilChanged()
                                .collect { (idx, offset) ->
                                    onSaveScroll(activeInterview.id, idx, offset)
                                }
                        }

                        DisposableEffect(activeInterview.id, listState, lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                                    onPersistScrollOnBackground(
                                        listState.firstVisibleItemIndex,
                                        listState.firstVisibleItemScrollOffset
                                    )
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                                onSaveScroll(
                                    activeInterview.id,
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("interview_questions_list"),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(
                                items = questions,
                                key = { _, q -> q.id }
                            ) { index, question ->
                                QuestionCardItem(
                                    number = index + 1,
                                    question = question
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom bar: Anterior | Grid Home | Siguiente
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InterviewPreviousPill(
                    enabled = uiState.hasPrevious,
                    onClick = onPrevious,
                    modifier = Modifier.testTag("interview_prev_button")
                )

                Surface(
                    onClick = onGoHome,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(54.dp)
                        .testTag("interview_grid_home_button"),
                    shape = CircleShape,
                    color = StudioWhite,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, CardBorderSoft)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        GridHomeIcon(tint = InkDark)
                    }
                }

                InterviewNextPill(
                    enabled = uiState.hasNext,
                    onClick = onNext,
                    modifier = Modifier.testTag("interview_next_button")
                )
            }
        }
    }
}

@Composable
private fun InterviewModePillHeader(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, CrimsonPrimary.copy(alpha = 0.7f))
                    )
                )
        )

        Surface(
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .padding(horizontal = 10.dp)
                .testTag("interview_mode_header_button"),
            shape = RoundedCornerShape(50),
            color = StudioWhite,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, RosePillBackground)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YouTubePlaySymbol(
                    modifier = Modifier
                        .width(22.dp)
                        .height(15.dp)
                )
                Text(
                    text = stringResource(R.string.interview_mode_badge),
                    style = MaterialTheme.typography.titleSmall,
                    color = InkDark,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(CrimsonPrimary.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun CounterIndicator(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(HairlineDivider.copy(alpha = 0.85f))
        )
        Text(
            text = stringResource(R.string.viewer_counter_format, current, total),
            style = MaterialTheme.typography.labelLarge,
            color = SlateMuted.copy(alpha = 0.78f),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(HairlineDivider.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun LocalImageProtagonist(
    media: MediaItem,
    isLocked: Boolean,
    onTapMedia: () -> Unit,
    onEditClick: () -> Unit
) {
    val file = remember(media.localPath) { File(media.localPath) }
    val exists = remember(media.localPath) { file.exists() && file.length() > 0L }

    if (!exists) {
        MissingMediaCard(
            message = stringResource(R.string.error_media_missing),
            editEnabled = !isLocked,
            onEditClick = onEditClick
        )
        return
    }

    var measuredRatio by remember(media.localPath) {
        mutableFloatStateOf(media.aspectRatio.coerceIn(0.25f, 4.0f))
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val (boxW, boxH) = fitDimensions(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            aspectRatio = measuredRatio
        )

        Surface(
            modifier = Modifier
                .size(width = boxW, height = boxH)
                .testTag("viewer_image_container")
                .clickable(
                    enabled = !isLocked,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTapMedia
                ),
            shape = RoundedCornerShape(22.dp),
            color = StudioWhite,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, CardBorderSoft)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .allowHardware(true)
                    .crossfade(140)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { result ->
                    val drawable = result.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        measuredRatio = (drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat())
                            .coerceIn(0.25f, 4.0f)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LocalVideoProtagonist(
    media: MediaItem,
    initialPositionMs: Long,
    isImmersive: Boolean,
    isLocked: Boolean,
    isViewerActive: Boolean,
    videoPauseHandle: VideoPauseHandle,
    onPositionUpdated: (Long) -> Unit,
    onToggleImmersive: () -> Unit,
    onEditClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val file = remember(media.localPath) { File(media.localPath) }
    val exists = remember(media.localPath) { file.exists() && file.length() > 0L }

    if (!exists) {
        MissingMediaCard(
            message = stringResource(R.string.error_media_missing),
            editEnabled = !isLocked,
            onEditClick = onEditClick
        )
        return
    }

    var videoAspectRatio by remember(media.localPath) {
        mutableFloatStateOf(media.aspectRatio.coerceIn(0.25f, 4.0f))
    }
    var isPlaying by remember(media.localPath) { mutableStateOf(false) }
    var showControls by remember(media.localPath) { mutableStateOf(true) }
    var positionMs by remember(media.localPath) { mutableLongStateOf(initialPositionMs.coerceAtLeast(0L)) }
    var durationMs by remember(media.localPath) { mutableLongStateOf(media.durationMs ?: 0L) }
    var playbackError by remember(media.localPath) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(media.localPath) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            setMediaItem(Media3Item.fromUri(Uri.fromFile(file)))
            if (initialPositionMs > 0L) {
                seekTo(initialPositionMs)
            }
            prepare()
        }
    }

    val pauseHandleToken = remember(exoPlayer) { Any() }

    LaunchedEffect(exoPlayer, isViewerActive) {
        if (!isViewerActive) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                exoPlayer.playWhenReady = false
                exoPlayer.pause()
            }
            positionMs = pos
            onPositionUpdated(pos)
        }
    }

    DisposableEffect(exoPlayer, videoPauseHandle, pauseHandleToken) {
        videoPauseHandle.register(pauseHandleToken) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                exoPlayer.playWhenReady = false
                exoPlayer.pause()
            }
            positionMs = pos
            onPositionUpdated(pos)
            pos
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    positionMs = pos
                    onPositionUpdated(pos)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                    videoAspectRatio = ratio.coerceIn(0.25f, 4.0f)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    showControls = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = context.getString(R.string.error_video_playback)
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    onPositionUpdated(pos)
                    exoPlayer.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            videoPauseHandle.unregister(pauseHandleToken)
            val finalPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            onPositionUpdated(finalPos)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Performance optimization: while playing with overlay hidden, update only the
    // non-reactive ViewModel position cache so Compose skips slider recompositions.
    LaunchedEffect(exoPlayer, isPlaying, showControls) {
        if (isPlaying && showControls) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            positionMs = pos
            if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            onPositionUpdated(pos)
        }
        while (isPlaying) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (showControls) {
                positionMs = pos
                if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            }
            onPositionUpdated(pos)
            delay(250L)
        }
    }

    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            delay(3_000L)
            showControls = false
        }
    }

    if (playbackError != null) {
        MissingMediaCard(
            message = playbackError!!,
            editEnabled = !isLocked,
            onEditClick = onEditClick
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val (boxW, boxH) = fitDimensions(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            aspectRatio = videoAspectRatio
        )

        Surface(
            modifier = Modifier
                .size(width = boxW, height = boxH)
                .testTag("viewer_video_container"),
            shape = RoundedCornerShape(22.dp),
            color = Color.Black,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, CardBorderSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(
                        enabled = !isLocked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls = !showControls
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = exoPlayer
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                AnimatedVisibility(
                    visible = (showControls || !isPlaying) && !isLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f))
                    ) {
                        // Discrete immersive toggle button in video overlay
                        Surface(
                            onClick = onToggleImmersive,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .minimumInteractiveComponentSize()
                                .size(44.dp)
                                .testTag("video_immersive_toggle_button"),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isImmersive) {
                                        Icons.Rounded.FullscreenExit
                                    } else {
                                        Icons.Rounded.Fullscreen
                                    },
                                    contentDescription = if (isImmersive) {
                                        stringResource(R.string.cd_exit_immersive)
                                    } else {
                                        stringResource(R.string.cd_enter_immersive)
                                    },
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                    exoPlayer.seekTo(0L)
                                    exoPlayer.play()
                                } else if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .testTag("video_play_pause_button"),
                            shape = CircleShape,
                            color = CrimsonPrimary.copy(alpha = 0.94f),
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play_pause),
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        if (durationMs > 0L) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Slider(
                                    value = positionMs.coerceIn(0L, durationMs).toFloat(),
                                    onValueChange = { newValue ->
                                        positionMs = newValue.toLong()
                                    },
                                    onValueChangeFinished = {
                                        exoPlayer.seekTo(positionMs)
                                        onPositionUpdated(positionMs)
                                    },
                                    valueRange = 0f..durationMs.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = CrimsonPrimary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatDuration(positionMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = formatDuration(durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTypeSwitcherPill(
    selectedType: MediaType,
    hasImage: Boolean,
    hasVideo: Boolean,
    enabled: Boolean,
    onSelectType: (MediaType) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = StudioWhite,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageSelected = selectedType == MediaType.IMAGE && hasImage
            val videoSelected = selectedType == MediaType.VIDEO && hasVideo

            Surface(
                onClick = { if (hasImage) onSelectType(MediaType.IMAGE) },
                enabled = enabled && hasImage,
                shape = RoundedCornerShape(50),
                color = if (imageSelected) CrimsonPrimary else PillInactiveGray.copy(alpha = 0.55f),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .height(44.dp)
                    .width(74.dp)
                    .testTag("switch_to_photo_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = stringResource(R.string.cd_photo_tab),
                        tint = when {
                            imageSelected -> Color.White
                            enabled && hasImage -> InkDark
                            else -> DisabledContent
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Surface(
                onClick = { if (hasVideo) onSelectType(MediaType.VIDEO) },
                enabled = enabled && hasVideo,
                shape = RoundedCornerShape(50),
                color = if (videoSelected) CrimsonPrimary else PillInactiveGray.copy(alpha = 0.55f),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .height(44.dp)
                    .width(74.dp)
                    .testTag("switch_to_video_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = stringResource(R.string.cd_video_tab),
                        tint = when {
                            videoSelected -> Color.White
                            enabled && hasVideo -> InkDark
                            else -> DisabledContent
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscreteNavPillButton(
    text: String,
    enabled: Boolean,
    isLeadingIcon: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(46.dp),
        shape = RoundedCornerShape(50),
        color = if (enabled) StudioWhite else StudioWhite.copy(alpha = 0.65f),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        border = BorderStroke(1.dp, if (enabled) CardBorderSoft else HairlineDivider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val contentColor = if (enabled) InkDark else DisabledContent
            if (isLeadingIcon) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            if (!isLeadingIcon) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun InterviewPreviousPill(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(50.dp),
        shape = RoundedCornerShape(50),
        color = if (enabled) StudioWhite else StudioWhite.copy(alpha = 0.65f),
        shadowElevation = if (enabled) 5.dp else 0.dp,
        border = BorderStroke(1.dp, if (enabled) CardBorderSoft else HairlineDivider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val contentColor = if (enabled) InkDark else DisabledContent
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(R.string.nav_previous),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

@Composable
private fun InterviewNextPill(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBrush = if (enabled) {
        Brush.horizontalGradient(listOf(CrimsonBright, CrimsonDark))
    } else {
        Brush.horizontalGradient(listOf(HairlineDivider, HairlineDivider))
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(50.dp),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        shadowElevation = if (enabled) 6.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(containerBrush, RoundedCornerShape(50))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val contentColor = if (enabled) Color.White else SlateMuted.copy(alpha = 0.55f)
                Text(
                    text = stringResource(R.string.nav_next),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyMediaState(
    editEnabled: Boolean,
    onAddMediaClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = StudioWhite,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BlushWashLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PermMedia,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "Sin fotografía ni video",
                style = MaterialTheme.typography.titleMedium,
                color = InkDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Agrega una imagen o video desde el editor para mostrarlo al entrevistado.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddMediaClick,
                enabled = editEnabled,
                modifier = Modifier.testTag("empty_media_add_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Agregar en el editor")
            }
        }
    }
}

@Composable
private fun EmptyQuestionsState(
    onAddQuestionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = StudioWhite,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(
            modifier = Modifier.padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(BlushWashLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.QuestionAnswer,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Sin preguntas todavía",
                style = MaterialTheme.typography.titleLarge,
                color = InkDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Agrega tus preguntas privadas desde el editor para consultarlas aquí al entrevistar.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddQuestionsClick,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agregar preguntas")
            }
        }
    }
}

@Composable
private fun MissingMediaCard(
    message: String,
    editEnabled: Boolean,
    onEditClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(22.dp),
        color = StudioWhite,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = CrimsonPrimary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = InkDark,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onEditClick,
                enabled = editEnabled,
                modifier = Modifier.testTag("missing_media_edit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "Reemplazar en el editor")
            }
        }
    }
}

private fun formatStyledTitle(title: String) = buildAnnotatedString {
    val colonIndex = title.indexOf(':')
    if (colonIndex in 1 until title.length - 1) {
        withStyle(SpanStyle(color = InkDark)) {
            append(title.substring(0, colonIndex + 1))
        }
        withStyle(SpanStyle(color = CrimsonPrimary)) {
            append(title.substring(colonIndex + 1))
        }
    } else {
        withStyle(SpanStyle(color = InkDark)) {
            append(title)
        }
    }
}

private fun fitDimensions(
    maxWidth: Dp,
    maxHeight: Dp,
    aspectRatio: Float
): Pair<Dp, Dp> {
    val safeRatio = aspectRatio.coerceIn(0.25f, 4.0f)
    val maxW = maxWidth.value
    val maxH = maxHeight.value
    return if (maxW / maxH > safeRatio) {
        (maxH * safeRatio).dp to maxH.dp
    } else {
        maxW.dp to (maxW / safeRatio).dp
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
