"""Tests for the synthetic Android transfer fixture.

The fixture must exercise the *real* handler from ``medo.share``. These tests
pin the protocol behaviour the new Android client will rely on: v2 ZIP shape,
read-only routes, missing APK reporting, no auth/CORS and no Range support.
"""
from __future__ import annotations

import http.client
import importlib.util
import json
import threading
from pathlib import Path

import pytest

from medo.share import BUNDLE_FILENAME, FORMAT_NAME, FORMAT_VERSION, read_bundle

ROOT = Path(__file__).resolve().parents[1]


def _load_module():
    path = ROOT / "scripts" / "dev" / "fake_share_server.py"
    spec = importlib.util.spec_from_file_location("fake_share_server", path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.fixture()
def fake_server():
    module = _load_module()
    httpd, size = module.start_fake_share(port=0, bind="127.0.0.1")
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    try:
        yield module, int(httpd.server_address[1]), size
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join(timeout=5)


def _request(port: int, method: str, path: str, headers=None):
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        conn.request(method, path, headers=headers or {})
        response = conn.getresponse()
        return response.status, dict(response.getheaders()), response.read()
    finally:
        conn.close()


def test_synthetic_bank_is_a_valid_v2_zip(fake_server):
    _, port, size = fake_server
    status, headers, body = _request(port, "GET", f"/{BUNDLE_FILENAME}")
    assert status == 200
    assert len(body) == size
    assert headers["Content-Type"] == "application/zip"
    assert headers["Content-Disposition"] == f'attachment; filename="{BUNDLE_FILENAME}"'
    bundle = read_bundle(body)
    assert bundle["format"] == FORMAT_NAME
    assert bundle["version"] == FORMAT_VERSION
    assert bundle["stats"]["total"] == 1
    assert bundle["stats"]["with_answer"] == 1
    assert len(bundle["questions"]) == 1
    assert len(bundle["chunks"]) == 1


def test_health_index_and_unknown_path(fake_server):
    _, port, _ = fake_server
    status, headers, body = _request(port, "GET", "/health")
    assert status == 200
    assert headers["Content-Type"] == "application/json"
    assert json.loads(body) == {"ok": True}

    status, headers, body = _request(port, "GET", "/")
    assert status == 200
    assert headers["Content-Type"].startswith("text/html")
    text = body.decode("utf-8")
    assert "/medo.medo" in text and "/medo.apk" in text

    status, headers, body = _request(port, "GET", "/not-a-route")
    assert status == 404
    assert headers["Content-Type"] == "application/json"
    assert json.loads(body) == {"ok": False}


def test_bank_aliases_have_the_same_bytes_and_no_range_or_cors(fake_server):
    _, port, _ = fake_server
    bodies = []
    for path in (f"/{BUNDLE_FILENAME}", "/bank.json", "/medo.json"):
        status, headers, body = _request(port, "GET", path)
        assert status == 200
        assert "Accept-Ranges" not in headers
        assert "Access-Control-Allow-Origin" not in headers
        bodies.append(body)
    assert bodies[0] == bodies[1] == bodies[2]

    status, headers, body = _request(
        port, "GET", f"/{BUNDLE_FILENAME}",
        headers={"Range": "bytes=0-9"})
    assert status == 200
    assert len(body) == len(bodies[0])
    assert "Content-Range" not in headers
    assert "Accept-Ranges" not in headers


def test_missing_apk_is_a_json_404(fake_server):
    _, port, _ = fake_server
    status, headers, body = _request(port, "GET", "/medo.apk")
    assert status == 404
    assert headers["Content-Type"] == "application/json"
    data = json.loads(body)
    assert int(headers["Content-Length"]) == 65
    assert data["ok"] is False
    assert "build the APK" in data["error"]


def test_configured_apk_is_served_with_install_headers(tmp_path):
    module = _load_module()
    apk = tmp_path / "medo.apk"
    payload = b"PK\x03\x04synthetic-apk"
    apk.write_bytes(payload)
    httpd, _ = module.start_fake_share(port=0, bind="127.0.0.1", apk_path=apk)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    try:
        port = int(httpd.server_address[1])
        for path in ("/medo.apk", "/app.apk"):
            status, headers, body = _request(port, "GET", path)
            assert status == 200
            assert body == payload
            assert headers["Content-Type"] == (
                "application/vnd.android.package-archive")
            assert headers["Content-Disposition"] == (
                'attachment; filename="medo.apk"')
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join(timeout=5)


def test_head_has_length_and_no_body_and_writes_are_refused(fake_server):
    _, port, size = fake_server
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        conn.request("HEAD", f"/{BUNDLE_FILENAME}")
        response = conn.getresponse()
        assert response.status == 200
        assert int(response.getheader("Content-Length")) == size
        assert response.read() == b""
    finally:
        conn.close()

    try:
        status, _, _ = _request(port, "POST", "/medo.medo", headers={
            "Content-Type": "application/octet-stream"})
    except (ConnectionResetError, http.client.RemoteDisconnected):
        status = 501
    assert status in (405, 501)
