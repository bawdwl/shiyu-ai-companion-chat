import unittest

from structured_memory import parse_extraction_result


class StructuredMemoryExtractionTests(unittest.TestCase):
    def test_truncated_response_keeps_complete_candidates(self):
        raw = (
            '{"candidates":['
            '{"kind":"user_report","subject":"user",'
            '"fact_key":"user.interview.time","value":"周五",'
            '"content":"面试改到周五","evidence":['
            '{"message_id":10,"role":"user","quote":"改到周五了"}]},'
            '{"kind":"user_report","subject":"user",'
            '"fact_key":"user.preference.drink","value":"咖啡",'
            '"content":"用户喜欢咖啡","evidence":['
            '{"message_id":11,"role":"user","quote":"我喜欢咖啡"}]},'
            '{"kind":"user_report","subject":"user",'
            '"fact_key":"user.interview.result","content":"面试结果还没出'
        )
        parsed = parse_extraction_result(raw)
        self.assertEqual(2, len(parsed['candidates']))
        self.assertEqual(
            'user.preference.drink',
            parsed['candidates'][1]['fact_key'],
        )
        self.assertIsNone(parsed['state_update'])

    def test_recovery_handles_braces_and_quotes_inside_strings(self):
        raw = (
            '{"candidates":['
            '{"kind":"shared_event","subject":"shared",'
            '"fact_key":"shared.sword.symbol",'
            '"content":"她写下“{剑}”这个字","evidence":['
            '{"message_id":20,"role":"assistant",'
            '"quote":"她写下“{剑}”这个字"}]},'
            '{"kind":"user_report","content":"未完成'
        )
        parsed = parse_extraction_result(raw)
        self.assertEqual(1, len(parsed['candidates']))
        self.assertIn('{剑}', parsed['candidates'][0]['content'])

    def test_no_complete_candidate_raises_clear_error(self):
        with self.assertRaisesRegex(ValueError, '可恢复'):
            parse_extraction_result(
                '{"candidates":[{"kind":"user_report","content":"未完成'
            )


if __name__ == '__main__':
    unittest.main()
