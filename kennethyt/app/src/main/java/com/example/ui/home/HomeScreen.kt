package com.example.ui.home

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.backup.BackupTransferManager
import com.example.domain.model.Interview
import com.example.ui.components.NumberBadge
import com.example.ui.components.SixDotDragHandle
import com.example.ui.components.StudioBackground
import com.example.ui.components.StudioClapperEmblem
import com.example.ui.components.rememberDragDropListState
import com.example.ui.theme.BlushWashLight
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.HairlineDivider
import com.example.ui.theme.InkDark
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenViewer: (Long) -> Unit,
    onOpenEditor: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showOptionsSheet by rememberSaveable { mutableStateOf(false) }

    val manualApkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.onManualApkSelected(uri)
    }

    val createZipDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        viewModel.saveExportPackageToUri(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.importBackupFromUri(uri)
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropListState(
        lazyListState = lazyListState,
        onMove = { from, to -> viewModel.moveInterview(from, to) },
        onDragEnd = { viewModel.commitReorder() }
    )

    StudioBackground(showTopRightWave = true, showBottomWave = true) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(end = 4.dp, bottom = 6.dp)
                        .size(60.dp)
                        .testTag("add_interview_fab"),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = CrimsonPrimary,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_interview),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                ) {
                    HomeHeader(
                        onMenuClick = { showOptionsSheet = true }
                    )

                    if (!uiState.isLoading && uiState.interviews.isEmpty()) {
                        HomeEmptyState(
                            onAddClick = { showCreateDialog = true },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("interview_list"),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 6.dp,
                                bottom = 104.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = uiState.interviews,
                                key = { _, item -> item.id }
                            ) { index, interview ->
                                val isDragging = dragDropState.draggedItemKey == interview.id
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 12.dp else 3.dp,
                                    label = "cardElevation"
                                )
                                InterviewCardRow(
                                    indexNumber = index + 1,
                                    interview = interview,
                                    elevation = elevation,
                                    isDragging = isDragging,
                                    translationY = if (isDragging) dragDropState.draggedDistance else 0f,
                                    onCardClick = { onOpenViewer(interview.id) },
                                    onEditClick = { onOpenEditor(interview.id) },
                                    onDragStart = { dragDropState.onDragStart(interview.id) },
                                    onDragDelta = { dy -> dragDropState.onDrag(dy, indexOffset = 0) },
                                    onDragEnd = { dragDropState.onDragInterrupted() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateInterviewDialog(
            onDismiss = { showCreateDialog = false },
            onCreateOnly = { title ->
                viewModel.createInterview(title) {
                    showCreateDialog = false
                }
            },
            onCreateAndEdit = { title ->
                viewModel.createInterview(title) { newId ->
                    showCreateDialog = false
                    onOpenEditor(newId)
                }
            }
        )
    }

    if (showOptionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            sheetState = sheetState,
            containerColor = StudioWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioClapperEmblem(size = 44.dp)
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = InkDark,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.interviews.size} entrevistas • ${
                                uiState.interviews.sumOf { it.questions.size }
                            } preguntas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                    }
                }

                HorizontalDivider(color = HairlineDivider)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = CrimsonPrimary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.street_mode_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = InkDark
                        )
                        Text(
                            text = stringResource(R.string.street_mode_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                    }
                    Switch(
                        checked = uiState.streetMode.enabled,
                        onCheckedChange = { viewModel.setStreetModeEnabled(it) },
                        modifier = Modifier.testTag("street_mode_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CrimsonPrimary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = if (uiState.streetMode.enabled) CrimsonPrimary else SlateMuted
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.street_brightness_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (uiState.streetMode.enabled) InkDark else SlateMuted
                        )
                        Text(
                            text = stringResource(R.string.street_brightness_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                    }
                    Switch(
                        checked = uiState.streetMode.boostBrightness,
                        onCheckedChange = { viewModel.setStreetBrightnessBoost(it) },
                        enabled = uiState.streetMode.enabled,
                        modifier = Modifier.testTag("street_brightness_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CrimsonPrimary
                        )
                    )
                }

                HorizontalDivider(color = HairlineDivider)

                Text(
                    text = stringResource(R.string.settings_transfer_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateMuted
                )

                SettingsActionRow(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.share_app_and_data_title),
                    subtitle = stringResource(R.string.share_app_and_data_subtitle),
                    accentPrimary = true,
                    onClick = {
                        showOptionsSheet = false
                        viewModel.startShareAppAndData()
                    },
                    modifier = Modifier.testTag("share_app_and_data_button")
                )

                SettingsActionRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = stringResource(R.string.import_my_data_title),
                    subtitle = stringResource(R.string.import_my_data_subtitle),
                    accentPrimary = false,
                    onClick = {
                        showOptionsSheet = false
                        importBackupLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.testTag("import_my_data_button")
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (uiState.transferState !is TransferUiState.Idle) {
        TransferStateDialog(
            state = uiState.transferState,
            onDismiss = viewModel::dismissTransferDialog,
            onSelectManualApk = {
                manualApkPickerLauncher.launch(
                    arrayOf(
                        "application/vnd.android.package-archive",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            },
            onSaveZip = {
                createZipDocumentLauncher.launch(BackupTransferManager.TRANSFER_PACKAGE_FILENAME)
            },
            onShareZip = { summary ->
                runCatching {
                    val shareUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        summary.packageFile
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_app_and_data_title))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            intent,
                            context.getString(R.string.share_app_and_data_title)
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(16.dp),
        color = if (accentPrimary) BlushWashLight else StudioWhite,
        border = BorderStroke(
            width = 1.dp,
            color = if (accentPrimary) CrimsonPrimary.copy(alpha = 0.35f) else CardBorderSoft
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CrimsonPrimary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = InkDark,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )
            }
        }
    }
}

@Composable
private fun TransferStateDialog(
    state: TransferUiState,
    onDismiss: () -> Unit,
    onSelectManualApk: () -> Unit,
    onSaveZip: () -> Unit,
    onShareZip: (com.example.data.backup.ExportPackageSummary) -> Unit
) {
    val context = LocalContext.current
    when (state) {
        TransferUiState.Idle -> Unit

        is TransferUiState.NeedsManualApk -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = StudioWhite,
                title = {
                    Text(
                        text = "Seleccionar APK universal",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                },
                text = {
                    Text(
                        text = state.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onSelectManualApk,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        modifier = Modifier.testTag("select_manual_apk_button")
                    ) {
                        Text("Elegir APK universal")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_cancel), color = SlateMuted)
                    }
                }
            )
        }

        is TransferUiState.InProgress -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = StudioWhite,
                title = {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                        val progress = state.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("transfer_progress_indicator"),
                                color = CrimsonPrimary,
                                trackColor = BlushWashLight
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("transfer_progress_indicator"),
                                color = CrimsonPrimary,
                                trackColor = BlushWashLight
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        is TransferUiState.ExportReady -> {
            val summary = state.summary
            val formattedSize = Formatter.formatFileSize(context, summary.totalSizeBytes)
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = StudioWhite,
                title = {
                    Text(
                        text = "Paquete listo",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Incluye Gestor_Entrevistas.apk, respaldo.zip y manifest.json ($formattedSize).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkDark
                        )
                        Text(
                            text = "${summary.interviewCount} entrevistas • ${summary.questionCount} preguntas • ${summary.mediaCount} archivos multimedia",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onSaveZip,
                            border = BorderStroke(1.dp, CrimsonPrimary),
                            modifier = Modifier.testTag("save_transfer_zip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SaveAlt,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar", color = CrimsonPrimary)
                        }
                        Button(
                            onClick = { onShareZip(summary) },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            modifier = Modifier.testTag("share_transfer_zip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", color = SlateMuted)
                    }
                }
            )
        }

        is TransferUiState.Completed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = StudioWhite,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircleOutline,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                },
                text = {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        modifier = Modifier.testTag("transfer_completed_ok_button")
                    ) {
                        Text("Aceptar")
                    }
                }
            )
        }

        is TransferUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = StudioWhite,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                },
                text = {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        modifier = Modifier.testTag("transfer_error_ok_button")
                    ) {
                        Text("Entendido")
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onMenuClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("home_menu_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = stringResource(R.string.cd_menu),
                tint = InkDark,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioClapperEmblem(size = 62.dp)

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(54.dp)
                    .background(HairlineDivider)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = stringResource(R.string.home_header_line1),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    lineHeight = 30.sp,
                    color = InkDark
                )
                Text(
                    text = stringResource(R.string.home_header_line2),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 29.sp,
                    lineHeight = 32.sp,
                    color = CrimsonPrimary
                )
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(CrimsonPrimary, CrimsonBright)
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun InterviewCardRow(
    indexNumber: Int,
    interview: Interview,
    elevation: androidx.compose.ui.unit.Dp,
    isDragging: Boolean,
    translationY: Float,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Surface(
        onClick = onCardClick,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                this.translationY = translationY
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .testTag("interview_card_${interview.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isDragging) BlushWashLight else StudioWhite,
        shadowElevation = elevation,
        border = BorderStroke(
            width = if (isDragging) 1.5.dp else 1.dp,
            color = if (isDragging) CrimsonPrimary.copy(alpha = 0.45f) else CardBorderSoft
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberBadge(number = indexNumber, size = 36.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(HairlineDivider)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = interview.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                color = InkDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_interview_${interview.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_interview),
                    tint = InkDark.copy(alpha = 0.78f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .pointerInput(interview.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                    .testTag("drag_interview_${interview.id}"),
                contentAlignment = Alignment.Center
            ) {
                SixDotDragHandle()
            }
        }
    }
}

@Composable
private fun HomeEmptyState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BlushWashLight,
            border = BorderStroke(1.dp, CardBorderSoft)
        ) {
            Box(
                modifier = Modifier.padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MovieCreation,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = InkDark
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = SlateMuted
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.dialog_new_interview_title))
        }
    }
}

@Composable
private fun CreateInterviewDialog(
    onDismiss: () -> Unit,
    onCreateOnly: (String) -> Unit,
    onCreateAndEdit: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioWhite,
        title = {
            Text(
                text = stringResource(R.string.dialog_new_interview_title),
                style = MaterialTheme.typography.titleLarge,
                color = InkDark
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (showError && it.isNotBlank()) showError = false
                    },
                    label = { Text(stringResource(R.string.dialog_interview_title_label)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.error_title_empty)) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_interview_title_input")
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (title.isBlank()) {
                            showError = true
                        } else {
                            onCreateOnly(title)
                        }
                    },
                    border = BorderStroke(1.dp, CrimsonPrimary),
                    modifier = Modifier.testTag("create_interview_only_button")
                ) {
                    Text(stringResource(R.string.dialog_create), color = CrimsonPrimary)
                }
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            showError = true
                        } else {
                            onCreateAndEdit(title)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    modifier = Modifier.testTag("create_and_edit_interview_button")
                ) {
                    Text("Crear y editar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = SlateMuted)
            }
        }
    )
}
