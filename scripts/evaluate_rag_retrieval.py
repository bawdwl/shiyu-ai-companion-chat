"""Evaluate structured-memory retrieval with standard IR metrics.

The fixture is synthetic and character-scoped. It is intended to measure the
retrieval stack, not to represent real-user conversation quality.
"""
from __future__ import annotations

import argparse
import json
import math
import statistics
import tempfile
import time
from pathlib import Path

from project_paths import configure

ROOT = configure()

from structured_memory import CandidateStore


CHARACTER_ID = "rag-eval-character"


TARGET_MEMORIES = [
    ("trip-suzhou", "我把苏州行程改到了10月18日，准备去拙政园和平江路。", [
        ("我的苏州行程改到几号了？", "lexical"),
        ("之前说的江南园林旅行定在哪一天？", "paraphrase"),
    ]),
    ("pet-cat", "我养的猫叫团子，是一只橘白相间的猫。", [
        ("我家的猫叫什么名字？", "lexical"),
        ("那个橘白色的小动物是什么名字？", "paraphrase"),
    ]),
    ("coffee-preference", "我喜欢燕麦拿铁，不加糖，也不太喜欢美式。", [
        ("我喜欢喝什么咖啡？", "lexical"),
        ("奶咖里我通常选哪一种？", "paraphrase"),
    ]),
    ("interview-date", "我的面试安排在11月28日下午两点。", [
        ("面试是哪天几点？", "lexical"),
        ("那个工作机会的面谈时间是什么时候？", "paraphrase"),
    ]),
    ("move-city", "我已经搬到杭州滨江区住了。", [
        ("我现在住在哪里？", "lexical"),
        ("我最近换到哪个城市生活？", "paraphrase"),
    ]),
    ("birthday", "我的生日是3月14日。", [
        ("我的生日是哪一天？", "lexical"),
        ("每年什么时候给我过生日？", "paraphrase"),
    ]),
    ("allergy-peanut", "我对花生过敏，吃东西需要避开花生。", [
        ("我对什么食物过敏？", "lexical"),
        ("点餐时最需要避开哪种坚果？", "paraphrase"),
    ]),
    ("coworker-zhoulan", "我的同事周岚是产品经理。", [
        ("周岚是做什么的？", "lexical"),
        ("那个和我一起做产品的同事叫什么？", "paraphrase"),
    ]),
    ("exercise-swim", "我每周二和周四去游泳。", [
        ("我每周什么时候游泳？", "lexical"),
        ("平时的运动安排是哪两天？", "paraphrase"),
    ]),
    ("movie-favorite", "我最喜欢的电影是《海上钢琴师》。", [
        ("我最喜欢哪部电影？", "lexical"),
        ("那部讲钢琴师的老电影叫什么？", "paraphrase"),
    ]),
    ("family-quanzhou", "我外婆住在泉州。", [
        ("我外婆住在哪里？", "lexical"),
        ("家里哪位长辈在闽南那座城市？", "paraphrase"),
    ]),
    ("book-current", "我最近在读《人类简史》。", [
        ("我最近在读什么书？", "lexical"),
        ("那本讲人类历史的中文书叫什么？", "paraphrase"),
    ]),
    ("pet-dog", "我养的狗叫麦芽，是一只金毛。", [
        ("我家狗叫什么名字？", "lexical"),
        ("那只金色的大狗叫什么？", "paraphrase"),
    ]),
    ("trip-kyoto", "我计划2027年春天去京都旅行。", [
        ("我计划什么时候去京都？", "lexical"),
        ("明年春天想去日本哪座城市？", "paraphrase"),
    ]),
    ("job-offer", "我拿到了远澜科技的产品经理 offer。", [
        ("我拿到了哪家公司的 offer？", "lexical"),
        ("那个新工作机会来自什么企业？", "paraphrase"),
    ]),
    ("project-deadline", "项目的最终提交截止时间是12月5日。", [
        ("项目什么时候截止？", "lexical"),
        ("那个方案最晚要在哪一天交？", "paraphrase"),
    ]),
    ("wedding-nanjing", "我朋友1月9日在南京办婚礼。", [
        ("我朋友的婚礼在哪里办？", "lexical"),
        ("那场冬天举行的喜宴在哪座城市？", "paraphrase"),
    ]),
    ("color-indigo", "我最喜欢的颜色是靛蓝色。", [
        ("我最喜欢什么颜色？", "lexical"),
        ("那种偏深的蓝紫色叫什么？", "paraphrase"),
    ]),
    ("sleep-habit", "我通常凌晨一点左右睡觉。", [
        ("我一般几点睡？", "lexical"),
        ("我的夜间作息是什么样？", "paraphrase"),
    ]),
    ("tea-wuyi", "我最近喜欢喝武夷岩茶。", [
        ("我最近喜欢喝什么茶？", "lexical"),
        ("那种福建岩茶叫什么？", "paraphrase"),
    ]),
    ("exam-cpa", "我要参加12月20日的CPA会计考试。", [
        ("我的考试是哪天？", "lexical"),
        ("注册会计师那一科什么时候考？", "paraphrase"),
    ]),
    ("concert-eason", "5月2日我要去上海看陈奕迅演唱会。", [
        ("陈奕迅演唱会是什么时候？", "lexical"),
        ("五一之后那场音乐演出在哪座城市？", "paraphrase"),
    ]),
    ("hike-moganshan", "我计划周末去莫干山徒步。", [
        ("我周末要去哪里徒步？", "lexical"),
        ("那个山里的户外路线叫什么？", "paraphrase"),
    ]),
    ("cook-beef", "我最近学会做番茄牛腩。", [
        ("我最近学会了做什么菜？", "lexical"),
        ("那道番茄和牛肉一起炖的菜是什么？", "paraphrase"),
    ]),
    ("language-japanese", "我正在准备日语N3考试。", [
        ("我在准备什么语言考试？", "lexical"),
        ("最近学习的那个东亚语言目标是什么？", "paraphrase"),
    ]),
    ("home-1802", "我住在18楼1802室。", [
        ("我住在哪个房间？", "lexical"),
        ("我家门牌号是多少？", "paraphrase"),
    ]),
    ("nickname-acheng", "你平时会叫我阿澄。", [
        ("你怎么称呼我？", "lexical"),
        ("我常用的那个昵称是什么？", "paraphrase"),
    ]),
    ("promise-photos", "我答应过晚上把旅行照片发给你。", [
        ("我答应过要发什么？", "lexical"),
        ("之前说好晚上要分享的东西是什么？", "paraphrase"),
    ]),
    ("hospital-derm", "我预约了11月30日去医院看皮肤科。", [
        ("我的医院预约是什么时候？", "lexical"),
        ("那个看皮肤的挂号排在几号？", "paraphrase"),
    ]),
    ("meeting-anniversary", "我们第一次见面是在8月17日的画廊。", [
        ("我们第一次见面是什么时候？", "lexical"),
        ("最初认识那天在哪个场所？", "paraphrase"),
    ]),
]


