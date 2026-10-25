"""Publish a signed release through a short-lived authenticated publishing deployment."""
from pathlib import Path
import argparse
import hashlib
import json
import urllib.request
import urllib.error
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--base-url", default="https://vexo-music-updates.kavindukavishka785.workers.dev")
parser.add_argument("--token-file", required=True)
parser.add_argument("--apk", required=True)
parser.add_argument("--source", required=True)
parser.add_argument("--version", required=True)
parser.add_argument("--code", required=True, type=int)
parser.add_argument("--summary-file", required=True)
args = parser.parse_args()
token = Path(args.token_file).read_text().strip()
base = args.base_url.rstrip("/")
agent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"

def upload(name, body, size, checksum=None):
    headers = {"Authorization": "Bearer " + token, "Content-Length": str(size), "Content-Type": "application/octet-stream", "User-Agent": agent}
    if checksum:
        headers["X-Upload-Sha256"] = checksum
    request = urllib.request.Request(base + "/__publish/" + name, data=body, headers=headers, method="PUT")
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            result = json.loads(response.read(16384))
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Publish failed ({error.code}): {error.read(500).decode(errors='replace')}") from None
    print(json.dumps({"file": name, "result": result}), flush=True)

apk, source = Path(args.apk), Path(args.source)
for file in (apk, source):
    assert file.is_file()
    with zipfile.ZipFile(file) as archive:
        assert archive.testzip() is None
    digest = hashlib.sha256(file.read_bytes()).hexdigest()
    with file.open("rb") as body:
        upload(file.name, body, file.stat().st_size, digest)

manifest = {
    "applicationId": "space.vexo.music.radio",
    "versionCode": args.code,
    "versionName": args.version,
    "summary": Path(args.summary_file).read_text().strip(),
    "downloadUrl": base + "/releases/" + apk.name,
    "sourceUrl": base + "/releases/" + source.name,
    "size": apk.stat().st_size,
    "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "certificateSha256": "0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3",
}
body = json.dumps(manifest).encode()
upload("stable.json", body, len(body))
