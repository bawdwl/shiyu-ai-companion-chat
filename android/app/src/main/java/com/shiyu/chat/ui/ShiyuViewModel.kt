package com.shiyu.chat.ui

import android.net.Uri
import android.util.Log
import com.shiyu.chat.data.ArtworkCropSelection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiyu.chat.data.CharacterEntity
import com.shiyu.chat.data.ChatRepository
import com.shiyu.chat.data.ConversationSummaryRow
import com.shiyu.chat.data.ConversationBranchEntity
import com.shiyu.chat.data.DeviceModelCompatibility
import com.shiyu.chat.data.Limits
import com.shiyu.chat.data.MemoryEntity
import com.shiyu.chat.data.MessageVariantEntity
import com.shiyu.chat.data.MessageEntity
import com.shiyu.chat.data.MODEL_SOURCE_LOCAL_MNN
import com.shiyu.chat.data.DreamGenerationSettings
import com.shiyu.chat.data.MnnDownloadCatalog
import com.shiyu.chat.data.StructuredMemoryEntity
import com.shiyu.chat.data.TurnTraceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShiyuUiState(
    val characters: List<CharacterEntity> = emptyList(),
    val conversationSummaries: List<ConversationSummaryRow> = emptyList(),
    val selected: CharacterEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val structuredMemories: List<StructuredMemoryEntity> = emptyList(),
    val turnTraces: List<TurnTraceEntity> = emptyList(),
    val cloudProvider: String = "opencode",
    val fullFeatures: Boolean = false,
    val showDevProviders: Boolean = false,
    val apiConfigured: Boolean = false,
    val openCodeConfigured: Boolean = false,
    val deepSeekConfigured: Boolean = false,
    val imageApiConfigured: Boolean = false,
    val limits: Limits = Limits(),
    val todayUsage: Int = 0,
    val streamingText: String = "",
    val busy: Boolean = false,
    val historyBusy: Boolean = false,
    val avatarBusy: Boolean = false,
    val imageGenerationActive: Boolean = false,
    val imageGenerationProgress: Float = 0f,
    val imageGenerationLabel: String = "",
    val memoryBusy: Boolean = false,
    val avatarModelReady: Boolean = false,
    val avatarModelBytes: Long = 0L,
    val avatarModelName: String = "Dream SD1.5",
    val dreamGenerationSettings: DreamGenerationSettings = DreamGenerationSettings(),
    val localModelReady: Boolean = false,
    val localModelBytes: Long = 0L,
    val localModelName: String = "Qwen3-1.7B MNN",
    val localModelBusy: Boolean = false,
    val modelDownloadBusy: Boolean = false,
    val modelDownloadLabel: String? = null,
    val modelDownloadProgress: Float = 0f,
    val deviceCompatibility: DeviceModelCompatibility? = null,
    val backupBusy: Boolean = false,
    val restoreReady: Boolean = false,
    val status: String? = null,
    val loadingOlderMessages: Boolean = false,
    val hasOlderMessages: Boolean = false,
    val ignoredMemoryIds: Set<Long> = emptySet(),
    val persona: String = "",
    val messageVariants: List<MessageVariantEntity> = emptyList(),
    val variantMessageId: Long? = null,
    val branches: List<ConversationBranchEntity> = emptyList(),
    val branchSheetVisible: Boolean = false,
    val branchBusy: Boolean = false,
    val messageSearchVisible: Boolean = false,
    val messageSearchQuery: String = "",
    val messageSearchResults: List<MessageEntity> = emptyList(),
    val messageSearchBusy: Boolean = false,
)

class ShiyuViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(ShiyuUiState())
    val state: StateFlow<ShiyuUiState> = _state.asStateFlow()
    private var stopRequested = false
    private var observeJob: Job? = null
    private var variantJob: Job? = null
    private var messageSearchJob: Job? = null

    init {
        viewModelScope.launch {
            val restoreResult = repository.consumeRestoreResult()
            repository.ensureDefaults()
            repository.ensureBuiltInCharacters()
            val provider = repository.cloudProvider()
            _state.value = _state.value.copy(
                cloudProvider = provider,
                fullFeatures = repository.fullFeatures,
                showDevProviders = repository.showDevProviders,
                apiConfigured = !repository.activeApiKey().isNullOrBlank(),
                openCodeConfigured = !repository.openCodeApiKey().isNullOrBlank(),
                deepSeekConfigured = !repository.apiKey().isNullOrBlank(),
                imageApiConfigured = repository.imageApiKeyConfigured(),
                persona = repository.persona(),
                limits = repository.limits(),
                todayUsage = repository.todayUsage(),
                avatarModelReady = repository.avatarModelReady(),
                avatarModelBytes = repository.avatarModelBytes(),
                avatarModelName = repository.avatarModelName(),
                dreamGenerationSettings = repository.dreamGenerationSettings(),
                localModelReady = repository.localModelReady(),
                localModelBytes = repository.localModelBytes(),
                localModelName = repository.localModelName(),
                deviceCompatibility = repository.deviceCompatibility(),
                status = restoreResult?.message,
            )
            launch {
                repository.conversationSummaries.collect { summaries ->
                    _state.value = _state.value.copy(
                        conversationSummaries = summaries,
                    )
                }
            }
            repository.characters.collect { characters ->
                val selected = _state.value.selected?.let { current ->
                    characters.firstOrNull { it.id == current.id }
                }
                _state.value = _state.value.copy(characters = characters, selected = selected)
            }
        }
    }

    fun saveApiKey(value: String) {
        runCatching { repository.saveApiKey(value) }
            .onSuccess {
                _state.value = _state.value.copy(
                    deepSeekConfigured = true,
                    apiConfigured = !repository.activeApiKey().isNullOrBlank(),
                    status = "DeepSeek API Key 已安全保存",
                )
            }
            .onFailure { _state.value = _state.value.copy(status = it.message) }
    }

    fun removeApiKey() {
        repository.removeApiKey()
        _state.value = _state.value.copy(
            deepSeekConfigured = false,
            apiConfigured = !repository.activeApiKey().isNullOrBlank(),
            status = "DeepSeek API Key 已删除",
        )
    }

    fun saveOpenCodeApiKey(value: String) {
        runCatching { repository.saveOpenCodeApiKey(value) }
            .onSuccess {
                _state.value = _state.value.copy(
                    openCodeConfigured = true,
                    apiConfigured = !repository.activeApiKey().isNullOrBlank(),
                    status = "OpenCode Go API Key 已安全保存",
                )
            }
            .onFailure { _state.value = _state.value.copy(status = it.message) }
    }

    fun removeOpenCodeApiKey() {
        repository.removeOpenCodeApiKey()
        _state.value = _state.value.copy(
            openCodeConfigured = false,
            apiConfigured = !repository.activeApiKey().isNullOrBlank(),
            status = "OpenCode Go API Key 已删除",
        )
    }

    fun setCloudProvider(provider: String) {
        runCatching { repository.setCloudProvider(provider) }
            .onSuccess {
                _state.value = _state.value.copy(
                    cloudProvider = repository.cloudProvider(),
                    apiConfigured = !repository.activeApiKey().isNullOrBlank(),
                    status = "聊天 Provider 已切换",
                )
            }
            .onFailure { _state.value = _state.value.copy(status = it.message) }
    }

    fun saveImageApiKey(value: String) {
        runCatching { repository.saveImageApiKey(value) }
            .onSuccess { _state.value = _state.value.copy(imageApiConfigured = true, status = "千问 AI 平台图片 API Key 已保存") }
            .onFailure { _state.value = _state.value.copy(status = it.message) }
    }

    fun removeImageApiKey() {
        repository.removeImageApiKey()
        _state.value = _state.value.copy(imageApiConfigured = false, status = "千问 AI 平台图片 API Key 已删除")
    }

    fun saveLimits(request: Int, daily: Int) {
        runCatching { repository.saveLimits(Limits(request, daily)) }
            .onSuccess { _state.value = _state.value.copy(limits = repository.limits(), status = "限额已保存") }
            .onFailure { _state.value = _state.value.copy(status = it.message) }
    }

    fun savePersona(value: String) {
        repository.savePersona(value)
        _state.value = _state.value.copy(
            persona = repository.persona(),
            status = "用户 Persona 已保存",
        )
    }

    fun createBackup(uri: Uri) {
        if (_state.value.backupBusy) return
        _state.value = _state.value.copy(
            backupBusy = true,
            status = "正在创建完整备份…",
        )
        viewModelScope.launch {
            runCatching { repository.exportBackup(uri) }
                .onSuccess { summary ->
                    _state.value = _state.value.copy(
                        backupBusy = false,
                        status = "备份已创建：${summary.entryCount} 个文件，" +
                            "${String.format(java.util.Locale.CHINA, "%.1f", summary.bytes / 1024.0 / 1024.0)} MiB",
                    )
                }
                .onFailure { error ->
                    Log.e("ShiyuViewModel", "Backup export failed", error)
                    _state.value = _state.value.copy(
                        backupBusy = false,
                        status = error.message ?: "备份创建失败，现有数据未改动",
                    )
                }
        }
    }

    fun restoreBackup(uri: Uri) {
        if (_state.value.backupBusy || _state.value.restoreReady) return
        _state.value = _state.value.copy(
            backupBusy = true,
            status = "正在校验备份并准备恢复…",
        )
        viewModelScope.launch {
            runCatching { repository.stageRestore(uri) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        backupBusy = false,
                        restoreReady = true,
                        status = "备份校验通过，重启应用后完成恢复",
                    )
                }
                .onFailure { error ->
                    Log.e("ShiyuViewModel", "Backup restore staging failed", error)
                    _state.value = _state.value.copy(
                        backupBusy = false,
                        status = error.message ?: "备份校验失败，现有数据未改动",
                    )
                }
        }
    }

    fun exportDiagnostics(uri: Uri) {
        if (_state.value.backupBusy) return
        _state.value = _state.value.copy(
            backupBusy = true,
            status = "正在导出脱敏诊断…",
        )
        viewModelScope.launch {
            runCatching { repository.exportDiagnostics(uri) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        backupBusy = false,
                        status = "脱敏诊断已导出，不包含聊天正文或 API Key",
                    )
                }
                .onFailure { error ->
                    Log.e("ShiyuViewModel", "Diagnostics export failed", error)
                    _state.value = _state.value.copy(
                        backupBusy = false,
                        status = error.message ?: "诊断导出失败",
                    )
                }
        }
    }

    fun showBranches() {
        if (_state.value.selected == null) return
        _state.value = _state.value.copy(branchSheetVisible = true)
    }

    fun hideBranches() {
        if (_state.value.branchBusy) return
        _state.value = _state.value.copy(branchSheetVisible = false)
    }

    fun createBranchAt(messageId: Long) {
        val character = _state.value.selected ?: return
        if (_state.value.branchBusy || messageId <= 0) return
        _state.value = _state.value.copy(branchBusy = true)
        viewModelScope.launch {
            runCatching { repository.createBranch(character.id, messageId) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        branchBusy = false,
                        status = "分支检查点已保存",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        branchBusy = false,
                        status = it.message ?: "创建分支失败",
                    )
                }
        }
    }

    fun createCurrentCheckpoint() {
        val messageId = _state.value.messages.lastOrNull()?.id ?: return
        createBranchAt(messageId)
    }

    fun switchBranch(branchId: Long) {
        val character = _state.value.selected ?: return
        if (_state.value.branchBusy || _state.value.busy) return
        _state.value = _state.value.copy(branchBusy = true)
        viewModelScope.launch {
            runCatching { repository.switchBranch(character.id, branchId) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        branchBusy = false,
                        branchSheetVisible = false,
                        status = "已切换到所选分支",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        branchBusy = false,
                        status = it.message ?: "切换分支失败",
                    )
                }
        }
    }

    fun deleteBranch(branchId: Long) {
        val character = _state.value.selected ?: return
        if (_state.value.branchBusy) return
        _state.value = _state.value.copy(branchBusy = true)
        viewModelScope.launch {
            runCatching { repository.deleteBranch(character.id, branchId) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        branchBusy = false,
                        status = "分支已删除",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        branchBusy = false,
                        status = it.message ?: "删除分支失败",
                    )
                }
        }
    }

    fun openMessageSearch() {
        if (_state.value.selected == null) return
        _state.value = _state.value.copy(
            messageSearchVisible = true,
            messageSearchQuery = "",
            messageSearchResults = emptyList(),
        )
    }

    fun closeMessageSearch() {
        messageSearchJob?.cancel()
        _state.value = _state.value.copy(
            messageSearchVisible = false,
            messageSearchBusy = false,
            messageSearchQuery = "",
            messageSearchResults = emptyList(),
        )
    }

    fun searchMessages(query: String) {
        val character = _state.value.selected ?: return
        val clean = query.trim()
        messageSearchJob?.cancel()
        _state.value = _state.value.copy(
            messageSearchQuery = query.take(80),
            messageSearchResults = if (clean.isEmpty()) emptyList() else _state.value.messageSearchResults,
            messageSearchBusy = clean.isNotEmpty(),
        )
        if (clean.isEmpty()) return
        messageSearchJob = viewModelScope.launch {
            runCatching { repository.searchMessages(character.id, clean) }
                .onSuccess { results ->
                    _state.value = _state.value.copy(
                        messageSearchResults = results,
                        messageSearchBusy = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        messageSearchBusy = false,
                        status = it.message ?: "搜索失败",
                    )
                }
        }
    }

    fun select(character: CharacterEntity, ensureOpening: Boolean = true) {
        observeJob?.cancel()
        variantJob?.cancel()
        _state.value = _state.value.copy(
            selected = character, messages = emptyList(), memories = emptyList(),
            structuredMemories = emptyList(), turnTraces = emptyList(),
            loadingOlderMessages = false, hasOlderMessages = false,
            ignoredMemoryIds = emptySet(),
            messageVariants = emptyList(), variantMessageId = null,
            branches = emptyList(), branchSheetVisible = false, branchBusy = false,
            messageSearchVisible = false, messageSearchQuery = "",
            messageSearchResults = emptyList(), messageSearchBusy = false,
        )
        observeJob = viewModelScope.launch {
            if (ensureOpening) repository.ensureOpening(character)
            launch {
                repository.observeRecentMessages(character.id).collect { recent ->
                    val current = _state.value.messages
                    val older = if (recent.isEmpty()) {
                        emptyList()
                    } else {
                        current.filter { it.id < recent.first().id }
                    }
                    val merged = (older + recent)
                        .distinctBy { it.id }
                        .sortedBy { it.id }
                    val hasOlder = recent.isNotEmpty() &&
                        repository.hasOlderMessages(character.id, recent.first().id)
                    _state.value = _state.value.copy(
                        messages = merged,
                        hasOlderMessages = hasOlder,
                    )
                }
            }
            launch {
                repository.observeMemories(character.id).collect { memories ->
                    _state.value = _state.value.copy(memories = memories)
                }
            }
            launch {
                repository.observeStructuredMemories(character.id).collect { memories ->
                    _state.value = _state.value.copy(structuredMemories = memories)
                }
            }
            launch {
                repository.observeTurnTraces(character.id).collect { traces ->
                    _state.value = _state.value.copy(turnTraces = traces)
                }
            }
            launch {
                repository.observeBranches(character.id).collect { branches ->
                    _state.value = _state.value.copy(branches = branches)
                }
            }
        }
    }

    fun closeCharacter() {
        observeJob?.cancel()
        variantJob?.cancel()
        _state.value = _state.value.copy(
            selected = null, messages = emptyList(), memories = emptyList(),
            structuredMemories = emptyList(), turnTraces = emptyList(),
            loadingOlderMessages = false, hasOlderMessages = false,
            ignoredMemoryIds = emptySet(),
            messageVariants = emptyList(), variantMessageId = null,
            branches = emptyList(), branchSheetVisible = false, branchBusy = false,
            messageSearchVisible = false, messageSearchQuery = "",
            messageSearchResults = emptyList(), messageSearchBusy = false,
        )
    }

    fun loadOlderMessages() {
        val character = _state.value.selected ?: return
        if (_state.value.loadingOlderMessages || !_state.value.hasOlderMessages) return
        val firstId = _state.value.messages.firstOrNull()?.id ?: return
        _state.value = _state.value.copy(loadingOlderMessages = true)
        viewModelScope.launch {
            runCatching {
                repository.olderMessages(character.id, firstId)
            }.onSuccess { older ->
                val merged = (older + _state.value.messages)
                    .distinctBy { it.id }
                    .sortedBy { it.id }
                val oldestId = merged.firstOrNull()?.id
                _state.value = _state.value.copy(
                    messages = merged,
                    loadingOlderMessages = false,
                    hasOlderMessages = oldestId != null &&
                        repository.hasOlderMessages(character.id, oldestId),
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loadingOlderMessages = false,
                    status = error.message ?: "加载更早消息失败",
                )
            }
        }
    }

    fun send(text: String, imageUri: Uri?) {
        val character = _state.value.selected ?: return
        if (text.isBlank() && imageUri == null) return
        stopRequested = false
        _state.value = _state.value.copy(busy = true, status = null, streamingText = "")
        viewModelScope.launch {
            try {
                repository.send(
                    character = character,
                    text = text.ifBlank { "请看看这张图片。" },
                    imageUri = imageUri,
                    ignoredMemoryIds = _state.value.ignoredMemoryIds,
                    isCancelled = { stopRequested },
                ) { piece ->
                    _state.value = _state.value.copy(streamingText = _state.value.streamingText + piece)
                }
                _state.value = _state.value.copy(
                    busy = false,
                    streamingText = "",
                    todayUsage = repository.todayUsage(),
                    ignoredMemoryIds = emptySet(),
                    status = null,
                )
            } catch (_: CancellationException) {
                _state.value = _state.value.copy(busy = false, streamingText = "", status = "已停止生成")
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    streamingText = "",
                    status = error.message ?: "生成失败",
                )
            }
        }
    }

    fun regenerate(messageId: Long) {
        val character = _state.value.selected ?: return
        if (_state.value.busy || messageId <= 0) return
        stopRequested = false
        _state.value = _state.value.copy(
            busy = true,
            status = "正在重新生成回复…",
            streamingText = "",
        )
        viewModelScope.launch {
            try {
                repository.regenerate(
                    character = character,
                    assistantMessageId = messageId,
                    ignoredMemoryIds = _state.value.ignoredMemoryIds,
                    isCancelled = { stopRequested },
                ) { piece ->
                    _state.value = _state.value.copy(
                        streamingText = _state.value.streamingText + piece,
                    )
                }
                _state.value = _state.value.copy(
                    busy = false,
                    streamingText = "",
                    ignoredMemoryIds = emptySet(),
                    status = "已生成新的回复版本，旧版本仍保留",
                )
            } catch (_: CancellationException) {
                _state.value = _state.value.copy(
                    busy = false,
                    streamingText = "",
                    status = "已停止生成",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    streamingText = "",
                    status = error.message ?: "重新生成失败",
                )
            }
        }
    }

    fun loadMessageVariants(messageId: Long) {
        variantJob?.cancel()
        _state.value = _state.value.copy(
            variantMessageId = messageId,
            messageVariants = emptyList(),
        )
        variantJob = viewModelScope.launch {
            repository.observeMessageVariants(messageId).collect { variants ->
                if (_state.value.variantMessageId == messageId) {
                    _state.value = _state.value.copy(messageVariants = variants)
                }
            }
        }
    }

    fun dismissMessageVariants() {
        variantJob?.cancel()
        _state.value = _state.value.copy(
            variantMessageId = null,
            messageVariants = emptyList(),
        )
    }

    fun selectMessageVariant(variantId: Long) {
        val character = _state.value.selected ?: return
        val messageId = _state.value.variantMessageId ?: return
        viewModelScope.launch {
            runCatching {
                repository.selectMessageVariant(character.id, messageId, variantId)
            }.onSuccess {
                _state.value = _state.value.copy(status = "已切换回复版本")
            }.onFailure {
                _state.value = _state.value.copy(status = it.message ?: "切换回复版本失败")
            }
        }
    }

    fun stop() {
        stopRequested = true
    }

    fun reset() {
        val character = _state.value.selected ?: return
        viewModelScope.launch {
            repository.reset(character.id)
            _state.value = _state.value.copy(
                memories = emptyList(), structuredMemories = emptyList(),
                turnTraces = emptyList(),
                status = "角色对话、记忆和状态已重置",
            )
        }
    }

    fun resetManagedCharacter(character: CharacterEntity) {
        if (_state.value.busy || _state.value.avatarBusy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            runCatching {
                repository.reset(character.id)
            }.onSuccess {
                _state.value = _state.value.copy(
                    busy = false,
                    memories = emptyList(),
                    structuredMemories = emptyList(),
                    turnTraces = emptyList(),
                    status = "${character.name}的对话、记忆和状态已重置",
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    busy = false,
                    status = error.message ?: "重置角色失败",
                )
            }
        }
    }

    fun rollbackToUserMessage(
        messageId: Long,
        onRestored: (String) -> Unit,
    ) {
        val character = _state.value.selected ?: return
        if (_state.value.busy || _state.value.historyBusy) return
        _state.value = _state.value.copy(
            historyBusy = true,
            status = "正在回退对话…",
        )
        viewModelScope.launch {
            runCatching {
                repository.rollbackToUserMessage(character.id, messageId)
            }.onSuccess { restoredText ->
                _state.value = _state.value.copy(
                    historyBusy = false,
                    streamingText = "",
                    status = "对话已回退，所选发言已放回输入框",
                )
                onRestored(restoredText)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    historyBusy = false,
                    status = error.message ?: "回退对话失败",
                )
            }
        }
    }

    fun deleteMemory(memoryId: Long) {
        val character = _state.value.selected ?: return
        viewModelScope.launch {
            repository.deleteMemory(character.id, memoryId)
            _state.value = _state.value.copy(status = "记忆已删除")
        }
    }

    fun editMemory(memoryId: Long, content: String) {
        val character = _state.value.selected ?: return
        viewModelScope.launch {
            runCatching { repository.editMemory(character.id, memoryId, content) }
                .onSuccess { _state.value = _state.value.copy(status = "记忆已修改") }
                .onFailure { _state.value = _state.value.copy(status = it.message ?: "修改失败") }
        }
    }

    fun deleteStructuredMemory(itemId: Long) {
        val character = _state.value.selected ?: return
        if (_state.value.memoryBusy) return
        _state.value = _state.value.copy(memoryBusy = true)
        viewModelScope.launch {
            runCatching {
                repository.deleteStructuredMemory(character.id, itemId)
            }
                .onSuccess {
                    _state.value = _state.value.copy(
                        memoryBusy = false,
                        status = "这项事实及其历史版本已删除",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(memoryBusy = false, status = it.message ?: "更新失败")
                }
        }
    }

    fun toggleStructuredMemoryIgnored(itemId: Long) {
        val current = _state.value.ignoredMemoryIds
        _state.value = _state.value.copy(
            ignoredMemoryIds = if (itemId in current) {
                current - itemId
            } else {
                current + itemId
            },
            status = if (itemId in current) {
                "已恢复这条记忆参与下一轮"
            } else {
                "这条记忆只会忽略下一轮，不会删除"
            },
        )
    }

    fun editStructuredMemory(itemId: Long, content: String) {
        val character = _state.value.selected ?: return
        if (_state.value.memoryBusy) return
        _state.value = _state.value.copy(memoryBusy = true)
        viewModelScope.launch {
            runCatching {
                repository.editStructuredMemory(character.id, itemId, content)
            }.onSuccess {
                _state.value = _state.value.copy(
                    memoryBusy = false,
                    status = "记忆已纠正",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    memoryBusy = false,
                    status = it.message ?: "记忆纠正失败",
                )
            }
        }
    }

    fun setStructuredMemoryPinned(itemId: Long, pinned: Boolean) {
        val character = _state.value.selected ?: return
        if (_state.value.memoryBusy) return
        _state.value = _state.value.copy(memoryBusy = true)
        viewModelScope.launch {
            runCatching {
                repository.setStructuredMemoryPinned(character.id, itemId, pinned)
            }.onSuccess {
                _state.value = _state.value.copy(
                    memoryBusy = false,
                    status = if (pinned) "记忆已钉住" else "已取消钉住",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    memoryBusy = false,
                    status = it.message ?: "钉住状态更新失败",
                )
            }
        }
    }

    fun markStructuredMemoryOutdated(itemId: Long) {
        val character = _state.value.selected ?: return
        if (_state.value.memoryBusy) return
        _state.value = _state.value.copy(memoryBusy = true)
        viewModelScope.launch {
            runCatching {
                repository.markStructuredMemoryOutdated(character.id, itemId)
            }.onSuccess {
                _state.value = _state.value.copy(
                    memoryBusy = false,
                    status = "记忆已标为过时",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    memoryBusy = false,
                    status = it.message ?: "标记失败",
                )
            }
        }
    }

    fun deleteCharacter() {
        val character = _state.value.selected ?: return
        if (_state.value.busy || _state.value.avatarBusy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                repository.deleteCharacter(character.id)
                observeJob?.cancel()
                _state.value = _state.value.copy(
                    selected = null, messages = emptyList(), memories = emptyList(),
                    structuredMemories = emptyList(), turnTraces = emptyList(),
                    busy = false, status = "角色已删除",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(busy = false, status = error.message ?: "删除角色失败")
            }
        }
    }

    fun deleteManagedCharacter(character: CharacterEntity) {
        if (_state.value.busy || _state.value.avatarBusy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                repository.deleteCharacter(character.id)
                if (_state.value.selected?.id == character.id) {
                    observeJob?.cancel()
                    _state.value = _state.value.copy(
                        selected = null,
                        messages = emptyList(),
                        memories = emptyList(),
                        structuredMemories = emptyList(),
                        turnTraces = emptyList(),
                        busy = false,
                        status = "${character.name}已删除",
                    )
                } else {
                    _state.value = _state.value.copy(
                        busy = false,
                        status = "${character.name}已删除",
                    )
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    status = error.message ?: "删除角色失败",
                )
            }
        }
    }

    fun generateCharacter(idea: String, onComplete: (CharacterEntity) -> Unit) {
        if (idea.isBlank()) return
        _state.value = _state.value.copy(busy = true, status = "正在生成角色档案…")
        viewModelScope.launch {
            try {
                val character = repository.generateCharacter(idea)
                _state.value = _state.value.copy(busy = false, status = "角色草稿已保存，请检查后再继续编辑")
                onComplete(character)
            } catch (error: Exception) {
                val rawMessage = error.message.orEmpty()
                val friendlyMessage = if (
                    rawMessage.length > 240 ||
                    rawMessage.startsWith("{") ||
                    rawMessage.contains("\"description\"")
                ) {
                    "角色卡生成未完成，请稍后重试"
                } else {
                    rawMessage.ifBlank { "角色生成失败" }
                }
                _state.value = _state.value.copy(
                    busy = false,
                    status = friendlyMessage,
                )
            }
        }
    }

    fun saveCharacter(character: CharacterEntity, onSaved: (() -> Unit)? = null) {
        viewModelScope.launch {
            runCatching {
                repository.saveCharacter(character)
            }.onSuccess {
                _state.value = _state.value.copy(status = "角色已保存")
                onSaved?.invoke()
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    status = error.message ?: "角色上下文 JSON 格式无效",
                )
            }
        }
    }

    fun importCharacterArtwork(
        character: CharacterEntity,
        uri: Uri,
        backgroundSelection: ArtworkCropSelection,
        avatarSelection: ArtworkCropSelection,
        onImported: (CharacterEntity) -> Unit,
    ) {
        if (_state.value.avatarBusy) return
        _state.value = _state.value.copy(
            avatarBusy = true,
            status = "正在裁剪角色背景和头像…",
        )
        viewModelScope.launch {
            runCatching {
                repository.importCharacterArtwork(
                    character,
                    uri,
                    backgroundSelection,
                    avatarSelection,
                )
            }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        avatarBusy = false,
                        status = "背景和头像已应用并保存",
                    )
                    onImported(updated)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        avatarBusy = false,
                        status = error.message ?: "角色图片导入失败",
                    )
                }
        }
    }

    fun applyAvatarCrop(
        character: CharacterEntity,
        selection: ArtworkCropSelection,
        onApplied: (CharacterEntity) -> Unit,
    ) {
        if (_state.value.avatarBusy) return
        _state.value = _state.value.copy(
            avatarBusy = true,
            status = "正在应用头像裁剪…",
        )
        viewModelScope.launch {
            runCatching {
                repository.applyAvatarCrop(character, selection)
            }.onSuccess { updated ->
                _state.value = _state.value.copy(
                    avatarBusy = false,
                    status = "角色头像裁剪已保存",
                )
                onApplied(updated)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    avatarBusy = false,
                    status = error.message ?: "头像裁剪失败",
                )
            }
        }
    }

    fun setCharacterModelSource(
        characterId: String,
        modelSource: String,
        onSaved: (CharacterEntity) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching { repository.setCharacterModelSource(characterId, modelSource) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        selected = if (_state.value.selected?.id == updated.id) {
                            updated
                        } else {
                            _state.value.selected
                        },
                        status = if (modelSource == MODEL_SOURCE_LOCAL_MNN) {
                            "角色已切换为手机本地 MNN"
                        } else {
                            "角色已切换为云端 API"
                        },
                    )
                    onSaved(updated)
                }
                .onFailure {
                    _state.value = _state.value.copy(status = it.message ?: "模型设置保存失败")
                }
        }
    }

    fun importAvatarModel(uri: Uri) {
        if (_state.value.modelDownloadBusy || _state.value.avatarBusy) return
        _state.value = _state.value.copy(avatarBusy = true, status = "正在导入本地生图模型…")
        viewModelScope.launch {
            runCatching { repository.importAvatarModel(uri) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        avatarBusy = false,
                        avatarModelReady = true,
                        avatarModelBytes = repository.avatarModelBytes(),
                        avatarModelName = repository.avatarModelName(),
                        status = "${repository.avatarModelName()} 生图模型已导入",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        avatarBusy = false,
                        avatarModelReady = repository.avatarModelReady(),
                        status = error.message ?: "生图模型导入失败",
                    )
                }
        }
    }

    fun saveDreamGenerationSettings(settings: DreamGenerationSettings) {
        runCatching { repository.saveDreamGenerationSettings(settings) }
            .onSuccess {
                _state.value = _state.value.copy(
                    dreamGenerationSettings = repository.dreamGenerationSettings(),
                    status = "本地生图参数已保存",
                )
            }
            .onFailure {
                _state.value = _state.value.copy(status = it.message ?: "生图参数保存失败")
            }
    }

    fun deleteAvatarModel() {
        if (_state.value.modelDownloadBusy || _state.value.avatarBusy) return
        _state.value = _state.value.copy(avatarBusy = true, status = "正在删除本地生图模型…")
        viewModelScope.launch {
            runCatching { repository.deleteAvatarModel() }
                .onSuccess {
                    _state.value = _state.value.copy(
                        avatarBusy = false,
                        avatarModelReady = false,
                        avatarModelBytes = 0L,
                        avatarModelName = "Dream SD1.5",
                        status = "本地生图模型已删除",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        avatarBusy = false,
                        avatarModelReady = repository.avatarModelReady(),
                        avatarModelBytes = repository.avatarModelBytes(),
                        avatarModelName = repository.avatarModelName(),
                        status = error.message ?: "本地生图模型删除失败",
                    )
                }
        }
    }

    fun importLocalModel(uri: Uri) {
        if (_state.value.localModelBusy || _state.value.modelDownloadBusy) return
        _state.value = _state.value.copy(
            localModelBusy = true,
            status = "正在导入手机本地 MNN 模型，约需数分钟…",
        )
        viewModelScope.launch {
            runCatching { repository.importLocalModel(uri) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        localModelBusy = false,
                        localModelReady = repository.localModelReady(),
                        localModelBytes = repository.localModelBytes(),
                        localModelName = repository.localModelName(),
                        status = "手机本地宋知意 LoRA 模型已导入",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        localModelBusy = false,
                        localModelReady = repository.localModelReady(),
                        localModelBytes = repository.localModelBytes(),
                        localModelName = repository.localModelName(),
                        status = error.message ?: "本地 MNN 模型导入失败",
                    )
                }
        }
    }

    fun deleteLocalModel() {
        if (_state.value.localModelBusy || _state.value.modelDownloadBusy || _state.value.busy) return
        _state.value = _state.value.copy(
            localModelBusy = true,
            status = "正在删除本地 MNN 模型…",
        )
        viewModelScope.launch {
            runCatching { repository.deleteLocalModel() }
                .onSuccess {
                    _state.value = _state.value.copy(
                        localModelBusy = false,
                        localModelReady = false,
                        localModelBytes = 0L,
                        localModelName = "Qwen3-1.7B MNN",
                        status = "本地 MNN 模型已删除",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        localModelBusy = false,
                        localModelReady = repository.localModelReady(),
                        localModelBytes = repository.localModelBytes(),
                        localModelName = repository.localModelName(),
                        status = error.message ?: "本地 MNN 模型删除失败",
                    )
                }
        }
    }

    fun downloadLocalModel(offerId: String) {
        if (_state.value.modelDownloadBusy || _state.value.localModelBusy ||
            _state.value.avatarBusy
        ) return
        val offer = MnnDownloadCatalog.byId(offerId) ?: return
        if (_state.value.deviceCompatibility?.mnnTextModelSupported != true) {
            _state.value = _state.value.copy(
                status = "当前设备需 ARM64 且至少 6 GB 内存才能安装 MNN 模型",
            )
            return
        }
        _state.value = _state.value.copy(
            modelDownloadBusy = true,
            modelDownloadLabel = "准备下载 ${offer.displayName}",
            modelDownloadProgress = 0f,
            status = null,
        )
        viewModelScope.launch {
            runCatching {
                repository.downloadAndInstallMnn(offer) { label, progress ->
                    _state.value = _state.value.copy(
                        modelDownloadLabel = label,
                        modelDownloadProgress = progress,
                    )
                }
            }
                .onSuccess {
                    _state.value = _state.value.copy(
                        modelDownloadBusy = false,
                        modelDownloadLabel = null,
                        modelDownloadProgress = 1f,
                        localModelReady = repository.localModelReady(),
                        localModelBytes = repository.localModelBytes(),
                        localModelName = repository.localModelName(),
                        status = "${offer.displayName} 已下载并安装",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        modelDownloadBusy = false,
                        modelDownloadLabel = null,
                        localModelReady = repository.localModelReady(),
                        localModelBytes = repository.localModelBytes(),
                        localModelName = repository.localModelName(),
                        status = error.message ?: "模型下载安装失败",
                    )
                }
        }
    }

    fun downloadDreamModel() {
        if (_state.value.modelDownloadBusy || _state.value.avatarBusy ||
            _state.value.localModelBusy
        ) return
        val compatibility = _state.value.deviceCompatibility
            ?: repository.deviceCompatibility()
        if (!compatibility.dreamNpuSupported) {
            _state.value = _state.value.copy(
                status = "当前设备无法确认 Qualcomm Hexagon V68+，请使用千问图片 API",
            )
            return
        }
        _state.value = _state.value.copy(
            modelDownloadBusy = true,
            modelDownloadLabel = "准备下载 ${compatibility.dreamPackageLabel}",
            modelDownloadProgress = 0f,
            status = null,
        )
        viewModelScope.launch {
            runCatching {
                repository.downloadAndInstallDream(compatibility) { label, progress ->
                    _state.value = _state.value.copy(
                        modelDownloadLabel = label,
                        modelDownloadProgress = progress,
                    )
                }
            }
                .onSuccess {
                    _state.value = _state.value.copy(
                        modelDownloadBusy = false,
                        modelDownloadLabel = null,
                        modelDownloadProgress = 1f,
                        avatarModelReady = repository.avatarModelReady(),
                        avatarModelBytes = repository.avatarModelBytes(),
                        avatarModelName = repository.avatarModelName(),
                        status = "MeinaMix V12 已下载并安装",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        modelDownloadBusy = false,
                        modelDownloadLabel = null,
                        avatarModelReady = repository.avatarModelReady(),
                        avatarModelBytes = repository.avatarModelBytes(),
                        avatarModelName = repository.avatarModelName(),
                        status = error.message ?: "本地生图模型下载安装失败",
                    )
                }
        }
    }

    fun generateCloudAvatar(character: CharacterEntity, onComplete: (CharacterEntity) -> Unit) {
        _state.value = _state.value.copy(
            avatarBusy = true,
            imageGenerationActive = true,
            imageGenerationProgress = 0f,
            imageGenerationLabel = "千问图片生成",
            status = "正在通过千问 AI 平台生成角色原图…",
        )
        generateAvatar(character, cloud = true) { updated, _ ->
            onComplete(updated)
        }
    }

    fun generateLocalAvatar(
        character: CharacterEntity,
        onComplete: (CharacterEntity, String?) -> Unit,
    ) {
        _state.value = _state.value.copy(
            avatarBusy = true,
            imageGenerationActive = true,
            imageGenerationProgress = 0f,
            imageGenerationLabel = "Local Dream 生成",
            status = "正在用手机 NPU 运行 MeinaMix V12 生成角色原图…",
        )
        generateAvatar(character, cloud = false, onComplete)
    }

    private fun generateAvatar(
        character: CharacterEntity,
        cloud: Boolean,
        onComplete: (CharacterEntity, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                repository.saveCharacter(character)
                val (updated, sourcePath) = if (cloud) {
                    val cloudResult = repository.generateCloudAvatar(character) { progress ->
                        _state.value = _state.value.copy(
                            imageGenerationProgress = progress,
                        )
                    }
                    cloudResult to cloudResult.backgroundPath
                } else {
                    val generated = repository.generateLocalAvatar(character) { progress ->
                        _state.value = _state.value.copy(
                            imageGenerationProgress = progress,
                        )
                    }
                    generated.character to generated.sourcePath
                }
                _state.value = _state.value.copy(
                    avatarBusy = false,
                    imageGenerationActive = false,
                    imageGenerationProgress = 1f,
                    selected = if (_state.value.selected?.id == updated.id) updated else _state.value.selected,
                    status = "角色原图已生成，请选择背景和头像",
                )
                onComplete(updated, sourcePath)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    avatarBusy = false,
                    imageGenerationActive = false,
                    status = error.message ?: "角色头像生成失败",
                )
            }
        }
    }

    fun applyGeneratedArtwork(
        character: CharacterEntity,
        sourcePath: String,
        backgroundSelection: ArtworkCropSelection,
        avatarSelection: ArtworkCropSelection,
        onComplete: (CharacterEntity) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.applyGeneratedArtwork(
                    character,
                    sourcePath,
                    backgroundSelection,
                    avatarSelection,
                )
            }.onSuccess { updated ->
                _state.value = _state.value.copy(
                    selected = if (_state.value.selected?.id == updated.id) {
                        updated
                    } else {
                        _state.value.selected
                    },
                    status = "角色背景和头像已保存",
                )
                onComplete(updated)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    status = error.message ?: "角色背景和头像保存失败",
                )
            }
        }
    }

    fun clearStatus() {
        _state.value = _state.value.copy(status = null)
    }
}

class ShiyuViewModelFactory(
    private val repository: ChatRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ShiyuViewModel::class.java))
        return ShiyuViewModel(repository) as T
    }
}