DISTRACTOR_MEMORIES = [
    "我朋友11月2日也去苏州，但他只去博物馆。",
    "邻居家的猫叫汤圆，是一只白猫。",
    "我偶尔会喝 flat white，但更喜欢手冲咖啡。",
    "另一家公司把面试安排在12月1日。",
    "我的朋友去年搬去了上海。",
    "我妹妹的生日是4月20日。",
    "我朋友对海鲜过敏。",
    "同事陈屿负责市场运营。",
    "我朋友每周一练瑜伽。",
    "我看过《星际穿越》，但不是最喜欢的。",
    "我外公以前住在福州。",
    "我读过《置身事内》，关注地方财政。",
    "朋友家的仓鼠叫栗子。",
    "我的同学计划去东京出差。",
    "朋友拿到北辰资本的实习 offer。",
    "一个旧项目在11月15日做过阶段汇报。",
    "同事的婚礼在苏州举行。",
    "我不喜欢荧光绿色。",
    "我朋友经常凌晨三点才睡。",
    "同事喜欢喝龙井茶。",
    "朋友在准备教师资格证考试。",
    "我同学想看周杰伦演唱会。",
    "我朋友去黄山看过日出。",
    "我会做番茄炒蛋，但不太会炖肉。",
    "朋友在学法语。",
    "同事住在8楼801室。",
    "朋友的小名叫小满。",
    "我答应过给同事带咖啡。",
    "朋友预约了牙科检查。",
    "同事第一次见面是在公司前台。",
]


