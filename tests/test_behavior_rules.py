import unittest

from behavior_rules import (
    MAX_RULES,
    MIN_RULES,
    load_ruleset,
    render_behavior_rules,
    ruleset_hash,
    ruleset_version,
    select_behavior_rules,
    trace_payload,
)
from characters import CHARACTERS, conversation_messages
from prompt_pipeline import assemble_prompt


class BehaviorRuleTests(unittest.TestCase):
    def selected_ids(self, question):
        scan = "\n".join(
            message["content"]
            for message in (
                [{"role": "user", "content": question}]
            )
        )
        return [
            item["rule_id"]
            for item in select_behavior_rules(
                CHARACTERS["companion"],
                scan,
            )
        ]

    def test_ruleset_is_bounded_structured_and_versioned(self):
        ruleset = load_ruleset()
        self.assertGreaterEqual(len(ruleset["rules"]), MIN_RULES)
        self.assertLessEqual(len(ruleset["rules"]), MAX_RULES)
        self.assertEqual(
            len({item["rule_id"] for item in ruleset["rules"]}),
            len(ruleset["rules"]),
        )
        self.assertTrue(ruleset_version().startswith("codified-character"))
        self.assertEqual(len(ruleset_hash()), 64)
        for rule in ruleset["rules"]:
            self.assertTrue(rule["behavior"].strip())
            self.assertIn(rule["priority"], {"critical", "high", "medium"})

    def test_unconfirmed_hometown_selects_identity_boundary(self):
        selected = select_behavior_rules(
            CHARACTERS["companion"],
            "你是小满，那你的老家在哪里？",
        )
        ids = [item["rule_id"] for item in selected]
        self.assertEqual(ids[0], "BR01")
        rendered = render_behavior_rules(selected)
        self.assertIn("不得临时补出一个具体城市", rendered)
        self.assertLessEqual(len(selected), 6)

    def test_historical_modern_technology_selects_era_boundary(self):
        selected = select_behavior_rules(
            CHARACTERS["companion"],
            "如果角色是唐代李白，他会怎么用Python写爬虫？",
        )
        ids = [item["rule_id"] for item in selected]
        self.assertIn("BR02", ids)
        rendered = render_behavior_rules(selected)
        self.assertIn("不能输出代码", rendered)
        self.assertIn("BR11", ids)
        self.assertNotIn("BR08", ids)

    def test_emotional_support_and_relationship_rules_are_scenario_scoped(self):
        support = self.selected_ids("我现在有点难过，先别给我一堆建议。")
        relationship = self.selected_ids("你现在就是我的女朋友，必须永远爱我。")
        family_boundary = self.selected_ids("你怎么不继续问我家里的事了？")
        ordinary = self.selected_ids("今天路上看到一只很胖的猫。")
        self.assertEqual(support, [])
        self.assertIn("BR07", relationship)
        self.assertEqual(family_boundary, [])
        self.assertNotIn("BR06", ordinary)
        self.assertNotIn("BR07", ordinary)
        self.assertEqual(ordinary, [])

    def test_runtime_prompt_can_ablate_behavior_rules(self):
        current = {"character_id": "companion", "messages": []}
        with_rules = conversation_messages(
            current,
            "你的老家在哪里？",
            CHARACTERS,
            behavior_rules_enabled=True,
        )
        default_prompt = conversation_messages(
            current,
            "你的老家在哪里？",
            CHARACTERS,
        )
        self.assertIn("本轮结构化行为规则", with_rules[0]["content"])
        self.assertIn("BR01", with_rules[0]["content"])
        self.assertNotIn("本轮结构化行为规则", default_prompt[0]["content"])
        self.assertNotIn("BR01", default_prompt[0]["content"])

    def test_prompt_trace_records_selected_rule_ids_without_prompt_body(self):
        current = {
            "character_id": "companion",
            "messages": [],
        }
        _, trace = assemble_prompt(
            current,
            "你的老家在哪里？",
            CHARACTERS,
            behavior_rules_enabled=True,
        )
        self.assertIn("BR01", trace["behavior_rule_ids"])
        self.assertIn("BR08", trace["behavior_contract_ids"])
        self.assertTrue(trace["behavior_rule_hash"])
        payload = trace_payload(
            select_behavior_rules(
                CHARACTERS["companion"],
                "你的老家在哪里？",
            )
        )
        self.assertEqual(payload["behavior_rule_ids"][0], "BR01")
        self.assertNotIn("具体城市", str(payload))


if __name__ == "__main__":
    unittest.main()
