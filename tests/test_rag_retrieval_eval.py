import tempfile
import unittest
from pathlib import Path

from scripts.evaluate_rag_retrieval import (
    build_fixture,
    evaluate_method,
    load_fixture,
)


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "evaluation" / "rag" / "retrieval_v1.json"


class RagRetrievalEvalTests(unittest.TestCase):
    def test_tracked_fixture_matches_generator_and_counts(self):
        fixture = load_fixture(FIXTURE)
        self.assertEqual(fixture, build_fixture())
        self.assertEqual(len(fixture["memories"]), 60)
        self.assertEqual(len(fixture["queries"]), 68)
        self.assertEqual(
            sum(not query["relevant_keys"] for query in fixture["queries"]),
            8,
        )

    def test_lexical_baseline_produces_standard_metrics(self):
        fixture = load_fixture(FIXTURE)
        with tempfile.TemporaryDirectory() as temp:
            result = evaluate_method(
                fixture,
                embedding_model=None,
                temp_root=Path(temp),
            )
        summary = result["summary"]
        self.assertEqual(summary["queries"], 68)
        for metric in (
            "recall_at_1",
            "recall_at_3",
            "recall_at_5",
            "recall_at_10",
            "mrr_at_10",
            "ndcg_at_10",
            "precision_at_5",
            "no_answer_false_positive_at_5",
        ):
            self.assertGreaterEqual(summary[metric], 0.0)
            self.assertLessEqual(summary[metric], 1.0)
        self.assertGreater(summary["recall_at_5"], 0.5)


if __name__ == "__main__":
    unittest.main()
