# auth/auth.py
import os
import yaml
import hashlib
import secrets
from datetime import datetime, timezone
import bcrypt
from flask import request, jsonify

AUTH_PATH = os.getenv("DEFAULT_USERS_PATH", "store/auth.yaml")

def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def _sha256_hex(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()

def load_auth_db():
    if not os.path.exists(AUTH_PATH):
        return {"users": {}}
    with open(AUTH_PATH, "r") as f:
        data = yaml.safe_load(f.read()) or {}
    data.setdefault("users", {})
    return data

def save_auth_db(db):
    os.makedirs(os.path.dirname(AUTH_PATH), exist_ok=True)
    with open(AUTH_PATH, "w") as f:
        yaml.safe_dump(db, f, default_flow_style=False, indent=2)

def verify_password(stored_bcrypt: str, password: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8"), stored_bcrypt.encode("utf-8"))
    except Exception:
        return False

def issue_token() -> str:
    # 32 bytes random => 64 hex chars
    return secrets.token_hex(32)

def store_token_for_user(db, username: str, raw_token: str):
    u = db["users"].setdefault(username, {})
    u.setdefault("tokens", [])
    u["tokens"].append({
        "token_sha256": _sha256_hex(raw_token),
        "created": _now_iso(),
    })

def user_from_token(raw_token: str):
    if not raw_token:
        return None
    db = load_auth_db()
    token_hash = _sha256_hex(raw_token)
    for username, u in db["users"].items():
        for t in (u.get("tokens") or []):
            if t.get("token_sha256") == token_hash:
                return username
    return None

def require_user():
    token = request.headers.get("X-Nudge-Token", "").strip()
    username = user_from_token(token)
    if not username:
        # return a flask response so callers can just "return err"
        return None, (jsonify({"message": "Unauthorized"}), 401)
    return username, None
