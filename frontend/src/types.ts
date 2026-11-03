export type ProviderName =
  | "opencode"
  | "deepseek"
  | "local"
  | "local-base"
  | "local-half"
  | "local-full"
  | "local-wukong"
  | "local-xucheng"
  | "local-haruhi"
  | "local-raw"
  | "local-brief"
  | "local-full-prompt";

export interface CharacterProfile {
  line?: string;
  tags?: string[];
  scene?: string;
  about?: string;
}

export type CharacterModelSource = "api" | "local";
export type CharacterLocalMode =
  | "base"
  | "half"
  | "full"
  | "wukong"
  | "xucheng"
  | "haruhi";

export interface CharacterModelSettings {
  source: CharacterModelSource;
  local_mode: CharacterLocalMode;
}

export interface Character {
  id: string;
  name: string;
  tag: string;
  color: string;
  description: string;
  greeting: string;
  starters: string[];
  profile?: CharacterProfile;
  custom?: boolean;
  avatar_url?: string | null;
  gender?: string;
  identity?: Record<string, unknown>;
  model_settings?: CharacterModelSettings;
}

export interface ImageAttachment {
  id: string;
  mime_type: string;
  width: number;
  height: number;
}

export interface RoleplayDisplay {
  version: number;
  speech: string;
  inner_thought: string;
  action: string;
  state?: {
    mood: string;
    affection: number;
    relationship: string;
  };
}

export interface Message {
  id: number;
  role: "user" | "assistant";
  content: string;
  created_at: string;
  image?: ImageAttachment;
  display?: RoleplayDisplay | null;
}

export interface Session {
  id: string;
  character_id: string;
  provider: ProviderName;
  model_id: string;
  model_mode: CharacterLocalMode | "lora";
  messages: Message[];
  created_at: string;
  updated_at: string;
}

export interface ProviderSettingsEntry {
  available: boolean;
  configured: boolean;
  model_id: string | null;
  model_mode?: CharacterLocalMode | "lora";
  prompt_mode?: "raw" | "brief" | "full";
  supports_text: boolean;
  supports_images: boolean;
  usage: Usage;
}

export interface ProviderSettings {
  default_provider: ProviderName;
  deepseek: ProviderSettingsEntry;
  opencode: ProviderSettingsEntry;
  local: ProviderSettingsEntry;
  "local-base": ProviderSettingsEntry;
  "local-half": ProviderSettingsEntry;
  "local-full": ProviderSettingsEntry;
  "local-wukong": ProviderSettingsEntry;
  "local-xucheng": ProviderSettingsEntry;
  "local-haruhi": ProviderSettingsEntry;
  "local-raw": ProviderSettingsEntry;
  "local-brief": ProviderSettingsEntry;
  "local-full-prompt": ProviderSettingsEntry;
  token_limits: {
    request_token_limit: number;
    daily_token_limit: number;
  };
}

export interface Usage {
  prompt_tokens: number;
  completion_tokens: number;
  cached_tokens: number;
  total_tokens: number;
  calls: number;
  usage_day: string;
}

export interface Health {
  status: "ready" | "model_unavailable";
  app_version?: string;
  default_provider: ProviderName;
  model_id: string | null;
  providers: Record<string, {
    available: boolean;
    configured?: boolean;
    model_id: string | null;
    supports_images: boolean;
  }>;
  token_limits: { request: number; daily: number };
  deepseek_usage: Usage;
  opencode_usage: Usage;
  structured_memory_enabled?: boolean;
  structured_memory_mode?: "automatic" | "manual" | "disabled";
  structured_memory_jobs?: Record<string, number>;
  memory_daily_token_limit?: number;
  speech_provider: string | null;
  asr_provider: string | null;
}

export interface MemoryItem {
  message_id: number;
  role: "user" | "assistant";
  quote: string;
}

export interface SessionMemory {
  items: MemoryItem[];
  covered_message_id: number;
  notes: string;
  auto_enabled: boolean;
  revision: number;
  excluded_message_ids?: number[];
}

export interface StructuredMemoryEvidence {
  message_id: number;
  role: "user" | "assistant";
  quote: string;
}

