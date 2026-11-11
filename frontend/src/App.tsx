import { useEffect, useMemo, useRef, useState } from "react";
import {
  ApiError,
  deleteCharacter,
  deleteDeepSeekKey,
  deleteOpenCodeKey,
  deleteStructuredMemorySuppression,
  forgetStructuredMemoryTopic,
  getCharacterCard,
  getCharacters,
  getHealth,
  getLocalImageSettings,
  getMemory,
  getProviderSettings,
  getRoleConversation,
  getStructuredMemories,
  getStructuredMemoryStatus,
  getTurnTraces,
  getVoiceProfile,
  generateCharacterAvatar,
  resetRoleConversation,
  reviewStructuredMemory,
  saveDeepSeekKey,
  saveLocalImageModel,
  saveOpenCodeKey,
  saveMemory,
  saveTokenLimits,
  saveVoiceProfile,
  stopGeneration,
  streamChat,
  synthesize,
  testProviderConnection,
  transcribe,
  uploadCharacterAvatar,
  uploadVoiceReferenceAudio,
  uploadImage,
  updateStructuredMemory,
} from "./api";
import type {
  Character,
  CharacterCard,
  CharacterModelSettings,
  Health,
  LocalImageSettings,
  MemorySuppression,
  Message,
  MemoryTopicSuppressionRequest,
  ProviderSettings,
  ProviderName,
  Session,
  SessionMemory,
  StructuredMemoryRecord,
  StructuredMemoryMutation,
  StructuredMemoryStatus,
  TurnTrace,
  VoiceProfile,
} from "./types";

const PORTRAITS: Record<string, string> = {
  "custom-li-bai-v1": "/ui/media/portraits/li-bai.webp",
  "custom-lora-song-zhiyi-v1": "/ui/media/portraits/song-zhiyi.jpg",
  "custom-pei-jiming-v1": "/ui/media/portraits/pei-jiming.jpg",
  "custom-xiao-chengyuan-v1": "/ui/media/portraits/xiao-chengyuan.webp",
};

function characterPortrait(character: Character | null | undefined) {
  if (!character) return "";
  return character.avatar_url || PORTRAITS[character.id] || "";
}

const VISIBLE_PROVIDERS: ProviderName[] = [
  "opencode",
  "deepseek",
];
type CloudProviderName = Extract<ProviderName, "opencode" | "deepseek">;
type BrowserSpeechRecognition = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: {
    resultIndex: number;
    results: ArrayLike<ArrayLike<{ transcript: string }>>;
  }) => void) | null;
  onerror: ((event: { error?: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
};
const LOCAL_MODEL_MODE_OPTIONS: Array<{
  value: CharacterModelSettings["local_mode"];
  label: string;
  provider: ProviderName;
}> = [
  { value: "base", label: "基础模型（不加 LoRA）", provider: "local-base" },
  { value: "half", label: "通用半轮 LoRA（75 步）", provider: "local-half" },
  { value: "full", label: "通用完整 LoRA（150 步）", provider: "local-full" },
  { value: "wukong", label: "孙悟空第一版专属 LoRA", provider: "local-wukong" },
  { value: "xucheng", label: "许澄 half1 专属 LoRA", provider: "local-xucheng" },
  { value: "haruhi", label: "凉宫春日最终专属 LoRA", provider: "local-haruhi" },
];

function initialProvider(): ProviderName {
  try {
    const stored = localStorage.getItem("shiyu-chat-provider");
    return stored === "deepseek" || stored === "opencode" ? stored : "opencode";
  } catch {
    return "opencode";
  }
}

function providerLabel(provider: ProviderName) {
  const labels: Record<ProviderName, string> = {
    opencode: "OpenCode Go",
    deepseek: "DeepSeek API",
    local: "本地 Qwen3-1.7B",
    "local-base": "本地 Qwen3 Base",
    "local-half": "本地 Qwen3 半轮 LoRA",
    "local-full": "本地 Qwen3 完整 LoRA",
    "local-wukong": "本地 Qwen3 · 孙悟空 LoRA",
    "local-xucheng": "本地 Qwen3 · 许澄 LoRA",
    "local-haruhi": "本地 Qwen3 · 凉宫春日 LoRA",
    "local-raw": "Qwen3 · 无提示",
    "local-brief": "Qwen3 · 少量提示",
    "local-full-prompt": "Qwen3 · 完整提示",
  };
  return labels[provider];
}

function providerDescription(provider: ProviderName) {
  const labels: Record<ProviderName, string> = {
    opencode: "云端 API",
    deepseek: "云端 API",
    local: "本机推理",
    "local-base": "未微调基础模型",
    "local-half": "训练 75 步",
    "local-full": "训练 150 步",
    "local-wukong": "第一版孙悟空专属 Adapter",
    "local-xucheng": "许澄第一轮半程 Adapter",
    "local-haruhi": "凉宫春日最终 Adapter",
    "local-raw": "仅用户消息与聊天历史",
    "local-brief": "一句角色约束",
    "local-full-prompt": "角色卡、世界书与完整规则",
  };
  return labels[provider];
}

function isCloudProvider(provider: ProviderName) {
  return provider === "opencode" || provider === "deepseek";
}

function cloudProviderForCharacter(
  preferredProvider: ProviderName,
  settings: ProviderSettings | null,
): CloudProviderName {
  const candidates: CloudProviderName[] = isCloudProvider(preferredProvider)
    ? [preferredProvider, "opencode", "deepseek"]
    : ["opencode", "deepseek"];
  const configured = candidates.find((provider) => {
    const entry = settings?.[provider];
    return Boolean(entry?.available && entry.configured);
  });
  return configured ?? candidates[0];
}

function providerForCharacter(
  character: Character | null,
  preferredProvider: ProviderName,
  settings: ProviderSettings | null,
): ProviderName {
  const modelSettings = character?.model_settings;
  if (modelSettings?.source === "local") {
    return `local-${modelSettings.local_mode}` as ProviderName;
  }
  return cloudProviderForCharacter(preferredProvider, settings);
}

function providerIsReady(provider: ProviderName, settings: ProviderSettings | null) {
  const entry = settings?.[provider];
  return Boolean(entry?.available && entry.configured);
}

function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, string> = {
    spark: "M12 2l1.8 6.2L20 10l-6.2 1.8L12 18l-1.8-6.2L4 10l6.2-1.8Z",
    chat: "M4 4h16v13H9l-5 4Z",
    settings: "M4 7h16M4 17h16M8 4v6m8 4v6",
    plus: "M12 5v14M5 12h14",
    image: "M4 5h16v14H4Zm2 11 4-4 3 3 2-2 3 3M8 9h.01",
    send: "M3 11.5 21 3l-8.5 18-2.2-7.3Z",
    stop: "M7 7h10v10H7Z",
    mic: "M9 5a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0Zm-3 5a6 6 0 0 0 12 0M12 16v4m-4 0h8",
    volume: "M3 10h4l5-4v12l-5-4H3Zm13-1a5 5 0 0 1 0 6m3-9a9 9 0 0 1 0 12",
    memory: "M8 4h8v4h4v8h-4v4H8v-4H4V8h4Zm2 6h4v4h-4Z",
    panel: "M4 4h16v16H4Zm11 0v16M14 8h3M14 12h3M14 16h3",
    close: "m6 6 12 12M6 18 18 6",
    trash: "M5 7h14M9 7V4h6v3m-8 0 1 13h8l1-13",
    mask: "M4 8c4-3 12-3 16 0v5c0 4-4 7-8 7s-8-3-8-7Zm4 3h.01M16 11h.01M9 16c2 1 4 1 6 0",
  };
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name] ?? paths.spark} />
    </svg>
  );
}

function errorText(error: unknown) {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return "操作未能完成";
}

function blankCharacterCard(): CharacterCard {
  return {
    id: `custom-${crypto.randomUUID()}`,
    name: "新角色",
    tagline: "等待你补完的人物",
    description: "",
    appearance: "",
    appearance_prompt: "",
    background: "",
    personality: "",
    speech_style: "",
    relationship: "",
    greeting: "你好。",
    scenario: "",
    motivation: "",
    internal_state_mode: "off",
    model_settings: {
      source: "api",
      local_mode: "base",
    },
    boundaries: "",
    source_notes: "",
    knowledge_boundary: {
      character_kind: "original",
      era: "",
      knowledge_cutoff: "",
      universe: "",
      allowed_knowledge: "",
      forbidden_knowledge: "",
      forbidden_terms: [],
      can_learn_from_user: true,
      allow_everyday_invention: true,
    },
    fixed_facts: [],
    worldbook: [],
    example_dialogue: [],
    generation_preset: {},
    draft_notice: "手动创建角色。请填写并检查全部字段后保存。",
  };
}

function withDefaultModelSettings(card: CharacterCard): CharacterCard {
  return {
    ...card,
    internal_state_mode: card.internal_state_mode ?? "off",
    model_settings: card.model_settings ?? {
      source: "api",
      local_mode: "base",
    },
  };
}

