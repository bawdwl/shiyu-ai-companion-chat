import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from image_generation import StableDiffusionAvatarGenerator, _compact_sd_prompt


class ImageGenerationPromptTests(unittest.TestCase):
    def test_local_fallback_is_english_and_keeps_critical_framing_first(self):
        prompt = StableDiffusionAvatarGenerator.build_avatar_prompt(
            {"name": "林婉清", "description": "温柔的书店店员"}
        )

        self.assertTrue(prompt.startswith("polished semi-realistic anime portrait"))
        self.assertIn("face fully visible", prompt)
        self.assertIn("centered composition", prompt)
        self.assertIn("natural detailed face", prompt)
        self.assertNotIn("photorealistic", prompt.lower())
        self.assertNotIn("林婉清", prompt)
        self.assertNotRegex(prompt, r"[\u3400-\u9fff]")

    def test_cloud_prompt_is_compacted_and_styled(self):
        prompt = StableDiffusionAvatarGenerator.prepare_prompt(
            (
                "masterpiece, young woman, shoulder-length dark hair, cream sweater, "
                "beige cardigan, looking at viewer, gentle smile, cozy bookstore, "
                "soft window light, detailed eyes, natural skin, realistic portrait, "
                "beautiful composition, photo, cinematic"
            ),
            {},
        )

        self.assertTrue(prompt.startswith("polished semi-realistic anime portrait"))
        self.assertNotIn("photo", prompt.lower())
        self.assertNotIn("cinematic", prompt.lower())
        self.assertLessEqual(len(prompt.split()), 45)

    def test_chinese_cloud_prompt_falls_back_instead_of_feeding_clip_cjk(self):
        prompt = StableDiffusionAvatarGenerator.prepare_prompt(
            "美丽的中国女孩，短发，在书店里",
            {"name": "小满"},
        )

        self.assertNotRegex(prompt, r"[\u3400-\u9fff]")
        self.assertIn("polished semi-realistic anime portrait", prompt)

    def test_character_english_appearance_prompt_is_used_locally(self):
        prompt = StableDiffusionAvatarGenerator.build_avatar_prompt(
            {
                "name": "宋知意",
                "appearance_prompt": (
                    "1girl, female, solo, 20 year old East Asian woman, "
                    "light brown eyes, long ash brown hair, cream cardigan"
                ),
            }
        )

        self.assertTrue(prompt.startswith("1girl, female, solo"))
        self.assertIn("cream cardigan", prompt)

    def test_negative_prompt_rejects_photographic_and_flat_rendering(self):
        negative = self._negative_prompt()

        for term in ("photo", "photorealistic", "3d render", "cgi", "flat colors"):
            self.assertIn(term, negative)

    @staticmethod
    def _negative_prompt():
        from image_generation import _DEFAULT_NEGATIVE_PROMPT

        return _DEFAULT_NEGATIVE_PROMPT.lower()

    def test_compaction_prefers_complete_visual_clauses(self):
        compact = _compact_sd_prompt(
            "young woman, shoulder-length dark hair, cream sweater with a very long "
            "description that should not be cut, unused trailing clause",
            max_words=12,
        )

        self.assertTrue(compact.startswith("young woman"))
        self.assertLessEqual(len(compact.split()), 12)


if __name__ == "__main__":
    unittest.main()
