"""SSE/取消/超时与提交语义测试，使用模拟流式引擎。"""
import json
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from threading import Event

from fastapi.testclient import TestClient
from backend import create_app


class FakeStream:
    adapter_available=True
    def __init__(self):
        self.slow=False;self.fail=False;self.entered=Event();self.allow_exit=None
        self.modes=[]
    def reply_stream(self,messages,mode,stop,emit,max_new_tokens=256,generation_overrides=None):
        self.modes.append(mode)
        self.last_limit=max_new_tokens
        self.last_overrides=generation_overrides
        self.entered.set();emit('中文');emit('\n回答')
        if self.slow:
            stop.wait(3)
            if self.allow_exit: self.allow_exit.wait(3)
        if self.fail: raise RuntimeError('simulated')
        return '中文\n回答',dict(seconds=0.1,output_tokens=3,ended_with_eos=True)


class FakeNaturalReply(FakeStream):
    def reply(self,messages,model_mode='base',max_new_tokens=256,generation_overrides=None):
        return (
            '（放下手里的书，看向屏幕）我在听。',
            dict(seconds=.1,output_tokens=24,ended_with_eos=True),
        )


class SwitchRepeatEngine(FakeStream):
    def __init__(self):
        super().__init__()
        self.half_calls=0
        self.base_answer='那声音像老唱片，每次听都像在翻旧相册。'
    def reply_stream(self,messages,mode,stop,emit,max_new_tokens=256,generation_overrides=None):
        self.modes.append(mode)
        if mode=='base':
            emit(self.base_answer)
            return self.base_answer,dict(seconds=.1,output_tokens=12,ended_with_eos=True)
        self.half_calls+=1
        if self.half_calls==1:
            repeated='（轻轻叹了口气）'+self.base_answer
            emit(repeated)
            return repeated,dict(seconds=.1,output_tokens=14,ended_with_eos=True)
        answer='我想到的是饭桌边那些细碎的声响，还有旧木椅挪动时的轻响。'
        emit(answer)
        return answer,dict(seconds=.1,output_tokens=18,ended_with_eos=True)


class LocalAdapterRepeatEngine(FakeStream):
    available_model_modes=['base','half','full','wukong','xucheng','haruhi']

    def __init__(self):
        super().__init__()
        self.local_calls=0
        self.first_answer='（把书放回架子上）今晚确实有点安静。'

    def reply_stream(self,messages,mode,stop,emit,max_new_tokens=256,generation_overrides=None):
        self.modes.append(mode)
        self.last_overrides=generation_overrides
        self.local_calls+=1
        if self.local_calls==1:
            answer=self.first_answer
        elif self.local_calls==2:
            answer='（整理旧杂志）今晚确实有点安静。'
        else:
            answer='（把杯子往你那边推了推）安静归安静，你想从哪件事说起？'
        emit(answer)
        return answer,dict(seconds=.1,output_tokens=12,ended_with_eos=True)


def parse(text):
    results=[]
    for block in text.split('\n\n'):
        fields=block.splitlines()
        if not fields: continue
        event=next(x[7:] for x in fields if x.startswith('event: '))
        data=json.loads(next(x[6:] for x in fields if x.startswith('data: ')))
        results.append((event,data))
    return results


class StreamingTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.engine=FakeStream()
        self.client=TestClient(create_app(Path(self.temp.name)/'chat.sqlite3',engine_factory=lambda:self.engine))
        self.client.__enter__()
        self.sid=self.client.post('/sessions',json={}).json()['id']
    def tearDown(self):
        self.client.__exit__(None,None,None);self.temp.cleanup()
    def send(self,timeout=2):
        return self.client.post('/chat/stream',json=dict(session_id=self.sid,message='问题',timeout_seconds=timeout))
    def history(self):
        return self.client.get('/sessions/'+self.sid).json()['messages']

    def test_success_stream_order_and_persistence(self):
        r=self.send();self.assertIn('text/event-stream',r.headers['content-type'])
        events=parse(r.text)
        self.assertEqual([x[0] for x in events],['start','delta','delta','done'])
        self.assertEqual(''.join(d['text'] for e,d in events if e=='delta'),'中文\n回答')
        self.assertEqual(len(self.history()),2)

    def test_local_full_provider_streams_with_full_adapter(self):
        self.engine.available_model_modes=['base','half','full']
        conversation=self.client.put(
            '/characters/companion/conversation',
            params={'provider':'local-full'},
        )
        sid=conversation.json()['id']
        response=self.client.post('/chat/stream',json={
            'session_id':sid,
            'message':'比较完整 LoRA',
            'timeout_seconds':2,
        })
        events=parse(response.text)
        self.assertEqual(events[-1][0],'done')
        self.assertEqual(events[-1][1]['model_mode'],'full')
        self.assertEqual(self.engine.modes[-1],'full')

    def test_provider_switch_retries_echo_and_only_emits_new_answer(self):
        engine=SwitchRepeatEngine()
        engine.available_model_modes=['base','half','full']
        app=create_app(
            Path(self.temp.name)/'switch-repeat.sqlite3',
            engine_factory=lambda:engine,
        )
        client=TestClient(app)
        client.__enter__()
        try:
            base=client.put(
                '/characters/companion/conversation',
                params={'provider':'local-base'},
            ).json()
            first=parse(client.post('/chat/stream',json={
                'session_id':base['id'],
                'message':'我记得饭桌边的声音。',
                'timeout_seconds':2,
            }).text)
            self.assertEqual(first[-1][0],'done')
            half=client.put(
                '/characters/companion/conversation',
                params={'provider':'local-half'},
            ).json()
            second=parse(client.post('/chat/stream',json={
                'session_id':half['id'],
                'message':'继续说说那个画面。',
                'timeout_seconds':2,
            }).text)
            visible=''.join(
                data['text'] for event,data in second if event=='delta'
            )
            self.assertIn('旧木椅',visible)
            self.assertNotIn('老唱片',visible)
            done=next(data for event,data in second if event=='done')
            self.assertEqual(done['metrics']['repetition_retry_count'],1)
            self.assertEqual(done['metrics']['provider_switch_from'],'local-base')
        finally:
            client.__exit__(None,None,None)

    def test_named_local_adapter_retries_cross_turn_repetition(self):
        engine=LocalAdapterRepeatEngine()
        app=create_app(
            Path(self.temp.name)/'local-adapter-repeat.sqlite3',
            engine_factory=lambda:engine,
            enable_local_models=True,
        )
        client=TestClient(app)
        client.__enter__()
        try:
            card={
                'id':'custom-lora-xucheng-test',
                'name':'许澄',
                'tagline':'夜班图书管理员',
                'description':'测试角色',
                'background':'社区文化空间。',
                'personality':'温和、克制。',
                'speech_style':'自然简短。',
                'relationship':'朋友。',
                'greeting':'（听见门口风铃响，抬头看向你）先进来吧，阅览区还开着。',
                'model_settings':{'source':'local','local_mode':'xucheng'},
                'generation_preset':{'repetition_penalty':1.18},
                'knowledge_boundary':{},
            }
            self.assertEqual(
                client.post('/characters',json={'card':card}).status_code,
                201,
            )
            conversation=client.put(
                '/characters/custom-lora-xucheng-test/conversation',
                params={'provider':'opencode'},
            ).json()
            first=parse(client.post('/chat/stream',json={
                'session_id':conversation['id'],
                'message':'今天安静吗？',
                'timeout_seconds':2,
            }).text)
            self.assertEqual(first[-1][0],'done')
            second=parse(client.post('/chat/stream',json={
                'session_id':conversation['id'],
                'message':'嗯，安静。',
                'timeout_seconds':2,
            }).text)
            visible=''.join(
                data['text'] for event,data in second if event=='delta'
            )
            self.assertIn('你想从哪件事说起',visible)
            self.assertNotIn('整理旧杂志',visible)
            done=next(data for event,data in second if event=='done')
            self.assertEqual(done['metrics']['repetition_retry_count'],1)
            self.assertTrue(done['metrics']['local_repeat_guard'])
            self.assertEqual(engine.last_overrides['repetition_penalty'],1.2)
        finally:
            client.__exit__(None,None,None)

    def test_natural_roleplay_stream_is_persisted_as_one_message(self):
        app=create_app(
            Path(self.temp.name)/'roleplay.sqlite3',
            engine_factory=lambda:FakeStream(),
        )
        client=TestClient(app)
        client.__enter__()
        try:
            sid=client.post('/sessions',json={'character_id':'companion'}).json()['id']
            response=client.post('/chat/stream',json={
                'session_id':sid,
                'message':'今天有点累',
                'timeout_seconds':2,
            })
            events=parse(response.text)
            visible=''.join(
                data['text'] for event,data in events if event=='delta'
            )
            self.assertEqual(visible,'中文\n回答')
            done=next(data for event,data in events if event=='done')
            self.assertEqual(done['answer'],'中文\n回答')
            self.assertIsNone(done['display'])

            messages=client.get('/sessions/'+sid).json()['messages']
            self.assertEqual(messages[-1]['content'],'中文\n回答')
            self.assertIsNone(messages[-1]['display'])
        finally:
            client.__exit__(None,None,None)

    def test_non_stream_natural_reply_keeps_inline_action(self):
        client=TestClient(create_app(
            Path(self.temp.name)/'roleplay-normal.sqlite3',
            engine_factory=lambda:FakeNaturalReply(),
        ))
        client.__enter__()
        try:
            sid=client.post('/sessions',json={'character_id':'companion'}).json()['id']
            response=client.post('/chat',json={
                'session_id':sid,
                'message':'今天不太开心',
            })
            self.assertEqual(response.status_code,200,response.text)
            body=response.json()
            self.assertEqual(body['answer'],'（放下手里的书，看向屏幕）我在听。')
            self.assertIsNone(body['display'])
            message=client.get('/sessions/'+sid).json()['messages'][-1]
            self.assertEqual(message['content'],'（放下手里的书，看向屏幕）我在听。')
            self.assertIsNone(message['display'])
        finally:
            client.__exit__(None,None,None)

    def test_timeout_no_partial_commit_and_reuse(self):
        self.engine.slow=True
        events=parse(self.send(.1).text)
        self.assertEqual(events[-1][1]['code'],'timeout');self.assertEqual(self.history(),[])
        self.engine.slow=False
        self.assertEqual(parse(self.send().text)[-1][0],'done')

    def test_stop_waits_for_actual_worker_exit(self):
        self.engine.slow=True;self.engine.allow_exit=Event()
        with ThreadPoolExecutor(max_workers=1) as pool:
            future=pool.submit(self.send)
            try:
                self.assertTrue(self.engine.entered.wait(2))
                r=self.client.post('/sessions/'+self.sid+'/stop')
                self.assertTrue(r.json()['stop_requested'])
                self.assertTrue(self.client.get('/health').json()['generation_busy'])
                self.assertEqual(self.client.delete('/sessions/'+self.sid+'/messages').status_code,409)
            finally: self.engine.allow_exit.set()
            events=parse(future.result().text)
        self.assertEqual(events[-1][1]['code'],'stopped');self.assertEqual(self.history(),[])

    def test_error_event_not_done_not_saved(self):
        self.engine.fail=True
        events=parse(self.send().text)
        self.assertEqual(events[-1][0],'error');self.assertNotIn('done',[e for e,d in events])
        self.assertEqual(self.history(),[])

    def test_timeout_bounds_and_invalid_input(self):
        for value in [0,301]:
            r=self.client.post('/chat/stream',json=dict(session_id=self.sid,message='x',timeout_seconds=value))
            self.assertEqual(r.status_code,422)
        self.assertEqual(self.client.post('/chat/stream',json=dict(session_id=self.sid,message=' ')).status_code,422)

    def test_stop_idle_and_other_session(self):
        self.assertFalse(self.client.post('/sessions/'+self.sid+'/stop').json()['stop_requested'])
        self.engine.slow=True
        other=self.client.post('/sessions',json={}).json()['id']
        with ThreadPoolExecutor(max_workers=1) as pool:
            future=pool.submit(self.send)
            self.assertTrue(self.engine.entered.wait(2))
            self.assertFalse(self.client.post('/sessions/'+other+'/stop').json()['stop_requested'])
            self.client.post('/sessions/'+self.sid+'/stop')
            self.assertEqual(parse(future.result().text)[-1][1]['code'],'stopped')


if __name__=='__main__': unittest.main(verbosity=2)