export interface StructuredMemoryRecord {
  id: number;
  character_id: string;
  subject: "user" | "character" | "shared";
  fact_key: string | null;
  value: string | null;
  content: string;
  kind: "user_report" | "character_detail" | "role_claim" | "shared_event" | "belief" | "plan";
  fact_type: "fact" | "preference" | "plan" | "hypothesis" | "opinion" | "event" | "role_claim";
  epistemic_status: "explicit" | "uncertain" | "inferred" | "disputed";
  source_authority: "canon" | "user_explicit" | "shared_event" | "character_self_report" | "model_inference";
  knowledge_source: "author" | "user_told" | "character_told" | "observed" | "inferred";
  known_at: string | null;
  visibility: "character_private";
  semantic_reason: string;
  evidence: StructuredMemoryEvidence[];
  event_date: string | null;
  observed_order: number;
  semantic_verified: boolean;
  state: "candidate" | "active" | "contested" | "superseded" | "deleted";
  supersedes: number | null;
  contested_with: number | null;
  auto_action: string | null;
  auto_reason: string | null;
  origin_job_id: number | null;
  created_at: string;
  updated_at: string;
}

export interface TurnTrace {
  run_id: string;
  session_id: string;
  character_id: string;
  user_message_id: number | null;
  assistant_message_id: number | null;
  provider: string;
  model: string;
  prompt_version: string;
  prompt_hash: string;
  role_version: string;
  generation_params: Record<string, unknown>;
  trace: {
    metadata?: {
      worldbook_ids?: string[];
      strict_knowledge_mode?: boolean;
      structured_memory_ids?: number[];
      session_memory_ids?: number[];
      memory_retrieval_mode?: string | null;
      memory_retrieval_scores?: Array<Record<string, unknown>>;
      structured_memory_chars?: number;
      internal_state?: Record<string, unknown> | null;
      repeat_sentences_removed?: number;
      memory_warning?: string | null;
      image_id?: string | null;
      response_source?: string;
    };
    usage?: Record<string, unknown>;
    background_memory?: Record<string, unknown>;
  };
  input_tokens: number;
  output_tokens: number;
  latency_ms: number | null;
  status: string;
  created_at: string;
}

export interface StructuredMemoryList {
  character_id: string;
  records: StructuredMemoryRecord[];
  suppressions?: MemorySuppression[];
  counts: { current: number; candidate: number; contested: number };
  jobs?: Record<string, number>;
  usage?: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
    calls: number;
    usage_day: string;
  };
  processed_through?: number;
}

export interface MemorySuppression {
  id: number;
  character_id: string;
  subject: StructuredMemoryRecord["subject"] | null;
  category: StructuredMemoryRecord["fact_type"] | null;
  fact_key_prefix: string | null;
  reason: string;
  created_at: string;
  updated_at: string;
}

export interface StructuredMemoryStatus {
  character_id: string;
  worker_running: boolean;
  jobs: Record<string, number>;
  usage_today: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
    calls: number;
    usage_day: string;
  };
  daily_token_limit: number;
  automatic: boolean;
}

export interface StructuredMemoryMutation {
  action: "edit" | "reject" | "restore";
  content?: string;
  value?: string | null;
  reason: string;
  expected_updated_at?: string;
}

export interface StructuredMemoryMutationResult {
  action: string;
  record?: StructuredMemoryRecord;
  affected_ids?: number[];
  suppressions?: Array<Record<string, unknown>>;
}

export interface MemoryTopicSuppressionRequest {
  subject: StructuredMemoryRecord["subject"];
  fact_key_prefix?: string | null;
  category?: StructuredMemoryRecord["fact_type"] | null;
  reason: string;
}

export interface CharacterCard {
  id: string;
  name: string;
  tagline: string;
  description: string;
  background: string;
  personality: string;
  speech_style: string;
  relationship: string;
  greeting: string;
  scenario: string;
  motivation: string;
  model_settings: CharacterModelSettings;
  boundaries: string;
  source_notes: string;
  knowledge_boundary: {
    character_kind: "original" | "historical" | "fictional_canon";
    era: string;
    knowledge_cutoff: string;
    universe: string;
    allowed_knowledge: string;
    forbidden_knowledge: string;
    forbidden_terms: string[];
    can_learn_from_user: boolean;
    allow_everyday_invention: boolean;
  };
  fixed_facts: Array<{ key: string; value: string; status: "confirmed" | "proposal" }>;
  worldbook: Array<{
    id?: string;
    title: string;
    keywords: string[];
    content: string;
    priority?: number;
    enabled?: boolean;
  }>;
  example_dialogue: Array<{ user: string; character: string }>;
  generation_preset?: Record<string, number>;
  draft_notice?: string;
  schema_version?: number;
}
