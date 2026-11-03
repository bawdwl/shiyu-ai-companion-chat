import unittest

from memory_semantics import (
    apply_verification,
    classify_candidate,
    parse_verification_result,
)


def evidence(role="user", quote="我可能考虑搬到杭州，但还没有决定"):
    return [{"message_id": 1, "role": role, "quote": quote}]


class MemorySemanticsTests(unittest.TestCase):
    def test_hypothetical_statement_is_not_a_current_fact(self):
        result = classify_candidate(
            {
                "kind": "user_report",
                "subject": "user",
                "content": "用户计划搬到杭州",
                "operation": "supersede",
            },
            evidence(),
        )
        self.assertEqual(result["fact_type"], "hypothesis")
        self.assertEqual(result["epistemic_status"], "uncertain")
        self.assertEqual(result["source_authority"], "user_explicit")

    def test_assistant_first_person_detail_becomes_role_claim(self):
        result = classify_candidate(
            {
                "kind": "character_detail",
                "subject": "character",
                "content": "小满小时候住在海边",
            },
            evidence("assistant", "我小时候住在海边"),
        )
        self.assertEqual(result["kind"], "role_claim")
        self.assertEqual(result["fact_type"], "role_claim")
        self.assertEqual(result["source_authority"], "character_self_report")
        self.assertEqual(result["knowledge_source"], "character_told")

    def test_verifier_reject_and_downgrade(self):
        parsed = parse_verification_result(
            '{"items":['
            '{"candidate_index":0,"decision":"reject","reason":"假设被写成事实"},'
            '{"candidate_index":1,"decision":"downgrade","fact_type":"plan",'
            '"epistemic_status":"uncertain","source_authority":"user_explicit",'
            '"knowledge_source":"user_told","reason":"只是计划"}'
            ']}',
            2,
        )
        self.assertIsNone(
            apply_verification(
                {"operation": "add", "confidence": 0.6}, parsed[0]
            )
        )
        downgraded = apply_verification(
            {
                "operation": "supersede",
                "epistemic_status": "explicit",
                "fact_type": "fact",
                "confidence": 0.8,
            },
            parsed[1],
        )
        self.assertEqual(downgraded["operation"], "contested")
        self.assertEqual(downgraded["fact_type"], "plan")
        self.assertTrue(downgraded["semantic_verified"])
        self.assertLessEqual(downgraded["confidence"], 0.55)


if __name__ == "__main__":
    unittest.main()