function formatTime(value: string) {
  const date = new Date(value);
  return date.toLocaleString("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function messageText(message: Message) {
  const content = message.content.trim();
  if (!message.display) return content;
  // Four-part messages are historical experiments. Flatten them into the
  // natural single-bubble format and intentionally omit inner thoughts/state.
  const speech = (message.display.speech || content).trim();
  const action = message.display.action?.trim();
  if (action && !speech.startsWith("（") && !speech.startsWith("(")) {
    return `（${action}）${speech}`;
  }
  return speech;
}

function CharacterAvatar({
  character,
  size = "normal",
}: {
  character: Character;
  size?: "small" | "normal" | "large";
}) {
  const src = character.avatar_url || PORTRAITS[character.id];
  const dimensions = size === "small" ? 36 : size === "large" ? 100 : 48;
  return (
    <div className={`character-avatar ${size}`}>
      {src
        ? <img src={src} alt="" width={dimensions} height={dimensions} loading="lazy" />
        : <span>{character.name.slice(0, 1)}</span>}
    </div>
  );
}

function Modal({
  title,
  eyebrow,
  onClose,
  children,
  wide = false,
  page = false,
}: {
  title: string;
  eyebrow?: string;
  onClose: () => void;
  children: React.ReactNode;
  wide?: boolean;
  page?: boolean;
}) {
  const dialogRef = useRef<HTMLElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const closeHandlerRef = useRef(onClose);
  closeHandlerRef.current = onClose;

  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    closeRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeHandlerRef.current();
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) return;
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
      )).filter((element) => !element.hasAttribute("hidden"));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      previous?.focus();
    };
  }, []);

  return (
    <div className={`modal-backdrop ${page ? "page-mode" : ""}`} onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section
        ref={dialogRef}
        className={`modal ${wide ? "wide" : ""} ${page ? "full-page" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="active-modal-title"
      >
        <header>
          <div>
            {eyebrow && <span className="eyebrow">{eyebrow}</span>}
            <h2 id="active-modal-title">{title}</h2>
          </div>
          <button ref={closeRef} className="icon-button" onClick={onClose} aria-label="关闭">
            <Icon name="close" />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

const MEMORY_STATE_LABELS: Record<StructuredMemoryRecord["state"], string> = {
  active: "当前事实",
  candidate: "待核验草稿",
  contested: "存在疑义",
  superseded: "历史旧值",
  deleted: "已删除",
};

const MEMORY_TYPE_LABELS: Record<StructuredMemoryRecord["fact_type"], string> = {
  fact: "事实",
  preference: "偏好",
  plan: "计划",
  hypothesis: "假设",
  opinion: "主观观点",
  event: "共同经历",
  role_claim: "角色自述",
};

const MEMORY_SUBJECT_LABELS: Record<StructuredMemoryRecord["subject"], string> = {
  user: "用户",
  character: "角色",
  shared: "共同",
};

const MEMORY_EPISTEMIC_LABELS: Record<StructuredMemoryRecord["epistemic_status"], string> = {
  explicit: "明确陈述",
  uncertain: "不确定",
  inferred: "模型推断",
  disputed: "存在争议",
};

const MEMORY_AUTHORITY_LABELS: Record<StructuredMemoryRecord["source_authority"], string> = {
  canon: "作者设定",
  user_explicit: "用户明确陈述",
  shared_event: "共同经历",
  character_self_report: "角色自述",
  model_inference: "模型推断",
};

function StructuredMemoryRecordCard({
  record,
  characterName,
  busy,
  onEdit,
  onReject,
  onRestore,
  onDelete,
  onForgetTopic,
}: {
  record: StructuredMemoryRecord;
  characterName: string;
  busy: boolean;
  onEdit: (
    record: StructuredMemoryRecord,
    body: StructuredMemoryMutation,
  ) => Promise<boolean>;
  onReject: (
    record: StructuredMemoryRecord,
    body: StructuredMemoryMutation,
  ) => Promise<boolean>;
  onRestore: (
    record: StructuredMemoryRecord,
    body: StructuredMemoryMutation,
  ) => Promise<boolean>;
  onDelete: (record: StructuredMemoryRecord) => Promise<void>;
  onForgetTopic: (
    record: StructuredMemoryRecord,
    body: MemoryTopicSuppressionRequest,
  ) => Promise<boolean>;
}) {
  const [mode, setMode] = useState<"edit" | "reject" | "forget" | null>(null);
  const [content, setContent] = useState(record.content);
  const [value, setValue] = useState(record.value ?? "");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const disabled = busy || submitting;

  useEffect(() => {
    setContent(record.content);
    setValue(record.value ?? "");
    setReason("");
    setMode(null);
  }, [record.id, record.updated_at, record.content, record.value]);

  function openMode(nextMode: "edit" | "reject" | "forget") {
    setMode(nextMode);
    setReason(
      nextMode === "reject"
        ? "这条记忆与事实不符"
        : nextMode === "forget"
          ? "以后不要记录这个主题"
          : "",
    );
  }

  async function submitEdit() {
    if (!content.trim() || !reason.trim() || disabled) return;
    setSubmitting(true);
    try {
      const saved = await onEdit(record, {
        action: "edit",
        content: content.trim(),
        value: value.trim() || null,
        reason: reason.trim(),
      });
      if (saved) setMode(null);
    } finally {
      setSubmitting(false);
    }
  }

  async function submitReject() {
    if (!reason.trim() || disabled) return;
    setSubmitting(true);
    try {
      const saved = await onReject(record, {
        action: "reject",
        reason: reason.trim(),
      });
      if (saved) setMode(null);
    } finally {
      setSubmitting(false);
    }
  }

  async function restore() {
    if (disabled) return;
    setSubmitting(true);
    try {
      await onRestore(record, {
        action: "restore",
        reason: "用户在记忆设置中恢复此历史值",
      });
    } finally {
      setSubmitting(false);
    }
  }

  async function forgetTopic() {
    if (!reason.trim() || disabled) return;
    setSubmitting(true);
    try {
      const saved = await onForgetTopic(record, {
        subject: record.subject,
        fact_key_prefix: record.fact_key,
        category: record.fact_type,
        reason: reason.trim(),
      });
      if (saved) setMode(null);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <article className={`structured-memory-record ${record.state}`}>
      <div>
        <span>{MEMORY_STATE_LABELS[record.state]}</span>
        <strong>{record.content}</strong>
      </div>
      <div className="memory-record-meta">
        <span>{MEMORY_SUBJECT_LABELS[record.subject]}</span>
        <span>{MEMORY_TYPE_LABELS[record.fact_type]}</span>
        {record.fact_key && <code>{record.fact_key}</code>}
        <span>{MEMORY_EPISTEMIC_LABELS[record.epistemic_status]}</span>
        <span>{MEMORY_AUTHORITY_LABELS[record.source_authority]}</span>
        {record.supersedes && <span>替代记录 #{record.supersedes}</span>}
        {record.contested_with && <span>与 #{record.contested_with} 冲突</span>}
      </div>
      <p>
        {record.value ? `结构化值：${record.value} · ` : ""}
        更新于 {formatTime(record.updated_at)}
      </p>
      {record.semantic_reason && (
        <p className="memory-reason">审核说明：{record.semantic_reason}</p>
      )}
      <details className="memory-evidence">
        <summary>查看来源与证据（{record.evidence.length} 条）</summary>
        {record.evidence.map((item, index) => (
          <blockquote key={`${item.message_id}-${index}`}>
            <span>
              消息 {item.message_id} · {item.role === "user" ? "用户" : characterName}
            </span>
            {item.quote}
          </blockquote>
        ))}
      </details>

      {mode === "edit" && (
        <div className="memory-edit-panel">
          <label className="field">
            <span>修正后的记忆</span>
            <textarea rows={3} value={content} onChange={(event) => setContent(event.target.value)} />
          </label>
          <label className="field">
            <span>结构化值（可留空）</span>
            <input value={value} onChange={(event) => setValue(event.target.value)} />
          </label>
          <label className="field">
            <span>修正原因</span>
            <input value={reason} onChange={(event) => setReason(event.target.value)} />
          </label>
          <p className="memory-edit-note">修正会新建一个版本，旧记录保留在历史中。</p>
          <div className="memory-edit-actions">
            <button onClick={() => setMode(null)} disabled={disabled}>取消</button>
            <button className="primary" onClick={submitEdit} disabled={disabled || !content.trim() || !reason.trim()}>
              {submitting ? "正在保存…" : "保存修正"}
            </button>
          </div>
        </div>
      )}

      {mode === "reject" && (
        <div className="memory-edit-panel">
          <label className="field">
            <span>这条记忆哪里不对？</span>
            <input value={reason} onChange={(event) => setReason(event.target.value)} />
          </label>
          <p className="memory-edit-note">标记错误后会停用此记录，并阻止同一段证据再次生成它。</p>
          <div className="memory-edit-actions">
            <button onClick={() => setMode(null)} disabled={disabled}>取消</button>
            <button className="danger" onClick={submitReject} disabled={disabled || !reason.trim()}>
              {submitting ? "正在处理…" : "确认标记错误"}
            </button>
          </div>
        </div>
      )}

      {mode === "forget" && (
        <div className="memory-edit-panel">
          <p className="memory-edit-note">
            将忽略“{MEMORY_SUBJECT_LABELS[record.subject]} · {MEMORY_TYPE_LABELS[record.fact_type]}”
            这一类后续记忆{record.fact_key ? `（${record.fact_key}）` : ""}。
          </p>
          <label className="field">
            <span>原因</span>
            <input value={reason} onChange={(event) => setReason(event.target.value)} />
          </label>
          <div className="memory-edit-actions">
            <button onClick={() => setMode(null)} disabled={disabled}>取消</button>
            <button className="danger" onClick={forgetTopic} disabled={disabled || !reason.trim()}>
              {submitting ? "正在保存…" : "以后不再记录"}
            </button>
          </div>
        </div>
      )}

      <div className="structured-memory-actions">
        {record.state !== "deleted" && (
          <button disabled={disabled} onClick={() => openMode("edit")}>修正内容</button>
        )}
        {(record.state === "active" || record.state === "candidate" || record.state === "contested") && (
          <button disabled={disabled} onClick={() => openMode("reject")}>标记错误</button>
        )}
        {record.state === "superseded" && (
          <button disabled={disabled} onClick={restore}>
            {submitting ? "正在恢复…" : "恢复此项"}
          </button>
        )}
        {record.state !== "deleted" && (
          <button disabled={disabled} onClick={() => openMode("forget")}>以后不记此主题</button>
        )}
        {record.state !== "deleted" && (
          <button
            className="danger-text"
            disabled={disabled}
            onClick={() => {
              if (window.confirm("删除这条记忆及其历史链？此操作不能撤销。")) {
                void onDelete(record);
              }
            }}
          >
            删除
          </button>
        )}
      </div>
    </article>
  );
}

function EmptyConversation({
  character,
  onStarter,
}: {
  character: Character;
  onStarter: (text: string) => void;
}) {
  return (
    <div className="empty-conversation">
      <CharacterAvatar character={character} size="large" />
      <span className="eyebrow">ONLY ONE STORY</span>
      <h2>和{character.name}的故事，从此刻开始</h2>
      <p>{character.description}</p>
      <div className="starter-grid">
        {(character.starters ?? []).map((starter) => (
          <button key={starter} onClick={() => onStarter(starter)}>
            <Icon name="spark" size={18} />
            <span>{starter}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

export default function App() {
  const [characters, setCharacters] = useState<Character[]>([]);
  const [settings, setSettings] = useState<ProviderSettings | null>(null);
  const [health, setHealth] = useState<Health | null>(null);
  const [preferredProvider, setPreferredProvider] = useState<ProviderName>(initialProvider);
  const [activeCharacter, setActiveCharacter] = useState<Character | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [view, setView] = useState<"discover" | "chat">("discover");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState("");
  const [draft, setDraft] = useState("");
  const [streamText, setStreamText] = useState("");
  const [selectedImage, setSelectedImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState("");
  const [showInspector, setShowInspector] = useState(
    () => typeof window === "undefined" || window.innerWidth > 1180,
  );
  const [showSettings, setShowSettings] = useState(false);
  const [showMemory, setShowMemory] = useState(false);
  const [showVoiceProfile, setShowVoiceProfile] = useState(false);
  const [showTrace, setShowTrace] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [deleteCharacterTarget, setDeleteCharacterTarget] = useState<Character | null>(null);
  const [editingCard, setEditingCard] = useState<CharacterCard | null>(null);
  const [memory, setMemory] = useState<SessionMemory | null>(null);
  const [memoryNotes, setMemoryNotes] = useState("");
  const [keptMemoryItems, setKeptMemoryItems] = useState<Set<number>>(new Set());
  const [structuredMemories, setStructuredMemories] = useState<StructuredMemoryRecord[]>([]);
  const [memorySuppressions, setMemorySuppressions] = useState<MemorySuppression[]>([]);
  const [structuredMemoryStatus, setStructuredMemoryStatus] = useState<StructuredMemoryStatus | null>(null);
  const [structuredMemoryBusy, setStructuredMemoryBusy] = useState(false);
  const [traces, setTraces] = useState<TurnTrace[]>([]);
  const [traceBusy, setTraceBusy] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const avatarFileRef = useRef<HTMLInputElement>(null);
  const messagesRef = useRef<HTMLDivElement>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const browserRecognitionRef = useRef<BrowserSpeechRecognition | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const [recording, setRecording] = useState(false);

  async function reloadBase() {
    const [nextHealth, nextSettings, nextCharacters] = await Promise.all([
      getHealth(),
      getProviderSettings(),
      getCharacters(),
    ]);
    setHealth(nextHealth);
    setSettings(nextSettings);
    setCharacters(nextCharacters);
    return { nextHealth, nextSettings, nextCharacters };
  }

  useEffect(() => {
    reloadBase()
      .catch((error) => setStatus(errorText(error)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const container = messagesRef.current;
    if (!container) return;
    // Scroll only the message viewport. scrollIntoView can move the page and
    // push the fixed chat header off-screen when a short opening is inserted.
    container.scrollTo({
      top: container.scrollHeight,
      behavior: busy || streamText ? "auto" : "smooth",
    });
  }, [session?.messages.length, streamText, busy]);

  useEffect(() => {
    return () => {
      if (imagePreview) URL.revokeObjectURL(imagePreview);
    };
  }, [imagePreview]);

  const activeTargetProvider = providerForCharacter(
    view === "chat" ? activeCharacter : null,
    preferredProvider,
    settings,
  );
  const activeProvider = settings?.[activeTargetProvider];
  const configured = providerIsReady(activeTargetProvider, settings);
  const usagePercent = useMemo(() => {
    const used = settings?.[activeTargetProvider]?.usage.total_tokens ?? 0;
    const limit = settings?.token_limits.daily_token_limit ?? 1;
    return Math.min(100, Math.round((used / limit) * 100));
  }, [activeTargetProvider, settings]);

  async function chooseProvider(provider: ProviderName) {
    if (busy) return;
    setPreferredProvider(provider);
    try {
      localStorage.setItem("shiyu-chat-provider", provider);
    } catch {
      // The selected provider still works for this page session.
    }
    const nextProvider = providerForCharacter(activeCharacter, provider, settings);
    const nextSetting = settings?.[nextProvider];
    if (
      activeCharacter &&
      session &&
      nextSetting?.available &&
      (!isCloudProvider(nextProvider) || nextSetting.configured)
    ) {
      try {
        setStatus(`正在切换到${providerLabel(nextProvider)}…`);
        const nextSession = await getRoleConversation(activeCharacter.id, provider);
        setSession(nextSession);
        setStatus(`已切换到${providerLabel(nextSession.provider)}，后续回答使用该模型`);
      } catch (error) {
        setStatus(errorText(error));
      }
    }
  }

  async function openCharacter(character: Character) {
    const targetProvider = providerForCharacter(
      character,
      preferredProvider,
      settings,
    );
    if (!providerIsReady(targetProvider, settings)) {
      setShowSettings(true);
      setStatus(
        isCloudProvider(targetProvider)
          ? `先配置 ${providerLabel(targetProvider)} API Key，再开始聊天。`
          : `${providerLabel(targetProvider)} 当前不可用，请先在角色档案中选择可用模型。`,
      );
      return;
    }
    setStatus("正在打开这段故事…");
    try {
      const nextSession = await getRoleConversation(character.id, targetProvider);
      setActiveCharacter(character);
      setSession(nextSession);
      setShowInspector(window.innerWidth > 1180);
      setMemory(null);
      setStructuredMemories([]);
      setStructuredMemoryStatus(null);
      setView("chat");
      setStatus("");
      void getMemory(nextSession.id).then(setMemory).catch(() => undefined);
      if (health?.structured_memory_enabled) {
        void Promise.all([
          getStructuredMemories(character.id),
          getStructuredMemoryStatus(character.id),
        ])
          .then(([loadedMemories, loadedStatus]) => {
            setStructuredMemories(loadedMemories.records);
            setMemorySuppressions(loadedMemories.suppressions ?? []);
            setStructuredMemoryStatus(loadedStatus);
          })
          .catch(() => undefined);
      }
    } catch (error) {
      setStatus(errorText(error));
    }
  }

  async function refreshSession() {
    if (!session) return;
    const next = await getRoleConversation(
      session.character_id,
      providerForCharacter(activeCharacter, preferredProvider, settings),
    );
    setSession(next);
  }

  async function sendMessage(textOverride?: string) {
    if (!session || !activeCharacter || busy) return;
    const text = (textOverride ?? draft).trim();
    if (!text) return;
    setBusy(true);
    setStatus("");
    setStreamText("");
    setDraft("");
    const controller = new AbortController();
    abortRef.current = controller;
    let imageId: string | undefined;
    const optimistic: Message = {
      id: -Date.now(),
      role: "user",
      content: text,
      created_at: new Date().toISOString(),
    };
    setSession((current) => current ? {
      ...current,
      messages: [...current.messages, optimistic],
    } : current);
    try {
      if (selectedImage) {
        setStatus("正在准备图片…");
        const uploaded = await uploadImage(session.id, selectedImage);
        imageId = uploaded.id;
      }
      setStatus("正在回应…");
      await streamChat(
        {
          session_id: session.id,
          message: text,
          image_id: imageId,
          max_new_tokens: 768,
          request_id: crypto.randomUUID(),
          timeout_seconds: 180,
        },
        (event) => {
          if (event.event === "delta") {
            setStreamText((current) => current + String(event.data.text ?? ""));
          } else if (event.event === "done") {
            setStatus("");
          } else if (event.event === "error") {
            setStatus(String(event.data.detail ?? "生成失败"));
          }
        },
        controller.signal,
      );
      await refreshSession();
      setStreamText("");
      clearImage();
      await reloadBase();
    } catch (error) {
      if (controller.signal.aborted) setStatus("已停止生成，本轮未保存。");
      else setStatus(errorText(error));
      await refreshSession().catch(() => undefined);
      setStreamText("");
    } finally {
      setBusy(false);
      abortRef.current = null;
    }
  }

  async function stop() {
    if (!session) return;
    abortRef.current?.abort();
    await stopGeneration(session.id).catch(() => undefined);
  }

  function chooseImage(file: File | undefined) {
    if (!file) return;
    if (imagePreview) URL.revokeObjectURL(imagePreview);
    setSelectedImage(file);
    setImagePreview(URL.createObjectURL(file));
  }

  function clearImage() {
    if (imagePreview) URL.revokeObjectURL(imagePreview);
    setSelectedImage(null);
    setImagePreview("");
    if (fileRef.current) fileRef.current.value = "";
  }

  async function chooseAvatar(file: File | undefined) {
    if (!file || !activeCharacter) return;
    setStatus("正在更新角色头像…");
    try {
      await uploadCharacterAvatar(activeCharacter.id, file);
      const refreshed = await reloadBase();
      const updated = refreshed.nextCharacters.find((item) => item.id === activeCharacter.id);
      if (updated) setActiveCharacter(updated);
      setStatus("角色头像已更新");
    } catch (error) {
      setStatus(errorText(error));
    } finally {
      if (avatarFileRef.current) avatarFileRef.current.value = "";
    }
  }

  async function generateAvatar() {
    if (!activeCharacter || busy) return;
    setStatus("正在用本地图片模型生成立绘，首次加载可能需要几分钟…");
    try {
      await generateCharacterAvatar(activeCharacter.id);
      const refreshed = await reloadBase();
      const updated = refreshed.nextCharacters.find(
        (item) => item.id === activeCharacter.id,
      );
      if (updated) setActiveCharacter(updated);
      setStatus("本地立绘已生成并保存");
    } catch (error) {
      setStatus(errorText(error));
    }
  }

  async function openMemory() {
    if (!session || !activeCharacter) return;
    try {
      const [loaded, structured, structuredStatus] = await Promise.all([
        getMemory(session.id),
        health?.structured_memory_enabled
          ? getStructuredMemories(activeCharacter.id)
          : Promise.resolve(null),
        health?.structured_memory_enabled
          ? getStructuredMemoryStatus(activeCharacter.id)
          : Promise.resolve(null),
      ]);
      setMemory(loaded);
      setMemoryNotes(loaded.notes);
      setKeptMemoryItems(new Set(loaded.items.map((_, index) => index)));
      setStructuredMemories(structured?.records ?? []);
      setMemorySuppressions(structured?.suppressions ?? []);
      setStructuredMemoryStatus(structuredStatus);
      setShowMemory(true);
    } catch (error) {
      setStatus(errorText(error));
    }
  }

  async function openTrace() {
    if (!session) return;
    setTraceBusy(true);
    try {
      setTraces(await getTurnTraces(session.id, 20));
      setShowTrace(true);
    } catch (error) {
      setStatus(errorText(error));
    } finally {
      setTraceBusy(false);
    }
  }

  async function refreshStructuredMemories() {
    if (!activeCharacter || !health?.structured_memory_enabled) return;
    const [loaded, nextStatus] = await Promise.all([
      getStructuredMemories(activeCharacter.id),
      getStructuredMemoryStatus(activeCharacter.id),
    ]);
    setStructuredMemories(loaded.records);
    setMemorySuppressions(loaded.suppressions ?? []);
    setStructuredMemoryStatus(nextStatus);
  }

  async function deleteMemoryRecord(record: StructuredMemoryRecord) {
    if (!activeCharacter || structuredMemoryBusy) return;
    setStructuredMemoryBusy(true);
    try {
      await reviewStructuredMemory(activeCharacter.id, {
        action: "delete",
        item_id: record.id,
      });
      await refreshStructuredMemories();
      setStatus("记忆已删除，系统不会用同一段旧消息重新创建它");
    } catch (error) {
      setStatus(errorText(error));
    } finally {
      setStructuredMemoryBusy(false);
    }
  }

  async function mutateMemoryRecord(
    record: StructuredMemoryRecord,
    mutation: StructuredMemoryMutation,
    successMessage: string,
  ): Promise<boolean> {
    if (!activeCharacter || structuredMemoryBusy) return false;
    setStructuredMemoryBusy(true);
    try {
      await updateStructuredMemory(
        activeCharacter.id,
        record.id,
        {
          ...mutation,
          expected_updated_at: record.updated_at,
        },
      );
      await refreshStructuredMemories();
      setStatus(successMessage);
      return true;
    } catch (error) {
      setStatus(errorText(error));
      return false;
    } finally {
      setStructuredMemoryBusy(false);
    }
  }

  async function forgetMemoryTopic(
    body: MemoryTopicSuppressionRequest,
    successMessage: string,
  ): Promise<boolean> {
    if (!activeCharacter || structuredMemoryBusy) return false;
    setStructuredMemoryBusy(true);
    try {
      await forgetStructuredMemoryTopic(activeCharacter.id, body);
      await refreshStructuredMemories();
      setStatus(successMessage);
      return true;
    } catch (error) {
      setStatus(errorText(error));
      return false;
    } finally {
      setStructuredMemoryBusy(false);
    }
  }

  async function allowMemoryTopicAgain(suppression: MemorySuppression) {
    if (!activeCharacter || structuredMemoryBusy) return;
    setStructuredMemoryBusy(true);
    try {
      await deleteStructuredMemorySuppression(activeCharacter.id, suppression.id);
      await refreshStructuredMemories();
      setStatus("已取消该主题的长期忽略规则");
    } catch (error) {
      setStatus(errorText(error));
    } finally {
      setStructuredMemoryBusy(false);
    }
  }

  async function openCharacterEditor() {
    if (!activeCharacter?.custom) {
      setStatus("内置角色档案暂不允许修改。");
      return;
    }
    try {
      const card = await getCharacterCard(activeCharacter.id);
      setEditingCard(card);
      setShowCreate(true);
    } catch (error) {
      setStatus(errorText(error));
    }
  }

  async function confirmDeleteCharacter() {
    const target = deleteCharacterTarget;
    if (!target || busy) return;
    setBusy(true);
    setStatus(`正在删除${target.name}…`);
    try {
      await deleteCharacter(target.id);
      const refreshed = await reloadBase();
      if (activeCharacter?.id === target.id) {
        setActiveCharacter(null);
        setSession(null);
        setMemory(null);
        setStructuredMemories([]);
        setMemorySuppressions([]);
        setStructuredMemoryStatus(null);
        setDraft("");
        setStreamText("");
        clearImage();
        setView("discover");
      }
      setDeleteCharacterTarget(null);
      setEditingCard(null);
      setShowCreate(false);
      setShowMemory(false);
      setStatus(`${target.name}已删除，相关聊天、记忆和素材已清理`);
      if (!refreshed.nextCharacters.some((item) => item.id === target.id)) {
        setCharacters(refreshed.nextCharacters);
      }
    } catch (error) {
      setStatus(errorText(error));
    } finally {
      setBusy(false);
    }
  }

  async function persistMemory() {
    if (!session || !memory) return;
    try {
      const saved = await saveMemory(session.id, {
        notes: memoryNotes,
        auto_enabled: memory.auto_enabled,
        revision: memory.revision,
        kept_items: [...keptMemoryItems].sort((left, right) => left - right),
      });
      setMemory(saved);
      setKeptMemoryItems(new Set(saved.items.map((_, index) => index)));
      setStatus("记忆已保存");
    } catch (error) {
      setStatus(errorText(error));
    }
  }

  async function resetConversation() {
    if (!activeCharacter || !window.confirm(
      `确定重置与${activeCharacter.name}的全部对话、长期记忆、图片和角色状态吗？`,
    )) return;
    try {
      const next = await resetRoleConversation(activeCharacter.id);
      setSession(next);
      setStreamText("");
      setStatus("角色对话已重置");
    } catch (error) {
      setStatus(errorText(error));
    }
  }

  async function playMessage(message: Message) {
    if (!session || !activeCharacter || message.role !== "assistant" || message.id < 0) return;
    const fallbackSpeech = () => {
      if (!("speechSynthesis" in window)) return false;
      const utterance = new SpeechSynthesisUtterance(
        messageText(message)
          .replace(/（[^）]*）/g, "")
          .replace(/\([^)]*\)/g, "")
          .replace(/\s+/g, " ")
          .trim(),
      );
      const voices = window.speechSynthesis.getVoices().filter((voice) => /^zh/i.test(voice.lang));
      if (voices.length) {
        const ordinal = [...activeCharacter.id].reduce((sum, value) => sum + value.charCodeAt(0), 0);
        utterance.voice = voices[ordinal % voices.length];
      }
      utterance.lang = "zh-CN";
      utterance.rate = 1;
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(utterance);
      return true;
    };
    try {
      setStatus("正在合成语音…");
      const url = await synthesize(session.id, message.id);
      const audio = new Audio(url);
      audio.onended = () => URL.revokeObjectURL(url);
      await audio.play();
      setStatus("");
    } catch (error) {
      if (!health?.speech_provider && fallbackSpeech()) {
        setStatus("正在使用系统语音朗读");
      } else {
        setStatus(errorText(error));
      }
    }
  }

  async function toggleRecording() {
    if (recording) {
      browserRecognitionRef.current?.stop();
      recorderRef.current?.stop();
      return;
    }
    const recognitionWindow = window as typeof window & {
      SpeechRecognition?: new () => BrowserSpeechRecognition;
      webkitSpeechRecognition?: new () => BrowserSpeechRecognition;
    };
    const Recognition = recognitionWindow.SpeechRecognition ?? recognitionWindow.webkitSpeechRecognition;
    if (!health?.asr_provider && Recognition) {
      const recognition = new Recognition();
      recognition.lang = "zh-CN";
      recognition.continuous = true;
      recognition.interimResults = false;
      recognition.onresult = (event) => {
        for (let index = event.resultIndex; index < event.results.length; index += 1) {
          const phrase = event.results[index]?.[0]?.transcript?.trim();
          if (phrase) setDraft((current) => current + phrase);
        }
      };
      recognition.onerror = (event) => {
        setStatus(`系统语音识别失败${event.error ? `：${event.error}` : ""}`);
        setRecording(false);
      };
      recognition.onend = () => {
        setRecording(false);
        browserRecognitionRef.current = null;
      };
      browserRecognitionRef.current = recognition;
      try {
        recognition.start();
        setRecording(true);
        setStatus("正在使用系统语音识别");
      } catch (error) {
        browserRecognitionRef.current = null;
        setStatus(errorText(error));
      }
      return;
    }
    if (!health?.asr_provider && !Recognition) {
      setStatus("当前环境没有可用的语音识别；请在网页版配置 Whisper 模型。");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      audioChunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size) audioChunksRef.current.push(event.data);
      };
      recorder.onstop = async () => {
        stream.getTracks().forEach((track) => track.stop());
        setRecording(false);
        const blob = new Blob(audioChunksRef.current, {
          type: recorder.mimeType || "audio/webm",
        });
        try {
          setStatus("正在识别语音…");
          const text = await transcribe(blob);
          setDraft((current) => current + text);
          setStatus("语音已转成文字，请确认后发送");
        } catch (error) {
          setStatus(errorText(error));
        }
      };
      recorder.start();
      recorderRef.current = recorder;
      setRecording(true);
    } catch (error) {
      setStatus(errorText(error));
    }
  }

  async function generatedCharacter(card: CharacterCard) {
    const refreshed = await reloadBase();
    setShowCreate(false);
    const next = refreshed.nextCharacters.find((item) => item.id === card.id);
    if (next) await openCharacter(next);
  }

  if (loading) {
    return (
      <div className="boot-screen">
        <div className="brand-mark">拾</div>
        <p>正在翻开你的故事…</p>
      </div>
    );
  }

  const messages = session?.messages ?? [];
  const featuredCharacter = activeCharacter ?? characters[0] ?? null;
  const featuredPortrait = characterPortrait(featuredCharacter);
  const activeMemories = structuredMemories.filter((item) => item.state === "active");
  const latestMemory = activeMemories[0] ?? null;
  const completedTurns = messages.filter((message) => message.role === "assistant").length;

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-workspace">跳到主要内容</a>
      <aside className="sidebar">
        <button className="brand" onClick={() => setView("discover")}>
          <span className="brand-mark">
            <img src="/ui/icon.png" alt="" width={44} height={44} />
          </span>
          <span><strong>拾语</strong><small>SHIYU</small></span>
        </button>
        <p className="side-intro">每一次对话，只属于一个角色。</p>
        <nav>
          <button
            className={view === "discover" ? "active" : ""}
            aria-current={view === "discover" ? "page" : undefined}
            onClick={() => setView("discover")}
          >
            <Icon name="spark" /> 发现角色
          </button>
          <button onClick={() => {
            setEditingCard(null);
            setShowCreate(true);
          }}>
            <Icon name="plus" /> 创建角色
          </button>
          <button onClick={() => setShowSettings(true)}>
            <Icon name="settings" /> 应用设置
          </button>
        </nav>
        <div className="sidebar-characters">
          <span>角色</span>
          {characters.slice(0, 6).map((character) => (
            <button
              key={character.id}
              className={activeCharacter?.id === character.id ? "selected" : ""}
              onClick={() => openCharacter(character)}
            >
              <CharacterAvatar character={character} size="small" />
              <span><strong>{character.name}</strong><small>{character.tag}</small></span>
            </button>
          ))}
        </div>
        <div className="provider-card">
          <span className={configured ? "online" : ""} />
          <div>
            <strong>{configured ? `${providerLabel(activeTargetProvider)} 已连接` : "模型尚不可用"}</strong>
            <small>
              {providerDescription(activeTargetProvider)}
              {activeProvider?.model_id ? ` · ${activeProvider.model_id}` : ""}
            </small>
          </div>
        </div>
      </aside>

      <main className="workspace" id="main-workspace" tabIndex={-1}>
        {view === "discover" ? (
          <section className="discover">
            <header className="page-top">
              <div className="discovery-heading">
                <h1>发现角色</h1>
                <p>认识一个人，走进另一段故事。</p>
              </div>
              <div>
                <span className="usage-pill">
                  {isCloudProvider(activeTargetProvider)
                    ? `今日 ${activeProvider?.usage.total_tokens ?? 0} tokens`
                    : "本机推理"}
                </span>
                <button
                  className="icon-button"
                  aria-label="应用设置"
                  title="应用设置"
                  onClick={() => setShowSettings(true)}
                >
                  <Icon name="settings" />
                </button>
              </div>
            </header>
            <div className="hero">
              <div className="hero-copy">
                <span className="eyebrow">让故事，从一句话开始</span>
                <h2>不只回复一句话，<br /><em>而是记住这段故事。</em></h2>
                <p>每一次回来，都从这里继续。与喜欢的角色聊天，让共同的经历慢慢积累。</p>
                <div className="hero-proof" aria-label="产品能力">
                  <span>角色卡</span>
                  <span>长期记忆</span>
                  <span>语音</span>
                  <span>图片</span>
                </div>
              </div>
              <div className="hero-portrait" aria-hidden="true">
                {featuredPortrait && (
                  <img
                    src={featuredPortrait}
                    alt=""
                    width={480}
                    height={640}
                    fetchPriority="high"
                  />
                )}
              </div>
              <div className="hero-actions">
                  <button className="primary-button" onClick={() => {
                    setEditingCard(null);
                    setShowCreate(true);
                  }}>
                    <Icon name="plus" /> 创建一位角色
                  </button>
                  <span className="hero-note">一个角色，一段持续的对话</span>
              </div>
            </div>
            <div className="section-heading">
              <div><h2>此刻，想和谁聊聊？</h2><p>熟悉的相遇，未完的故事。</p></div>
              <span>{characters.length} 位角色</span>
            </div>
            {characters.length === 0 ? (
              <section className="empty-character-state">
                <Icon name="mask" size={42} />
                <div>
                  <h3>还没有角色</h3>
                  <p>拾语不再预置角色。创建一位原创人物，系统会为它建立唯一对话和长期记忆。</p>
                </div>
                <button
                  className="primary-button"
                  onClick={() => {
                    setEditingCard(null);
                    setShowCreate(true);
                  }}
                >
                  <Icon name="plus" /> 创建第一位角色
                </button>
              </section>
            ) : (
              <div className="character-grid">
              {characters.map((character, index) => (
                <div key={character.id} className="character-card-shell">
                  <button
                    className={`character-card card-${index % 4}`}
                    onClick={() => openCharacter(character)}
                  >
                    <div className="character-art">
                      <div className="character-cover">
                      {character.avatar_url || PORTRAITS[character.id]
                        ? (
                          <img
                            src={character.avatar_url || PORTRAITS[character.id]}
                            alt=""
                            width={400}
                            height={500}
                            loading="lazy"
                          />
                        )
                        : <div className="custom-art" aria-hidden="true"><div className="cover-emblem"><Icon name="spark" size={32} /></div><span>故事，等你翻开</span></div>}
                      <span className="character-cover-tag">{character.tag}</span>
                      </div>
                      <div className="card-copy">
                        <div className="card-identity">
                          <CharacterAvatar character={character} size="small" />
                          <div><h3>{character.name}</h3><small>{character.profile?.scene || character.tag}</small></div>
                        </div>
                        <p>{character.profile?.line || character.description}</p>
                      </div>
                    </div>
                    <div className="card-footer">
                      <span>{character.profile?.tags?.join(" · ") || "原创 · AI 角色"}</span>
                      <b>进入故事</b>
                    </div>
                  </button>
                  {character.custom && (
                    <button
                      className="character-delete"
                      title={`删除${character.name}`}
                      aria-label={`删除${character.name}`}
                      disabled={busy}
                      onClick={() => setDeleteCharacterTarget(character)}
                    >
                      <Icon name="trash" size={16} />
                    </button>
                  )}
                </div>
              ))}
              </div>
            )}
            <footer className="page-footer">
              AI 的回答可能不准确 · 对话与记忆默认保存在本机 · 当前使用 {providerLabel(activeTargetProvider)}
            </footer>
          </section>
        ) : activeCharacter && session ? (
          <section className={`chat-workbench ${showInspector ? "inspector-open" : ""}`}>
          <section className="chat-screen">
            {characterPortrait(activeCharacter) && (
              <div
                className="chat-background"
                style={{ backgroundImage: `url("${characterPortrait(activeCharacter)}")` }}
              />
            )}
            <header className="chat-top">
              <button
                className="back-button"
                aria-label="返回角色列表"
                onClick={() => setView("discover")}
              >
                ←
              </button>
              <CharacterAvatar character={activeCharacter} size="small" />
              <div className="chat-title">
                <h1>{activeCharacter.name}</h1>
                <p>{activeCharacter.tag} · 唯一对话</p>
              </div>
              <div className="chat-actions">
                <input
                  ref={avatarFileRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  hidden
                  onChange={(event) => void chooseAvatar(event.target.files?.[0])}
                />
                <button onClick={() => avatarFileRef.current?.click()}>
                  <Icon name="image" /> 头像
                </button>
                <button onClick={openMemory}><Icon name="memory" /> 记忆</button>
                <button onClick={openTrace} disabled={traceBusy}>
                  <Icon name="settings" /> {traceBusy ? "读取中…" : "Trace"}
                </button>
                <button onClick={openCharacterEditor}><Icon name="mask" /> 档案</button>
                <button
                  className={showInspector ? "active" : ""}
                  aria-expanded={showInspector}
                  aria-controls="character-inspector"
                  onClick={() => setShowInspector((current) => !current)}
                >
                  <Icon name="panel" /> 详情
                </button>
                <button
                  className="danger-text"
                  aria-label={`重置与${activeCharacter.name}的对话`}
                  title="重置对话"
                  onClick={resetConversation}
                >
                  <Icon name="trash" />
                </button>
              </div>
            </header>
            <div className="messages" ref={messagesRef}>
              {messages.length === 0 && (
                <EmptyConversation character={activeCharacter} onStarter={(text) => setDraft(text)} />
              )}
              {messages.map((message) => (
                <article key={message.id} className={`message ${message.role}`}>
                  {message.role === "assistant" && <CharacterAvatar character={activeCharacter} size="small" />}
                  <div className="message-body">
                    <div className="bubble">
                      {messageText(message).split("\n").map((line, index) => (
                        <span key={index}>{line}<br /></span>
                      ))}
                      {message.image && (
                        <img
                          className="message-image"
                          src={`/images/${message.image.id}?session_id=${session.id}`}
                          alt="用户上传的图片"
                          width={message.image.width}
                          height={message.image.height}
                        />
                      )}
                    </div>
                    <div className="message-meta">
                      <span>{formatTime(message.created_at)}</span>
                      {message.role === "assistant" && (
                        <button onClick={() => playMessage(message)} aria-label="朗读">
                          <Icon name="volume" size={16} /> 朗读
                        </button>
                      )}
                    </div>
                  </div>
                </article>
              ))}
              {busy && (
                <article className="message assistant streaming">
                  <CharacterAvatar character={activeCharacter} size="small" />
                  <div className="message-body">
                    <div className="bubble">
                      {streamText || <span className="typing"><i /><i /><i /></span>}
                    </div>
                  </div>
                </article>
              )}
            </div>
            <div className="composer-wrap">
              {imagePreview && (
                <div className="image-preview">
                  <img src={imagePreview} alt="" width={90} height={90} />
                  <button aria-label="移除待发送图片" onClick={clearImage}>
                    <Icon name="close" size={16} />
                  </button>
                </div>
              )}
                      {status && <p className="status-line" role="status" aria-live="polite">{status}</p>}
              <div className="composer">
                <textarea
                  value={draft}
                  maxLength={4096}
                  rows={2}
                  placeholder={`和${activeCharacter.name}说点什么…`}
                  onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" && !event.shiftKey) {
                      event.preventDefault();
                      sendMessage();
                    }
                  }}
                />
                <div className="composer-actions">
                  <div>
                    {activeProvider?.supports_images && (
                      <>
                        <input
                          ref={fileRef}
                          type="file"
                          accept="image/jpeg,image/png,image/gif,image/webp"
                          hidden
                          onChange={(event) => chooseImage(event.target.files?.[0])}
                        />
                        <button
                          className="icon-button"
                          onClick={() => fileRef.current?.click()}
                          title="上传图片"
                          aria-label="上传图片"
                        >
                          <Icon name="image" />
                        </button>
                      </>
                    )}
                    <button
                      className={`icon-button ${recording ? "recording" : ""}`}
                      onClick={toggleRecording}
                      title="语音输入"
                      aria-label={recording ? "停止语音输入" : "开始语音输入"}
                      aria-pressed={recording}
                    >
                      <Icon name="mic" />
                    </button>
                  </div>
                  {busy ? (
                    <button className="stop-button" onClick={stop}><Icon name="stop" /> 停止</button>
                  ) : (
                    <button className="send-button" disabled={!draft.trim()} onClick={() => sendMessage()}>
                      发送 <Icon name="send" />
                    </button>
                  )}
                </div>
              </div>
              <p className="composer-note">
                Enter 发送 · Shift + Enter 换行 · 当前模型：{providerLabel(activeTargetProvider)}
              </p>
            </div>
          </section>
          {showInspector && (
            <aside
              className="character-inspector"
              id="character-inspector"
              aria-label={`${activeCharacter.name}角色信息`}
            >
              <button
                className="inspector-close"
                aria-label="收起角色信息"
                onClick={() => setShowInspector(false)}
              >
                <Icon name="close" size={17} />
              </button>
              <div className="inspector-portrait">
                {characterPortrait(activeCharacter) ? (
                  <img
                    src={characterPortrait(activeCharacter)}
                    alt={`${activeCharacter.name}角色立绘`}
                    width={480}
                    height={640}
                    loading="lazy"
                  />
                ) : (
                  <span>{activeCharacter.name.slice(0, 1)}</span>
                )}
                <div className="inspector-portrait-copy">
                  <span>{activeCharacter.tag}</span>
                  <h2>{activeCharacter.name}</h2>
                </div>
              </div>
              <div className="inspector-copy">
                <p>{activeCharacter.description}</p>
                <div className="inspector-metrics">
                  <div><strong>{completedTurns}</strong><span>轮对话</span></div>
                  <div><strong>{activeMemories.length}</strong><span>条记忆</span></div>
                </div>
                <section className="inspector-section">
                  <div className="inspector-section-heading">
                    <span>共同记忆</span>
                    <button onClick={openMemory}>管理</button>
                  </div>
                  <p>
                    {latestMemory?.content
                      || "共同经历会在聊天后自动沉淀，不需要逐条确认。"}
                  </p>
                </section>
                <section className="inspector-section">
                  <div className="inspector-section-heading">
                    <span>当前语境</span>
                    <button onClick={openTrace}>Trace</button>
                  </div>
                  <dl className="inspector-facts">
                    <div><dt>模型</dt><dd>{providerLabel(activeTargetProvider)}</dd></div>
                    <div><dt>关系</dt><dd>由共同经历逐步发展</dd></div>
                    <div><dt>状态</dt><dd>数值仅用于生成，不直接展示</dd></div>
                  </dl>
                </section>
                <div className="inspector-actions">
                  <button onClick={openCharacterEditor}>
                    <Icon name="mask" size={17} /> 编辑角色
                  </button>
                  <button onClick={() => avatarFileRef.current?.click()}>
                    <Icon name="image" size={17} /> 导入图片
                  </button>
                  <button onClick={() => setShowVoiceProfile(true)}>
                    <Icon name="volume" size={17} /> 角色音色
                  </button>
                  <button onClick={generateAvatar}>
                    <Icon name="spark" size={17} /> 本地生成立绘
                  </button>
                </div>
                <p className="inspector-note">
                  对话与记忆保存在本机。云端模式只发送本轮需要的上下文。
                </p>
              </div>
            </aside>
          )}
          </section>
        ) : null}
      </main>

      {showSettings && (
        <SettingsModal
          settings={settings}
          health={health}
          provider={activeTargetProvider}
          onProviderChange={chooseProvider}
          onClose={() => setShowSettings(false)}
          onSaved={async () => {
            const refreshed = await reloadBase();
            const targetProvider = providerForCharacter(
              activeCharacter,
              preferredProvider,
              refreshed.nextSettings,
            );
            const selected = refreshed.nextSettings[targetProvider];
            if (
              activeCharacter &&
              session &&
              selected?.available &&
              (!isCloudProvider(targetProvider) || selected.configured)
            ) {
              const nextSession = await getRoleConversation(activeCharacter.id, targetProvider);
              setSession(nextSession);
            }
            setStatus("设置已保存");
          }}
          usagePercent={usagePercent}
        />
      )}
      {showVoiceProfile && activeCharacter && (
        <VoiceProfileModal
          character={activeCharacter}
          onClose={() => setShowVoiceProfile(false)}
        />
      )}
      {showMemory && activeCharacter && session && memory && (
        <Modal
          title={`${activeCharacter.name}的记忆`}
          eyebrow="LONG-TERM MEMORY"
          onClose={() => setShowMemory(false)}
          page
        >
          <div className="memory-summary">
            <strong>{memory.items.length}</strong>
            <span>条来自早期对话的原文记忆</span>
          </div>
          <label className="field">
            <span>用户修订</span>
            <textarea value={memoryNotes} onChange={(event) => setMemoryNotes(event.target.value)} rows={4} />
          </label>
          <label className="memory-auto">
            <input
              type="checkbox"
              checked={memory.auto_enabled}
              onChange={(event) => setMemory((current) => current ? {
                ...current,
                auto_enabled: event.target.checked,
              } : current)}
            />
            <span>允许在长对话中自动整理早期记忆</span>
          </label>
          <div className="memory-list">
            {memory.items.length === 0 && <p className="empty-copy">长对话产生早期摘要后，这里会显示可追溯的原文记忆。</p>}
            {memory.items.map((item, index) => (
              <label key={`${item.message_id}-${item.quote}`}>
                <input
                  type="checkbox"
                  checked={keptMemoryItems.has(index)}
                  onChange={(event) => {
                    setKeptMemoryItems((current) => {
                      const next = new Set(current);
                      if (event.target.checked) next.add(index);
                      else next.delete(index);
                      return next;
                    });
                  }}
                />
                <span>{item.role === "user" ? "用户" : activeCharacter.name}</span>
                <p>{item.quote}</p>
              </label>
            ))}
          </div>
          {health?.structured_memory_enabled && (
            <section className="structured-memory-section">
              <div className="setting-heading">
                <div>
                  <h3>结构化长期记忆</h3>
                  <p>聊天过程中由后台自动整理，不需要逐条确认。你可以删除或直接在对话中更正；删除一项事实会同时移除它的历史版本。</p>
                  {structuredMemoryStatus && (
                    <p className="memory-status">
                      {(structuredMemoryStatus.jobs.failed ?? 0) > 0
                        ? "部分记忆整理暂时失败，聊天记录和已有记忆不受影响"
                        : (structuredMemoryStatus.jobs.running ?? 0) > 0
                        ? "正在整理最近的对话"
                        : (structuredMemoryStatus.jobs.queued ?? 0) > 0
                          ? "最近的对话等待后台整理"
                          : "后台记忆已整理到最新"}
                      {structuredMemoryStatus.usage_today.calls > 0
                        ? ` · 今日 ${structuredMemoryStatus.usage_today.total_tokens.toLocaleString()} / ${structuredMemoryStatus.daily_token_limit.toLocaleString()} Token`
                        : ""}
                    </p>
                  )}
                </div>
                <button className="secondary-button" disabled={structuredMemoryBusy} onClick={refreshStructuredMemories}>
                  {structuredMemoryBusy ? "读取中…" : "刷新"}
                </button>
              </div>
              <div className="memory-list structured-memory-list">
                {structuredMemories.length === 0 && (
                  <p className="empty-copy">新一轮聊天完成后，系统会在后台自动整理值得长期保留的信息。</p>
                )}
                {structuredMemories.map((record) => (
                  <StructuredMemoryRecordCard
                    key={record.id}
                    record={record}
                    characterName={activeCharacter.name}
                    busy={structuredMemoryBusy}
                    onEdit={(target, body) => mutateMemoryRecord(
                      target,
                      body,
                      "记忆已修正，新版本已进入当前上下文",
                    )}
                    onReject={(target, body) => mutateMemoryRecord(
                      target,
                      body,
                      "错误记忆已停用，并已阻止同一证据再次生成",
                    )}
                    onRestore={(target, body) => mutateMemoryRecord(
                      target,
                      body,
                      "历史值已恢复为新的当前版本",
                    )}
                    onDelete={deleteMemoryRecord}
                    onForgetTopic={(_, body) => forgetMemoryTopic(
                      body,
                      "该主题已加入长期忽略列表",
                    )}
                  />
                ))}
              </div>
              {memorySuppressions.length > 0 && (
                <div className="memory-suppressions">
                  <h4>不再记录的主题</h4>
                  <p>这些规则只阻止以后继续提取同类信息，不会恢复已经删除的记忆。</p>
                  {memorySuppressions.map((item) => (
                    <div className="memory-suppression-row" key={item.id}>
                      <span>
                        {item.subject ? MEMORY_SUBJECT_LABELS[item.subject] : "全部主体"}
                        {item.category ? ` · ${MEMORY_TYPE_LABELS[item.category]}` : ""}
                        {item.fact_key_prefix ? ` · ${item.fact_key_prefix}` : ""}
                      </span>
                      <small>{item.reason}</small>
                      <button
                        disabled={structuredMemoryBusy}
                        onClick={() => allowMemoryTopicAgain(item)}
                      >
                        取消忽略
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}
          <div className="modal-actions">
            <button className="secondary-button" onClick={() => setShowMemory(false)}>关闭</button>
            <button className="primary-button" onClick={persistMemory}>保存修订</button>
          </div>
        </Modal>
      )}
      {showTrace && session && (
        <Modal
          title="最近回答 Trace"
          eyebrow="LOCAL DEBUG METADATA"
          onClose={() => setShowTrace(false)}
          wide
        >
          <p className="modal-lead">
            这里只显示本机保存的运行元数据，不保存 API Key；Prompt 以哈希、记忆 ID 和检索分数表示。
          </p>
          <div className="memory-list">
            {traces.length === 0 && <p className="empty-copy">完成一轮聊天后，这里会出现 Trace。</p>}
            {traces.map((trace) => (
              <details className="structured-memory-record" key={trace.run_id}>
                <summary>
                  {formatTime(trace.created_at)} · {trace.model} · {trace.prompt_version}
                </summary>
                <pre className="trace-json">
                  {JSON.stringify({
                    run_id: trace.run_id,
                    provider: trace.provider,
                    model: trace.model,
                    prompt_version: trace.prompt_version,
                    prompt_hash: trace.prompt_hash,
                    role_version: trace.role_version,
                    generation_params: trace.generation_params,
                    input_tokens: trace.input_tokens,
                    output_tokens: trace.output_tokens,
                    latency_ms: trace.latency_ms,
                    metadata: trace.trace.metadata,
                    usage: trace.trace.usage,
                    background_memory: trace.trace.background_memory,
                    user_message_id: trace.user_message_id,
                    assistant_message_id: trace.assistant_message_id,
                  }, null, 2)}
                </pre>
              </details>
            ))}
          </div>
          <div className="modal-actions">
            <span className="spacer" />
            <button className="primary-button" onClick={() => setShowTrace(false)}>关闭</button>
          </div>
        </Modal>
      )}
      {showCreate && (
        <CreateCharacterModal
          initialCard={editingCard}
          provider={preferredProvider}
          settings={settings}
          onClose={() => {
            setShowCreate(false);
            setEditingCard(null);
          }}
          onSaved={generatedCharacter}
        />
      )}
      {deleteCharacterTarget && (
        <Modal
          title={`删除${deleteCharacterTarget.name}？`}
          eyebrow="DELETE CHARACTER"
          onClose={() => !busy && setDeleteCharacterTarget(null)}
        >
          <p>
            这会永久删除该角色的聊天、长期记忆、内部状态、头像记录和 App
            生成素材。手机相册中的原图不会被删除，此操作无法撤销。
          </p>
          <div className="modal-actions">
            <button className="secondary-button" disabled={busy} onClick={() => setDeleteCharacterTarget(null)}>
              取消
            </button>
            <button className="stop-button" disabled={busy} onClick={confirmDeleteCharacter}>
              {busy ? "正在删除…" : "永久删除"}
            </button>
          </div>
        </Modal>
      )}
      {!configured && !showSettings && (
        <div className="setup-banner">
          <span>{isCloudProvider(activeTargetProvider)
            ? `使用 ${providerLabel(activeTargetProvider)} 前需要填写自己的 API Key`
            : `${providerLabel(activeTargetProvider)} 当前不可用`}</span>
          <button onClick={() => setShowSettings(true)}>打开设置</button>
        </div>
      )}
    </div>
  );
}

function VoiceProfileModal({
  character,
  onClose,
}: {
  character: Character;
  onClose: () => void;
}) {
  const [profile, setProfile] = useState<VoiceProfile | null>(null);
  const [displayName, setDisplayName] = useState(`${character.name} · 音色`);
  const [voiceStyle, setVoiceStyle] = useState(character.tag);
  const [referenceText, setReferenceText] = useState("");
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [message, setMessage] = useState("正在读取角色音色档案…");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getVoiceProfile(character.id)
      .then((loaded) => {
        setProfile(loaded);
        setDisplayName(loaded.display_name);
        setVoiceStyle(loaded.voice_style);
        setReferenceText(loaded.reference_text);
        setMessage(
          loaded.has_reference_audio
            ? "参考音频已配置，可用于 CosyVoice 2 或 GPT-SoVITS。"
            : "请先保存档案，再上传与逐字稿完全一致的 WAV 录音。",
        );
      })
      .catch((error) => setMessage(errorText(error)));
  }, [character.id]);

  async function save() {
    if (!voiceStyle.trim() || saving) return;
    setSaving(true);
    setMessage("正在保存音色档案…");
    try {
      const saved = await saveVoiceProfile(character.id, {
        display_name: displayName.trim() || `${character.name} · 音色`,
        voice_style: voiceStyle.trim(),
        reference_text: referenceText.trim(),
        language: "zh",
      });
      setProfile(saved);
      if (audioFile) {
        setMessage("正在上传参考音频…");
        await uploadVoiceReferenceAudio(character.id, audioFile);
        const refreshed = await getVoiceProfile(character.id);
        setProfile(refreshed);
        setAudioFile(null);
        setMessage("音色档案和参考音频已保存。");
      } else {
        setMessage("音色档案已保存。");
      }
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title={`${character.name}的音色`}
      eyebrow="LOCAL VOICE PROFILE"
      onClose={onClose}
    >
      <p className="modal-lead">
        这里是本地零样本音色档案。CosyVoice 2 会使用这段录音和逐字稿，
        生成符合角色身份的声音；音频只保存在本机。
      </p>
      <div className="draft-notice">
        如果要形成不同的角色音色，需要每个角色提供一段本人或已获授权的参考音频。
        如果只要调整情绪、语速和语气，可以复用同一段授权录音，再修改声音描述。
      </div>
      <label className="field">
        <span>音色名称</span>
        <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
      </label>
      <label className="field">
        <span>声音描述</span>
        <input
          value={voiceStyle}
          placeholder="例如：语气温柔，语速稍慢，情绪自然克制"
          onChange={(event) => setVoiceStyle(event.target.value)}
        />
      </label>
      <label className="field">
        <span>参考音频逐字稿</span>
        <textarea
          rows={3}
          value={referenceText}
          placeholder="必须与上传录音逐字对应，包含标点。"
          onChange={(event) => setReferenceText(event.target.value)}
        />
      </label>
      <label className="field">
        <span>参考音频（3–10 秒单声道 WAV）</span>
        <input
          type="file"
          accept=".wav,audio/wav"
          onChange={(event) => setAudioFile(event.target.files?.[0] ?? null)}
        />
        <small className="field-hint">
          当前档案：{profile?.has_reference_audio ? "已有参考音频" : "尚未上传参考音频"}
        </small>
      </label>
      {message && <p className="form-message" role="status" aria-live="polite">{message}</p>}
      <div className="modal-actions">
        <button className="secondary-button" onClick={onClose}>关闭</button>
        <button className="primary-button" disabled={saving || !voiceStyle.trim()} onClick={save}>
          {saving ? "正在保存…" : "保存音色档案"}
        </button>
      </div>
    </Modal>
  );
}

function SettingsModal({
  settings,
  health,
  provider,
  onProviderChange,
  onClose,
  onSaved,
  usagePercent,
}: {
  settings: ProviderSettings | null;
  health: Health | null;
  provider: ProviderName;
  onProviderChange: (provider: ProviderName) => void | Promise<void>;
  onClose: () => void;
  onSaved: () => Promise<void>;
  usagePercent: number;
}) {
  const [openCodeKey, setOpenCodeKey] = useState("");
  const [deepSeekKey, setDeepSeekKey] = useState("");
  const [requestLimit, setRequestLimit] = useState(settings?.token_limits.request_token_limit ?? 12000);
  const [dailyLimit, setDailyLimit] = useState(settings?.token_limits.daily_token_limit ?? 200000);
  const [localImage, setLocalImage] = useState<LocalImageSettings | null>(null);
  const [localImagePath, setLocalImagePath] = useState("");
  const [message, setMessage] = useState("");
  const [testingProvider, setTestingProvider] = useState<CloudProviderName | null>(null);
  const [providerTestMessage, setProviderTestMessage] = useState("");
  const active = settings?.[provider];
  const cosyVoiceReady = Boolean(health?.speech_provider?.includes("CosyVoice"));

  useEffect(() => {
    getLocalImageSettings()
      .then((loaded) => {
        setLocalImage(loaded);
        setLocalImagePath(loaded.model_path);
      })
      .catch(() => setLocalImage(null));
  }, []);

  async function save() {
    try {
      if (openCodeKey.trim()) await saveOpenCodeKey(openCodeKey.trim());
      if (deepSeekKey.trim()) await saveDeepSeekKey(deepSeekKey.trim());
      await saveTokenLimits(requestLimit, dailyLimit);
      setOpenCodeKey("");
      setDeepSeekKey("");
      setMessage("设置已保存");
      await onSaved();
    } catch (error) {
      setMessage(errorText(error));
    }
  }

  async function removeKey(target: CloudProviderName) {
    if (!window.confirm(`从这台电脑删除 ${providerLabel(target)} API Key？`)) return;
    try {
      if (target === "opencode") await deleteOpenCodeKey();
      else await deleteDeepSeekKey();
      setMessage(`${providerLabel(target)} API Key 已删除`);
      await onSaved();
    } catch (error) {
      setMessage(errorText(error));
    }
  }

  async function testConnection(target: CloudProviderName) {
    if (testingProvider) return;
    setTestingProvider(target);
    setProviderTestMessage(`正在测试 ${providerLabel(target)}…`);
    try {
      const result = await testProviderConnection(target);
      setProviderTestMessage(
        `${providerLabel(target)} 连接成功 · ${result.model_id} · ${result.seconds}s`,
      );
    } catch (error) {
      setProviderTestMessage(`${providerLabel(target)}：${errorText(error)}`);
    } finally {
      setTestingProvider(null);
    }
  }

  async function saveImageModel() {
    try {
      setMessage("正在检查本地图片模型…");
      const saved = await saveLocalImageModel(localImagePath.trim());
      setLocalImage(saved);
      setMessage(saved.available ? "本地图片模型已就绪" : "本地图片模型不可用");
    } catch (error) {
      setMessage(errorText(error));
    }
  }

  return (
    <Modal title="应用设置" eyebrow="MODEL & LIMITS" onClose={onClose} page>
      <section className="setting-section image-model-settings">
        <div className="setting-heading">
          <div>
            <h3>MeinaMix V12 本地生图</h3>
            <p>
              使用本机 Diffusers 模型生成角色立绘。图片不会发送给任何云端图片 API，提示词也只在本机处理。
            </p>
          </div>
          <span className={`provider-state ${localImage?.available ? "ready" : ""}`}>
            {localImage?.available ? "已就绪" : "未配置"}
          </span>
        </div>
        <label className="field">
          <span>模型目录或单文件路径</span>
          <input
            value={localImagePath}
            spellCheck={false}
            placeholder="outputs/models/meinamix-v12"
            onChange={(event) => setLocalImagePath(event.target.value)}
          />
          <small className="field-hint">
            支持完整 Diffusers 目录，或 `.safetensors`、`.ckpt` 单文件。模型权重不随应用分发。
          </small>
        </label>
        <button className="secondary-button" onClick={saveImageModel}>
          保存并检测模型
        </button>
      </section>
      <section className="setting-section voice-model-settings">
        <div className="setting-heading">
          <div>
            <h3>CosyVoice 2 角色语音</h3>
            <p>
              每个角色可以使用自己的参考音频和声音描述；服务不可用时自动回退到其他本地语音。
            </p>
          </div>
          <span className={`provider-state ${cosyVoiceReady ? "ready" : ""}`}>
            {cosyVoiceReady ? "已配置" : "未启用"}
          </span>
        </div>
        <div className="provider-facts">
          <span>当前链路 <strong>{health?.speech_provider || "未配置"}</strong></span>
          <span>音频仅保存在本机</span>
          <span>在角色详情的“角色音色”中配置</span>
        </div>
      </section>
      <section className="setting-section">
        <div className="setting-heading">
          <div>
            <h3>默认云端 API</h3>
            <p>没有单独选择本地模型的角色，会使用这里的 API。每个角色的选择保存在角色档案中。</p>
          </div>
          <span className={`provider-state ${active?.available && active?.configured ? "ready" : ""}`}>
            {active?.available && active?.configured ? "可用" : "不可用"}
          </span>
        </div>
        <div className="provider-choice" role="group" aria-label="聊天 Provider">
          {VISIBLE_PROVIDERS.map((name) => {
            const option = settings?.[name];
            return (
              <button
                key={name}
                className={provider === name ? "active" : ""}
                onClick={() => void onProviderChange(name)}
              >
                <strong>{providerLabel(name)}</strong>
                <small>{option?.available && option?.configured ? providerDescription(name) : "当前不可用"}</small>
              </button>
            );
          })}
        </div>
        {providerTestMessage && (
          <p className="form-message" role="status" aria-live="polite">
            {providerTestMessage}
          </p>
        )}
      </section>
      <section className="setting-section">
        <div className="setting-heading">
          <div>
            <h3>OpenCode Go API</h3>
            <p>Key 只在 FastAPI 后端使用，加密保存在本机。</p>
          </div>
          <span className={`provider-state ${settings?.opencode.configured ? "ready" : ""}`}>
            {settings?.opencode.configured ? "已配置" : "未配置"}
          </span>
        </div>
        <label className="field">
          <span>{settings?.opencode.configured ? "替换 API Key（留空则不变）" : "OpenCode Go API Key"}</span>
          <input
            type="password"
            value={openCodeKey}
            placeholder="oc_sk_..."
            autoComplete="off"
            onChange={(event) => setOpenCodeKey(event.target.value)}
          />
        </label>
        <div className="provider-facts">
          <span>模型 <strong>{settings?.opencode.model_id ?? "deepseek-v4.1-flash"}</strong></span>
          <span>支持文字、图片</span>
          <span>OpenAI 兼容接口</span>
        </div>
        <button
          className="secondary-button"
          disabled={!settings?.opencode.configured || testingProvider !== null}
          onClick={() => void testConnection("opencode")}
        >
          {testingProvider === "opencode" ? "正在测试…" : "测试 OpenCode 连接"}
        </button>
        {settings?.opencode.configured && (
          <button className="danger-text" onClick={() => removeKey("opencode")}>删除 OpenCode Key</button>
        )}
      </section>
      <section className="setting-section">
        <div className="setting-heading">
          <div>
            <h3>DeepSeek 备用接口</h3>
            <p>保留原有调用路径，可在上方切换后使用。</p>
          </div>
          <span className={`provider-state ${settings?.deepseek.configured ? "ready" : ""}`}>
            {settings?.deepseek.configured ? "已配置" : "未配置"}
          </span>
        </div>
        <label className="field">
          <span>{settings?.deepseek.configured ? "替换 API Key（留空则不变）" : "DeepSeek API Key"}</span>
          <input
            type="password"
            value={deepSeekKey}
            placeholder="sk-..."
            autoComplete="off"
            onChange={(event) => setDeepSeekKey(event.target.value)}
          />
        </label>
        <div className="provider-facts">
          <span>模型 <strong>{settings?.deepseek.model_id ?? "deepseek-chat"}</strong></span>
          <span>支持文字、图片</span>
        </div>
        <button
          className="secondary-button"
          disabled={!settings?.deepseek.configured || testingProvider !== null}
          onClick={() => void testConnection("deepseek")}
        >
          {testingProvider === "deepseek" ? "正在测试…" : "测试 DeepSeek 连接"}
        </button>
        {settings?.deepseek.configured && (
          <button className="danger-text" onClick={() => removeKey("deepseek")}>删除 DeepSeek Key</button>
        )}
      </section>
      <section className="setting-section">
        <div className="setting-heading">
          <div><h3>Token 限额</h3><p>防止误操作产生过高费用。</p></div>
        </div>
        <div className="two-fields">
          <label className="field">
            <span>单次请求上限</span>
            <input
              type="number"
              min={256}
              value={requestLimit}
              onChange={(event) => setRequestLimit(Number(event.target.value))}
            />
          </label>
          <label className="field">
            <span>每日上限</span>
            <input
              type="number"
              min={1000}
              value={dailyLimit}
              onChange={(event) => setDailyLimit(Number(event.target.value))}
            />
          </label>
        </div>
        <div className="usage-line">
          <span>
            {isCloudProvider(provider)
              ? `${providerLabel(provider)} 今日已用 ${active?.usage.total_tokens ?? 0} tokens`
              : `${providerLabel(provider)} 在本机 GPU 上推理`}
          </span>
          {isCloudProvider(provider) && <div><i style={{ width: `${usagePercent}%` }} /></div>}
        </div>
      </section>
      <section className="setting-section compact">
        <h3>本地模型按角色生效</h3>
        <p>在角色档案中选择“使用本地模型”，再决定使用基础模型、半轮 LoRA 或完整 LoRA。该设置只影响当前角色，其他角色仍按各自档案选择运行模型。</p>
      </section>
      {message && <p className="form-message" role="status" aria-live="polite">{message}</p>}
      <div className="modal-actions">
        <span className="spacer" />
        <button className="secondary-button" onClick={onClose}>取消</button>
        <button className="primary-button" onClick={save}>保存设置</button>
      </div>
    </Modal>
  );
}

function CreateCharacterModal({
  initialCard,
  provider,
  settings,
  onClose,
  onSaved,
}: {
  initialCard?: CharacterCard | null;
  provider: ProviderName;
  settings: ProviderSettings | null;
  onClose: () => void;
  onSaved: (card: CharacterCard) => Promise<void>;
}) {
  const startingCard = initialCard ? withDefaultModelSettings(initialCard) : null;
  const [inspiration, setInspiration] = useState("");
  const [card, setCard] = useState<CharacterCard | null>(startingCard);
  const [rawCard, setRawCard] = useState(startingCard ? JSON.stringify(startingCard, null, 2) : "");
  const [status, setStatus] = useState("");
  const [generating, setGenerating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [avatarChoice, setAvatarChoice] = useState<"local" | "upload" | "blank">("blank");
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState("");
  const [localImageReady, setLocalImageReady] = useState<boolean | null>(null);

  useEffect(() => {
    if (card) setRawCard(JSON.stringify(card, null, 2));
  }, [card]);

  useEffect(() => {
    getLocalImageSettings()
      .then((loaded) => setLocalImageReady(loaded.available))
      .catch(() => setLocalImageReady(false));
  }, []);

  useEffect(() => {
    if (!avatarFile) {
      setAvatarPreview("");
      return;
    }
    const url = URL.createObjectURL(avatarFile);
    setAvatarPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [avatarFile]);

  async function generate() {
    if (!inspiration.trim()) return;
    setGenerating(true);
    setStatus(`${providerLabel(provider)} 正在整理角色档案…`);
    try {
      const response = await fetch("/characters/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ inspiration, provider }),
      });
      const body = await response.json();
      if (!response.ok) throw new ApiError(response.status, body.detail ?? body);
      setCard(body.card);
      const missing = [
        !String(body.card.appearance ?? "").trim() ? "外貌描述" : "",
        !String(body.card.appearance_prompt ?? "").trim() ? "英文生图提示词" : "",
        !Array.isArray(body.card.worldbook) || body.card.worldbook.length === 0 ? "世界书" : "",
      ].filter(Boolean);
      setStatus(
        missing.length
          ? `草稿已生成，但缺少${missing.join("、")}，请在保存前补全。`
          : "草稿已生成，外貌、生图提示词和世界书已补齐。",
      );
    } catch (error) {
      setStatus(errorText(error));
    } finally {
      setGenerating(false);
    }
  }

  function patch<K extends keyof CharacterCard>(key: K, value: CharacterCard[K]) {
    setCard((current) => current ? { ...current, [key]: value } : current);
  }

  function patchBoundary<K extends keyof CharacterCard["knowledge_boundary"]>(
    key: K,
    value: CharacterCard["knowledge_boundary"][K],
  ) {
    if (!card) return;
    setCard({ ...card, knowledge_boundary: { ...card.knowledge_boundary, [key]: value } });
  }

  function patchModelSettings<K extends keyof CharacterModelSettings>(
    key: K,
    value: CharacterModelSettings[K],
  ) {
    if (!card) return;
    setCard({
      ...card,
      model_settings: {
        ...card.model_settings,
        [key]: value,
      },
    });
  }

  function patchWorldbook(
    index: number,
    values: Partial<CharacterCard["worldbook"][number]>,
  ) {
    setCard((current) => current ? {
      ...current,
      worldbook: current.worldbook.map((entry, entryIndex) => (
        entryIndex === index ? { ...entry, ...values } : entry
      )),
    } : current);
  }

  function addWorldbookEntry() {
    setCard((current) => current ? {
      ...current,
      worldbook: [
        ...current.worldbook,
        {
          title: "新的世界书条目",
          keywords: [],
          content: "",
          priority: 8,
          enabled: true,
        },
      ],
    } : current);
  }

  function removeWorldbookEntry(index: number) {
    setCard((current) => current ? {
      ...current,
      worldbook: current.worldbook.filter((_, entryIndex) => entryIndex !== index),
    } : current);
  }

  async function save() {
    if (!card || saving) return;
    setSaving(true);
    setStatus("正在保存角色…");
    try {
      const response = await fetch("/characters", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ card }),
      });
      const body = await response.json();
      if (!response.ok) throw new ApiError(response.status, body.detail ?? body);
      if (avatarChoice === "upload" && avatarFile) {
        setStatus("角色已保存，正在上传头像…");
        await uploadCharacterAvatar(body.card.id, avatarFile);
      } else if (avatarChoice === "local") {
        setStatus("角色已保存，正在用 MeinaMix V12 生成立绘…");
        await generateCharacterAvatar(body.card.id, body.card.appearance_prompt);
      }
      await onSaved(body.card);
    } catch (error) {
      setStatus(errorText(error));
    } finally {
      setSaving(false);
    }
  }

  if (!card) {
    return (
      <Modal title="创建一位角色" eyebrow="CHARACTER STUDIO" onClose={onClose} page>
        <p className="modal-lead">
          写下身份、时代、关系和说话方式。生成内容只是草稿，正式事实需要你确认。
        </p>
        <label className="field">
          <span>角色灵感</span>
          <textarea
            rows={5}
            maxLength={1800}
            value={inspiration}
            placeholder="例如：李白身处唐代；以诗酒和游历为生活重心；说话豪放、敏锐；不能知道现代科技和后世历史。"
            onChange={(event) => setInspiration(event.target.value)}
          />
        </label>
        {status && <p className="form-message" role="status" aria-live="polite">{status}</p>}
        <div className="modal-actions">
          <button className="secondary-button" onClick={() => setCard(blankCharacterCard())}>
            手动创建
          </button>
          <button className="secondary-button" onClick={onClose}>取消</button>
          <button className="primary-button" disabled={generating || !inspiration.trim()} onClick={generate}>
            {generating ? "正在生成…" : "生成角色草稿"}
          </button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal title="检查角色档案" eyebrow="REVIEW BEFORE SAVING" onClose={onClose} wide page>
      <div className="draft-notice">{card.draft_notice || "请检查所有正式事实后再保存。"}</div>
      <div className="form-grid">
        <label className="field"><span>名字</span><input value={card.name} onChange={(e) => patch("name", e.target.value)} /></label>
        <label className="field"><span>一句话介绍</span><input value={card.tagline} onChange={(e) => patch("tagline", e.target.value)} /></label>
        <label className="field full"><span>简介</span><textarea rows={2} value={card.description} onChange={(e) => patch("description", e.target.value)} /></label>
        <label className="field full">
          <span>外貌描述（中文）</span>
          <textarea
            rows={3}
            value={card.appearance ?? ""}
            placeholder="年龄感、脸型、眼睛、发型、服装和整体气质"
            onChange={(e) => patch("appearance", e.target.value)}
          />
        </label>
        <label className="field full">
          <span>本地生图提示词（英文）</span>
          <textarea
            rows={2}
            value={card.appearance_prompt ?? ""}
            placeholder="1girl, female, solo, 20 year old East Asian woman…"
            onChange={(e) => patch("appearance_prompt", e.target.value)}
          />
          <small className="field-hint">
            MeinaMix / SD1.5 的文本编码窗口为 77 Token。网页端会先保留主体、年龄、发型、眼睛和服装，再自动压缩过长提示词。
          </small>
        </label>
        <label className="field full"><span>背景</span><textarea rows={3} value={card.background} onChange={(e) => patch("background", e.target.value)} /></label>
        <label className="field"><span>性格</span><textarea rows={3} value={card.personality} onChange={(e) => patch("personality", e.target.value)} /></label>
        <label className="field"><span>说话方式</span><textarea rows={3} value={card.speech_style} onChange={(e) => patch("speech_style", e.target.value)} /></label>
        <label className="field"><span>与用户的关系</span><textarea rows={2} value={card.relationship} onChange={(e) => patch("relationship", e.target.value)} /></label>
        <label className="field"><span>开场白</span><textarea rows={2} value={card.greeting} onChange={(e) => patch("greeting", e.target.value)} /></label>
      </div>

      <h3 className="form-section-title">角色立绘</h3>
      <section className="character-avatar-choice" aria-label="角色立绘来源">
        <label className={`model-source-option ${avatarChoice === "local" ? "active" : ""}`}>
          <input
            type="radio"
            name={`avatar-source-${card.id}`}
            checked={avatarChoice === "local"}
            disabled={localImageReady === false}
            onChange={() => setAvatarChoice("local")}
          />
          <span>
            <strong>本地模型生成立绘</strong>
            <small>
              {localImageReady === null
                ? "正在检查 MeinaMix V12…"
                : localImageReady
                  ? "保存角色后生成立绘；首次加载需要一些时间。"
                  : "本地生图模型尚未准备，请先在应用设置中配置。"}
            </small>
          </span>
        </label>
        <label className={`model-source-option ${avatarChoice === "upload" ? "active" : ""}`}>
          <input
            type="radio"
            name={`avatar-source-${card.id}`}
            checked={avatarChoice === "upload"}
            onChange={() => setAvatarChoice("upload")}
          />
          <span>
            <strong>使用自己的图片</strong>
            <small>图片仅保存到本机角色资源目录。</small>
          </span>
        </label>
        <label className={`model-source-option ${avatarChoice === "blank" ? "active" : ""}`}>
          <input
            type="radio"
            name={`avatar-source-${card.id}`}
            checked={avatarChoice === "blank"}
            onChange={() => setAvatarChoice("blank")}
          />
          <span>
            <strong>暂时空着</strong>
            <small>先创建角色，之后可在角色详情中补充立绘。</small>
          </span>
        </label>
        {avatarChoice === "upload" && (
          <label className="field avatar-upload-field">
            <span>选择角色图片</span>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={(event) => setAvatarFile(event.target.files?.[0] ?? null)}
            />
          </label>
        )}
        {avatarPreview && (
          <img className="avatar-choice-preview" src={avatarPreview} alt="待上传的角色立绘预览" />
        )}
      </section>

      <h3 className="form-section-title">世界书</h3>
      <section className="worldbook-editor-list">
        {card.worldbook.length === 0 && (
          <div className="worldbook-empty">
            <p>还没有世界书。角色创建会自动生成；也可以现在手动添加。</p>
          </div>
        )}
        {card.worldbook.map((entry, index) => (
          <div className="worldbook-editor" key={`${entry.id ?? "worldbook"}-${index}`}>
            <div className="worldbook-editor-heading">
              <strong>条目 {index + 1}</strong>
              <button
                type="button"
                className="danger-text"
                onClick={() => removeWorldbookEntry(index)}
              >
                删除
              </button>
            </div>
            <div className="form-grid">
              <label className="field">
                <span>标题</span>
                <input
                  value={entry.title}
                  onChange={(event) => patchWorldbook(index, { title: event.target.value })}
                />
              </label>
              <label className="field">
                <span>触发关键词（用逗号分隔）</span>
                <input
                  value={entry.keywords.join("，")}
                  onChange={(event) => patchWorldbook(index, {
                    keywords: event.target.value
                      .split(/[，,\n]/)
                      .map((value) => value.trim())
                      .filter(Boolean),
                  })}
                />
              </label>
              <label className="field full">
                <span>内容</span>
                <textarea
                  rows={2}
                  value={entry.content}
                  onChange={(event) => patchWorldbook(index, { content: event.target.value })}
                />
              </label>
            </div>
          </div>
        ))}
        <button type="button" className="secondary-button" onClick={addWorldbookEntry}>
          添加世界书条目
        </button>
      </section>

      <h3 className="form-section-title">模型运行方式</h3>
      <section className="character-model-settings">
        <label className={`model-source-option ${card.model_settings.source === "api" ? "active" : ""}`}>
          <input
            type="radio"
            name="character-model-source"
            checked={card.model_settings.source === "api"}
            onChange={() => patchModelSettings("source", "api")}
          />
          <span>
            <strong>云端大模型 API</strong>
            <small>
              默认使用应用设置中的 {providerLabel(provider)}
              {settings?.[provider]?.configured ? "，当前已配置" : "，尚未配置 API Key"}
            </small>
          </span>
        </label>
        <label className={`model-source-option ${card.model_settings.source === "local" ? "active" : ""}`}>
          <input
            type="radio"
            name="character-model-source"
            checked={card.model_settings.source === "local"}
            onChange={() => patchModelSettings("source", "local")}
          />
          <span>
            <strong>本地 Qwen3-1.7B</strong>
            <small>在本机 GPU 推理；只有当前角色会使用这里的本地模型与 LoRA 设置。</small>
          </span>
        </label>
        {card.model_settings.source === "local" && (
          <label className="field">
            <span>本地运行模式</span>
            <select
              value={card.model_settings.local_mode}
              onChange={(event) => patchModelSettings(
                "local_mode",
                event.target.value as CharacterModelSettings["local_mode"],
              )}
            >
              {LOCAL_MODEL_MODE_OPTIONS.map((option) => (
                <option
                  key={option.value}
                  value={option.value}
                  disabled={!settings?.[option.provider]?.available}
                >
                  {option.label}
                </option>
              ))}
            </select>
            <small className="field-hint">
              LoRA 只在该角色的会话中启用；切换到其他角色时恢复各自设置。本地模式不支持图片上传。
            </small>
          </label>
        )}
      </section>
      <h3 className="form-section-title">内部状态</h3>
      <label className="field">
        <span>普通聊天如何使用心情和关系状态</span>
        <select
          value={card.internal_state_mode ?? "off"}
          onChange={(event) => patch(
            "internal_state_mode",
            event.target.value as CharacterCard["internal_state_mode"],
          )}
        >
          <option value="off">关闭持久状态（推荐）</option>
          <option value="lightweight">轻量事件上下文</option>
          <option value="legacy">完整数值状态（实验）</option>
        </select>
        <small className="field-hint">
          关闭和轻量模式都不会隐藏用户边界或本轮即时情绪。完整数值状态只用于对照实验。
        </small>
      </label>
      <h3 className="form-section-title">认知边界</h3>
      <div className="form-grid">
        <label className="field">
          <span>角色类型</span>
          <select
            value={card.knowledge_boundary.character_kind}
            onChange={(e) => patchBoundary("character_kind", e.target.value as CharacterCard["knowledge_boundary"]["character_kind"])}
          >
            <option value="original">原创角色</option>
            <option value="historical">真实历史人物</option>
            <option value="fictional_canon">已有虚构作品角色</option>
          </select>
        </label>
        <label className="field"><span>时代 / 世界</span><input value={card.knowledge_boundary.era} onChange={(e) => patchBoundary("era", e.target.value)} /></label>
        <label className="field"><span>知识截止</span><input value={card.knowledge_boundary.knowledge_cutoff} onChange={(e) => patchBoundary("knowledge_cutoff", e.target.value)} /></label>
        <label className="field"><span>作品 / 世界范围</span><input value={card.knowledge_boundary.universe} onChange={(e) => patchBoundary("universe", e.target.value)} /></label>
        <label className="field full"><span>允许知道的内容</span><textarea rows={3} value={card.knowledge_boundary.allowed_knowledge} onChange={(e) => patchBoundary("allowed_knowledge", e.target.value)} /></label>
        <label className="field full"><span>禁止知道的内容</span><textarea rows={3} value={card.knowledge_boundary.forbidden_knowledge} onChange={(e) => patchBoundary("forbidden_knowledge", e.target.value)} /></label>
        <label className="field full">
          <span>禁止知识关键词</span>
          <input
            value={card.knowledge_boundary.forbidden_terms.join("，")}
            placeholder="Python，人工智能，智能手机"
            onChange={(e) => patchBoundary("forbidden_terms", e.target.value.split(/[，,\n]/).map((v) => v.trim()).filter(Boolean))}
          />
        </label>
        <label className="check-field">
          <input type="checkbox" checked={card.knowledge_boundary.can_learn_from_user} onChange={(e) => patchBoundary("can_learn_from_user", e.target.checked)} />
          <span>用户解释后，角色可以作为“听用户说的”理解</span>
        </label>
        <label className="check-field">
          <input type="checkbox" checked={card.knowledge_boundary.allow_everyday_invention} onChange={(e) => patchBoundary("allow_everyday_invention", e.target.checked)} />
          <span>允许角色虚构普通日常细节</span>
        </label>
      </div>
      <label className="field">
        <span>完整 JSON（高级编辑）</span>
        <textarea
          className="code-area"
          rows={10}
          value={rawCard}
          onChange={(event) => {
            setRawCard(event.target.value);
          }}
          onBlur={() => {
            try {
              setCard(withDefaultModelSettings(JSON.parse(rawCard)));
              setStatus("JSON 格式有效");
            } catch {
              setStatus("JSON 格式暂时无效");
            }
          }}
        />
      </label>
      {status && <p className="form-message" role="status" aria-live="polite">{status}</p>}
      <div className="modal-actions">
        <button className="secondary-button" onClick={onClose}>取消</button>
        <button className="primary-button" disabled={saving} onClick={save}>
          {saving ? "正在保存…" : "保存角色"}
        </button>
      </div>
    </Modal>
  );
}
