"""Local-only SMTP sink and OTP lookup for disposable API smoke tests."""
import json
import re
import socketserver
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

latest = {}
lock = threading.Lock()


class SmtpHandler(socketserver.StreamRequestHandler):
    def handle(self):
        self.wfile.write(b"220 local-test-smtp\r\n")
        recipient = None
        while line := self.rfile.readline():
            command = line.decode("utf-8", "replace").strip()
            upper = command.upper()
            if upper.startswith(("EHLO", "HELO")):
                self.wfile.write(b"250 localhost\r\n")
            elif upper.startswith("MAIL FROM:"):
                self.wfile.write(b"250 OK\r\n")
            elif upper.startswith("RCPT TO:"):
                recipient = command[8:].strip().strip("<>").lower()
                self.wfile.write(b"250 OK\r\n")
            elif upper == "DATA":
                self.wfile.write(b"354 End with dot\r\n")
                parts = []
                while data := self.rfile.readline():
                    if data == b".\r\n":
                        break
                    parts.append(data)
                message = b"".join(parts).decode("utf-8", "replace")
                match = re.search(r"code is (\d{6})", message)
                if recipient and match:
                    with lock:
                        latest[recipient] = match.group(1)
                self.wfile.write(b"250 Accepted\r\n")
            elif upper == "QUIT":
                self.wfile.write(b"221 Bye\r\n")
                break
            else:
                self.wfile.write(b"250 OK\r\n")


class LookupHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlparse(self.path)
        email = parse_qs(url.query).get("email", [""])[0].lower()
        with lock:
            code = latest.get(email) if url.path == "/latest" else None
        payload = json.dumps({"code": code}).encode()
        self.send_response(200 if code else 404)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format, *args):
        pass


class SmtpServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


smtp = SmtpServer(("127.0.0.1", 1025), SmtpHandler)
threading.Thread(target=smtp.serve_forever, daemon=True).start()
http = ThreadingHTTPServer(("127.0.0.1", 1026), LookupHandler)
print("Local SMTP fixture ready on 1025; OTP lookup on 1026", flush=True)
http.serve_forever()
