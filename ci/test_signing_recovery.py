"""Behaviour tests for ci/SigningRecovery.java.

The Java helper is launched through the real source launcher. The self-test performs a
throwaway RSA-3072 / AES-256-GCM roundtrip, a wrong-recipient check, a tampered-ciphertext
check and a minimum-key-size check. The remaining cases exercise the CLI failure paths with
synthetic material only; no real signing secrets are read.
"""

from __future__ import annotations

import base64
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAVA_HOME = Path(r"C:\Program Files\Android\Android Studio\jbr")
CHILD_ENV_NAMES = ("PATH", "JAVA_HOME", "SystemRoot", "TEMP", "TMP", "LANG")
SYNTHETIC_KEYSTORE = base64.b64encode(b"synthetic-recovery-keystore").decode("ascii")
SYNTHETIC_PASSWORD = "synthetic-recovery-password-9c1f"
TIMEOUT_SECONDS = 180


def _tool(java_home: Path, name: str) -> Path:
    suffix = ".exe" if os.name == "nt" else ""
    return java_home / "bin" / f"{name}{suffix}"


def _child_env(java_home: Path, **explicit: str | None) -> dict[str, str]:
    env = {}
    for name in CHILD_ENV_NAMES:
        value = os.environ.get(name)
        if value:
            env[name] = value
    env["JAVA_HOME"] = str(java_home)
    env.setdefault("PATH", os.defpath)
    for name, value in explicit.items():
        if value is None:
            env.pop(name, None)
        else:
            env[name] = value
    return env


def _run(command, *, env):
    return subprocess.run(
        [str(part) for part in command],
        cwd=str(REPO_ROOT),
        env=env,
        capture_output=True,
        text=True,
        timeout=TIMEOUT_SECONDS,
    )


class SigningRecoveryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        java_home_setting = os.environ.get("JAVA_HOME")
        java_home = (
            Path(java_home_setting).expanduser().resolve()
            if java_home_setting
            else DEFAULT_JAVA_HOME
        )
        java = _tool(java_home, "java")
        if not java.is_file():
            raise RuntimeError(f"JAVA_HOME has no java: {java_home}")
        cls._java = java
        cls._java_home = java_home
        cls._source = REPO_ROOT / "ci" / "SigningRecovery.java"
        cls._temp = tempfile.TemporaryDirectory(prefix="signing-recovery-")
        cls._work = Path(cls._temp.name)

    @classmethod
    def tearDownClass(cls):
        cls._temp.cleanup()

    def _output(self, name: str = "envelope.json") -> Path:
        path = self._work / name
        path.unlink(missing_ok=True)
        return path

    def _run_cli(self, output: Path, **env_updates):
        return _run(
            [self._java, self._source, output],
            env=_child_env(self._java_home, **env_updates),
        )

    def _assert_clean_failure(self, result, output: Path, expected_message: str):
        combined = result.stdout + result.stderr
        self.assertNotEqual(result.returncode, 0, combined)
        self.assertIn(expected_message, result.stderr)
        self.assertFalse(output.exists(), "a failed run must not leave an envelope")
        self.assertNotIn(SYNTHETIC_PASSWORD, combined)
        self.assertNotIn("Exception", combined)
        self.assertNotIn("\tat ", combined)

    def test_self_test_performs_real_roundtrip(self):
        result = _run(
            [self._java, self._source, "--self-test"],
            env=_child_env(self._java_home),
        )
        combined = result.stdout + result.stderr
        self.assertEqual(result.returncode, 0, combined)
        self.assertIn("self-test: ok", result.stdout)
        self.assertNotIn("PRIVATE KEY", combined)
        # The self-test payload uses this marker; the helper must never echo it.
        self.assertNotIn("self-test-password-never-print", combined)

    def test_missing_environment_is_rejected(self):
        output = self._output("missing.json")
        result = self._run_cli(
            output,
            ANDROID_RELEASE_KEYSTORE_B64=None,
            ANDROID_RELEASE_PASSWORD=None,
            SIGNING_RECOVERY_PUBLIC_KEY=None,
        )
        self._assert_clean_failure(
            result, output, "missing environment variable ANDROID_RELEASE_KEYSTORE_B64"
        )

    def test_missing_recipient_is_rejected(self):
        output = self._output("missing-recipient.json")
        result = self._run_cli(
            output,
            ANDROID_RELEASE_KEYSTORE_B64=SYNTHETIC_KEYSTORE,
            ANDROID_RELEASE_PASSWORD=SYNTHETIC_PASSWORD,
            SIGNING_RECOVERY_PUBLIC_KEY=None,
        )
        self._assert_clean_failure(
            result, output, "missing environment variable SIGNING_RECOVERY_PUBLIC_KEY"
        )

    def test_invalid_recipient_is_rejected(self):
        output = self._output("invalid-recipient.json")
        result = self._run_cli(
            output,
            ANDROID_RELEASE_KEYSTORE_B64=SYNTHETIC_KEYSTORE,
            ANDROID_RELEASE_PASSWORD=SYNTHETIC_PASSWORD,
            SIGNING_RECOVERY_PUBLIC_KEY=(
                "-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----\n"
            ),
        )
        self._assert_clean_failure(result, output, "invalid recipient public key")

    def test_invalid_keystore_is_rejected(self):
        output = self._output("invalid-keystore.json")
        result = self._run_cli(
            output,
            ANDROID_RELEASE_KEYSTORE_B64="not base64 !!",
            ANDROID_RELEASE_PASSWORD=SYNTHETIC_PASSWORD,
            SIGNING_RECOVERY_PUBLIC_KEY="not a pem",
        )
        self._assert_clean_failure(
            result, output, "environment keystore is not valid Base64"
        )

    def test_existing_output_is_not_overwritten(self):
        output = self._output("existing.json")
        original = b"existing contents must survive\n"
        output.write_bytes(original)
        try:
            result = self._run_cli(
                output,
                ANDROID_RELEASE_KEYSTORE_B64=None,
                ANDROID_RELEASE_PASSWORD=None,
                SIGNING_RECOVERY_PUBLIC_KEY=None,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("output file already exists", result.stderr)
            self.assertEqual(output.read_bytes(), original)
        finally:
            output.unlink(missing_ok=True)

    def test_missing_output_argument_prints_usage(self):
        result = _run(
            [self._java, self._source],
            env=_child_env(self._java_home),
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("usage:", result.stderr + result.stdout)


if __name__ == "__main__":
    unittest.main()
