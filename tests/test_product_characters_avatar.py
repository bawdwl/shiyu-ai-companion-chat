import io
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient
from PIL import Image

from backend import create_app


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