NO_ANSWER_QUERIES = [
    "火星殖民什么时候能实现？",
    "请解释量子色动力学里的渐近自由。",
    "世界最高峰在哪个国家？",
    "如何证明黎曼猜想？",
    "明天纽约的天气怎么样？",
    "请推荐一只适合长期持有的股票。",
    "地球内部的地核温度是多少？",
    "请翻译一段古埃及象形文字。",
]


def build_fixture() -> dict:
    memories = []
    queries = []
    message_id = 1
    for key, content, query_rows in TARGET_MEMORIES:
        memories.append({
            "key": key,
            "content": content,
            "message_id": message_id,
            "relevant": True,
        })
        for query, category in query_rows:
            queries.append({
                "query": query,
                "category": category,
                "relevant_keys": [key],
            })
        message_id += 1
    for index, content in enumerate(DISTRACTOR_MEMORIES, 1):
        memories.append({
            "key": f"distractor-{index:02d}",
            "content": content,
            "message_id": message_id,
            "relevant": False,
        })
        message_id += 1
    for query in NO_ANSWER_QUERIES:
        queries.append({
            "query": query,
            "category": "no_answer",
            "relevant_keys": [],
        })
    return {
        "version": 1,
        "description": (
            "Synthetic Chinese structured-memory retrieval fixture. "
            "It measures retrieval behavior, not real-user conversation quality."
        ),
        "character_id": CHARACTER_ID,
        "memories": memories,
        "queries": queries,
    }


def load_fixture(path: Path) -> dict:
    """Load and validate the tracked fixture instead of mutating it at run time."""
    fixture = json.loads(path.read_text(encoding="utf-8"))
    memories = fixture.get("memories")
    queries = fixture.get("queries")
    if not isinstance(memories, list) or not memories:
        raise ValueError("fixture must contain a non-empty memories list")
    if not isinstance(queries, list) or not queries:
        raise ValueError("fixture must contain a non-empty queries list")
    memory_keys = [item.get("key") for item in memories]
    if any(not isinstance(key, str) or not key for key in memory_keys):
        raise ValueError("every memory must have a non-empty key")
    if len(memory_keys) != len(set(memory_keys)):
        raise ValueError("memory keys must be unique")
    known_keys = set(memory_keys)
    for query in queries:
        if not isinstance(query.get("query"), str) or not query["query"].strip():
            raise ValueError("every query must contain non-empty query text")
        relevant_keys = query.get("relevant_keys")
        if not isinstance(relevant_keys, list):
            raise ValueError("every query must contain relevant_keys")
        unknown = set(relevant_keys) - known_keys
        if unknown:
            raise ValueError(f"query references unknown memory keys: {sorted(unknown)}")
    return fixture


def ingest(store: CandidateStore, fixture: dict) -> dict[str, int]:
    key_to_id = {}
    for item in fixture["memories"]:
        content = item["content"]
        result = store.auto_ingest(
            fixture["character_id"],
            {
                "status": "candidate",
                "kind": "user_report",
                "subject": "user",
                "fact_key": "rag." + item["key"].replace("-", "."),
                "content": content,
                "evidence": [{
                    "message_id": int(item["message_id"]),
                    "role": "user",
                    "quote": content,
                }],
                "operation": "add",
                "semantic_verified": True,
            },
        )
        memory = result.get("item")
        if memory is None:
            raise RuntimeError(f"failed to ingest {item['key']}: {result}")
        key_to_id[item["key"]] = int(memory["id"])
    return key_to_id


def dcg(rank: int) -> float:
    return 1.0 / math.log2(rank + 1)


