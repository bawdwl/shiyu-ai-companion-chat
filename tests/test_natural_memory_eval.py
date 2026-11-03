import json
import tempfile
import unittest
from pathlib import Path

from scripts.prepare_natural_memory_eval import (
    ABLATION_MODES,
    build_curated_cases,
    redact_text,
    run_replay,
    validate_dataset,
)


ROOT = Path(__file__).resolve().parents[1]
EXAMPLE = ROOT / "evaluation" / "natural-memory" / "example-case.json"
SCHEMA = ROOT / "evaluation" / "natural-memory" / "schema.json"


class NaturalMemoryEvalTests(unittest.TestCase):
    def test_public_example_matches_expected_shape(self):
        dataset = {
            "schema_version": 1,
            "suite_version": "example",
            "cases": [json.loads(EXAMPLE.read_text(encoding="utf-8"))],
        }
        counts = validate_dataset(dataset, allow_public_example=True)
        self.assertEqual(counts["case_count"], 1)
        self.assertEqual(counts["probe_count"], 5)
        self.assertEqual(
            json.loads(SCHEMA.read_text(encoding="utf-8"))["$id"],
            "https://local.shiyu.chat/natural-memory-schema-v1.json",
        )

    def test_curated_suite_has_frozen_size_and_exactly_five_probes(self):
        cases = build_curated_cases()
        dataset = {
            "schema_version": 1,
            "suite_version": "curated-test",
            "cases": cases,
        }
        counts = validate_dataset(dataset, expected_min_cases=17)
        self.assertEqual(counts["case_count"], 17)
        self.assertEqual(counts["turn_count"], 408)
        self.assertEqual(counts["probe_count"], 85)
        self.assertEqual(counts["source_counts"], {"curated_synthetic": 17})
        for case in cases:
            self.assertEqual(len(case["turns"]), 24)
            self.assertEqual(len(case["probes"]), 5)

    def test_redaction_removes_direct_identifiers(self):
        text, counts = redact_text(
            "我叫张三，电话 13800138000，邮箱 user@example.com，"
            "地址 C:\\Users\\name\\private.txt，称我小满。",
            role_name="小满",
        )
        self.assertNotIn("张三", text)
        self.assertNotIn("13800138000", text)
        self.assertNotIn("user@example.com", text)
        self.assertNotIn("C:\\Users\\name\\private.txt", text)
        self.assertNotIn("小满", text)
        self.assertIn("[姓名]", text)
        self.assertTrue("[角色名]" in text or "[姓名]" in text)
        self.assertGreaterEqual(counts["phone"], 1)
        self.assertGreaterEqual(counts["email"], 1)

    def test_golden_replay_runs_all_ablations_in_isolated_db(self):
        cases = build_curated_cases()[:3]
        dataset = {
            "schema_version": 1,
            "suite_version": "replay-test",
            "cases": cases,
        }
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "output"
            report = run_replay(dataset, output)
            self.assertTrue((output / "results.json").is_file())
            self.assertTrue((output / "report.md").is_file())
            self.assertEqual(set(report["summary"]), set(ABLATION_MODES))
            self.assertEqual(report["summary"]["facts_only"]["probes"], 15)
            for row in report["results"]:
                self.assertIn(
                    row["failure_stage"],
                    {None, "store", "retrieve", "generate"},
                )
                self.assertEqual(
                    set(row["stages"]),
                    {
                        "extract",
                        "validate",
                        "semantic_review",
                        "store",
                        "retrieve",
                        "generate",
                        "ui",
                    },
                )


if __name__ == "__main__":
    unittest.main()
