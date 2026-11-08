import json
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SUITE = ROOT / "evaluation" / "experience" / "roleplay_experience_v1.json"


class ExperienceSuiteTests(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(SUITE.read_text(encoding="utf-8"))
        self.cases = self.data["cases"]

    def test_suite_has_frozen_category_counts(self):
        counts = Counter(case["category"] for case in self.cases)
        self.assertEqual(self.data["categories"], dict(counts))
        self.assertEqual(40, len(self.cases))

    def test_case_ids_and_roles_are_valid(self):
        ids = [case["id"] for case in self.cases]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertEqual(
            {"li_bai", "xiao_chengyuan"},
            {case["role"] for case in self.cases},
        )
        for case in self.cases:
            self.assertTrue(case["turns"])
            self.assertTrue(all(str(turn).strip() for turn in case["turns"]))
            self.assertIn("checks", case)

    def test_suite_is_frozen_to_opencode_go_deepseek(self):
        self.assertEqual("opencode", self.data["provider"])
        self.assertEqual("deepseek-v4.1-flash", self.data["model"])


if __name__ == "__main__":
    unittest.main()
