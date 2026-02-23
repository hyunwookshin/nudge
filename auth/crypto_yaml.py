from __future__ import annotations

import base64
import os
from dataclasses import dataclass
from typing import Any, Optional

import yaml
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes


MAGIC = b"NUDGEYAML"  # 8 bytes
VERSION = b"\x01"     # 1 byte
NONCE_LEN = 12        # AESGCM recommended
SALT_LEN = 16         # HKDF salt (random per file)


@dataclass(frozen=True)
class EncryptedBlob:
    salt: bytes
    nonce: bytes
    ciphertext: bytes

    def to_bytes(self) -> bytes:
        # Format: MAGIC(8) + VERSION(1) + salt(16) + nonce(12) + ciphertext(var)
        return MAGIC + VERSION + self.salt + self.nonce + self.ciphertext

    @staticmethod
    def from_bytes(data: bytes) -> "EncryptedBlob":
        if len(data) < len(MAGIC) + 1 + SALT_LEN + NONCE_LEN + 1:
            raise ValueError("Encrypted data too short")

        if not data.startswith(MAGIC):
            raise ValueError("Not an encrypted Nudge YAML blob")

        ver = data[len(MAGIC):len(MAGIC) + 1]
        if ver != VERSION:
            raise ValueError(f"Unsupported version: {ver!r}")

        off = len(MAGIC) + 1
        salt = data[off:off + SALT_LEN]
        off += SALT_LEN
        nonce = data[off:off + NONCE_LEN]
        off += NONCE_LEN
        ciphertext = data[off:]
        if not ciphertext:
            raise ValueError("Missing ciphertext")

        return EncryptedBlob(salt=salt, nonce=nonce, ciphertext=ciphertext)


def _derive_key_from_password_hash(password_hash: bytes, salt: bytes) -> bytes:
    """
    Derive a 32-byte AES key from password_hash using HKDF-SHA256.
    password_hash should be stable for that user (same bytes each time).
    """
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        info=b"nudge-yaml-v1",
    )
    return hkdf.derive(password_hash)


def encrypt_bytes(plaintext: bytes, password_hash: bytes) -> bytes:
    salt = os.urandom(SALT_LEN)
    key = _derive_key_from_password_hash(password_hash, salt)
    nonce = os.urandom(NONCE_LEN)
    aesgcm = AESGCM(key)
    ciphertext = aesgcm.encrypt(nonce, plaintext, associated_data=None)
    blob = EncryptedBlob(salt=salt, nonce=nonce, ciphertext=ciphertext)
    return blob.to_bytes()


def decrypt_bytes(blob_bytes: bytes, password_hash: bytes) -> bytes:
    blob = EncryptedBlob.from_bytes(blob_bytes)
    key = _derive_key_from_password_hash(password_hash, blob.salt)
    aesgcm = AESGCM(key)
    return aesgcm.decrypt(blob.nonce, blob.ciphertext, associated_data=None)


def dump_yaml_to_file(
    path: str,
    data: Any,
    *,
    encrypt: bool = False,
    password_hash: Optional[bytes] = None,
) -> None:
    yaml_text = yaml.safe_dump(data, sort_keys=False, allow_unicode=True)
    raw = yaml_text.encode("utf-8")

    if not encrypt:
        with open(path, "wb") as f:
            f.write(raw)
        return

    if not password_hash:
        raise ValueError("password_hash is required when encrypt=True")

    encrypted = encrypt_bytes(raw, password_hash)

    # Store as base64 text so it’s still “file-friendly”
    b64 = base64.b64encode(encrypted)
    with open(path, "wb") as f:
        f.write(b64)


def load_yaml_from_file(
    path: str,
    *,
    encrypt: bool = False,
    password_hash: Optional[bytes] = None,
) -> Any:
    raw = open(path, "rb").read()

    if not encrypt:
        return yaml.safe_load(raw.decode("utf-8"))

    if not password_hash:
        raise ValueError("password_hash is required when encrypt=True")

    blob_bytes = base64.b64decode(raw)
    plaintext = decrypt_bytes(blob_bytes, password_hash)
    return yaml.safe_load(plaintext.decode("utf-8"))
