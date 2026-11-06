import io
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient
from PIL import Image

from backend import create_app
from fixed_character_prompts import FIXED_CHARACTER_PROMPTS


EXPECTED_NAMES = ["李白", "宋知意", "裴既明", "萧承渊"]


def png_bytes() -> bytes:
    output = io.BytesIO()
    Image.new("RGB", (96, 96), "#315f4a").save(output, format="PNG")
    return output.getvalue()


class ProductCharactersAndAvatarTests(unittest.TestCase):
    def test_demo_characters_seed_once_and_avatar_can_be_replaced(self):
        with tempfile.TemporaryDirectory() as folder:
            database = Path(folder) / "chat.sqlite3"
            app = create_app(
                db_path=str(database),
                default_provider="deepseek",
                structured_memory=False,
                seed_product_character_cards=True,
            )
            with TestClient(app) as client:
                response = client.get("/characters")
                self.assertEqual(response.status_code, 200)
                rows = response.json()["characters"]
                self.assertCountEqual([row["name"] for row in rows], EXPECTED_NAMES)
                character_id = rows[1]["id"]

                uploaded = client.post(
                    f"/characters/{character_id}/avatar",
                    content=png_bytes(),
                    headers={"Content-Type": "image/png"},
                )
                self.assertEqual(uploaded.status_code, 201, uploaded.text)
                self.assertTrue(uploaded.json()["uploaded"])
                avatar = client.get(f"/characters/{character_id}/avatar")
                self.assertEqual(avatar.status_code, 200)
                self.assertEqual(avatar.headers["content-type"], "image/webp")

                deleted = client.delete(f"/characters/{character_id}")
                self.assertEqual(deleted.status_code, 200)

            restarted = create_app(
                db_path=str(database),
                default_provider="deepseek",
                structured_memory=False,
                seed_product_character_cards=True,
            )
            with TestClient(restarted) as client:
                names = [
                    row["name"]
                    for row in client.get("/characters").json()["characters"]
                ]
                self.assertCountEqual(names, ["李白", "裴既明", "萧承渊"])

    def test_retired_role_is_removed_with_its_saved_card(self):
        with tempfile.TemporaryDirectory() as folder:
            database = Path(folder) / "chat.sqlite3"
            from storage import ChatStore
            store = ChatStore(database)
            store.save_character_card({
                "id": "custom-gu-qinghe-v1",
                "name": "顾清禾",
                "tagline": "待删除角色",
            })
            app = create_app(
                db_path=str(database),
                default_provider="deepseek",
                structured_memory=False,
                seed_product_character_cards=True,
            )
            with TestClient(app) as client:
                rows = client.get("/characters").json()["characters"]
                self.assertNotIn("顾清禾", [row["name"] for row in rows])
                self.assertEqual(len(rows), 4)

    def test_fixed_role_cards_are_refreshed_to_training_prompt_fields(self):
        with tempfile.TemporaryDirectory() as folder:
            database = Path(folder) / "chat.sqlite3"
            from storage import ChatStore
            store = ChatStore(database)
            store.save_character_card({
                "id": "custom-lora-song-zhiyi-v1",
                "name": "宋知意",
                "description": "旧版完整档案",
                "personality": "旧版性格",
                "speech_style": "旧版表达",
                "relationship": "旧版关系",
                "worldbook": [
                    {"title": "旧条目", "keywords": ["旧"], "content": "旧内容"}
                ],
            })
            store.set_setting("product_characters_seeded_v1", {"version": 1})
            app = create_app(
                db_path=str(database),
                default_provider="deepseek",
                structured_memory=False,
                seed_product_character_cards=True,
            )
            with TestClient(app) as client:
                card = client.get(
                    "/characters/custom-lora-song-zhiyi-v1"
                ).json()["card"]
                self.assertEqual(
                    card["description"],
                    "20 岁女性，是用户同校低一年级的学妹，视觉传达设计专业，家在泉州，目前在厦门读大学。",
                )
                self.assertNotEqual(card["personality"], "旧版性格")
                self.assertEqual(card["worldbook"], [])

    def test_fixed_roles_do_not_append_card_state_or_memory_sections(self):
        with tempfile.TemporaryDirectory() as folder:
            app = create_app(
                db_path=str(Path(folder) / "chat.sqlite3"),
                default_provider="deepseek",
                structured_memory=True,
                structured_memory_db_path=str(Path(folder) / "memory.sqlite3"),
                seed_product_character_cards=True,
            )
            with TestClient(app) as client:
                for character_id, expected in FIXED_CHARACTER_PROMPTS.items():
                    session = client.post(
                        "/sessions",
                        json={"character_id": character_id},
                    ).json()
                    preview = client.get(
                        f"/sessions/{session['id']}/prompt",
                        params={"message": "继续聊"},
                    ).json()
                    self.assertEqual(
                        preview["messages"][0]["content"],
                        expected,
                    )
                    self.assertEqual(preview["prompt_mode"], "fixed_training")
                    self.assertEqual(preview["worldbook_ids"], [])
                    self.assertEqual(preview["structured_memory_ids"], [])
                    self.assertEqual(
                        preview["internal_state_description"],
                        "不注入（固定训练提示词）",
                    )

    def test_local_diffusers_avatar_generation_contract(self):
        class Generator:
            def available(self):
                return True

            def generate_avatar(self, card, prompt=None):
                self.card = card
                self.prompt = prompt
                return png_bytes(), {
                    "prompt": prompt or "local prompt",
                    "seed": 7,
                    "width": 96,
                    "height": 96,
                }

        with tempfile.TemporaryDirectory() as folder:
            model = Path(folder) / "model"
            model.mkdir()
            (model / "model_index.json").write_text("{}", encoding="utf-8")
            generator = Generator()
            db = Path(folder) / "chat.sqlite3"
            app = create_app(
                db_path=str(db),
                default_provider="deepseek",
                structured_memory=False,
                seed_product_character_cards=True,
                image_generator_factory=lambda _path: generator,
            )
            with TestClient(app) as client:
                configured = client.put(
                    "/settings/local-image-model",
                    json={"model_path": str(model)},
                )
                self.assertEqual(configured.status_code, 200, configured.text)
                generated = client.post(
                    "/characters/custom-lora-song-zhiyi-v1/avatar/generate",
                    json={},
                )
                self.assertEqual(generated.status_code, 201, generated.text)
                self.assertTrue(generated.json()["generated"])
                avatar = client.get(
                    "/characters/custom-lora-song-zhiyi-v1/avatar"
                )
                self.assertEqual(avatar.status_code, 200)
                self.assertEqual(avatar.headers["content-type"], "image/webp")
                self.assertIn("appearance_prompt", generator.card)


if __name__ == "__main__":
    unittest.main()
