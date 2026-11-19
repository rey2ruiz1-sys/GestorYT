package com.example.ui.editor

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.domain.model.MediaItem
import com.example.domain.model.Question
import com.example.ui.components.CircularWhiteButton
import com.example.ui.components.NumberBadge
import com.example.ui.components.QuestionCardItem
import com.example.ui.components.SixDotDragHandle
import com.example.ui.components.StudioBackground
import com.example.ui.components.rememberDragDropListState
import com.example.ui.theme.BlushWashLight
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.HairlineDivider
import com.example.ui.theme.InkDark
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioWhite
import java.io.File
import java.util.Locale

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenViewer: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenDuplicate: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val interview = uiState.interview
    val snackbarHostState = remember { SnackbarHostState() }

    var titleText by rememberSaveable(interview?.id) {
        mutableStateOf(interview?.title.orEmpty())
    }
    var titleInitialized by rememberSaveable(interview?.id) { mutableStateOf(false) }
    LaunchedEffect(interview?.title) {
        if (!titleInitialized && interview != null) {
            titleText = interview.title
            titleInitialized = true
        }
    }

    var newQuestionText by rememberSaveable { mutableStateOf("") }
    var editingQuestion by remember { mutableStateOf<Question?>(null) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var videoPreviewPath by remember { mutableStateOf<String?>(null) }
    var titleError by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importImage(uri)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importVideo(uri)
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropListState(
        lazyListState = lazyListState,
        onMove = { from, to -> viewModel.moveQuestion(from, to) },
        onDragEnd = { viewModel.commitQuestionOrder() }
    )

    // Header items count before questions in LazyColumn: Title card (0), Media card (1), Add question header (2)
    val questionIndexOffset = 3

    StudioBackground(showTopRightWave = true, showBottomWave = false) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                ) {
                    // Editor Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularWhiteButton(
                            onClick = onBack,
                            contentDescription = stringResource(R.string.cd_back),
                            modifier = Modifier.testTag("editor_back_button")
                        )

                        Text(
                            text = stringResource(R.string.editor_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = OutfitFontFamily,
                            color = InkDark,
                            modifier = Modifier.weight(1f)
                        )

                        if (interview != null) {
                            OutlinedButton(
                                onClick = { onOpenViewer(interview.id) },
                                border = BorderStroke(1.dp, CrimsonPrimary),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("editor_open_viewer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    tint = CrimsonPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Visor", color = CrimsonPrimary)
                            }
                        }
                    }

                    if (interview == null && !uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Entrevista no encontrada", color = SlateMuted)
                        }
                    } else if (interview != null) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 36.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 0: Title Section
                            item(key = "section_title") {
                                EditorSectionCard(title = "Título de la entrevista") {
                                    OutlinedTextField(
                                        value = titleText,
                                        onValueChange = {
                                            titleText = it
                                            if (titleError && it.isNotBlank()) titleError = false
                                        },
                                        label = { Text("Título") },
                                        singleLine = true,
                                        isError = titleError,
                                        supportingText = if (titleError) {
                                            { Text(stringResource(R.string.error_title_empty)) }
                                        } else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("editor_title_input")
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            if (titleText.isBlank()) {
                                                titleError = true
                                            } else {
                                                viewModel.saveTitle(titleText)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("editor_save_title_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Guardar título")
                                    }
                                }
                            }

                            // 1: Media (Photo & Video) Section
                            item(key = "section_media") {
                                EditorSectionCard(title = "Fotografía y video") {
                                    // Photo sub-block
                                    PhotoEditorBlock(
                                        media = interview.imageMedia,
                                        isBusy = uiState.isImportingImage,
                                        onPickPhoto = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onRemovePhoto = viewModel::removeImage
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 14.dp),
                                        color = HairlineDivider
                                    )

                                    // Video sub-block
                                    VideoEditorBlock(
                                        media = interview.videoMedia,
                                        isBusy = uiState.isImportingVideo,
                                        onPickVideo = {
                                            videoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        },
                                        onPreviewVideo = { path -> videoPreviewPath = path },
                                        onRemoveVideo = viewModel::removeVideo
                                    )
                                }
                            }

                            // 2: Questions header & Add Input
                            item(key = "section_questions_header") {
                                EditorSectionCard(
                                    title = "Preguntas (${uiState.orderedQuestions.size})",
                                    trailingAction = {
                                        if (uiState.orderedQuestions.isNotEmpty()) {
                                            TextButton(
                                                onClick = { showPreviewDialog = true },
                                                modifier = Modifier.testTag("editor_preview_questions_button")
                                            ) {
                                                Text("Vista previa", color = CrimsonPrimary)
                                            }
                                        }
                                    }
                                ) {
                                    OutlinedTextField(
                                        value = newQuestionText,
                                        onValueChange = { newQuestionText = it },
                                        label = { Text("Nueva pregunta") },
                                        placeholder = { Text("Escribe una pregunta clara para la entrevista...") },
                                        minLines = 2,
                                        maxLines = 5,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("editor_new_question_input")
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            viewModel.addQuestion(newQuestionText) {
                                                newQuestionText = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("editor_add_question_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Agregar pregunta")
                                    }
                                    if (uiState.orderedQuestions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Mantén presionado :: o usa las flechas para cambiar el orden.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateMuted
                                        )
                                    }
                                }
                            }

                            // 3+: Editable & Reorderable Questions
                            itemsIndexed(
                                items = uiState.orderedQuestions,
                                key = { _, q -> q.id }
                            ) { index, question ->
                                val isDragging = dragDropState.draggedItemKey == question.id
                                EditableQuestionRow(
                                    number = index + 1,
                                    question = question,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < uiState.orderedQuestions.lastIndex,
                                    isDragging = isDragging,
                                    translationY = if (isDragging) dragDropState.draggedDistance else 0f,
                                    onEdit = { editingQuestion = question },
                                    onDelete = { viewModel.deleteQuestion(question.id) },
                                    onMoveUp = { viewModel.moveQuestionStep(index, -1) },
                                    onMoveDown = { viewModel.moveQuestionStep(index, +1) },
                                    onDragStart = { dragDropState.onDragStart(question.id) },
                                    onDragDelta = { dy ->
                                        dragDropState.onDrag(dy, indexOffset = questionIndexOffset)
                                    },
                                    onDragEnd = { dragDropState.onDragInterrupted() }
                                )
                            }

                            // Admin Actions (Duplicate / Delete)
                            item(key = "section_admin") {
                                EditorSectionCard(title = "Administración") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.duplicateInterview(onOpenDuplicate) },
                                            border = BorderStroke(1.dp, HairlineDivider),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("editor_duplicate_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = null,
                                                tint = InkDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Duplicar", color = InkDark)
                                        }

                                        OutlinedButton(
                                            onClick = { showDeleteConfirm = true },
                                            border = BorderStroke(1.dp, ErrorRose.copy(alpha = 0.55f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("editor_delete_interview_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = null,
                                                tint = ErrorRose,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Eliminar", color = ErrorRose)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Question Dialog
    editingQuestion?.let { question ->
        EditQuestionDialog(
            initialText = question.text,
            onDismiss = { editingQuestion = null },
            onSave = { updated ->
                viewModel.updateQuestion(question.id, updated) {
                    editingQuestion = null
                }
            }
        )
    }

    // Preview Questions Dialog
    if (showPreviewDialog) {
        Dialog(onDismissRequest = { showPreviewDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BlushWashLight,
                border = BorderStroke(1.dp, CardBorderSoft)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Vista previa: Modo entrevista",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(uiState.orderedQuestions) { idx, q ->
                            QuestionCardItem(number = idx + 1, question = q)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showPreviewDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }

    // Delete Interview Confirmation Dialog
    if (showDeleteConfirm && interview != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = StudioWhite,
            title = { Text("¿Eliminar entrevista?") },
            text = {
                Text(
                    "Se eliminará \"${interview.title}\" junto con sus preguntas, fotografía y video guardados en el dispositivo. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteInterview(onDeleted)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRose),
                    modifier = Modifier.testTag("confirm_delete_interview_button")
                ) {
                    Text(stringResource(R.string.dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = SlateMuted)
                }
            }
        )
    }

    // Inline Video Playback Verification Dialog
    videoPreviewPath?.let { path ->
        VideoPreviewDialog(
            filePath = path,
            onDismiss = { videoPreviewPath = null }
        )
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    trailingAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = StudioWhite,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = InkDark
                )
                trailingAction?.invoke()
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PhotoEditorBlock(
    media: MediaItem?,
    isBusy: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = CrimsonPrimary)
            Text(
                text = "Fotografía principal",
                style = MaterialTheme.typography.titleSmall,
                color = InkDark
            )
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = CrimsonPrimary
                )
            }
        }

        if (media != null) {
            val file = remember(media.localPath) { File(media.localPath) }
            if (file.exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Vista previa de fotografía",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(media.aspectRatio.coerceIn(0.4f, 2.5f))
                            .clip(RoundedCornerShape(14.dp))
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.error_media_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRose
                )
            }
            Text(
                text = "${media.originalName} • ${media.width} × ${media.height}",
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = "Sin fotografía asignada.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onPickPhoto,
                enabled = !isBusy,
                border = BorderStroke(1.dp, CrimsonPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("editor_pick_photo_button")
            ) {
                Text(
                    text = if (media == null) "Elegir foto" else "Reemplazar foto",
                    color = CrimsonPrimary
                )
            }
            if (media != null) {
                TextButton(
                    onClick = onRemovePhoto,
                    enabled = !isBusy,
                    modifier = Modifier.testTag("editor_remove_photo_button")
                ) {
                    Text("Eliminar", color = ErrorRose)
                }
            }
        }
    }
}

@Composable
private fun VideoEditorBlock(
    media: MediaItem?,
    isBusy: Boolean,
    onPickVideo: () -> Unit,
    onPreviewVideo: (String) -> Unit,
    onRemoveVideo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = CrimsonPrimary)
            Text(
                text = "Video opcional",
                style = MaterialTheme.typography.titleSmall,
                color = InkDark
            )
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = CrimsonPrimary
                )
            }
        }

        if (media != null) {
            val file = remember(media.localPath) { File(media.localPath) }
            val durationLabel = media.durationMs?.let { ms ->
                val sec = ms / 1000L
                String.format(Locale.getDefault(), "%02d:%02d", sec / 60L, sec % 60L)
            } ?: "--:--"

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BlushWashLight,
                border = BorderStroke(1.dp, CardBorderSoft)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.originalName,
                            style = MaterialTheme.typography.titleSmall,
                            color = InkDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${media.width} × ${media.height} • $durationLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                    if (file.exists()) {
                        Button(
                            onClick = { onPreviewVideo(media.localPath) },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("editor_preview_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Probar")
                        }
                    } else {
                        Text("Falta archivo", color = ErrorRose, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Text(
                text = "Sin video asignado.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onPickVideo,
                enabled = !isBusy,
                border = BorderStroke(1.dp, CrimsonPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("editor_pick_video_button")
            ) {
                Text(
                    text = if (media == null) "Elegir video" else "Reemplazar video",
                    color = CrimsonPrimary
                )
            }
            if (media != null) {
                TextButton(
                    onClick = onRemoveVideo,
                    enabled = !isBusy,
                    modifier = Modifier.testTag("editor_remove_video_button")
                ) {
                    Text("Eliminar", color = ErrorRose)
                }
            }
        }
    }
}

@Composable
private fun EditableQuestionRow(
    number: Int,
    question: Question,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isDragging: Boolean,
    translationY: Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                this.translationY = translationY
            }
            .testTag("editor_question_row_${question.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isDragging) BlushWashLight else StudioWhite,
        shadowElevation = if (isDragging) 10.dp else 3.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberBadge(number = number, size = 34.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = question.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = InkDark
                )
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(44.dp)
                        .pointerInput(question.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SixDotDragHandle()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Subir pregunta",
                        tint = if (canMoveUp) InkDark else SlateMuted.copy(alpha = 0.35f)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = "Bajar pregunta",
                        tint = if (canMoveDown) InkDark else SlateMuted.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("editor_edit_question_${question.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.cd_edit_question),
                        tint = CrimsonPrimary
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("editor_delete_question_${question.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.cd_delete_question),
                        tint = ErrorRose
                    )
                }
            }
        }
    }
}

@Composable
private fun EditQuestionDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioWhite,
        title = { Text("Editar pregunta") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (showError && it.isNotBlank()) showError = false
                },
                minLines = 2,
                maxLines = 6,
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.error_question_empty)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isBlank()) {
                        showError = true
                    } else {
                        onSave(text)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = SlateMuted)
            }
        }
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPreviewDialog(
    filePath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(Uri.fromFile(File(filePath))))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioWhite)
                        .padding(10.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", color = CrimsonPrimary)
                    }
                }
            }
        }
    }
}
