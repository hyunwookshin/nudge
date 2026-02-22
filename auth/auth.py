import os
import yaml
import hashlib
from typing import Optional, Tuple

from flask import request, jsonify, Response

DEFAULT_USERS_FILE = "auth.yaml"

def users_path(cfg) -> str:
    base = cfg.getStorePath()
    os.makedirs(base, exist_ok=True)
    return os.path.join(base, DEFAULT_USERS_FILE)

def load_users_db(cfg) -> dict:
    path = users_path(cfg)
    if not os.path.exists(path):
        return {"users": {}}
    with open(path, "r") as f:
        return yaml.safe_load(f) or {"users": {}}

def sha256_hex(s: str) -> str:
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()

def user_from_token(token: str, cfg) -> Optional[str]:
    token = (token or "").strip()
    if not token:
        return None

    token_hash = sha256_hex(token)
    db = load_users_db(cfg).get("users", {})

    for username, info in db.items():
        tokens = info.get("tokens") or []
        for t in tokens:
            if t.get("token_sha256") == token_hash:
                return username

    return None

def require_user(cfg) -> Tuple[Optional[str], Optional[Tuple[Response, int]]]:
    auth_header = request.headers.get("Authorization", "").strip()
    token = ""

    if auth_header.lower().startswith("bearer "):
        token = auth_header[7:].strip()
    else:
        token = request.headers.get("X-Nudge-Token", "").strip()

    user = user_from_token(token, cfg)
    if not user:
        return None, (jsonify({"message": "Unauthorized"}), 401)

    return user, None
