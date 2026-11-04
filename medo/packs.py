"""
Additive Format v3 Pack Builder and Local LAN Server for Medo Offline.
Usage:
  python3 -m medo.packs build-packs --out DIR [--categories cat1,cat2] [--levels text,lessons,media]
  python3 -m medo.packs serve DIR [--port 8765]
"""
import argparse
import gzip
import hashlib
import http.server
import json
import os
import socketserver
from typing import Dict, List, Optional


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build_packs(
    questions: List[Dict],
    categories: List[Dict],
    out_dir: str,
    selected_categories: Optional[List[str]] = None,
    levels: Optional[List[str]] = None,
    include_archived: bool = False,
    bank_version: str = "2026.11.1",
) -> Dict:
    os.makedirs(out_dir, exist_ok=True)
    chunks_dir = os.path.join(out_dir, "chunks")
    media_dir = os.path.join(out_dir, "media")
    packs_dir = os.path.join(out_dir, "packs")
    os.makedirs(chunks_dir, exist_ok=True)
    os.makedirs(media_dir, exist_ok=True)
    os.makedirs(packs_dir, exist_ok=True)

    allowed_levels = set(levels or ["text", "lessons", "media"])
    active_categories = [
        c for c in categories if include_archived or not c.get("archived", False)
    ]
    if selected_categories:
        active_categories = [
            c for c in active_categories if c["id"] in selected_categories
        ]

    manifest_packs = []
    question_revisions = {}

    for cat in active_categories:
        cat_id = cat["id"]
        cat_questions = [
            q for q in questions
            if cat_id in q.get("nodeIds", []) and (include_archived or not q.get("archived", False))
        ]
        if not cat_questions:
            continue

        for level in ["text", "lessons", "media"]:
            if level not in allowed_levels:
                continue

            chunk_hashes = []
            media_hashes = []
            total_size = 0

            # Split into chunks of 200 questions
            for i in range(0, len(cat_questions), 200):
                batch = cat_questions[i : i + 200]
                serialized_batch = []
                for q in batch:
                    q_id = q["siteQuestionId"]
                    rev_raw = f"{q.get('stemHtml','')}|{q.get('correctOptionIndex','')}|{q.get('lessonHtml','')}"
                    question_revisions[q_id] = sha256_hex(rev_raw.encode("utf-8"))[:16]

                    item = {
                        "siteQuestionId": q_id,
                        "stemHtml": q["stemHtml"],
                        "options": q["options"],
                        "correctOptionIndex": q.get("correctOptionIndex"),
                        "nodeIds": q.get("nodeIds", []),
                    }
                    if level in ("lessons", "media"):
                        item["explanationHtml"] = q.get("explanationHtml")
                        item["lessonHtml"] = q.get("lessonHtml")
                    if level == "media" and q.get("mindMapMediaHash"):
                        m_hash = q["mindMapMediaHash"]
                        item["mindMapMediaHash"] = m_hash
                        if m_hash not in media_hashes:
                            media_hashes.append(m_hash)
                    serialized_batch.append(item)

                raw_json = json.dumps(serialized_batch, ensure_ascii=False).encode("utf-8")
                compressed = gzip.compress(raw_json)
                c_hash = sha256_hex(compressed)[:24]
                chunk_path = os.path.join(chunks_dir, f"{c_hash}.json.gz")
                with open(chunk_path, "wb") as f:
                    f.write(compressed)
                chunk_hashes.append(c_hash)
                total_size += len(compressed)

            pack_id = f"pack_{cat_id}_{level}"
            pack_desc = {
                "id": pack_id,
                "categoryId": cat_id,
                "categoryPath": cat.get("name", cat_id),
                "level": level,
                "questionCount": len(cat_questions),
                "sizeBytes": total_size,
                "chunkHashes": chunk_hashes,
                "mediaHashes": media_hashes,
            }
            manifest_packs.append(pack_desc)
            with open(os.path.join(packs_dir, f"{pack_id}.json"), "w", encoding="utf-8") as pf:
                json.dump(pack_desc, pf, ensure_ascii=False, indent=2)

    manifest = {
        "formatVersion": 3,
        "bankVersion": bank_version,
        "categories": active_categories,
        "packs": manifest_packs,
        "questionRevisions": question_revisions,
        "tombstones": [],
    }
    manifest_path = os.path.join(out_dir, "manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as mf:
        json.dump(manifest, mf, ensure_ascii=False, indent=2)

    return manifest


def serve_directory(directory: str, port: int = 8765) -> None:
    handler = lambda *args, **kwargs: http.server.SimpleHTTPRequestHandler(
        *args, directory=directory, **kwargs
    )
    with socketserver.TCPServer(("0.0.0.0", port), handler) as httpd:
        print(f"Serving Medo v3 packs at http://0.0.0.0:{port}")
        httpd.serve_forever()


def main():
    parser = argparse.ArgumentParser(description="Medo Format v3 Pack Builder & Server")
    sub = parser.add_subparsers(dest="cmd")

    b_parser = sub.add_parser("build-packs")
    b_parser.add_argument("--out", required=True, help="Output directory for v3 pack")
    b_parser.add_argument("--categories", default="", help="Comma-separated category IDs")
    b_parser.add_argument("--levels", default="text,lessons,media", help="Comma-separated levels")

    s_parser = sub.add_parser("serve")
    s_parser.add_argument("dir", help="Directory to serve over LAN")
    s_parser.add_argument("--port", type=int, default=8765)

    args = parser.parse_args()
    if args.cmd == "build-packs":
        cats = [c.strip() for c in args.categories.split(",") if c.strip()] or None
        lvls = [l.strip() for l in args.levels.split(",") if l.strip()]
        sample_categories = [{"id": "cat_dastyari", "name": "دستیاری پزشکی", "archived": False}]
        sample_questions = [
            {
                "siteQuestionId": "q_1001",
                "stemHtml": "<p>سوال نمونه دستیاری</p>",
                "options": ["الف", "ب", "ج", "د"],
                "correctOptionIndex": 1,
                "explanationHtml": "<p>تشریح نمونه</p>",
                "lessonHtml": "<p>درسنامه نمونه</p>",
                "nodeIds": ["cat_dastyari"],
            }
        ]
        build_packs(sample_questions, sample_categories, args.out, cats, lvls)
    elif args.cmd == "serve":
        serve_directory(args.dir, args.port)


if __name__ == "__main__":
    main()
