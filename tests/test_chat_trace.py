import unittest

from chat_trace import (
    PROMPT_PIPELINE_VERSION,
    make_turn_trace,
    role_version,
    stable_hash,
)


class ChatTraceTests(unittest.TestCase):
    def test_stable_hash_ignores_dict_order(self):
        self.assertEqual(stable_hash({"a": 1, "b": 2}), stable_hash({"b": 2, "a": 1}))

    def test_role_version_ignores_dynamic_trace_fields(self):
        role = {"id": "xiaoman", "name": "小满", "prompt": "角色提示"}
        self.assertEqual(role_version(role), role_version({**role, "state": {"trust": 0.9}}))
        self.assertNotEqual(role_version(role), role_version({**role, "prompt": "新提示"}))

    def test_trace_stores_metadata_not_prompt_body(self):
        result = make_turn_trace(
            run_id="run-1",
            provider="opencode",
            model="deepseek-v4.1-flash",
            role={"id": "companion", "name": "小满", "prompt": "固定角色"},
            messages=[
                {"role": "system", "content": "固定角色\n秘密记忆正文"},
                {"role": "user", "content": "你好"},
            ],
            prompt_trace={
                "structured_memory_ids": [7],
                "memory_retrieval_scores": [{"id": 7, "score": 0.9}],
                "generation_preset": {"temperature": 0.7},
            },
            metrics={"input_tokens": 20, "output_tokens": 8, "seconds": 1.2},
            max_new_tokens=256,
        )
        rendered = str(result)
        self.assertTrue(result["prompt_version"].startswith(PROMPT_PIPELINE_VERSION))
        self.assertEqual(result["metadata"]["structured_memory_ids"], [7])
        self.assertEqual(result["generation_params"]["max_new_tokens"], 256)
        self.assertNotIn("秘密记忆正文", rendered)


if __name__ == "__main__":
    unittest.main()
