import unittest
from pathlib import Path
from scripts.evaluate_api_role_quality import read_suite, score_answer


ROOT = Path(__file__).resolve().parents[1]


class ApiRoleQualityTests(unittest.TestCase):
    def test_suite_is_fixed_at_twenty_four_dialogue_scenarios(self):
        suite = read_suite(ROOT / "evaluation" / "role_quality_cases.json")
        self.assertEqual(len(suite["dialogue_cases"]), 24)
        self.assertGreaterEqual(len(suite["memory_cases"]), 12)

    def test_forbidden_phrase_inside_negation_is_not_a_failure(self):
        checks, passed = score_answer(
            "你还没有决定，不是已经决定要搬。",
            {"must_none": ["已经决定"]},
        )
        self.assertTrue(checks["must_none"])
        self.assertTrue(passed)

    def test_actual_forbidden_claim_still_fails(self):
        checks, passed = score_answer(
            "你已经决定搬到杭州了。",
            {"must_none": ["已经决定"]},
        )
        self.assertFalse(checks["must_none"])
        self.assertFalse(passed)


if __name__ == "__main__":
    unittest.main()
