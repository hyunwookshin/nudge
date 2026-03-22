#!/usr/bin/env python3

import unittest
import sys
import os
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from auth import auth, crypto_utils


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _user_doc(username="alice", pw_hash="hashval", enc_key="hashval"):
    return {
        "users": {
            username: {
                "password": {
                    "salt":    "securekeybob" + username,
                    "pw_hash": pw_hash,
                    "enc_key": enc_key,
                },
                "tokens": [],
            }
        }
    }


# ---------------------------------------------------------------------------
# crypto_utils tests
# ---------------------------------------------------------------------------

class TestCryptoUtils(unittest.TestCase):

    def setUp(self):
        import hashlib
        self.key = hashlib.sha256(b"test_password_hash").digest()

    def test_encrypt_decrypt_roundtrip(self):
        plaintext = "Hello, World!"
        encrypted = crypto_utils.encrypt_text(plaintext, self.key)
        self.assertTrue(encrypted.startswith(crypto_utils.PREFIX))
        self.assertEqual(crypto_utils.decrypt_text(encrypted, self.key), plaintext)

    def test_encrypt_empty_string_returns_empty(self):
        self.assertEqual(crypto_utils.encrypt_text("", self.key), "")

    def test_decrypt_empty_string_returns_empty(self):
        self.assertEqual(crypto_utils.decrypt_text("", self.key), "")

    def test_decrypt_plaintext_passthrough(self):
        # Values without the enc:v1: prefix are returned as-is.
        self.assertEqual(crypto_utils.decrypt_text("not encrypted", self.key), "not encrypted")

    def test_encrypt_produces_unique_ciphertexts(self):
        # Random salt/nonce means same plaintext encrypts differently each time.
        a = crypto_utils.encrypt_text("same", self.key)
        b = crypto_utils.encrypt_text("same", self.key)
        self.assertNotEqual(a, b)

    def test_encrypt_reminder_fields_roundtrip(self):
        info = {
            "Title":       "Doctor appointment",
            "Description": "Annual checkup",
            "Link":        "http://clinic.example.com",
            "Priority":    2,
            "Closed":      False,
        }
        encrypted = crypto_utils.encrypt_reminder_fields(info, self.key)

        # Only text fields are encrypted
        self.assertTrue(encrypted["Title"].startswith(crypto_utils.PREFIX))
        self.assertTrue(encrypted["Description"].startswith(crypto_utils.PREFIX))
        self.assertTrue(encrypted["Link"].startswith(crypto_utils.PREFIX))
        self.assertEqual(encrypted["Priority"], 2)
        self.assertEqual(encrypted["Closed"], False)

        decrypted = crypto_utils.decrypt_reminder_fields(encrypted, self.key)
        self.assertEqual(decrypted["Title"],       "Doctor appointment")
        self.assertEqual(decrypted["Description"], "Annual checkup")
        self.assertEqual(decrypted["Link"],        "http://clinic.example.com")

    def test_encrypt_reminder_fields_empty_link(self):
        info = {"Title": "T", "Description": "D", "Link": ""}
        encrypted = crypto_utils.encrypt_reminder_fields(info, self.key)
        # Empty link should stay empty (not encrypted).
        self.assertEqual(encrypted["Link"], "")


# ---------------------------------------------------------------------------
# auth tests
# ---------------------------------------------------------------------------

class TestValidatePassword(unittest.TestCase):

    def test_valid_password(self):
        self.assertIsNone(auth.validate_password("Secure1"))

    def test_too_short(self):
        self.assertIsNotNone(auth.validate_password("Ab1"))

    def test_no_uppercase(self):
        self.assertIsNotNone(auth.validate_password("secure1"))

    def test_no_lowercase(self):
        self.assertIsNotNone(auth.validate_password("SECURE1"))

    def test_no_digit(self):
        self.assertIsNotNone(auth.validate_password("SecurePass"))


class TestCreateUser(unittest.TestCase):

    @patch("auth.auth._save_auth")
    @patch("auth.auth._load_auth", return_value={"users": {}})
    @patch("auth.auth.get_secure_key", return_value="secretkey")
    def test_create_user_success(self, mock_key, mock_load, mock_save):
        user_obj, err = auth.create_user("bob", "Password1")
        self.assertIsNone(err)
        self.assertIsNotNone(user_obj)
        mock_save.assert_called_once()

    @patch("auth.auth._load_auth")
    @patch("auth.auth.get_secure_key", return_value="secretkey")
    def test_create_user_duplicate(self, mock_key, mock_load):
        mock_load.return_value = _user_doc("bob")
        _, err = auth.create_user("bob", "Password1")
        self.assertEqual(err, "User already exists")


