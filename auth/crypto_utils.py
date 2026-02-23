from __future__ import annotations

import base64
import os
from dataclasses import dataclass
from typing import Optional

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes

# Self-identifying prefix so plaintext continues working.
PREFIX = "enc:v1:"
SALT_LEN = 16
NONCE_LEN = 12  # AESGCM recommended


def _derive_key(password_hash: bytes, salt: bytes) -> bytes:
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        info=b"nudge-field-v1",
    )
    return hkdf.derive(password_hash)


def encrypt_text(plaintext: str, password_hash: bytes) -> str:
    """
    Encrypt a string and return a tagged string: enc:v1:<b64(salt|nonce|ciphertext)>
    """
    if plaintext is None:
        return plaintext
    if plaintext == "":
        return ""  # keep empty empty (helps UX + avoids extra noise)

    salt = os.urandom(SALT_LEN)
    key = _derive_key(password_hash, salt)
    nonce = os.urandom(NONCE_LEN)

    aesgcm = AESGCM(key)
    ct = aesgcm.encrypt(nonce, plaintext.encode("utf-8"), associated_data=None)

    blob = salt + nonce + ct
    b64 = base64.b64encode(blob).decode("ascii")
    return PREFIX + b64


def decrypt_text(value: str, password_hash: bytes) -> str:
    """
    If value is not encrypted (no prefix), return as-is.
    If encrypted, decrypt and return plaintext.
    """
    if value is None or value == "":
        return value
    if not value.startswith(PREFIX):
        return value

    b64 = value[len(PREFIX):]
    blob = base64.b64decode(b64)

    if len(blob) < SALT_LEN + NONCE_LEN + 1:
        raise ValueError("Encrypted field blob too short")

    salt = blob[:SALT_LEN]
    nonce = blob[SALT_LEN:SALT_LEN + NONCE_LEN]
    ct = blob[SALT_LEN + NONCE_LEN:]

    key = _derive_key(password_hash, salt)
    aesgcm = AESGCM(key)
    pt = aesgcm.decrypt(nonce, ct, associated_data=None)
    return pt.decode("utf-8")


def encrypt_reminder_fields(info: dict, password_hash: bytes) -> dict:
    """
    Takes a reminder dict (your YAML info) and encrypts only certain fields.
    Returns a NEW dict.
    """
    out = dict(info)
    for k in ("Title", "Description", "Link"):
        if k in out and isinstance(out[k], str):
            out[k] = encrypt_text(out[k], password_hash)
    return out


def decrypt_reminder_fields(info: dict, password_hash: bytes) -> dict:
    """
    Decrypt only certain fields if they are tagged.
    Returns a NEW dict.
    """
    out = dict(info)
    for k in ("Title", "Description", "Link"):
        if k in out and isinstance(out[k], str):
            out[k] = decrypt_text(out[k], password_hash)
    return out
