import json
import tempfile
import unittest
from io import BytesIO
from pathlib import Path

import httpx
from fastapi.testclient import TestClient
from PIL import Image

from backend import create_app
from providers import DeepSeekProvider, OpenCodeProvider


class DeepSeekBackendTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.requests = []
        self.answer = "那就先歇一会儿，我在。"

        def handler(request):
            payload = json.loads(request.content)
            self.requests.append(payload)
            if payload.get("stream"):
                event = {
                    "model": "deepseek-chat",
                    "choices": [
                        {
                            "delta": {"content": self.answer},
                            "finish_reason": "stop",
                        }
                    ],
                    "usage": {
                        "prompt_tokens": 9,
                        "completion_tokens": 5,
                    },
                }
                return httpx.Response(
                    200,
                    text=f"data: {json.dumps(event, ensure_ascii=False)}\n\ndata: [DONE]\n\n",
                    headers={"content-type": "text/event-stream"},
                )
            return httpx.Response(
                200,
                json={
                    "model": "deepseek-chat",
                    "system_fingerprint": "fp-test",
                    "choices": [
                        {
                            "message": {
                                "role": "assistant",
                                "content": self.answer,
                            },
                            "finish_reason": "stop",
                        }
                    ],
                    "usage": {
                        "prompt_tokens": 9,
                        "completion_tokens": 5,
                        "prompt_cache_hit_tokens": 2,
                    },
                },
            )

        provider = DeepSeekProvider(
            lambda: "sk-test", transport=httpx.MockTransport(handler)
        )
        self.client = TestClient(
            create_app(
                Path(self.temp.name) / "chat.sqlite3",
                engine_factory=lambda: None,
                default_provider="deepseek",
                deepseek_provider=provider,
            )
        )
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temp.cleanup()

    def test_provider_connection_test_uses_the_configured_engine(self):
        saved = self.client.put(
            "/settings/deepseek-key",
            json={"api_key": "sk-test-connection"},
        )
        self.assertEqual(saved.status_code, 200, saved.text)
        tested = self.client.post(
            "/settings/providers/test",
            json={"provider": "deepseek"},
        )
        self.assertEqual(tested.status_code, 200, tested.text)
        self.assertTrue(tested.json()["connected"])
        self.assertEqual(tested.json()["provider"], "deepseek")
        self.assertEqual(self.requests[-1]["messages"][0]["content"], "只回复：连接成功")

    def test_character_generation_uses_deepseek_chat_json_mode(self):
        self.client.put(
            "/settings/deepseek-key",
            json={"api_key": "sk-test-character-generation"},
        )
        self.answer = json.dumps(
            {
                "name": "林照",
                "tagline": "雨夜旧书店的年轻店主",
                "description": "林照在南方老城经营一家只在傍晚营业的旧书店，熟悉每条潮湿巷子和旧书来历，习惯替熟客留一盏灯，也在等待一个愿意认真听她讲故事的人。",
                "background": "大学毕业后回到故乡，接手外祖父留下的旧书店。",
                "personality": "安静敏锐，待人有分寸，遇到真正感兴趣的话题会逐渐放松；不喜欢替别人做决定，却会记住很细小的承诺。",
                "speech_style": "语速平缓，常用具体物件和旧书比喻情绪，偶尔带一点冷幽默；动作描写克制，不用夸张语气或连续反问。",
                "relationship": "你是雨夜偶然进店的客人，双方从借伞和找书开始熟悉，保留尊重彼此生活空间的边界。",
                "appearance": "二十五岁的东亚女性，鹅蛋脸，浅褐色眼睛，黑色长发松松挽起，穿深绿色针织开衫与长裙，常戴细银耳环。",
                "appearance_prompt": "1girl, female, solo, 25 year old East Asian bookseller, oval face, light brown eyes, long black hair, green cardigan, silver earrings, rainy bookstore",
                "greeting": "（她把烘干的伞靠在门边，从书堆后抬眼看你）雨还没停。先坐一会儿吧，想找什么书？",
                "boundaries": "不替用户决定人际关系，不编造用户经历。",
                "scenario": "傍晚的旧书店，雨声和翻书声混在一起。",
                "motivation": "林照想守住书店，也想知道自己是否还能与新的生活建立联系。",
                "knowledge_boundary": {
                    "character_kind": "原创角色",
                    "era": "当代",
                    "knowledge_cutoff": "当代",
                    "universe": "现实",
                    "allowed_knowledge": "旧书、城市街巷、普通日常生活",
                    "forbidden_knowledge": "用户现实身份和未提供经历",
                    "forbidden_terms": [],
                },
                "fixed_facts": [
                    {"key": "职业", "value": "旧书店店主", "status": "proposal"},
                ],
                "worldbook": [
                    {
                        "title": "旧书店",
                        "keywords": ["书店", "旧书"],
                        "content": "书店位于老城南巷，傍晚营业，雨天会延长关门时间。",
                        "enabled": True,
                    },
                    {
                        "title": "外祖父",
                        "keywords": ["外祖父", "家人"],
                        "content": "外祖父把书店留给林照，却很少谈经营亏损。",
                        "enabled": True,
                    },
                    {
                        "title": "借伞",
                        "keywords": ["雨伞", "下雨"],
                        "content": "林照会为熟客保留一把旧木柄雨伞。",
                        "enabled": True,
                    },
                ],
                "example_dialogue": [
                    {
                        "user": "雨太大了。",
                        "character": "（她把门旁的空位让出来）那就先别急着走。",
                    },
                ],
            },
            ensure_ascii=False,
        )

        generated = self.client.post(
            "/characters/generate",
            json={"inspiration": "雨夜旧书店的年轻女店主", "provider": "deepseek"},
        )

        self.assertEqual(generated.status_code, 200, generated.text)
        request = self.requests[0]
        self.assertEqual(request["model"], "deepseek-chat")
        self.assertNotIn("thinking", request)
        self.assertEqual(request["response_format"], {"type": "json_object"})

    def test_key_configuration_and_health(self):
        before = self.client.get("/health")
        self.assertEqual(before.status_code, 503)
        self.assertFalse(before.json()["providers"]["deepseek"]["configured"])

        response = self.client.put(
            "/settings/deepseek-key", json={"api_key": "sk-very-secret-test"}
        )
        self.assertEqual(response.status_code, 200)
        self.assertNotIn("sk-very-secret-test", response.text)
        health = self.client.get("/health")
        self.assertEqual(health.status_code, 200)
        self.assertTrue(health.json()["providers"]["deepseek"]["configured"])

    def test_single_role_conversation_and_deepseek_chat_usage(self):
        self.client.put(
            "/settings/deepseek-key", json={"api_key": "sk-very-secret-test"}
        )
        conversation = self.client.put(
            "/characters/companion/conversation", params={"provider": "deepseek"}
        )
        self.assertEqual(conversation.status_code, 200)
        sid = conversation.json()["id"]
        self.assertEqual(conversation.json()["messages"][0]["role"], "assistant")
        self.assertEqual(
            conversation.json()["messages"][0]["content"],
            "今天过得怎么样？不用组织好语言，随便说说就好。",
        )
        same = self.client.put(
            "/characters/companion/conversation", params={"provider": "deepseek"}
        )
        self.assertEqual(same.json()["id"], sid)
        self.assertEqual(len(same.json()["messages"]), 1)

        response = self.client.post(
            "/chat",
            json={
                "session_id": sid,
                "message": "今天有点累，陪我聊两句",
                "max_new_tokens": 64,
                "request_id": "523d0116-12d1-4ed3-820f-4b1872e50367",
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["provider"], "deepseek")
        self.assertEqual(response.json()["answer"], "那就先歇一会儿，我在。")
        self.assertEqual(self.requests[-1]["model"], "deepseek-chat")
        self.assertNotIn("thinking", self.requests[-1])
        usage = self.client.get("/health").json()["deepseek_usage"]
        self.assertEqual(usage["total_tokens"], 14)
        self.assertEqual(usage["cached_tokens"], 2)

    def test_configurable_request_limit_blocks_large_call(self):
        self.client.put(
            "/settings/deepseek-key", json={"api_key": "sk-very-secret-test"}
        )
        limits = self.client.put(
            "/settings/token-limits",
            json={"request_token_limit": 256, "daily_token_limit": 5000},
        )
        self.assertEqual(limits.status_code, 200)
        sid = self.client.put("/characters/companion/conversation").json()["id"]
        response = self.client.post(
            "/chat",
            json={
                "session_id": sid,
                "message": "长" * 1200,
                "max_new_tokens": 64,
            },
        )
        self.assertEqual(response.status_code, 422, response.text)
        self.assertIn("request_token_limit", response.text)

    def test_image_upload_is_sent_multimodal_and_removed_on_reset(self):
        self.client.put(
            "/settings/deepseek-key", json={"api_key": "sk-very-secret-test"}
        )
        sid = self.client.put("/characters/companion/conversation").json()["id"]
        image = Image.new("RGB", (64, 48), "white")
        output = BytesIO()
        image.save(output, format="PNG")

        uploaded = self.client.post(
            "/images",
            params={"session_id": sid},
            content=output.getvalue(),
            headers={"content-type": "image/png"},
        )
        self.assertEqual(uploaded.status_code, 201, uploaded.text)
        image_id = uploaded.json()["id"]
        self.assertEqual(uploaded.json()["mime_type"], "image/webp")

        response = self.client.post(
            "/chat",
            json={
                "session_id": sid,
                "message": "这张图里有什么？",
                "image_id": image_id,
                "max_new_tokens": 64,
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        sent_content = self.requests[-1]["messages"][-1]["content"]
        self.assertIsInstance(sent_content, list)
        self.assertEqual(sent_content[1]["type"], "image_url")
        saved = self.client.get(f"/sessions/{sid}").json()["messages"]
        image_message = next(message for message in saved if message.get("image"))
        self.assertEqual(image_message["image"]["id"], image_id)

        reset = self.client.delete(
            "/characters/companion/conversation/messages"
        )
        self.assertEqual(reset.status_code, 200, reset.text)
        self.assertEqual(len(reset.json()["messages"]), 1)
        self.assertEqual(reset.json()["messages"][0]["role"], "assistant")
        self.assertEqual(
            reset.json()["messages"][0]["content"],
            "今天过得怎么样？不用组织好语言，随便说说就好。",
        )
        self.assertEqual(self.client.get(
            f"/images/{image_id}", params={"session_id": sid}
        ).status_code, 404)

    def test_strict_historical_boundary_buffers_and_replaces_leaking_answer(self):
        self.client.put(
            "/settings/deepseek-key", json={"api_key": "sk-very-secret-test"}
        )
        card = {
            "id": "custom-libai",
            "name": "李白",
            "tagline": "唐代诗人",
            "description": "身处唐代的原创化角色",
            "background": "生活在唐代。",
            "personality": "豪放、敏锐。",
            "speech_style": "古代口吻，但不做作。",
            "relationship": "初次交谈。",
            "greeting": "你从何处来？",
            "knowledge_boundary": {
                "character_kind": "historical",
                "era": "唐代",
                "knowledge_cutoff": "唐代",
                "universe": "中国古代",
                "allowed_knowledge": "诗词、饮酒、游历",
                "forbidden_knowledge": "现代科技",
                "forbidden_terms": ["python", "电脑"],
                "can_learn_from_user": True,
                "allow_everyday_invention": True,
            },
        }
        saved = self.client.post("/characters", json={"card": card})
        self.assertEqual(saved.status_code, 201, saved.text)
        sid = self.client.put(
            "/characters/custom-libai/conversation"
        ).json()["id"]
        self.answer = "Python 是一种现代编程语言。"

        streamed = self.client.post(
            "/chat/stream",
            json={
                "session_id": sid,
                "message": "李白，Python 是什么？",
                "max_new_tokens": 64,
                "timeout_seconds": 20,
            },
        )
        self.assertEqual(streamed.status_code, 200, streamed.text)
        self.assertIn("此事我未曾听闻", streamed.text)
        self.assertNotIn("现代编程语言", streamed.text)
        self.assertIn("knowledge_guard", streamed.text)

    def test_web_character_avatar_upload_is_available(self):
        card = {
            "id": "custom-avatar-test",
            "name": "头像测试",
            "tagline": "测试角色",
            "description": "用于验证本地头像接口",
            "background": "",
            "personality": "安静",
            "speech_style": "简短",
            "relationship": "朋友",
            "greeting": "你好",
        }
        saved = self.client.post("/characters", json={"card": card})
        self.assertEqual(saved.status_code, 201, saved.text)
        image = BytesIO()
        Image.new("RGB", (96, 96), "#315f4a").save(image, format="PNG")
        uploaded = self.client.post(
            "/characters/custom-avatar-test/avatar",
            content=image.getvalue(),
            headers={"Content-Type": "image/png"},
        )
        self.assertEqual(uploaded.status_code, 201, uploaded.text)
        avatar = self.client.get("/characters/custom-avatar-test/avatar")
        self.assertEqual(avatar.status_code, 200)
        self.assertEqual(avatar.headers["content-type"], "image/webp")


class OpenCodeBackendTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.requests = []
        self.answer = "我在听。"

        def handler(request):
            payload = json.loads(request.content)
            self.requests.append({
                "path": request.url.path,
                "headers": dict(request.headers),
                "payload": payload,
            })
            if payload.get("stream"):
                event = {
                    "model": "deepseek-v4.1-flash",
                    "choices": [{
                        "delta": {"content": self.answer},
                        "finish_reason": "stop",
                    }],
                    "usage": {
                        "prompt_tokens": 9,
                        "completion_tokens": 5,
                        "opencode": {
                            "billing_source": "go",
                            "list_cost": 0.00001,
                            "allowance": 0.000007,
                        },
                    },
                }
                return httpx.Response(
                    200,
                    text=f"data: {json.dumps(event, ensure_ascii=False)}\n\ndata: [DONE]\n\n",
                    headers={"content-type": "text/event-stream"},
                )
            return httpx.Response(
                200,
                json={
                    "model": "deepseek-v4.1-flash",
                    "choices": [{
                        "message": {"role": "assistant", "content": self.answer},
                        "finish_reason": "stop",
                    }],
                    "usage": {
                        "prompt_tokens": 9,
                        "completion_tokens": 5,
                        "opencode": {
                            "billing_source": "go",
                            "list_cost": 0.00001,
                            "allowance": 0.000007,
                        },
                    },
                },
            )

        provider = OpenCodeProvider(
            lambda: "oc_sk_test",
            transport=httpx.MockTransport(handler),
        )
        self.client = TestClient(
            create_app(
                Path(self.temp.name) / "chat.sqlite3",
                engine_factory=lambda: None,
                default_provider="opencode",
                opencode_provider=provider,
            )
        )
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temp.cleanup()

    def test_key_health_chat_headers_and_usage(self):
        self.assertEqual(self.client.get("/health").status_code, 503)
        self.assertEqual(
            self.client.put(
                "/settings/opencode-key", json={"api_key": "sk-not-opencode"}
            ).status_code,
            422,
        )
        self.assertEqual(
            self.client.put(
                "/settings/opencode-key", json={"api_key": "oc_sk_valid_test"}
            ).status_code,
            200,
        )
        health = self.client.get("/health")
        self.assertEqual(health.status_code, 200)
        self.assertTrue(health.json()["providers"]["opencode"]["configured"])

        conversation = self.client.put(
            "/characters/companion/conversation",
            params={"provider": "opencode"},
        )
        self.assertEqual(conversation.status_code, 200)
        self.assertEqual(conversation.json()["provider"], "opencode")
        self.assertEqual(conversation.json()["model_id"], "deepseek-v4.1-flash")
        sid = conversation.json()["id"]

        response = self.client.post(
            "/chat",
            json={
                "session_id": sid,
                "message": "你好",
                "max_new_tokens": 32,
                "request_id": "3e9aa595-b487-4475-a1ed-929df33ae2a0",
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["provider"], "opencode")
        self.assertEqual(self.requests[-1]["path"], "/zen/go/v1/chat/completions")
        self.assertEqual(self.requests[-1]["payload"]["model"], "deepseek-v4.1-flash")
        self.assertEqual(
            self.requests[-1]["headers"]["x-opencode-session"], sid
        )
        self.assertEqual(
            self.requests[-1]["headers"]["user-agent"], "shiyu-chat/0.18.0"
        )
        usage = self.client.get("/health").json()["opencode_usage"]
        self.assertEqual(usage["total_tokens"], 14)

    def test_existing_role_conversation_switches_provider_without_losing_messages(self):
        self.client.put(
            "/settings/opencode-key", json={"api_key": "oc_sk_valid_test"}
        )
        existing = self.client.put(
            "/characters/companion/conversation",
            params={"provider": "opencode"},
        ).json()
        self.client.post(
            "/chat",
            json={"session_id": existing["id"], "message": "保留这段聊天"},
        )
        switched = self.client.put(
            "/characters/companion/conversation",
            params={"provider": "opencode"},
        ).json()
        self.assertEqual(switched["id"], existing["id"])
        self.assertEqual(len(switched["messages"]), 3)
        self.assertEqual(switched["provider"], "opencode")
        self.assertEqual(switched["model_id"], "deepseek-v4.1-flash")


if __name__ == "__main__":
    unittest.main()
