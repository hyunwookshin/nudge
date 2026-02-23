# auth/auth.py
import os, yaml, base64, hashlib, secrets
from datetime import datetime, timezone
from functools import wraps
from flask import request, jsonify, g

DEFAULT_USERS_PATH = os.getenv("DEFAULT_USERS_PATH", "store/auth.yaml")

def get_secure_key():
    securePath = os.getenv("NUDGE_SECURE_KEY_PATH", "")
    with open(securePath, "r") as f:
        key = f.read().strip()
        assert key != "", "Empty Key"
    return key

def _now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def _sha256_hex(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()

def _load_auth():
    if not os.path.exists(DEFAULT_USERS_PATH):
        return {"users": {}}
    with open(DEFAULT_USERS_PATH, "r") as f:
        raw = f.read().strip()
        if not raw:
            return {"users": {}}
        return yaml.safe_load(raw) or {"users": {}}

def _save_auth(doc):
    os.makedirs(os.path.dirname(DEFAULT_USERS_PATH), exist_ok=True)
    with open(DEFAULT_USERS_PATH, "w") as f:
        f.write(yaml.safe_dump(doc, default_flow_style=False, indent=4).strip())

def _get_user(doc, username: str):
    return (doc.get("users") or {}).get(username)

def _set_user(doc, username: str, user_obj):
    doc.setdefault("users", {})
    doc["users"][username] = user_obj

def _hash_password(password: str, salt_b64: str) -> str:
    # simple: sha256(salt + ":" + password)
    return _sha256_hex(f"{salt_b64}:{password}")

def create_user(username: str, password: str):
    doc = _load_auth()
    if _get_user(doc, username) is not None:
        return None, "User already exists"

    salt = get_secure_key() + username
    pw_hash = _hash_password(password, salt)

    user_obj = {
        "password": {
            "salt": salt,
            "pw_hash": pw_hash,
            "created": _now_iso(),
        },
        "tokens": []
    }
    _set_user(doc, username, user_obj)
    _save_auth(doc)
    return user_obj, None

def verify_password(username: str, password: str) -> bool:
    doc = _load_auth()
    user = _get_user(doc, username)
    if not user:
        return False
    pw = user.get("password") or {}
    salt = pw.get("salt") or ""
    expected = pw.get("pw_hash") or ""
    return _hash_password(password, salt) == expected

def issue_token(username: str):
    # return: (raw_token, token_sha256)
    raw = secrets.token_urlsafe(32)  # long random token
    token_sha = _sha256_hex(raw)

    doc = _load_auth()
    user = _get_user(doc, username)
    if not user:
        return None, None

    user.setdefault("tokens", [])
    user["tokens"].append({
        "token_sha256": token_sha,
        "created": _now_iso(),
    })
    if len(user["tokens"]) >= 5:
        user["tokens"].pop(0)

    _set_user(doc, username, user)
    _save_auth(doc)
    return raw, token_sha

def user_from_token_header():
    token = request.headers.get("X-Nudge-Token", "").strip()
    if not token:
        return None

    token_sha = _sha256_hex(token)
    doc = _load_auth()
    users = doc.get("users") or {}

    for username, u in users.items():
        for t in (u.get("tokens") or []):
            if t.get("token_sha256") == token_sha:
                return username
    return None

def require_user(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        username = user_from_token_header()
        if not username:
            return jsonify({"message": "Unauthorized"}), 401
        g.username = username
        return fn(*args, **kwargs)
    return wrapper

def load_auth_db():
    return _load_auth()
