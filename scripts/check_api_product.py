"""Run the cloud-API product regression set without local-model dependencies."""
import os
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "src"))

MODULES = (
    "test_backend",
    "test_behavior_rules",
    "test_chat_trace",
    "test_full_character_evaluation",
    "test_prune_builtin_characters",
    "test_memory_admin",
    "test_memory_admin_api",
    "test_memory_retrieval_budget",
    "test_memory_hybrid_retrieval",
    "test_memory_records",
    "test_memory_semantics",
    "test_memory_semantics_integration",
    "test_openai_compat",
    "test_natural_memory_eval",
    "test_internal_state",
    "test_hybrid_memory_and_state",
    "test_product_app",
    "test_provider_backend",
    "test_provider_request_options",
    "test_providers",
    "test_roleplay_response",
    "test_secret_store",
    "test_session_prompt_pipeline",
    "test_state_ablation",
    "test_storage_v2",
    "test_streaming",
    "test_structured_context",
    "test_structured_memory_auto",
    "test_structured_memory_backend",
    "test_trace_backend",
)


if __name__ == "__main__":
    os.chdir(ROOT)
    suite = unittest.TestSuite()
    for name in MODULES:
        suite.addTests(unittest.defaultTestLoader.loadTestsFromName(f"tests.{name}"))
    result = unittest.TextTestRunner(verbosity=1).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)
