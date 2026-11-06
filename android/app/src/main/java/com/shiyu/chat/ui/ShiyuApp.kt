package com.shiyu.chat.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiyu.chat.data.CharacterEntity
import com.shiyu.chat.data.ChatRepository
import com.shiyu.chat.data.ArtworkCropSelection
import com.shiyu.chat.data.CharacterArtworkPolicy
import com.shiyu.chat.data.DreamGenerationSettings
import com.shiyu.chat.data.insertActionBrackets
import com.shiyu.chat.data.MemoryEntity
import com.shiyu.chat.data.MessageEntity
import com.shiyu.chat.data.MODEL_SOURCE_API
import com.shiyu.chat.data.MODEL_SOURCE_LOCAL_MNN
import com.shiyu.chat.data.StructuredMemoryEntity
import com.shiyu.chat.data.TurnTraceEntity
import com.shiyu.chat.data.LocalVoice
import com.shiyu.chat.data.TTS_SELECTABLE_SPEAKER_IDS
import com.shiyu.chat.data.normalizeTtsSpeakerId
import com.shiyu.chat.data.naturalMessageText
import com.shiyu.chat.data.speechOnlyText
import com.shiyu.chat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.io.File
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private val DeepForest = Color(0xFF4E3A86)
private val Forest = Color(0xFF7457C8)
private val Cream = Color(0xFFFBF9FF)
private val Sage = Color(0xFFEDE7FA)
private val WarmGold = Color(0xFFFFE5EF)
private val CanvasMist = Color(0xFFFBFAFE)
private val InkMuted = Color(0xFF746F80)
private val Hairline = Color(0xFFEAE4F2)
private val Apricot = Color(0xFFF8E8EF)
private val LilacMist = Color(0xFFF3EFFF)

