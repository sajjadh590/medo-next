#!/usr/bin/env python
"""A synthetic bank served through the real desktop transfer handler.

This is a development fixture for the Android transfer client. It serves a
one-question synthetic ``.medo`` v2 ZIP and, if ``--apk`` is given, that APK.
The HTTP implementation is imported from :mod:`medo.share`, so routes, headers,
methods and status codes are exactly the desktop transfer protocol rather than
a second implementation that can drift.

No real bank, cookie, session or question text is read.
"""
from __future__ import annotations

import argparse
import io
import json
import sys
import zipfile
from datetime import datetime, timezone
from http.server import ThreadingHTTPServer
from pathlib import Path
from typing import Optional

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from medo.share import (  # noqa: E402
    BUNDLE_FILENAME,
    CHUNK_DIR,
    FORMAT_NAME,
    FORMAT_VERSION,
    INDEX_NAME,
    _make_handler,
    index_and_chunks,
)

SYNTHETIC_TOTAL = 1


def synthetic_bank() -> dict:
    """A complete v2 payload with the same keys as a real export."""
    question = {
        "id": 1,
        "topic_id": 101,
        "tag_id": 0,
        "nodes": [{"topic_id": 101, "tag_id": 0}],
        "source_url": "/qbank/topic-id-101?page=1",
        "page": 1,
        "ordinal": 1,
        "stem_html": "<p>SYNTHETIC-QUESTION-1</p>",
        "stem_text": "SYNTHETIC-QUESTION-1",
        "options": [
            {"index": i, "text": f"SYNTHETIC-OPTION-{i}"} for i in range(1, 5)
        ],
        "correct": 1,
        "answer_source": "synthetic",
        "votes": {},
        "explanation_html": "<p>SYNTHETIC-LESSON-1</p>",
        "explanation_text": "SYNTHETIC-LESSON-1",
        "explanation_status": "synthetic",
        "has_images": False,
        "path": "SYNTHETIC-CATEGORY › SYNTHETIC-TOPIC",
        "bookmarked": False,
        "selected": None,
    }
    topic = {
        "id": 11,
        "parent_id": 1,
        "kind": "topic",
        "title": "SYNTHETIC-TOPIC",
        "source_url": "/qbank/topic-id-101",
        "position": 1,
        "question_count": 1,
        "progress": {},
        "partial": False,
        "children": [],
    }
    category = {
        "id": 1,
        "parent_id": None,
        "kind": "category",
        "title": "SYNTHETIC-CATEGORY",
        "source_url": "/qbank/cat-id-1",
        "position": 1,
        "question_count": 1,
        "progress": {},
        "partial": False,
        "children": [topic],
    }
    return {
        "format": FORMAT_NAME,
        "version": FORMAT_VERSION,
        "generated_at": datetime.now(timezone.utc).replace(
            microsecond=0).isoformat(),
        "stats": {
            "total": SYNTHETIC_TOTAL,
            "with_answer": 1,
            "missing_answer": 0,
            "with_explanation": 1,
            "bookmarked": 0,
            "with_images": False,
        },
        "tree": [category],
        "questions": [question],
    }


def synthetic_bundle_bytes() -> bytes:
    """The same ZIP layout as ``medo.share.bundle_bytes``."""
    index, chunks = index_and_chunks(synthetic_bank())
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(INDEX_NAME, json.dumps(index, ensure_ascii=False,
                                           separators=(",", ":")))
        for cid, questions in chunks.items():
            body = {
                "format": FORMAT_NAME,
                "version": FORMAT_VERSION,
                "chunk": cid,
                "questions": questions,
            }
            zf.writestr(f"{CHUNK_DIR}/{cid}.json",
                        json.dumps(body, ensure_ascii=False,
                                   separators=(",", ":")))
    return buf.getvalue()


def start_fake_share(
    port: int = 8765,
    bind: str = "0.0.0.0",
    apk_path: Optional[Path | str] = None,
) -> tuple[ThreadingHTTPServer, int]:
    """Start the real share handler over the synthetic bank.

    Returns ``(httpd, bundle_bytes)``. The caller owns shutdown/server_close.
    """
    blob = synthetic_bundle_bytes()
    handler = _make_handler(blob, ":memory:", apk_path)
    httpd = ThreadingHTTPServer((bind, int(port)), handler)
    httpd.daemon_threads = True
    return httpd, len(blob)


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765,
                        help="default: 8765; 0 chooses a free port")
    parser.add_argument("--bind", default="0.0.0.0",
                        help="default: 0.0.0.0 (LAN); use 127.0.0.1 for USB only")
    parser.add_argument("--apk", type=Path, default=None,
                        help="optional APK file to serve at /medo.apk")
    args = parser.parse_args(argv)

    if args.apk is not None and not args.apk.is_file():
        print(f"APK_MISSING path={args.apk}")
        return 2

    httpd, size = start_fake_share(args.port, args.bind, args.apk)
    actual_port = int(httpd.server_address[1])
    print(f"FAKE_BANK format={FORMAT_NAME} version={FORMAT_VERSION} "
          f"questions={SYNTHETIC_TOTAL} chunks=1 bytes={size}")
    print(f"BIND {args.bind}:{actual_port}")
    print(f"BASE_URL http://127.0.0.1:{actual_port}"
          if args.bind in {"127.0.0.1", "localhost"}
          else f"BASE_URL http://<this-machine-lan-ip>:{actual_port}")
    print("ROUTES GET /, /index.html, /medo.medo, /bank.json, /medo.json, "
          "/medo.apk, /app.apk, /health")
    print(f"BANK {BUNDLE_FILENAME} bytes={size}")
    print(f"APK {'configured' if args.apk else 'not configured (404)'}")
    print("AUTH none CORS none RANGE none (read-only GET/HEAD)")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("STOPPED")
    finally:
        httpd.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
