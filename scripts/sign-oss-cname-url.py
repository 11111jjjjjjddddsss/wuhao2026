#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path


def load_profile(profile_name: str | None) -> tuple[str, str]:
    env_id = os.environ.get("OSS_ACCESS_KEY_ID") or os.environ.get("ALIBABA_CLOUD_ACCESS_KEY_ID")
    env_secret = os.environ.get("OSS_ACCESS_KEY_SECRET") or os.environ.get("ALIBABA_CLOUD_ACCESS_KEY_SECRET")
    if env_id and env_secret:
        return env_id, env_secret

    config_path = Path.home() / ".aliyun" / "config.json"
    if not config_path.exists():
        raise SystemExit("aliyun config not found; set OSS_ACCESS_KEY_ID/OSS_ACCESS_KEY_SECRET or configure aliyun CLI")
    config = json.loads(config_path.read_text(encoding="utf-8"))
    selected = profile_name or config.get("current")
    for profile in config.get("profiles", []):
        if profile.get("name") == selected:
            access_key_id = profile.get("access_key_id") or ""
            access_key_secret = profile.get("access_key_secret") or ""
            if access_key_id and access_key_secret:
                return access_key_id, access_key_secret
    raise SystemExit(f"aliyun profile missing access key: {selected}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Create an OSS signed URL for a custom CNAME endpoint.")
    parser.add_argument("--bucket", required=True)
    parser.add_argument("--endpoint", required=True, help="Custom endpoint, for example https://download.example.com")
    parser.add_argument("--object-key", required=True)
    parser.add_argument("--expires-seconds", type=int, default=259200)
    parser.add_argument("--method", default="GET", choices=("GET", "HEAD"))
    parser.add_argument("--profile")
    args = parser.parse_args()

    try:
        import oss2  # type: ignore
    except Exception as exc:
        raise SystemExit("python package oss2 is required; run: python -m pip install --user oss2") from exc

    if args.expires_seconds < 60 or args.expires_seconds > 315360000:
        raise SystemExit("expires-seconds must be between 60 seconds and 10 years")
    access_key_id, access_key_secret = load_profile(args.profile)
    auth = oss2.Auth(access_key_id, access_key_secret)
    bucket = oss2.Bucket(auth, args.endpoint.rstrip("/"), args.bucket, is_cname=True)
    print(bucket.sign_url(args.method, args.object_key.lstrip("/"), args.expires_seconds, slash_safe=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