class TestVerifyPassword(unittest.TestCase):

    @patch("auth.auth.get_secure_key", return_value="securekey")
    def test_correct_password(self, mock_key):
        # Create a real user so we have a valid hash to verify against.
        with patch("auth.auth._save_auth"), \
             patch("auth.auth._load_auth", return_value={"users": {}}):
            auth.create_user("carol", "Password1")

        # Re-derive what _load_auth would return after create_user.
        salt = "securekeycarol"
        pw_hash = auth._sha256_hex(f"{salt}:Password1")
        doc = {"users": {"carol": {"password": {"salt": salt, "pw_hash": pw_hash, "enc_key": pw_hash}, "tokens": []}}}

        with patch("auth.auth._load_auth", return_value=doc):
            self.assertTrue(auth.verify_password("carol", "Password1"))

    @patch("auth.auth._load_auth")
    def test_wrong_password(self, mock_load):
        mock_load.return_value = _user_doc("carol", pw_hash="correcthash")
        self.assertFalse(auth.verify_password("carol", "WrongPass1"))

    @patch("auth.auth._load_auth", return_value={"users": {}})
    def test_unknown_user(self, mock_load):
        self.assertFalse(auth.verify_password("nobody", "Password1"))


class TestGetEncKeyAndPwHash(unittest.TestCase):

    @patch("auth.auth._load_auth")
    def test_get_enc_key(self, mock_load):
        mock_load.return_value = _user_doc("alice", enc_key="myenckey")
        self.assertEqual(auth.get_enc_key("alice"), "myenckey")

    @patch("auth.auth._load_auth")
    def test_get_enc_key_missing_user(self, mock_load):
        mock_load.return_value = {"users": {}}
        self.assertEqual(auth.get_enc_key("nobody"), "")

    @patch("auth.auth._load_auth")
    def test_get_pw_hash(self, mock_load):
        mock_load.return_value = _user_doc("alice", pw_hash="myhash")
        self.assertEqual(auth.get_pw_hash("alice"), "myhash")


class TestIssueToken(unittest.TestCase):

    @patch("auth.auth._save_auth")
    @patch("auth.auth._load_auth")
    def test_issue_token_returns_raw_token(self, mock_load, mock_save):
        mock_load.return_value = _user_doc("alice")
        raw, sha = auth.issue_token("alice")
        self.assertIsNotNone(raw)
        self.assertEqual(sha, auth._sha256_hex(raw))
        mock_save.assert_called_once()

    @patch("auth.auth._load_auth", return_value={"users": {}})
    def test_issue_token_unknown_user(self, mock_load):
        raw, sha = auth.issue_token("nobody")
        self.assertIsNone(raw)
        self.assertIsNone(sha)

    @patch("auth.auth._save_auth")
    @patch("auth.auth._load_auth")
    def test_issue_token_keeps_at_most_five(self, mock_load, mock_save):
        doc = _user_doc("alice")
        doc["users"]["alice"]["tokens"] = [{"token_sha256": f"old{i}", "created": "x"} for i in range(5)]
        mock_load.return_value = doc
        auth.issue_token("alice")
        saved_doc = mock_save.call_args[0][0]
        self.assertLessEqual(len(saved_doc["users"]["alice"]["tokens"]), 5)


class TestChangePassword(unittest.TestCase):

    @patch("auth.auth._save_auth")
    @patch("auth.auth._load_auth")
    def test_change_password_success(self, mock_load, mock_save):
        salt = "securekeyalice"
        old_hash = auth._sha256_hex(f"{salt}:OldPass1")
        doc = {"users": {"alice": {"password": {"salt": salt, "pw_hash": old_hash, "enc_key": old_hash}, "tokens": []}}}
        mock_load.return_value = doc
        err = auth.change_password("alice", "OldPass1", "NewPass2")
        self.assertIsNone(err)
        mock_save.assert_called_once()

    @patch("auth.auth._load_auth")
    def test_change_password_wrong_current(self, mock_load):
        salt = "securekeyalice"
        old_hash = auth._sha256_hex(f"{salt}:OldPass1")
        doc = {"users": {"alice": {"password": {"salt": salt, "pw_hash": old_hash, "enc_key": old_hash}, "tokens": []}}}
        mock_load.return_value = doc
        err = auth.change_password("alice", "WrongPass1", "NewPass2")
        self.assertIsNotNone(err)

    @patch("auth.auth._load_auth", return_value={"users": {}})
    def test_change_password_unknown_user(self, mock_load):
        err = auth.change_password("nobody", "OldPass1", "NewPass2")
        self.assertEqual(err, "User not found")


class TestDeleteUser(unittest.TestCase):

    @patch("auth.auth._save_auth")
    @patch("auth.auth._load_auth")
    def test_delete_user_success(self, mock_load, mock_save):
        mock_load.return_value = _user_doc("alice")
        err = auth.delete_user("alice")
        self.assertIsNone(err)
        mock_save.assert_called_once()

    @patch("auth.auth._load_auth", return_value={"users": {}})
    def test_delete_user_not_found(self, mock_load):
        err = auth.delete_user("nobody")
        self.assertEqual(err, "User not found")


if __name__ == "__main__":
    unittest.main()
