package com.shiyu.chat.ui

import android.net.Uri
import com.shiyu.chat.data.ArtworkCropSelection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiyu.chat.data.CharacterEntity
import com.shiyu.chat.data.ChatRepository
import com.shiyu.chat.data.DeviceModelCompatibility
import com.shiyu.chat.data.Limits
import com.shiyu.chat.data.MemoryEntity
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
    val selected: CharacterEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val structuredMemories: List<StructuredMemoryEntity> = emptyList(),
    val turnTraces: List<TurnTraceEntity> = emptyList(),
    val cloudProvider: String = "deepseek",
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
    val status: String? = null,
)

class ShiyuViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(ShiyuUiState())
    val state: StateFlow<ShiyuUiState> = _state.asStateFlow()
    private var stopRequested = false
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureDefaults()
            repository.ensureBuiltInCharacters()
            val provider = repository.cloudProvider()
            _state.value = _state.value.copy(
                cloudProvider = provider,
                showDevProviders = repository.showDevProviders,
                apiConfigured = !repository.activeApiKey().isNullOrBlank(),
                openCodeConfigured = !repository.openCodeApiKey().isNullOrBlank(),
                deepSeekConfigured = !repository.apiKey().isNullOrBlank(),
                imageApiConfigured = repository.imageApiKeyConfigured(),
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
            )
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

    fun select(character: CharacterEntity) {
        observeJob?.cancel()
        _state.value = _state.value.copy(
            selected = character, messages = emptyList(), memories = emptyList(),
            structuredMemories = emptyList(), turnTraces = emptyList(),
        )
        observeJob = viewModelScope.launch {
            repository.ensureOpening(character)
            launch {
                repository.observeMessages(character.id).collect { messages ->
                    _state.value = _state.value.copy(messages = messages)
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
        }
    }

    fun closeCharacter() {
        observeJob?.cancel()
        _state.value = _state.value.copy(
            selected = null, messages = emptyList(), memories = emptyList(),
            structuredMemories = emptyList(), turnTraces = emptyList(),
        )
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
                    isCancelled = { stopRequested },
                ) { piece ->
                    _state.value = _state.value.copy(streamingText = _state.value.streamingText + piece)
                }
                _state.value = _state.value.copy(
                    busy = false,
                    streamingText = "",
                    todayUsage = repository.todayUsage(),
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

    fun generateCharacter(idea: String, onComplete: (CharacterEntity) -> Unit) {
        if (idea.isBlank()) return
        _state.value = _state.value.copy(busy = true, status = "正在生成角色档案…")
        viewModelScope.launch {
            try {
                val character = repository.generateCharacter(idea)
                _state.value = _state.value.copy(busy = false, status = "角色草稿已保存，请检查后再继续编辑")
                onComplete(character)
            } catch (error: Exception) {
                _state.value = _state.value.copy(busy = false, status = error.message ?: "角色生成失败")
            }
        }
    }

    fun saveCharacter(character: CharacterEntity, onSaved: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.saveCharacter(character)
            _state.value = _state.value.copy(status = "角色已保存")
            onSaved?.invoke()
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
                        status = "背景和头像已应用，保存角色后生效",
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
        _state.value = _state.value.copy(avatarBusy = true, status = "正在通过千问 AI 平台生成角色头像…")
        generateAvatar(character, cloud = true, onComplete)
    }

    fun generateLocalAvatar(character: CharacterEntity, onComplete: (CharacterEntity) -> Unit) {
        _state.value = _state.value.copy(
            avatarBusy = true,
            status = "正在用手机 NPU 运行 MeinaMix V12 生成角色头像…",
        )
        generateAvatar(character, cloud = false, onComplete)
    }

    private fun generateAvatar(
        character: CharacterEntity,
        cloud: Boolean,
        onComplete: (CharacterEntity) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                repository.saveCharacter(character)
                val updated = if (cloud) {
                    repository.generateCloudAvatar(character)
                } else {
                    repository.generateLocalAvatar(character)
                }
                _state.value = _state.value.copy(
                    avatarBusy = false,
                    selected = if (_state.value.selected?.id == updated.id) updated else _state.value.selected,
                    status = "角色头像已保存",
                )
                onComplete(updated)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    avatarBusy = false,
                    status = error.message ?: "角色头像生成失败",
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
