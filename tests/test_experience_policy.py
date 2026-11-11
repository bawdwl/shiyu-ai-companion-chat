import unittest
import tempfile
from pathlib import Path

from experience_policy import (
    affect_prompt,
    current_turn_affect,
    derive_event_update,
    dialogue_policy,
    memory_need,
    scene_from_messages,
)
from prompt_pipeline import assemble_prompt
from character_cards import card_to_runtime, normalize_card, parse_generated_card
from characters import CHARACTERS
from structured_memory import CandidateStore
from internal_state import render_boundaries


class ExperiencePolicyTests(unittest.TestCase):
    def test_boundary_rendering_is_independent_from_mood_state(self):
        text = render_boundaries({
            'mood': {'label': '平静'},
            'relationship': {'stage': '亲近朋友'},
            'boundaries': ['不要叫娘子', '不要连续追问'],
        })
        self.assertIn('不要叫娘子', text)
        self.assertIn('不要连续追问', text)
        self.assertNotIn('平静', text)
        self.assertNotIn('亲近朋友', text)

    def test_dialogue_policy_defaults_to_none(self):
        self.assertEqual(
            'NONE',
            dialogue_policy('今天看到一只很胖的猫', [])['strategy'],
        )
        listen = dialogue_policy('我不想听建议，也别追问', [])
        self.assertEqual('LISTEN', listen['strategy'])
        self.assertEqual('low', listen['question_preference'])
        self.assertEqual(
            'CELEBRATE',
            dialogue_policy('我考上研究生了', [])['strategy'],
        )

    def test_memory_need_skips_small_talk_and_expands_anaphora(self):
        history = [
            {'id': 1, 'role': 'user', 'content': '周五下午要去软件园面试'},
            {'id': 2, 'role': 'assistant', 'content': '我记住了。'},
        ]
        skipped = memory_need(history, '哈哈')
        self.assertFalse(skipped['retrieve'])
        expanded = memory_need(history, '那个后来怎么样了？')
        self.assertTrue(expanded['retrieve'])
        self.assertIn('软件园面试', expanded['query'])
        self.assertEqual([1], expanded['expanded_source_ids'])

    def test_affect_is_temporary_and_does_not_claim_relationship_change(self):
        affect = current_turn_affect('我考上研究生了')
        prompt = affect_prompt(affect)
        self.assertIn('高兴', prompt)
        self.assertIn('不代表关系数值已经永久变化', prompt)

    def test_scene_only_changes_on_explicit_scene(self):
        daily = scene_from_messages([], '今天好累')
        self.assertEqual('daily', daily['mode'])
        immersive = scene_from_messages([], '我们现在在海边散步')
        self.assertEqual('immersive', immersive['mode'])
        self.assertEqual('海边', immersive['location'])
        self.assertEqual('散步', immersive['activity'])
        exited = scene_from_messages(
            [{'role': 'user', 'content': '我们现在在海边散步'}],
            '退出剧情，回到现实',
        )
        self.assertEqual('daily', exited['mode'])

    def test_event_updates_are_derived_from_verified_memory(self):
        plan = {
            'subject': 'user',
            'kind': 'plan',
            'fact_type': 'plan',
            'fact_key': 'user.interview.plan',
            'content': '用户计划周五参加面试',
        }
        update = derive_event_update(plan)
        self.assertEqual('interview', update['topic_key'])
        self.assertEqual('open', update['status'])
        completed = {
            **plan,
            'kind': 'shared_event',
            'fact_type': 'event',
            'fact_key': 'shared.interview.result',
            'content': '用户已经完成面试',
        }
        self.assertEqual('completed', derive_event_update(completed)['status'])

    def test_memory_store_keeps_event_chain_and_clears_it_with_scope(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            store = CandidateStore(root / 'memory.sqlite3', allowed_root=root)
            try:
                plan = {
                    'status': 'candidate',
                    'kind': 'plan',
                    'subject': 'user',
                    'fact_key': 'user.interview.plan',
                    'value': None,
                    'content': '用户计划周五参加面试',
                    'fact_type': 'plan',
                    'epistemic_status': 'explicit',
                    'event_date': None,
                    'operation': 'add',
                    'target_item_id': None,
                    'evidence': [{
                        'message_id': 1,
                        'role': 'user',
                        'quote': '我周五要参加面试',
                    }],
                }
                outcome = store.auto_ingest('custom-test', plan)
                store.record_event_from_memory('custom-test', outcome['item'])
                events = store.relevant_memory_events(
                    'custom-test',
                    '面试后来怎么样了',
                )
                self.assertEqual('interview', events[0]['topic_key'])
                self.assertEqual('open', events[0]['status'])
                moving = {
                    **plan,
                    'fact_key': 'user.moving.plan',
                    'content': '用户准备月底搬家',
                    'evidence': [{
                        'message_id': 3,
                        'role': 'user',
                        'quote': '我月底要搬家',
                    }],
                }
                moving_result = store.auto_ingest('custom-test', moving)
                store.record_event_from_memory(
                    'custom-test',
                    moving_result['item'],
                )
                ambiguous = store.relevant_memory_events(
                    'custom-test',
                    '那件事后来怎么样了',
                )
                self.assertEqual(1, len(ambiguous))

                result = {
                    'status': 'candidate',
                    'kind': 'shared_event',
                    'subject': 'shared',
                    'fact_key': 'shared.interview.result',
                    'value': None,
                    'content': '用户已经完成面试',
                    'fact_type': 'event',
                    'epistemic_status': 'explicit',
                    'event_date': None,
                    'operation': 'add',
                    'target_item_id': None,
                    'evidence': [{
                        'message_id': 2,
                        'role': 'user',
                        'quote': '我面试结束了',
                    }],
                }
                completed = store.auto_ingest('custom-test', result)
                store.record_event_from_memory(
                    'custom-test',
                    completed['item'],
                )
                rows = store.db.execute(
                    '''SELECT * FROM memory_events
                       WHERE character_id='custom-test'
                         AND topic_key='interview' ''',
                ).fetchall()
                self.assertEqual(1, len(rows))
                self.assertEqual('completed', rows[0]['status'])
                self.assertIn('用户已经完成面试', rows[0]['timeline_json'])

                store.clear_scope('custom-test')
                self.assertEqual(
                    0,
                    store.db.execute(
                        '''SELECT COUNT(*) FROM memory_events
                           WHERE character_id='custom-test' ''',
                    ).fetchone()[0],
                )
            finally:
                store.close()

    def test_prompt_pipeline_adds_scene_and_turn_hint_without_touching_fixed(self):
        card = normalize_card({
            'name': '林音',
            'scenario': '在书店整理新到的诗集。',
        })
        role = card_to_runtime(card)
        registry = {**CHARACTERS, card['id']: role}
        messages, trace = assemble_prompt(
            {
                'character_id': card['id'],
                'messages': [
                    {'id': 1, 'role': 'user', 'content': '我们现在在海边散步。'},
                    {'id': 2, 'role': 'assistant', 'content': '海风有些凉。'},
                ],
            },
            '我考上了！',
            registry,
        )
        system = messages[0]['content']
        self.assertIn('沉浸式剧情模式', system)
        self.assertIn('CELEBRATE', system)
        self.assertEqual('immersive', trace['scene_mode'])
        self.assertEqual('CELEBRATE', trace['dialogue_strategy'])

        legacy_messages, legacy_trace = assemble_prompt(
            {
                'character_id': card['id'],
                'messages': [
                    {'id': 1, 'role': 'user', 'content': '我们现在在海边散步。'},
                ],
            },
            '我考上了！',
            registry,
            experience_policy_enabled=False,
        )
        self.assertNotIn('沉浸式剧情模式', legacy_messages[0]['content'])
        self.assertNotIn('本轮即时反应', legacy_messages[0]['content'])
        self.assertEqual('NONE', legacy_trace['dialogue_strategy'])

    def test_generated_card_accepts_android_camel_case_context_fields(self):
        card = parse_generated_card(
            '''
            {
              "name":"林音",
              "fixedFacts":[{"key":"职业","value":"书店店员"}],
              "exampleDialogue":[{"user":"你好","character":"我刚整理完书架。"}],
              "useExamples":true,
              "interactionMode":"immersive",
              "goals":["完成插画集"]
            }
            ''',
            '书店店员林音',
        )
        self.assertEqual('书店店员', card['fixed_facts'][0]['value'])
        self.assertEqual('proposal', card['fixed_facts'][0]['status'])
        self.assertTrue(card['use_examples'])
        self.assertEqual('immersive', card['interaction_mode'])
        self.assertEqual(['完成插画集'], card['goals'])


if __name__ == '__main__':
    unittest.main()
