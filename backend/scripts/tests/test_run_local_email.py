import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "run-local-email.sh"


class LocalEmailEnvironmentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.backend = self.root / "backend"
        scripts = self.backend / "scripts"
        scripts.mkdir(parents=True)
        self.script = scripts / SCRIPT.name
        shutil.copyfile(SCRIPT, self.script)
        self.bin_dir = self.root / "bin"
        self.bin_dir.mkdir()
        java = self.bin_dir / "java"
        java.write_text("#!/bin/sh\nexit 0\n")
        java.chmod(0o700)
        self.maven = self.bin_dir / "mvn"
        self.maven.write_text(
            f"#!{sys.executable}\n"
            "import json, os, sys\n"
            "for key, value in json.loads(os.environ['EXPECTED_SETTINGS']).items():\n"
            "    if os.environ.get(key) != value:\n"
            "        sys.exit('Unexpected setting: ' + key)\n"
            "assert os.environ.get('SPRING_JPA_HIBERNATE_DDL_AUTO') == 'validate'\n"
            "assert os.environ.get('SPRING_FLYWAY_ENABLED') == 'true'\n"
            "assert not any('h2:mem:' in arg or 'create-drop' in arg or 'useTestClasspath' in arg for arg in sys.argv)\n"
            "assert os.environ['DB_PASSWORD'] not in ' '.join(sys.argv)\n"
            "print('Maven received expected settings')\n"
        )
        self.maven.chmod(0o700)

    def run_script(self, contents=None, overrides=None, expected=None):
        if contents is not None:
            (self.backend / ".env").write_bytes(contents.encode())
        environment = {
            "PATH": str(self.bin_dir) + os.pathsep + os.defpath,
            "MVN_BIN": str(self.maven),
            "JWT_SECRET": "local-test-only-secret-with-at-least-32-bytes",
            "OTP_HMAC_SECRET": "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "DB_URL": "jdbc:postgresql://db.fixture.supabase.co:5432/postgres?sslmode=require",
            "DB_USERNAME": "test_user",
            "DB_PASSWORD": "test-db-only-secret",
            "EXPECTED_SETTINGS": json.dumps(expected or {}),
            **(overrides or {}),
        }
        return subprocess.run(
            ["bash", str(self.script)], cwd=self.root, env=environment,
            stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=10,
        )

    def test_reads_file_from_backend_and_preserves_literal_password(self):
        marker = self.root / "must-not-exist"
        password = f"$(touch {marker}) `touch {marker}` $OTHER # literal=characters"
        result = self.run_script(
            "  # Gmail settings\r\n\r\n"
            " MAIL_USERNAME = 'sender@example.test' \r\n"
            f'MAIL_PASSWORD="{password}"\r\n'
            "MAIL_FROM=\r\nMAIL_HOST=smtp.gmail.com\r\nMAIL_PORT=587",
            expected={"MAIL_USERNAME": "sender@example.test", "MAIL_PASSWORD": password,
                      "MAIL_FROM": "sender@example.test", "MAIL_HOST": "smtp.gmail.com",
                      "MAIL_PORT": "587"},
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(marker.exists())
        self.assertNotIn(password, result.stdout + result.stderr)

    def test_existing_environment_takes_precedence(self):
        result = self.run_script(
            "MAIL_USERNAME=file@example.test\nMAIL_PASSWORD=file-placeholder\n"
            "JWT_SECRET=file-placeholder\n",
            overrides={"MAIL_USERNAME": "process@example.test", "MAIL_PASSWORD": "process-placeholder"},
            expected={"MAIL_USERNAME": "process@example.test", "MAIL_PASSWORD": "process-placeholder",
                      "JWT_SECRET": "local-test-only-secret-with-at-least-32-bytes"},
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_exported_settings_work_without_file(self):
        result = self.run_script(
            overrides={"MAIL_USERNAME": "sender@example.test", "MAIL_PASSWORD": "test-placeholder"},
            expected={"MAIL_FROM": "sender@example.test"},
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_invalid_entry_reports_line_number_without_contents(self):
        result = self.run_script("# comment\ninvalid sensitive-placeholder\n")
        self.assertEqual(result.returncode, 2)
        self.assertIn(".env line 2", result.stderr)
        self.assertNotIn("sensitive-placeholder", result.stdout + result.stderr)
        self.assertNotIn("Maven received", result.stdout)

    def test_missing_jwt_secret_fails_instead_of_regenerating_sessions(self):
        result = self.run_script(overrides={"JWT_SECRET": "", "MAIL_USERNAME": "sender@example.test", "MAIL_PASSWORD": "test"})
        self.assertEqual(result.returncode, 2)
        self.assertIn("JWT_SECRET", result.stderr)
        self.assertNotIn("Maven received", result.stdout)

    def test_missing_otp_hmac_secret_fails_instead_of_invalidating_codes(self):
        result = self.run_script(overrides={"OTP_HMAC_SECRET": "", "MAIL_USERNAME": "sender@example.test", "MAIL_PASSWORD": "test"})
        self.assertEqual(result.returncode, 2)
        self.assertIn("OTP_HMAC_SECRET", result.stderr)
        self.assertNotIn("Maven received", result.stdout)

    def test_missing_database_password_fails_without_using_h2(self):
        result = self.run_script(overrides={"DB_PASSWORD": "", "MAIL_USERNAME": "sender@example.test", "MAIL_PASSWORD": "test"})
        self.assertEqual(result.returncode, 2)
        self.assertIn("DB_PASSWORD", result.stderr)
        self.assertNotIn("Maven received", result.stdout)

    def test_refuses_local_database_and_missing_tls(self):
        for url in ['jdbc:postgresql://127.0.0.1:5432/algolearner', 'jdbc:postgresql://db.fixture.supabase.co:5432/postgres']:
            with self.subTest(url=url):
                result = self.run_script(overrides={"DB_URL": url, "MAIL_USERNAME": "sender@example.test", "MAIL_PASSWORD": "test"})
                self.assertEqual(result.returncode, 1)
                self.assertIn("Supabase", result.stderr)
                self.assertNotIn("Maven received", result.stdout)

    def test_empty_password_still_requires_user_input(self):
        result = self.run_script("MAIL_USERNAME=sender@example.test\nMAIL_PASSWORD=\n")
        self.assertEqual(result.returncode, 2)
        self.assertIn("Set MAIL_PASSWORD", result.stderr)
        self.assertNotIn("Maven received", result.stdout)


if __name__ == "__main__":
    unittest.main()