def evaluate_method(
    fixture: dict,
    *,
    embedding_model: Path | None,
    temp_root: Path,
    semantic_threshold: float | None = None,
) -> dict:
    name = "fts_lexical_bge" if embedding_model else "fts_lexical"
    if embedding_model:
        effective_threshold = (
            semantic_threshold if semantic_threshold is not None else 0.30
        )
        name += f"@{effective_threshold:.2f}"
    db_path = temp_root / (name + ".sqlite3")
    store = CandidateStore(
        db_path,
        allowed_root=temp_root,
        embedding_model_path=embedding_model,
    )
    try:
        key_to_id = ingest(store, fixture)
        rows = []
        latencies = []
        for query in fixture["queries"]:
            started = time.perf_counter()
            results = store.search_hybrid(
                fixture["character_id"],
                query["query"],
                limit=20,
            )
            if embedding_model:
                results = [
                    item
                    for item in results
                    if float(item.get("retrieval", {}).get("semantic", 0.0))
                    >= effective_threshold
                ]
            results = results[:10]
            latencies.append((time.perf_counter() - started) * 1000.0)
            ranked_ids = [int(item["id"]) for item in results]
            relevant_ids = {
                key_to_id[key] for key in query["relevant_keys"]
            }
            first_rank = next(
                (
                    index
                    for index, item_id in enumerate(ranked_ids, 1)
                    if item_id in relevant_ids
                ),
                None,
            )
            row = {
                **query,
                "ranked_ids": ranked_ids,
                "ranked": [
                    {
                        "id": int(item["id"]),
                        "score": float(item.get("retrieval_score", 0.0)),
                        "semantic": float(
                            item.get("retrieval", {}).get("semantic", 0.0)
                        ),
                        "fts": float(
                            item.get("retrieval", {}).get("fts", 0.0)
                        ),
                        "lexical": float(
                            item.get("retrieval", {}).get("lexical", 0.0)
                        ),
                    }
                    for item in results
                ],
                "first_rank": first_rank,
                "hit_at_1": bool(first_rank and first_rank <= 1),
                "hit_at_3": bool(first_rank and first_rank <= 3),
                "hit_at_5": bool(first_rank and first_rank <= 5),
                "hit_at_10": bool(first_rank and first_rank <= 10),
                "mrr_at_10": (1.0 / first_rank) if first_rank and first_rank <= 10 else 0.0,
                "ndcg_at_10": dcg(first_rank) if first_rank and first_rank <= 10 else 0.0,
                "precision_at_5": (
                    sum(item_id in relevant_ids for item_id in ranked_ids[:5]) / 5.0
                    if relevant_ids else 0.0
                ),
            }
            rows.append(row)
        answerable = [row for row in rows if row["relevant_keys"]]
        no_answer = [row for row in rows if not row["relevant_keys"]]
        summary = {
            "retrieval_mode": name,
            "embedding_model": str(embedding_model) if embedding_model else None,
            "semantic_threshold": (
                effective_threshold if embedding_model else None
            ),
            "queries": len(rows),
            "answerable_queries": len(answerable),
            "no_answer_queries": len(no_answer),
            "recall_at_1": round(statistics.mean(row["hit_at_1"] for row in answerable), 4),
            "recall_at_3": round(statistics.mean(row["hit_at_3"] for row in answerable), 4),
            "recall_at_5": round(statistics.mean(row["hit_at_5"] for row in answerable), 4),
            "recall_at_10": round(statistics.mean(row["hit_at_10"] for row in answerable), 4),
            "mrr_at_10": round(statistics.mean(row["mrr_at_10"] for row in answerable), 4),
            "ndcg_at_10": round(statistics.mean(row["ndcg_at_10"] for row in answerable), 4),
            "precision_at_5": round(statistics.mean(row["precision_at_5"] for row in answerable), 4),
            "no_answer_false_positive_at_5": round(
                statistics.mean(bool(row["ranked_ids"][:5]) for row in no_answer),
                4,
            ) if no_answer else None,
            "latency_ms_mean": round(statistics.mean(latencies), 3),
            "latency_ms_first_query": round(latencies[0], 3),
            "latency_ms_warm_mean": round(
                statistics.mean(latencies[1:]) if len(latencies) > 1 else 0.0,
                3,
            ),
            "latency_ms_p95": round(
                sorted(latencies[1:] or latencies)[
                    max(0, math.ceil(len(latencies[1:] or latencies) * 0.95) - 1)
                ],
                3,
            ),
            "category_recall_at_5": {
                category: round(
                    statistics.mean(
                        row["hit_at_5"]
                        for row in answerable
                        if row["category"] == category
                    ),
                    4,
                )
                for category in sorted({row["category"] for row in answerable})
            },
        }
        return {"summary": summary, "rows": rows}
    finally:
        store.close()