@Composable
private fun FullScreenPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFDFBFF),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = DeepForest,
                        )
                    }
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(color = Hairline)
                content(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
fun ShiyuApp(repository: ChatRepository) {
    val vm: ShiyuViewModel = viewModel(factory = ShiyuViewModelFactory(repository))
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showTrace by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var pendingCharacter by remember { mutableStateOf<CharacterEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.status) {
        state.status?.let {
            snackbar.showSnackbar(it)
            vm.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(LilacMist, CanvasMist, Color(0xFFFFF4F8)),
                    ),
                )
                .padding(padding),
        ) {
            if (state.selected == null) {
                CharacterHome(
                    state = state,
                    onSelect = { character ->
                        pendingCharacter = null
                        vm.select(character)
                    },
                    onSettings = { showSettings = true },
                    onCreate = {
                        pendingCharacter = null
                        showCreate = true
                    },
                )
            } else {
                ChatScreen(
                    state = state,
                    onBack = {
                        pendingCharacter = null
                        vm.closeCharacter()
                    },
                    onSend = vm::send,
                    onStop = vm::stop,
                    onMemory = { showMemory = true },
                    onTrace = { showTrace = true },
                    onEdit = {
                        pendingCharacter = null
                        showEdit = true
                    },
                    onRollback = vm::rollbackToUserMessage,
                    onReset = vm::reset,
                    onDelete = vm::deleteCharacter,
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onSaveKey = vm::saveApiKey,
            onRemoveKey = vm::removeApiKey,
            onSaveOpenCodeKey = vm::saveOpenCodeApiKey,
            onRemoveOpenCodeKey = vm::removeOpenCodeApiKey,
            onProviderChange = vm::setCloudProvider,
            onSaveLimits = vm::saveLimits,
            onSaveImageKey = vm::saveImageApiKey,
            onRemoveImageKey = vm::removeImageApiKey,
            onSaveDreamSettings = vm::saveDreamGenerationSettings,
            onImportAvatarModel = vm::importAvatarModel,
            onDeleteAvatarModel = vm::deleteAvatarModel,
            onImportLocalModel = vm::importLocalModel,
            onDeleteLocalModel = vm::deleteLocalModel,
            onDownloadLocalModel = vm::downloadLocalModel,
            onDownloadDreamModel = vm::downloadDreamModel,
        )
    }
    if (showCreate) {
        CreateCharacterDialog(
            busy = state.busy,
            onDismiss = {
                pendingCharacter = null
                showCreate = false
            },
            onCreate = { idea ->
                vm.generateCharacter(idea) { character ->
                    showCreate = false
                    pendingCharacter = character
                    showEdit = true
                }
            },
            onManual = {
                val character = CharacterEntity(
                    id = "custom-" + UUID.randomUUID().toString().take(12),
                    name = "新角色",
                    tag = "原创角色",
                    description = "等待补完的人物",
                    personality = "",
                    speechStyle = "",
                    relationship = "初次交谈",
                    appearance = "",
                    appearancePrompt = "",
                    greeting = "",
                    era = "原创世界",
                    knowledgeCutoff = "",
                    forbiddenKnowledge = "",
                    forbiddenTerms = "",
                    custom = true,
                )
                showCreate = false
                pendingCharacter = character
                showEdit = true
            },
        )
    }
    if (showMemory && state.selected != null) {
        MemorySheet(
            character = state.selected!!,
            memories = state.memories,
            structuredMemories = state.structuredMemories,
            memoryBusy = state.memoryBusy,
            onDismiss = { showMemory = false },
            onDelete = vm::deleteMemory,
            onEdit = vm::editMemory,
            onDeleteStructured = vm::deleteStructuredMemory,
        )
    }
    if (showTrace && state.selected != null) {
        TraceSheet(
            traces = state.turnTraces,
            onDismiss = { showTrace = false },
        )
    }
    val editingCharacter = pendingCharacter ?: state.selected
    if (showEdit && editingCharacter != null) {
        EditCharacterDialog(
            character = editingCharacter,
            avatarBusy = state.avatarBusy,
            imageGenerationActive = state.imageGenerationActive,
            imageGenerationProgress = state.imageGenerationProgress,
            imageGenerationLabel = state.imageGenerationLabel,
            avatarModelReady = state.avatarModelReady,
            imageApiConfigured = state.imageApiConfigured,
            localModelReady = state.localModelReady,
            onDismiss = {
                showEdit = false
                pendingCharacter = null
            },
            onGenerateLocalAvatar = { edited, onGenerated ->
                vm.generateLocalAvatar(edited) { updated ->
                    pendingCharacter = updated
                    onGenerated(updated)
                }
            },
            onGenerateCloudAvatar = { edited, onGenerated ->
                vm.generateCloudAvatar(edited) { updated ->
                    pendingCharacter = updated
                    onGenerated(updated)
                }
            },
            onImportArtwork = { edited, uri, backgroundSelection, avatarSelection ->
                vm.importCharacterArtwork(
                    edited,
                    uri,
                    backgroundSelection,
                    avatarSelection,
                ) { updated ->
                    pendingCharacter = updated
                }
            },
            onApplyAvatarCrop = { edited, selection ->
                vm.applyAvatarCrop(edited, selection) { updated ->
                    pendingCharacter = updated
                }
            },
            onModelSourceChange = { source ->
                vm.setCharacterModelSource(editingCharacter.id, source) { updated ->
                    if (pendingCharacter?.id == updated.id) pendingCharacter = updated
                }
            },
            onSave = { updated ->
                vm.saveCharacter(updated) {
                    showEdit = false
                    pendingCharacter = null
                    vm.select(updated)
                }
            },
        )
    }
}

@Composable
private fun CharacterHome(
    state: ShiyuUiState,
    onSelect: (CharacterEntity) -> Unit,
    onSettings: () -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HomeHeader(onSettings = onSettings)
        }
        item {
            DiscoveryHero(
                state = state,
                onCreate = onCreate,
                onContinue = state.characters.firstOrNull()?.let { character ->
                    { onSelect(character) }
                },
                onSettings = onSettings,
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        "你的故事",
                        color = Forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "角色",
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    onClick = onCreate,
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.84f),
                    border = BorderStroke(1.dp, Hairline),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Forest)
                        Text("新角色", color = DeepForest, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (state.characters.isEmpty()) {
            item {
                EmptyCharacters(onCreate = onCreate)
            }
        } else {
            items(state.characters.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    row.forEach { character ->
                        CharacterCard(
                            character = character,
                            onClick = { onSelect(character) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(2 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = Hairline,
            )
            Text(
                "AI 的回答可能不准确 · 聊天、记忆和图片保存在本机\n聊天内容和图片会发送给 ${if (state.cloudProvider == "opencode") "OpenCode Go" else "DeepSeek"}",
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text("拾语", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    "把片刻，留在故事里",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
        Surface(
            onClick = onSettings,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, Hairline),
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = DeepForest,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoveryHero(
    state: ShiyuUiState,
    onCreate: () -> Unit,
    onContinue: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    val providerName = if (state.cloudProvider == "opencode") "OpenCode Go" else "DeepSeek"
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4D3A86), Color(0xFF765BC8), Color(0xFFB76AA7)),
                ),
            ),
    ) {
        Text(
            "拾",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 4.dp)
                .alpha(0.055f),
            color = Color.White,
            fontSize = 142.sp,
            lineHeight = 142.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(Modifier.padding(22.dp)) {
            Text(
                "A STORY THAT REMEMBERS",
                color = Color(0xFFE0D8F5),
                letterSpacing = 1.5.sp,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                if (onContinue == null) "让故事，从一句话开始。" else "今晚，想聊些什么？",
                color = Cream,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                if (onContinue == null) {
                    "每位角色都与你有独立而持续的对话。"
                } else {
                    "回到那个人还在等你的故事里，从上一句话继续。"
                },
                color = Color(0xFFEDE7FA),
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(17.dp))
            val primaryAction = onContinue ?: onCreate
            Button(
                onClick = primaryAction,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmGold,
                    contentColor = DeepForest,
                ),
            ) {
                Icon(
                    if (onContinue == null) Icons.Default.Add else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (onContinue == null) {
                        "创建新角色"
                    } else {
                        "继续和${state.characters.firstOrNull()?.name ?: "角色"}聊天"
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onContinue != null) {
                TextButton(onClick = onCreate, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("创建一个新角色", color = Color(0xFFFFFFFF), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onSettings,
                color = Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (state.apiConfigured) Color(0xFFA7D7B8) else Color(0xFFF0C497)),
                    )
                    Text(
                        if (state.apiConfigured) "$providerName 已连接" else "配置 $providerName",
                        color = Color(0xFFE3EDE6),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCharacters(onCreate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, Hairline),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(LilacMist),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF725F91))
            }
            Text("你的第一位角色", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "从一个名字、身份或一段想象开始。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onCreate) { Text("开始创建") }
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundPath = character.backgroundPath?.takeIf { File(it).isFile }
        ?: character.avatarPath?.takeIf { File(it).isFile }
    val hasAvatar = backgroundPath != null
    val artworkColors = when (character.id.hashCode().mod(4)) {
        0 -> listOf(Color(0xFFB9A8D9), Color(0xFF5E4A91))
        1 -> listOf(Color(0xFFA6B7DC), Color(0xFF4F5E8D))
        2 -> listOf(Color(0xFFE2B8CE), Color(0xFF8D5C78))
        else -> listOf(Color(0xFFC8B6E8), Color(0xFF6C559E))
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(0.79f),
        shape = RoundedCornerShape(18.dp),
        color = artworkColors.first(),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f)),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(artworkColors)),
            )
            if (hasAvatar) {
                LocalAvatar(
                    path = backgroundPath,
                    modifier = Modifier.fillMaxSize(),
                    cropTop = true,
                )
            } else {
                Text(
                    character.name.take(1),
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Transparent,
                                Color(0xFF241A39).copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            ) {
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Text(
                    character.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    character.description.ifBlank { "进入这段尚未写完的故事" },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "开始聊天",
                        color = Color.White.copy(alpha = 0.74f),
                        fontSize = 9.sp,
                    )
                    Box(
                        Modifier
                            .size(27.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    state: ShiyuUiState,
    onBack: () -> Unit,
    onSend: (String, Uri?) -> Unit,
    onStop: () -> Unit,
    onMemory: () -> Unit,
    onTrace: () -> Unit,
    onEdit: () -> Unit,
    onRollback: (Long, (String) -> Unit) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    val character = state.selected ?: return
    val voiceContext = LocalContext.current
    val voice = remember { LocalVoice(voiceContext.applicationContext) }
    val voiceScope = rememberCoroutineScope()
    var voiceBusy by remember { mutableStateOf(false) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var previewingVoice by remember { mutableStateOf<Int?>(null) }
    var recording by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val voicePrefs = remember { voiceContext.getSharedPreferences("character_voices", android.content.Context.MODE_PRIVATE) }
    var speaker by remember(character.id) {
        mutableIntStateOf(normalizeTtsSpeakerId(voicePrefs.getInt(character.id, 0)))
    }
    var showVoicePicker by remember(character.id) { mutableStateOf(false) }
    var confirmDelete by remember(character.id) { mutableStateOf(false) }
    var confirmReset by remember(character.id) { mutableStateOf(false) }
    var showRollback by remember(character.id) { mutableStateOf(false) }
    var pendingRollback by remember(character.id) { mutableStateOf<MessageEntity?>(null) }
    var draft by remember(character.id) { mutableStateOf(TextFieldValue("")) }
    val composerFocusRequester = remember { FocusRequester() }
    var historyVisible by remember(character.id) { mutableStateOf(false) }
    val userMessages = remember(state.messages) {
        state.messages.filter { it.role == "user" }
    }
    val latestRoleMessage = remember(
        state.messages,
        state.streamingText,
        state.busy,
    ) {
        if (state.busy) {
            MessageEntity(
                characterId = character.id,
                role = "assistant",
                content = state.streamingText.ifBlank { "正在回应…" },
            )
        } else {
            state.messages.lastOrNull { it.role == "assistant" }
        }
    }
    fun startRecording() {
        if (voiceBusy) return
        voiceBusy = true; recording = true; voice.recording = true
        voiceScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { voice.recordAndRecognize() } }
            result.onSuccess { text ->
                if (text.isBlank()) voiceStatus = "没有识别到语音，请重试"
                else {
                    val updated = (draft.text + text).take(4096)
                    draft = TextFieldValue(
                        text = updated,
                        selection = TextRange(updated.length),
                    )
                    voiceStatus = "识别完成，请检查后发送"
                }
            }.onFailure { voiceStatus = it.message ?: "语音识别失败" }
            recording = false; voiceBusy = false
        }
    }
    fun playMessage(message: MessageEntity) {
        if (playingId == message.id) {
            voice.stopPlayback()
            voiceStatus = "已停止播放"
            return
        }
        if (voiceBusy || message.content.isBlank()) return
        voiceBusy = true
        playingId = message.id
        voiceStatus = "正在生成语音…"
        voice.preparePlayback()
        voiceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    voice.speak(speechOnlyText(message.content), speaker)
                }
            }.onFailure {
                voiceStatus = it.message ?: "语音播放失败"
            }
            voiceBusy = false
            playingId = null
            if (voiceStatus == "正在生成语音…") voiceStatus = null
        }
    }
    fun insertActionBrackets() {
        draft = insertActionBrackets(draft)
        composerFocusRequester.requestFocus()
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else voiceStatus = "请允许使用麦克风后重试"
    }
    DisposableEffect(voice) { onDispose { voice.recording = false; voice.stopPlayback() } }
    var imageUri by remember(character.id) { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { imageUri = it }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    var initialPositionApplied by remember(character.id) { mutableStateOf(false) }
    LaunchedEffect(character.id, state.messages.isNotEmpty(), historyVisible) {
        if (
            historyVisible &&
            !initialPositionApplied &&
            state.messages.isNotEmpty()
        ) {
            listState.scrollToItem(state.messages.lastIndex)
            initialPositionApplied = true
        }
    }
    LaunchedEffect(
        state.messages.size,
        state.busy,
        state.streamingText.length / 96,
        historyVisible,
        imeBottom,
    ) {
        if (historyVisible && state.messages.isNotEmpty()) {
            if (imeBottom > 0 || !initialPositionApplied) {
                withFrameNanos { }
            }
            val lastItem = state.messages.lastIndex + if (state.busy) 1 else 0
            listState.scrollToItem(lastItem)
            initialPositionApplied = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF3EDFF), CanvasMist, Color(0xFFFFF5F8)),
                ),
            ),
    ) {
        LocalAvatar(
            path = character.backgroundPath?.takeIf { File(it).isFile }
                ?: character.avatarPath,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
        Surface(
            color = Color.Black.copy(alpha = 0.30f),
            shadowElevation = 0.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White,
                    )
                }
                CharacterBadge(character, 38.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        character.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF77D6A0)),
                        )
                        Text(
                            "${character.tag.ifBlank { "AI 角色" }} · ${
                                if (character.modelSource == MODEL_SOURCE_LOCAL_MNN) "本地 MNN" else "云端 API"
                            } · 唯一对话",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onMemory) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = "长期记忆",
                        tint = Color.White,
                    )
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "设置与更多",
                            tint = Color.White,
                        )
                    }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("编辑角色档案") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                            onClick = {
                                menu = false
                                onEdit()
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("调试 Trace") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                menu = false
                                onTrace()
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("回退到之前的发言") },
                            leadingIcon = {
                                Icon(Icons.Default.History, contentDescription = null)
                            },
                            enabled = userMessages.isNotEmpty() &&
                                !state.busy &&
                                !state.historyBusy,
                            onClick = {
                                menu = false
                                showRollback = true
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("重置角色对话") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = {
                                menu = false
                                confirmReset = true
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("选择音色（${ttsVoiceLabel(speaker)}）") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                            },
                            onClick = {
                                menu = false
                                showVoicePicker = true
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("删除角色") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            enabled = !state.busy && !state.avatarBusy,
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
        Box(Modifier.weight(1f)) {
            if (state.messages.isEmpty() && state.streamingText.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(30.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CharacterBadge(character, 82.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "和${character.name}的故事，从此刻开始",
                        fontSize = 22.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        character.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (!historyVisible) {
                latestRoleMessage?.let { message ->
                    LatestRoleMessage(
                        message = message,
                        playing = { playingId == message.id },
                        onPlay = { playMessage(message) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                }
                HistoryToggle(
                    historyVisible = false,
                    onClick = { historyVisible = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 64.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            character,
                            message,
                            onPlay = { playMessage(message) },
                            playing = { playingId == message.id },
                        )
                    }
                    if (state.busy) {
                        item {
                            MessageBubble(
                                character,
                                MessageEntity(
                                    characterId = character.id,
                                    role = "assistant",
                                    content = state.streamingText.ifBlank { "正在回应…" },
                                ),
                            )
                        }
                    }
                }
                HistoryToggle(
                    historyVisible = true,
                    onClick = { historyVisible = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
            }
        }
        Surface(
            color = Color.White.copy(alpha = 0.97f),
            shadowElevation = 3.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                val transientVoiceStatus = voiceStatus ?: when {
                    recording -> "录音中，点击停止后识别"
                    voiceBusy -> "语音处理中…"
                    else -> null
                }
                transientVoiceStatus?.let {
                    Text(
                        it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                imageUri?.let { uri ->
                    Row(
                        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UriImage(
                            uri,
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        TextButton(onClick = { imageUri = null }) {
                            Text("移除图片")
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.size(48.dp),
                        enabled = character.modelSource != MODEL_SOURCE_LOCAL_MNN &&
                            !state.busy &&
                            !state.historyBusy,
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "上传图片",
                            tint = InkMuted,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (recording) {
                                voice.recording = false
                                recording = false
                                voiceStatus = "正在识别…"
                            } else if (!voiceBusy && !state.busy && !state.historyBusy) {
                                microphone.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        enabled = recording || (!voiceBusy && !state.busy && !state.historyBusy),
                    ) {
                        Icon(
                            if (recording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (recording) "结束录音" else "语音输入",
                            tint = if (recording) MaterialTheme.colorScheme.error else InkMuted,
                        )
                    }
                    IconButton(
                        onClick = ::insertActionBrackets,
                        modifier = Modifier.size(48.dp),
                        enabled = !state.busy && !state.historyBusy,
                    ) {
                        Text(
                            "（）",
                            color = if (state.busy || state.historyBusy) InkMuted else Forest,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 96.dp),
                        shape = RoundedCornerShape(21.dp),
                        color = Color(0xFFF7F5FA),
                        border = BorderStroke(1.dp, Hairline),
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = {
                                val text = it.text.take(4096)
                                draft = TextFieldValue(
                                    text = text,
                                    selection = TextRange(
                                        it.selection.start.coerceAtMost(text.length),
                                        it.selection.end.coerceAtMost(text.length),
                                    ),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp, max = 96.dp)
                                .focusRequester(composerFocusRequester)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            enabled = !state.busy && !state.historyBusy,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                            ),
                            cursorBrush = SolidColor(Forest),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (
                                    (draft.text.isNotBlank() || imageUri != null) &&
                                    !state.busy &&
                                    !state.historyBusy
                                ) {
                                    onSend(draft.text.trim(), imageUri)
                                    draft = TextFieldValue("")
                                    imageUri = null
                                    historyVisible = false
                                }
                            }),
                            decorationBox = { innerTextField ->
                                innerTextField()
                            },
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    val canSend = (draft.text.isNotBlank() || imageUri != null) &&
                        !state.busy &&
                        !state.historyBusy
                    IconButton(
                        onClick = {
                            if (state.busy) {
                                onStop()
                            } else if (canSend) {
                                onSend(draft.text.trim(), imageUri)
                                draft = TextFieldValue("")
                                imageUri = null
                                historyVisible = false
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    state.busy -> MaterialTheme.colorScheme.error
                                    canSend -> Forest
                                    else -> Hairline
                                },
                            ),
                        enabled = state.busy || canSend,
                    ) {
                        Icon(
                            if (state.busy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (state.busy) "停止生成" else "发送",
                            tint = if (state.busy || canSend) Color.White else InkMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
    }
    if (showVoicePicker) {
        VoicePickerSheet(
            current = speaker,
            previewingVoice = previewingVoice,
            busy = voiceBusy,
            onPreview = { id ->
                if (!voiceBusy) {
                    voice.stopPlayback()
                    voice.preparePlayback()
                    previewingVoice = id
                    voiceBusy = true
                    voiceStatus = "正在试听 ${ttsVoiceLabel(id)}…"
                    voiceScope.launch {
                        val sample = "你好，这是我在拾语里的声音。希望接下来的每一句话，都能让你觉得自然。"
                        runCatching {
                            withContext(Dispatchers.IO) { voice.speak(sample, id) }
                        }.onFailure {
                            voiceStatus = it.message ?: "音色试听失败"
                        }
                        previewingVoice = null
                        voiceBusy = false
                        if (voiceStatus?.startsWith("正在试听") == true) voiceStatus = null
                    }
                }
            },
            onStopPreview = {
                voice.stopPlayback()
                previewingVoice = null
                voiceBusy = false
                voiceStatus = null
            },
            onDismiss = {
                voice.stopPlayback()
                previewingVoice = null
                voiceBusy = false
                showVoicePicker = false
            },
            onSelect = { selected ->
                speaker = selected
                voicePrefs.edit().putInt(character.id, selected).apply()
                showVoicePicker = false
            },
        )
    }
    if (showRollback) {
        RollbackPickerSheet(
            messages = userMessages,
            onDismiss = { showRollback = false },
            onSelect = { message ->
                showRollback = false
                pendingRollback = message
            },
        )
    }
    pendingRollback?.let { message ->
        AlertDialog(
            onDismissRequest = { pendingRollback = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFFFDFBFF),
            title = { Text("回退到这句话之前？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "“${message.content.take(160)}${if (message.content.length > 160) "…" else ""}”",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "会删除这条发言及其后的全部聊天，并同步清理这段时间产生的记忆、关系状态和调试记录。所选文字会放回输入框。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRollback = null
                        onRollback(message.id) { restored ->
                            draft = TextFieldValue(
                                text = restored,
                                selection = TextRange(restored.length),
                            )
                            voiceStatus = null
                            imageUri = null
                            historyVisible = false
                        }
                    },
                ) {
                    Text("确认回退", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) { Text("取消") }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFFFDFBFF),
            title = { Text("重置和${character.name}的对话？") },
            text = {
                Text(
                    "这会删除当前角色的全部聊天、长期记忆、内部状态、语音记录和生成图片，无法恢复。角色档案本身会保留。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    },
                ) {
                    Text("确认重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("取消")
                }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFFFDFBFF),
            title = { Text("删除${character.name}？") },
            text = { Text("角色档案、聊天记录、记忆及应用生成的头像会被删除，无法恢复。你从手机相册选择的原始图片不会删除。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除角色", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LatestRoleMessage(
    message: MessageEntity,
    playing: () -> Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.66f)),
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(38.dp),
                enabled = message.id > 0 && message.content.isNotBlank(),
            ) {
                Icon(
                    if (playing()) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (playing()) "停止朗读" else "播放角色语音",
                    tint = if (message.id > 0) Forest else InkMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                naturalMessageText(message.content, message.displayJson),
                modifier = Modifier.weight(1f).padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HistoryToggle(
    historyVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.48f),
        contentColor = Color.White,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                if (historyVisible) Icons.Default.Close else Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Text(
                if (historyVisible) "收起" else "历史",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RollbackPickerSheet(
    messages: List<MessageEntity>,
    onDismiss: () -> Unit,
    onSelect: (MessageEntity) -> Unit,
) {
    FullScreenPage(
        title = "回退对话",
        onBack = onDismiss,
    ) { modifier ->
        Column(
            modifier
                .padding(horizontal = 18.dp),
        ) {
            Text(
                "选择一句话，回到它发出之前。该消息及之后的内容会从聊天、记忆和状态中删除，所选文字会放回输入框。",
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                items(messages.asReversed(), key = { it.id }) { message ->
                    Surface(
                        onClick = { onSelect(message) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Hairline),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(LilacMist),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = Forest,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    message.content
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                        .ifBlank { "（图片消息）" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                )
                                Text(
                                    SimpleDateFormat(
                                        "M月d日 HH:mm",
                                        Locale.CHINA,
                                    ).format(Date(message.createdAt)),
                                    modifier = Modifier.padding(top = 3.dp),
                                    color = InkMuted,
                                    fontSize = 9.sp,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "回退到这条消息之前",
                                tint = Forest,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ttsVoiceLabel(id: Int): String = when (id) {
    0 -> "女声 1"
    1, 4 -> "男声 1"
    2 -> "女声 2"
    3 -> "男声 2"
    else -> "声部 ${id + 1}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerSheet(
    current: Int,
    previewingVoice: Int?,
    busy: Boolean,
    onPreview: (Int) -> Unit,
    onStopPreview: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val voices = remember(query) {
        TTS_SELECTABLE_SPEAKER_IDS.filter { id ->
            query.isBlank() || ttsVoiceLabel(id).contains(query.trim(), ignoreCase = true) ||
                id.toString() == query.trim()
        }
    }
    FullScreenPage(
        title = "选择角色音色",
        onBack = onDismiss,
    ) { modifier ->
        Column(
            modifier
                .padding(horizontal = 18.dp),
        ) {
            Text(
                "VITS 中文模型提供 4 个不同的可用声部：女声 1、男声 1、女声 2、男声 2。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("搜索编号、女声或男声") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "共 ${voices.size} 个结果 · 当前 ${ttsVoiceLabel(current)}",
                color = InkMuted,
                fontSize = 10.sp,
            )
            LazyColumn(
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(voices, key = { it }) { id ->
                    val selected = id == current
                    Surface(
                        onClick = { onSelect(id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) Sage else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (selected) Forest else Hairline,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (id == 1 || id == 3) Apricot else LilacMist,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${id + 1}",
                                    color = DeepForest,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Text(
                                ttsVoiceLabel(id),
                                modifier = Modifier.weight(1f),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            IconButton(
                                onClick = {
                                    if (previewingVoice == id) onStopPreview() else onPreview(id)
                                },
                                enabled = !busy || previewingVoice == id,
                            ) {
                                Icon(
                                    if (previewingVoice == id) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (previewingVoice == id) "停止试听" else "试听音色",
                                    tint = Forest,
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "当前音色",
                                    tint = Forest,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(character: CharacterEntity, message: MessageEntity, onPlay: (() -> Unit)? = null, playing: () -> Boolean = { false }) {
    val user = message.role == "user"
    val shownContent = naturalMessageText(message.content, message.displayJson)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!user) {
            CharacterBadge(character, 34.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (user) Alignment.End else Alignment.Start,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 322.dp),
                shape = if (user) {
                    RoundedCornerShape(18.dp, 6.dp, 18.dp, 18.dp)
                } else {
                    RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp)
                },
                color = if (user) Forest else Color.White.copy(alpha = 0.96f),
                border = if (user) null else BorderStroke(1.dp, Hairline),
                shadowElevation = if (user) 0.dp else 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
                    Text(
                        shownContent,
                        color = if (user) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                    message.imageUri?.let { raw ->
                        Spacer(Modifier.height(8.dp))
                        UriImage(
                            Uri.parse(raw),
                            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
            if (message.id > 0) {
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!user && message.content.isNotBlank() && onPlay != null) {
                        TextButton(onClick = onPlay) {
                            Icon(
                                if (playing()) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(if (playing()) "停止" else "朗读", fontSize = 10.sp)
                        }
                    }
                    Text(
                        SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(message.createdAt)),
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterBadge(character: CharacterEntity, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.84f), CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Forest, Color(0xFFC1639A)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LocalAvatar(
            path = character.avatarPath,
            modifier = Modifier.fillMaxSize(),
            cropFace = true,
        )
        if (character.avatarPath == null || !File(character.avatarPath).isFile) {
            Text(character.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LocalAvatar(
    path: String?,
    modifier: Modifier = Modifier,
    cropFace: Boolean = false,
    cropTop: Boolean = false,
) {
    val modified = path?.let { File(it).lastModified() } ?: 0L
    val bitmap = remember(path, modified) {
        path?.takeIf { File(it).isFile }?.let {
            runCatching { BitmapFactory.decodeFile(it) }.getOrNull()
        }
    }
    if (bitmap != null) {
        if (cropFace && bitmap.height > bitmap.width) {
            val image = bitmap.asImageBitmap()
            Canvas(modifier) {
                val side = minOf(bitmap.width, bitmap.height)
                val left = (bitmap.width - side) / 2
                val preferredTop = (bitmap.height * 0.16f).roundToInt()
                val top = preferredTop.coerceIn(0, bitmap.height - side)
                drawImage(
                    image = image,
                    srcOffset = IntOffset(left, top),
                    srcSize = IntSize(side, side),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(
                        size.width.roundToInt(),
                        size.height.roundToInt(),
                    ),
                    filterQuality = FilterQuality.High,
                )
            }
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                alignment = if (cropTop) Alignment.TopCenter else Alignment.Center,
            )
        }
    }
}

@Composable
private fun UriImage(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Sage), contentAlignment = Alignment.Center) {
            Text("图片", color = Forest)
        }
    }
}

@Composable
private fun DreamSchedulerChoice(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected == value) {
        FilledTonalButton(
            onClick = { onSelect(value) },
            modifier = modifier.height(46.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Text(label, fontSize = 9.sp, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = { onSelect(value) },
            modifier = modifier.height(46.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Text(label, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SettingsDialog(
    state: ShiyuUiState,
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onSaveOpenCodeKey: (String) -> Unit,
    onRemoveOpenCodeKey: () -> Unit,
    onProviderChange: (String) -> Unit,
    onSaveLimits: (Int, Int) -> Unit,
    onSaveImageKey: (String) -> Unit,
    onRemoveImageKey: () -> Unit,
    onSaveDreamSettings: (DreamGenerationSettings) -> Unit,
    onImportAvatarModel: (Uri) -> Unit,
    onDeleteAvatarModel: () -> Unit,
    onImportLocalModel: (Uri) -> Unit,
    onDeleteLocalModel: () -> Unit,
    onDownloadLocalModel: (String) -> Unit,
    onDownloadDreamModel: () -> Unit,
) {
    var openCodeKey by remember { mutableStateOf("") }
    var deepSeekKey by remember { mutableStateOf("") }
    var imageKey by remember { mutableStateOf("") }
    var dreamPositive by remember(state.dreamGenerationSettings) {
        mutableStateOf(state.dreamGenerationSettings.positiveTemplate)
    }
    var dreamNegative by remember(state.dreamGenerationSettings) {
        mutableStateOf(state.dreamGenerationSettings.negativePrompt)
    }
    var dreamSteps by remember(state.dreamGenerationSettings) {
        mutableStateOf(state.dreamGenerationSettings.steps.toString())
    }
    var dreamCfg by remember(state.dreamGenerationSettings) {
        mutableStateOf(state.dreamGenerationSettings.cfg.toString())
    }
    var dreamSeed by remember(state.dreamGenerationSettings) {
        mutableStateOf(state.dreamGenerationSettings.seed.toString())
    }
    var dreamScheduler by remember(state.dreamGenerationSettings) {
        mutableStateOf(state.dreamGenerationSettings.scheduler)
    }
    var dreamResolution by remember(state.dreamGenerationSettings) {
        mutableStateOf(state.dreamGenerationSettings.resolution.toString())
    }
    val dreamSchedulerBase = dreamScheduler.removeSuffix("_karras")
    var requestLimit by remember(state.limits) { mutableStateOf(state.limits.requestTokens.toString()) }
    var dailyLimit by remember(state.limits) { mutableStateOf(state.limits.dailyTokens.toString()) }
    var settingsPage by remember { mutableIntStateOf(0) }
    val localAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(onImportAvatarModel)
    }
    val localChatModelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(onImportLocalModel)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFFFDFBFF),
        tonalElevation = 0.dp,
        title = { Text("应用设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Sage.copy(alpha = 0.65f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = { settingsPage = 0 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settingsPage == 0) DeepForest else Color.Transparent,
                            contentColor = if (settingsPage == 0) Color.White else DeepForest,
                        ),
                    ) { Text("对话与模型") }
                    Button(
                        onClick = { settingsPage = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settingsPage == 1) DeepForest else Color.Transparent,
                            contentColor = if (settingsPage == 1) Color.White else DeepForest,
                        ),
                    ) { Text("图片生成") }
                }
                if (settingsPage == 0) {
                Text("当前聊天 Provider", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.showDevProviders) {
                        if (state.cloudProvider == "opencode") {
                            FilledTonalButton(onClick = { onProviderChange("opencode") }) {
                                Text("OpenCode Go")
                            }
                        } else {
                            OutlinedButton(onClick = { onProviderChange("opencode") }) {
                                Text("OpenCode Go")
                            }
                        }
                    }
                    if (state.cloudProvider == "deepseek") {
                        FilledTonalButton(onClick = { onProviderChange("deepseek") }) {
                            Text("DeepSeek")
                        }
                    } else {
                        OutlinedButton(onClick = { onProviderChange("deepseek") }) {
                            Text("DeepSeek")
                        }
                    }
                }
                if (state.showDevProviders) {
                    HorizontalDivider()
                    Text("OpenCode Go API", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = openCodeKey,
                        onValueChange = { openCodeKey = it },
                        label = { Text(if (state.openCodeConfigured) "替换 Key（留空不变）" else "OpenCode Go API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("模型：deepseek-v4.1-flash", fontSize = 10.sp)
                    if (state.openCodeConfigured) {
                        TextButton(onClick = onRemoveOpenCodeKey) {
                            Text("删除 OpenCode Go Key", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
                Text("DeepSeek 备用接口", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = deepSeekKey,
                    onValueChange = { deepSeekKey = it },
                    label = { Text(if (state.deepSeekConfigured) "替换 Key（留空不变）" else "DeepSeek API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("模型：deepseek-flash · 保留原有调用路径", fontSize = 10.sp)
                if (state.deepSeekConfigured) {
                    TextButton(onClick = onRemoveKey) {
                        Text("删除 DeepSeek Key", color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
                Text("手机本地 Qwen3-1.7B · ${state.localModelName}", fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.localModelReady) {
                        "模型已安装，约 ${
                            String.format(
                                Locale.CHINA,
                                "%.2f",
                                state.localModelBytes / 1024.0 / 1024.0 / 1024.0,
                            )
                        } GiB。可在每个角色档案中选择云端 API 或本地模型。"
                    } else {
                        "导入 MNN Chat 兼容的模型 ZIP（需包含 config.json、llm.mnn、llm.mnn.weight 和 tokenizer.mtok）。"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { localChatModelPicker.launch("*/*") },
                    enabled = !state.localModelBusy && !state.modelDownloadBusy,
                ) {
                    if (state.localModelBusy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            state.localModelBusy -> "正在导入…"
                            state.localModelReady -> "重新导入 MNN 模型"
                            else -> "导入本地 MNN 模型"
                        },
                    )
                }
                if (state.localModelReady) {
                    TextButton(
                        onClick = onDeleteLocalModel,
                        enabled = !state.localModelBusy && !state.modelDownloadBusy && !state.busy,
                    ) {
                        Text("删除本地 MNN 模型", color = MaterialTheme.colorScheme.error)
                    }
                }
                state.deviceCompatibility?.let { device ->
                    Text(
                        "${device.socLabel} · RAM ${String.format(Locale.CHINA, "%.1f", device.totalRamGb)} GB · " +
                            "MNN ${if (device.mnnTextModelSupported) "可用" else "需 ARM64/至少 6 GB"}; " +
                            if (device.dreamNpuSupported) "Dream ${device.htpArchitecture}" else "Dream 不可用",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (device.mnnTextModelSupported) {
                        Text("按需下载并安装，不放入 APK；同一时间保留一个端侧聊天模型。", fontSize = 9.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onDownloadLocalModel("song-zhiyi") },
                                enabled = !state.modelDownloadBusy && !state.localModelBusy,
                            ) { Text("下载宋知意 Q8") }
                            OutlinedButton(
                                onClick = { onDownloadLocalModel("pei-jiming") },
                                enabled = !state.modelDownloadBusy && !state.localModelBusy,
                            ) { Text("下载裴既明 Q8") }
                        }
                    }
                }
                if (state.modelDownloadBusy) {
                    Text(state.modelDownloadLabel.orEmpty(), fontSize = 9.sp)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.modelDownloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
                Text("角色朗读", fontWeight = FontWeight.SemiBold)
                Text(
                    "语音模型已随应用完整安装，不需要再下载精简音色包。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = requestLimit,
                        onValueChange = { requestLimit = it.filter(Char::isDigit) },
                        label = { Text("单次 token") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = dailyLimit,
                        onValueChange = { dailyLimit = it.filter(Char::isDigit) },
                        label = { Text("每日 token") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("今日已使用 ${state.todayUsage} tokens", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                if (settingsPage == 1) {
                Text("千问 AI 平台云端角色头像", fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.imageApiConfigured) {
                        "千问 AI 平台图片 API Key 已配置"
                    } else {
                        "填写千问 AI 平台通用 API Key；与 DeepSeek、OpenCode Key 不通用。"
                    },
                    fontSize = 10.sp,
                )
                OutlinedTextField(
                    value = imageKey,
                    onValueChange = { imageKey = it },
                    label = {
                        Text(
                            if (state.imageApiConfigured) {
                                "替换千问 AI 平台 Key（留空不变）"
                            } else {
                                "千问 AI 平台 API Key"
                            },
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Qwen-Image 2.0 按图片计费；额度和实际费用以千问 AI 平台控制台为准。",
                    fontSize = 10.sp,
                )
                if (state.imageApiConfigured) {
                    TextButton(onClick = onRemoveImageKey) {
                        Text("删除千问 AI 平台 Key", color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
                Text("Dream 本地生图 · ${state.avatarModelName}", fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.avatarModelReady) {
                        "${state.avatarModelName} 已导入，约 ${
                            String.format(
                                Locale.CHINA,
                                "%.2f",
                                state.avatarModelBytes / 1024.0 / 1024.0 / 1024.0,
                            )
                        } GiB。生成时使用手机高通 NPU。"
                    } else {
                        "导入 SD1.5 QNN 模型 ZIP 后，可直接在拾语内生成头像，不需要另装 Local Dream。"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { localAvatarPicker.launch("*/*") },
                    enabled = !state.avatarBusy && !state.modelDownloadBusy,
                ) {
                    if (state.avatarBusy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            state.avatarBusy -> "正在导入模型…"
                            state.avatarModelReady -> "重新导入 Dream 模型"
                            else -> "导入 SD1.5 QNN 模型"
                        },
                    )
                }
                state.deviceCompatibility?.let { device ->
                    Text(
                        "${device.socLabel} · ${device.htpArchitecture ?: "Hexagon 未知"} · ${device.dreamPackageLabel}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(device.note, fontSize = 9.sp)
                    if (device.dreamNpuSupported) {
                        Button(
                            onClick = onDownloadDreamModel,
                            enabled = !state.modelDownloadBusy && !state.avatarBusy,
                        ) {
                            Text("下载并安装兼容 MeinaMix V12")
                        }
                    }
                }
                if (state.modelDownloadBusy) {
                    Text(state.modelDownloadLabel.orEmpty(), fontSize = 9.sp)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.modelDownloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.avatarModelReady) {
                    TextButton(
                        onClick = onDeleteAvatarModel,
                        enabled = !state.avatarBusy && !state.modelDownloadBusy,
                    ) {
                        Text("删除本地生图模型", color = MaterialTheme.colorScheme.error)
                    }
                }
                Text("分辨率", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("512" to "512×512", "768" to "768×768", "1024" to "1024×1024")
                        .forEach { (value, label) ->
                            DreamSchedulerChoice(
                                label = label,
                                value = value,
                                selected = dreamResolution,
                                onSelect = { dreamResolution = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                }
                Text(
                    "1024×1024 需要模型包内存在 1024.patch；缺失时自动回退到 768。",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "SD1.5 QNN 先按方形画布生成，保存前自动转换为 1080×1920 手机竖屏背景；生成后只需调整头像裁剪。",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dreamSteps,
                        onValueChange = {
                            dreamSteps = it.filter(Char::isDigit)
                                .take(2)
                                .takeIf(String::isNotEmpty)
                                ?.toIntOrNull()
                                ?.coerceAtMost(50)
                                ?.toString()
                                .orEmpty()
                        },
                        label = { Text("步数（1–50）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = dreamCfg,
                        onValueChange = { dreamCfg = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                        label = { Text("CFG") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = dreamSeed,
                    onValueChange = { dreamSeed = it.filter(Char::isDigit).take(12) },
                    label = { Text("固定种子") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("采样器", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DreamSchedulerChoice(
                        label = "DPM++ 2M",
                        value = "dpm",
                        selected = dreamSchedulerBase,
                        onSelect = {
                            dreamScheduler = if (
                                dreamScheduler.endsWith("_karras")
                            ) {
                                "${it}_karras"
                            } else {
                                it
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DreamSchedulerChoice(
                        label = "DPM++ 2M SDE",
                        value = "dpm_sde",
                        selected = dreamSchedulerBase,
                        onSelect = {
                            dreamScheduler = if (
                                dreamScheduler.endsWith("_karras")
                            ) {
                                "${it}_karras"
                            } else {
                                it
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DreamSchedulerChoice(
                        label = "Euler A",
                        value = "euler_a",
                        selected = dreamSchedulerBase,
                        onSelect = {
                            dreamScheduler = if (
                                dreamScheduler.endsWith("_karras")
                            ) {
                                "${it}_karras"
                            } else {
                                it
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DreamSchedulerChoice(
                        label = "Euler",
                        value = "euler",
                        selected = dreamSchedulerBase,
                        onSelect = {
                            dreamScheduler = if (
                                dreamScheduler.endsWith("_karras")
                            ) {
                                "${it}_karras"
                            } else {
                                it
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DreamSchedulerChoice(
                        label = "LCM",
                        value = "lcm",
                        selected = dreamSchedulerBase,
                        onSelect = { dreamScheduler = it },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Karras",
                        fontSize = 11.sp,
                        color = if (dreamScheduler == "lcm") {
                            InkMuted
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = dreamScheduler.endsWith("_karras"),
                        enabled = dreamScheduler != "lcm",
                        onCheckedChange = { enabled ->
                            val base = dreamScheduler.removeSuffix("_karras")
                            dreamScheduler = if (enabled) {
                                "${base}_karras"
                            } else {
                                base
                            }
                        },
                    )
                }
                Text(
                    "调度器来自 Local Dream；Karras 是时间步变体，LCM 不支持 Karras。",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = dreamPositive,
                    onValueChange = { dreamPositive = it.take(1_800) },
                    label = { Text("正面提示词模板") },
                    supportingText = {
                        Text(
                            "最多 77 Token；{appearance} 替换为角色外貌。" +
                                "超限时自动保留前段核心描述。",
                            fontSize = 9.sp,
                        )
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dreamNegative,
                    onValueChange = { dreamNegative = it.take(1_800) },
                    label = { Text("负面提示词") },
                    supportingText = {
                        Text("最多 77 Token；超限时自动移除末尾次要项。", fontSize = 9.sp)
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "内置 Dream 引擎来自 Local Dream，采用 CC BY-NC 4.0，仅用于非商业演示。" +
                        "首次加载约需数秒，生成期间请保持应用在前台。",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (openCodeKey.isNotBlank()) onSaveOpenCodeKey(openCodeKey)
                if (deepSeekKey.isNotBlank()) onSaveKey(deepSeekKey)
                if (imageKey.isNotBlank()) onSaveImageKey(imageKey)
                onSaveDreamSettings(
                    DreamGenerationSettings(
                        positiveTemplate = dreamPositive,
                        negativePrompt = dreamNegative,
                        steps = dreamSteps.toIntOrNull() ?: 25,
                        cfg = dreamCfg.toFloatOrNull() ?: 7.0f,
                        scheduler = dreamScheduler,
                        seed = dreamSeed.toLongOrNull() ?: 20261102L,
                        resolution = dreamResolution.toIntOrNull() ?: 1024,
                    ),
                )
                onSaveLimits(requestLimit.toIntOrNull() ?: 12_000, dailyLimit.toIntOrNull() ?: 200_000)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CreateCharacterDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onManual: () -> Unit,
) {
    var idea by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFFFDFBFF),
        tonalElevation = 0.dp,
        title = { Text("创建角色", fontSize = 24.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "写下身份、时代、关系和不能知道的内容。生成结果是草稿，请后续检查。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                OutlinedTextField(
                    value = idea,
                    onValueChange = { idea = it.take(1800) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = {
                        Text("例如：李白身处唐代，以诗酒游历为生活重心；说话豪放敏锐；不能知道 Python、电脑和后世历史。")
                    },
                )
                TextButton(enabled = !busy, onClick = onManual) {
                    Text("不调用 API，手动创建空白角色")
                }
            }
        },
        confirmButton = {
            Button(enabled = idea.isNotBlank() && !busy, onClick = { onCreate(idea) }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Text(if (busy) " 生成中" else " 生成草稿")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCharacterDialog(
    character: CharacterEntity,
    avatarBusy: Boolean,
    imageGenerationActive: Boolean,
    imageGenerationProgress: Float,
    imageGenerationLabel: String,
    avatarModelReady: Boolean,
    imageApiConfigured: Boolean,
    localModelReady: Boolean,
    onDismiss: () -> Unit,
    onGenerateLocalAvatar: (CharacterEntity, (CharacterEntity) -> Unit) -> Unit,
    onGenerateCloudAvatar: (CharacterEntity, (CharacterEntity) -> Unit) -> Unit,
    onImportArtwork: (
        CharacterEntity,
        Uri,
        ArtworkCropSelection,
        ArtworkCropSelection,
    ) -> Unit,
    onApplyAvatarCrop: (CharacterEntity, ArtworkCropSelection) -> Unit,
    onModelSourceChange: (String) -> Unit,
    onSave: (CharacterEntity) -> Unit,
) {
    var name by remember(character.id) { mutableStateOf(character.name) }
    var tag by remember(character.id) { mutableStateOf(character.tag) }
    var description by remember(character.id) { mutableStateOf(character.description) }
    var personality by remember(character.id) { mutableStateOf(character.personality) }
    var speechStyle by remember(character.id) { mutableStateOf(character.speechStyle) }
    var relationship by remember(character.id) { mutableStateOf(character.relationship) }
    var appearance by remember(character.id) { mutableStateOf(character.appearance) }
    var appearancePrompt by remember(character.id) {
        mutableStateOf(character.appearancePrompt)
    }
    var greeting by remember(character.id) { mutableStateOf(character.greeting) }
    var characterKind by remember(character.id) { mutableStateOf(character.characterKind) }
    var era by remember(character.id) { mutableStateOf(character.era) }
    var cutoff by remember(character.id) { mutableStateOf(character.knowledgeCutoff) }
    var forbidden by remember(character.id) { mutableStateOf(character.forbiddenKnowledge) }
    var forbiddenTerms by remember(character.id) { mutableStateOf(character.forbiddenTerms) }
    var worldbookJson by remember(character.id) { mutableStateOf(character.worldbookJson) }
    var modelSource by remember(character.id) { mutableStateOf(character.modelSource) }
    var avatarPath by remember(character.id, character.avatarPath) {
        mutableStateOf(character.avatarPath)
    }
    var backgroundPath by remember(character.id, character.backgroundPath) {
        mutableStateOf(character.backgroundPath ?: character.avatarPath)
    }
    var pendingImportArtwork by remember(character.id) {
        mutableStateOf<Uri?>(null)
    }
    var generatedArtwork by remember(character.id) {
        mutableStateOf<String?>(null)
    }
    fun editedCharacter() = character.copy(
        name = name.trim().ifBlank { character.name },
        tag = tag.trim(),
        description = description.trim(),
        personality = personality.trim(),
        speechStyle = speechStyle.trim(),
        relationship = relationship.trim(),
        appearance = appearance.trim(),
        appearancePrompt = appearancePrompt.trim(),
        greeting = greeting.trim(),
        characterKind = characterKind.trim(),
        era = era.trim(),
        knowledgeCutoff = cutoff.trim(),
        forbiddenKnowledge = forbidden.trim(),
        forbiddenTerms = forbiddenTerms.split(',', '，')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(","),
        worldbookJson = worldbookJson.trim().ifBlank { "[]" },
        modelSource = modelSource,
        avatarPath = avatarPath,
        backgroundPath = backgroundPath,
    )
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        pendingImportArtwork = uri
    }

    FullScreenPage(
        title = "编辑${character.name}",
        onBack = onDismiss,
    ) { modifier ->
        LazyColumn(
            modifier,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "认知边界会进入每次模型请求。正式修改保存在本机。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
            }
            item {
                Surface(
                    color = LilacMist.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("聊天模型", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (modelSource == MODEL_SOURCE_API) {
                                FilledTonalButton(onClick = {
                                    modelSource = MODEL_SOURCE_API
                                    onModelSourceChange(MODEL_SOURCE_API)
                                }) {
                                    Text("云端 API")
                                }
                            } else {
                                OutlinedButton(onClick = {
                                    modelSource = MODEL_SOURCE_API
                                    onModelSourceChange(MODEL_SOURCE_API)
                                }) {
                                    Text("云端 API")
                                }
                            }
                            if (modelSource == MODEL_SOURCE_LOCAL_MNN) {
                                FilledTonalButton(onClick = {
                                    modelSource = MODEL_SOURCE_LOCAL_MNN
                                    onModelSourceChange(MODEL_SOURCE_LOCAL_MNN)
                                }) {
                                    Text("本地 MNN")
                                }
                            } else {
                                OutlinedButton(onClick = {
                                    modelSource = MODEL_SOURCE_LOCAL_MNN
                                    onModelSourceChange(MODEL_SOURCE_LOCAL_MNN)
                                }) {
                                    Text("本地 MNN")
                                }
                            }
                        }
                        Text(
                            when {
                                modelSource == MODEL_SOURCE_API ->
                                    "使用设置中选定的云端 API，支持图片和云端记忆整理。"
                                localModelReady ->
                                    "文字由这台手机上已安装的 MNN LoRA 模型生成；不发送到电脑或云端。当前本地模型暂不支持图片。"
                                else ->
                                    "尚未安装本地 MNN 模型。请先在应用设置中导入模型；未安装时本地聊天会给出提示。"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "切换后立即保存；底部“保存档案”用于保存其他角色资料。",
                            fontSize = 9.sp,
                            color = Forest,
                        )
                    }
                }
            }
            item {
                Surface(
                    color = Sage.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text("角色背景与头像", fontWeight = FontWeight.SemiBold)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFEDE7FA), Color(0xFF8B72C9)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            LocalAvatar(
                                path = backgroundPath ?: avatarPath,
                                modifier = Modifier.fillMaxSize(),
                                cropTop = true,
                            )
                            CharacterBadge(
                                character.copy(avatarPath = avatarPath),
                                92.dp,
                            )
                        }
                        Text(
                            if (avatarModelReady) {
                                "本地 Dream 生图模型已就绪，可在手机 NPU 上离线生成；也可以切换千问图片 API。"
                            } else {
                                "先在应用设置中导入 SD1.5 QNN 模型；云端生成需要千问 AI 平台图片 Key。"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onGenerateLocalAvatar(editedCharacter()) { updated ->
                                        avatarPath = updated.avatarPath
                                        backgroundPath = updated.backgroundPath
                                        generatedArtwork = updated.backgroundPath
                                    }
                                },
                                enabled = avatarModelReady && !avatarBusy && name.isNotBlank(),
                            ) {
                                Text(if (avatarBusy) "生成中…" else "NPU 生成竖屏图")
                            }
                            Button(
                                onClick = {
                                    onGenerateCloudAvatar(editedCharacter()) { updated ->
                                        avatarPath = updated.avatarPath
                                        backgroundPath = updated.backgroundPath
                                        generatedArtwork = updated.backgroundPath
                                    }
                                },
                                enabled = imageApiConfigured && !avatarBusy && name.isNotBlank(),
                            ) {
                                Text(if (avatarBusy) "生成中…" else "云端生成竖屏图")
                            }
                        }
                        if (imageGenerationActive) {
                            val percent = (
                                imageGenerationProgress.coerceIn(0f, 1f) * 100
                                ).roundToInt()
                            Text(
                                "$imageGenerationLabel · $percent%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Forest,
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = {
                                    imageGenerationProgress.coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedButton(
                            onClick = { avatarPicker.launch("image/*") },
                            enabled = !avatarBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (avatarBusy) "正在导入…" else "从相册导入并裁剪背景/头像")
                        }
                    }
                }
            }
            item { OutlinedTextField(name, { name = it }, label = { Text("名字") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(tag, { tag = it }, label = { Text("标签") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(description, { description = it }, label = { Text("简介") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(personality, { personality = it }, label = { Text("性格") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(speechStyle, { speechStyle = it }, label = { Text("说话方式") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(relationship, { relationship = it }, label = { Text("与用户的关系") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(
                    value = appearance,
                    onValueChange = { appearance = it },
                    label = { Text("外貌描述（中文）") },
                    placeholder = {
                        Text("例如：二十五岁女性，鹅蛋脸，黑褐色及肩发，温和的眼睛，米白针织衫")
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "这段描述会参与本地生图。建议写清年龄、脸型、眼睛、发型、服装、神态和场景。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = appearancePrompt,
                    onValueChange = { appearancePrompt = it.take(1_200) },
                    label = { Text("生图专用外貌提示词（创建角色时自动生成）") },
                    placeholder = {
                        Text("例如：1boy, male, solo, 26 year old, short black hair, dark suit")
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "留空时使用上面的中文外貌描述；填写后会优先用于 Dream/SD1.5。" +
                        "建议写 30–50 个英文视觉标签；超过 77 Token 时会从末尾压缩。",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item { OutlinedTextField(greeting, { greeting = it }, label = { Text("角色主动开场") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(characterKind, { characterKind = it }, label = { Text("类型：original / historical / fictional_canon") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(era, { era = it }, label = { Text("时代与世界") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(cutoff, { cutoff = it }, label = { Text("知识截止 / 剧情阶段") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(forbidden, { forbidden = it }, label = { Text("不能知道的内容") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(
                    forbiddenTerms,
                    { forbiddenTerms = it },
                    label = { Text("禁止知识关键词，用逗号分隔") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    worldbookJson,
                    { worldbookJson = it },
                    label = { Text("世界书 JSON（title / keywords / content）") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
                            onSave(editedCharacter())
                        },
                        enabled = !avatarBusy,
                    ) { Text("保存档案") }
                }
            }
        }
    }
    pendingImportArtwork?.let { uri ->
        ArtworkCropDialog(
            sourceUri = uri,
            avatarOnly = false,
            onDismiss = { pendingImportArtwork = null },
            onApply = { backgroundSelection, avatarSelection ->
                pendingImportArtwork = null
                onImportArtwork(
                    editedCharacter(),
                    uri,
                    backgroundSelection,
                    avatarSelection,
                )
            },
        )
    }
    generatedArtwork?.let { path ->
        ArtworkCropDialog(
            sourcePath = path,
            avatarOnly = true,
            onDismiss = { generatedArtwork = null },
            onApply = { _, avatarSelection ->
                val base = editedCharacter().copy(backgroundPath = path)
                generatedArtwork = null
                onApplyAvatarCrop(base, avatarSelection)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkCropDialog(
    sourceUri: Uri? = null,
    sourcePath: String? = null,
    avatarOnly: Boolean,
    onDismiss: () -> Unit,
    onApply: (ArtworkCropSelection, ArtworkCropSelection) -> Unit,
) {
    val context = LocalContext.current
    val sourceKey = sourceUri?.toString() ?: sourcePath.orEmpty()
    val bitmap = remember(sourceKey) {
        decodeArtworkPreview(context, sourceUri, sourcePath)
    }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }
    var step by remember(sourceKey, avatarOnly) {
        mutableIntStateOf(if (avatarOnly) 1 else 0)
    }
    var backgroundSelection by remember(sourceKey) {
        mutableStateOf(CharacterArtworkPolicy.DEFAULT_BACKGROUND)
    }
    var avatarSelection by remember(sourceKey) {
        mutableStateOf(CharacterArtworkPolicy.DEFAULT_AVATAR)
    }
    val selectingBackground = step == 0 && !avatarOnly
    val previewSelection = if (selectingBackground) {
        backgroundSelection
    } else {
        avatarSelection
    }
    val updateSelection: (ArtworkCropSelection) -> Unit = { updated ->
        if (selectingBackground) {
            backgroundSelection = updated
        } else {
            avatarSelection = updated
        }
    }

    FullScreenPage(
        title = when {
            selectingBackground -> "裁剪聊天背景"
            avatarOnly -> "选择角色头像"
            else -> "选择角色头像"
        },
        onBack = onDismiss,
    ) { modifier ->
        Column(
            modifier
                .padding(horizontal = 18.dp),
        ) {
            Text(
                when {
                    selectingBackground ->
                        "背景会以 9:16 手机竖屏显示。调整左右、上下和缩放，让角色在聊天界面中处于合适位置。"
                    avatarOnly ->
                        "使用刚刚生成的手机竖屏图选择头像区域。建议让脸部和眼睛位于圆形头像中央。"
                    else ->
                        "头像使用 1:1 正方形裁切。可以直接把脸部移到圆形预览中央。"
                },
                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap == null) {
                    Text(
                        "无法读取这张图片，请重新选择。",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    val ratio = if (selectingBackground) 9f / 16f else 1f
                    ArtworkCropPreview(
                        bitmap = bitmap,
                        aspectRatio = ratio,
                        selection = previewSelection,
                        roundAvatar = !selectingBackground,
                        modifier = Modifier
                            .fillMaxHeight(0.96f)
                            .aspectRatio(ratio),
                    )
                }
            }
            ArtworkCropControls(
                selection = previewSelection,
                onChange = updateSelection,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!avatarOnly && selectingBackground) {
                    TextButton(
                        onClick = {
                            updateSelection(CharacterArtworkPolicy.DEFAULT_BACKGROUND)
                        },
                    ) {
                        Text("重置背景")
                    }
                } else {
                    TextButton(
                        onClick = {
                            updateSelection(CharacterArtworkPolicy.DEFAULT_AVATAR)
                        },
                    ) {
                        Text("脸部居中")
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = bitmap != null,
                    onClick = {
                        if (selectingBackground) {
                            step = 1
                        } else {
                            onApply(
                                backgroundSelection,
                                avatarSelection,
                            )
                        }
                    },
                ) {
                    Text(
                        if (selectingBackground) "下一步：头像" else "应用",
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkCropPreview(
    bitmap: android.graphics.Bitmap,
    aspectRatio: Float,
    selection: ArtworkCropSelection,
    roundAvatar: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = if (roundAvatar) CircleShape else RoundedCornerShape(18.dp)
    val image = bitmap.asImageBitmap()
    Canvas(
        modifier
            .clip(shape)
            .background(Color(0xFF241A39)),
    ) {
        val crop = CharacterArtworkPolicy.crop(
            width = bitmap.width,
            height = bitmap.height,
            aspectRatio = aspectRatio.toDouble(),
            selection = selection,
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(crop.left, crop.top),
            srcSize = IntSize(crop.width, crop.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(
                size.width.roundToInt(),
                size.height.roundToInt(),
            ),
            filterQuality = FilterQuality.High,
        )
    }
}

@Composable
private fun ArtworkCropControls(
    selection: ArtworkCropSelection,
    onChange: (ArtworkCropSelection) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "左右位置 ${"%.0f".format(selection.horizontal * 100)}%",
            fontSize = 10.sp,
            color = InkMuted,
        )
        Slider(
            value = selection.horizontal,
            onValueChange = {
                onChange(selection.copy(horizontal = it))
            },
            valueRange = 0f..1f,
        )
        Text(
            "上下位置 ${"%.0f".format(selection.vertical * 100)}%",
            fontSize = 10.sp,
            color = InkMuted,
        )
        Slider(
            value = selection.vertical,
            onValueChange = {
                onChange(selection.copy(vertical = it))
            },
            valueRange = 0f..1f,
        )
        Text(
            "缩放 ${"%.1f".format(selection.zoom)}×",
            fontSize = 10.sp,
            color = InkMuted,
        )
        Slider(
            value = selection.zoom,
            onValueChange = {
                onChange(selection.copy(zoom = it))
            },
            valueRange = 1f..4f,
        )
    }
}

private fun decodeArtworkPreview(
    context: android.content.Context,
    uri: Uri?,
    path: String?,
): android.graphics.Bitmap? = runCatching {
    val bytes = when {
        uri != null -> context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 20 * 1024 * 1024) {
                    "图片不能超过 20 MiB"
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        path != null && File(path).isFile -> File(path).readBytes()
        else -> null
    } ?: return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 2400) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return@runCatching null
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
    }
    if (matrix.isIdentity) {
        decoded
    } else {
        android.graphics.Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true,
        ).also {
            if (it !== decoded) decoded.recycle()
        }
    }
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TraceSheet(
    traces: List<TurnTraceEntity>,
    onDismiss: () -> Unit,
) {
    FullScreenPage(
        title = "调试 Trace",
        onBack = onDismiss,
    ) { modifier ->
        Column(
            modifier.padding(horizontal = 18.dp),
        ) {
            Text(
                "仅保存模型、Prompt 哈希、记忆 ID、状态、Token 与耗时，不保存 API Key 或完整 Prompt。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(12.dp))
            if (traces.isEmpty()) {
                Text("完成一轮聊天后，这里会出现本机 Trace。")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(traces, key = { it.runId }) { trace ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.76f),
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text(
                                    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                                        .format(Date(trace.createdAt)),
                                    fontSize = 10.sp,
                                    color = Forest,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${trace.provider} · ${trace.model}",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Prompt ${trace.promptVersion}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "输入 ${trace.inputTokens} / 输出 ${trace.outputTokens} tokens" +
                                        (trace.latencyMs?.let { " · ${it} ms" } ?: ""),
                                    fontSize = 10.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    trace.traceJson,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "后台记忆：" + trace.memoryResultJson,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemorySheet(
    character: CharacterEntity,
    memories: List<MemoryEntity>,
    structuredMemories: List<StructuredMemoryEntity>,
    memoryBusy: Boolean,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Long, String) -> Unit,
    onDeleteStructured: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var deleting by remember { mutableStateOf<MemoryEntity?>(null) }
    var editedText by remember { mutableStateOf("") }
    FullScreenPage(
        title = "${character.name}的长期记忆",
        onBack = onDismiss,
    ) { modifier ->
        Column(
            modifier.padding(20.dp),
        ) {
            Text(
                "结构化事实会在聊天后由后台自动整理，不需要逐条确认。删除一项事实会同时移除它的历史版本；你也可以直接在对话中更正。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 15.dp),
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("结构化事实", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${structuredMemories.count { it.state == "active" }} 条当前 · " +
                                    "${structuredMemories.count { it.state == "contested" }} 条待判断",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                if (structuredMemories.isEmpty()) {
                    item {
                        Text(
                            "还没有结构化记忆。下一轮聊天完成后，后台会自动整理值得长期保留的信息。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(structuredMemories, key = { "structured-${it.id}" }) { memory ->
                    Surface(
                        color = when (memory.state) {
                            "active" -> Sage
                            "candidate", "contested" -> WarmGold.copy(alpha = 0.65f)
                            else -> Color.White.copy(alpha = 0.72f)
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(13.dp)) {
                            Text(
                                when {
                                    memory.state == "candidate" -> "历史草稿"
                                    memory.state == "contested" -> "有疑义"
                                    memory.state == "superseded" -> "已被替代"
                                    memory.state == "deleted" -> "已删除"
                                    memory.kind == "role_claim" ||
                                        memory.factType == "role_claim" ||
                                        memory.sourceAuthority == "character_self_report" ->
                                        "角色自述 · 非正式设定"
                                    memory.factType == "plan" -> "当前计划"
                                    memory.factType == "hypothesis" -> "假设 · 非既成事实"
                                    memory.factType == "opinion" -> "主观观点"
                                    else -> "当前事实"
                                },
                                color = Forest,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(memory.content, fontSize = 12.sp, lineHeight = 19.sp)
                            memory.value?.let {
                                Text("准确值：$it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            Text(
                                "类型：${memory.factType} · 权限：${memory.sourceAuthority}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                            )
                            if (memory.state != "deleted") {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(
                                        enabled = !memoryBusy,
                                        onClick = { onDeleteStructured(memory.id) },
                                    ) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("旧对话摘录", fontWeight = FontWeight.SemiBold)
                }
                if (memories.isEmpty()) {
                    item {
                        Text(
                            "还没有旧对话摘录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
                items(memories, key = { "excerpt-${it.id}" }) { memory ->
                        Surface(
                            color = Color.White.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (memory.memoryType == "user_edited") "用户修订 · 来源为旧对话" else "旧对话摘录", color = Forest, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { editing = memory; editedText = memory.content }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑记忆")
                                    }
                                    IconButton(onClick = { deleting = memory }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除记忆")
                                    }
                                }
                                Text(memory.content, fontSize = 12.sp, lineHeight = 19.sp)
                                Text("来源消息编号：${memory.sourceMessageIds}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                }
            }
        }
    }
    editing?.let { memory ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("修改记忆") },
            text = { OutlinedTextField(value = editedText, onValueChange = { editedText = it.take(1200) }, minLines = 3, label = { Text("记忆内容") }) },
            confirmButton = { TextButton(enabled = editedText.isNotBlank(), onClick = { onEdit(memory.id, editedText); editing = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    }
    deleting?.let { memory ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这条记忆？") },
            text = { Text("该摘录会被删除，同一来源不会再自动提取。原始聊天不会删除，近期聊天原文仍可能影响回答。") },
            confirmButton = { TextButton(onClick = { onDelete(memory.id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}