def markdown_report(
    fixture: dict,
    lexical: dict,
    hybrid_variants: list[dict],
) -> str:
    methods = [lexical]
    methods.extend(hybrid_variants)
    lines = [
        "# 拾语 RAG 检索评测",
        "",
        "> 合成 fixture，仅用于比较检索算法，不代表真实用户数据或最终回答正确率。",
        "",
        "## Fixture",
        "",
        f"- 记忆条数：{len(fixture['memories'])}",
        f"- 查询数：{len(fixture['queries'])}",
        f"- 可回答问题：{sum(bool(q['relevant_keys']) for q in fixture['queries'])}",
        f"- 无答案问题：{sum(not q['relevant_keys'] for q in fixture['queries'])}",
        "",
        "## 汇总",
        "",
        "| 方法 | R@1 | R@3 | R@5 | R@10 | MRR@10 | nDCG@10 | P@5 | 无答案误召回@5 | 首次延迟 ms | 热查询均延迟 ms | P95 ms |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for method in methods:
        s = method["summary"]
        lines.append(
            f"| {s['retrieval_mode']} | {s['recall_at_1']:.3f} | "
            f"{s['recall_at_3']:.3f} | {s['recall_at_5']:.3f} | "
            f"{s['recall_at_10']:.3f} | {s['mrr_at_10']:.3f} | "
            f"{s['ndcg_at_10']:.3f} | {s['precision_at_5']:.3f} | "
            f"{s['no_answer_false_positive_at_5']:.3f} | "
            f"{s['latency_ms_first_query']:.3f} | "
            f"{s['latency_ms_warm_mean']:.3f} | {s['latency_ms_p95']:.3f} |"
        )
    lines += [
        "",
        "## 分类召回",
        "",
    ]
    for method in methods:
        lines.append(f"### {method['summary']['retrieval_mode']}")
        lines.append("")
        for category, value in method["summary"]["category_recall_at_5"].items():
            lines.append(f"- {category}: Recall@5 `{value:.3f}`")
        lines.append("")
    lines += [
        "## 说明",
        "",
        "- 本评测衡量的是记忆候选是否被检索到，不是最终回答是否正确。",
        "- 尚未运行端到端 LLM 生成、事实幻觉分类或多人工盲评。",
        "- 合成数据包含同义改写、易混事实和无关问题，可用于后续回归。",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dataset",
        type=Path,
        default=ROOT / "evaluation/rag/retrieval_v1.json",
    )
    parser.add_argument(
        "--embedding-model",
        type=Path,
        default=ROOT / "outputs/models/bge-small-zh-v1.5",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=ROOT / "outputs/rag-evaluation-v1",
    )
    parser.add_argument("--no-embedding", action="store_true")
    parser.add_argument(
        "--refresh-fixture",
        action="store_true",
        help="Regenerate the tracked fixture from the constants in this script.",
    )
    args = parser.parse_args()

    if args.refresh_fixture:
        fixture = build_fixture()
        args.dataset.parent.mkdir(parents=True, exist_ok=True)
        args.dataset.write_text(
            json.dumps(fixture, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    else:
        fixture = load_fixture(args.dataset)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    research_root = ROOT / "outputs/memory-research"
    research_root.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(
        prefix="rag-eval-",
        dir=research_root,
    ) as temp_dir:
        temp_root = Path(temp_dir)
        lexical = evaluate_method(
            fixture,
            embedding_model=None,
            temp_root=temp_root,
        )
        hybrid_variants = []
        embedding_path = args.embedding_model.resolve()
        if not args.no_embedding and embedding_path.is_dir():
            hybrid_variants = [
                evaluate_method(
                    fixture,
                    embedding_model=embedding_path,
                    temp_root=temp_root,
                    semantic_threshold=threshold,
                )
                for threshold in (0.30, 0.35, 0.40, 0.45, 0.50)
            ]

    result = {
        "fixture": str(args.dataset),
        "lexical": lexical,
        "hybrid": hybrid_variants[0] if hybrid_variants else None,
        "hybrid_variants": hybrid_variants,
    }
    (args.output_dir / "results.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    report = markdown_report(fixture, lexical, hybrid_variants)
    (args.output_dir / "report.md").write_text(report, encoding="utf-8")
    print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
