#!/usr/bin/env python3
"""
Nova Studio jar signer. Signs a NovaGuard jar with the studio's private key.

The private key lives at ~/.novaguard/novaguard-private.pem and is NEVER
committed or shipped. Anyone rebuilding without it gets an unsigned jar
that the LicenseGuard will refuse to run.

Manifest format (must match LicenseGuard.buildManifest exactly):
  for every jar entry except META-INF/NOVAGUARD.SF, sorted by name:
    "<name>:<hex(sha256(name_utf8 + entry_bytes)>\\n"
The manifest bytes are signed with SHA256+RSA; the base64 signature is
stored as META-INF/NOVAGUARD.SF inside the jar.

Usage: sign.py <input.jar> <output.jar>
"""
import hashlib
import subprocess
import sys
import zipfile
import os

SIG_ENTRY = "META-INF/NOVAGUARD.SF"
PRIVATE_KEY = os.path.expanduser("~/.novaguard/novaguard-private.pem")


def build_manifest(zf):
    names = sorted(n for n in zf.namelist() if n != SIG_ENTRY)
    out = []
    for name in names:
        data = zf.read(name)
        h = hashlib.sha256(name.encode("utf-8") + data).hexdigest()
        out.append(f"{name}:{h}\n")
    return "".join(out).encode("utf-8")


def main():
    if len(sys.argv) != 3:
        print("Usage: sign.py <input.jar> <output.jar>")
        sys.exit(1)
    src, dst = sys.argv[1], sys.argv[2]
    if not os.path.exists(PRIVATE_KEY):
        print(f"Private key not found: {PRIVATE_KEY}")
        sys.exit(1)

    with zipfile.ZipFile(src, "r") as zf:
        manifest = build_manifest(zf)
        entries = [(n, zf.read(n)) for n in zf.namelist() if n != SIG_ENTRY]

    # sign manifest bytes with openssl (SHA256 + RSA)
    p = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", PRIVATE_KEY],
        input=manifest, capture_output=True)
    if p.returncode != 0:
        print("Signing failed:", p.stderr.decode())
        sys.exit(1)
    import base64
    sig_b64 = base64.b64encode(p.stdout).decode()

    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as out:
        for name, data in entries:
            out.writestr(name, data)
        out.writestr(SIG_ENTRY, sig_b64 + "\n")
    print(f"Signed: {dst}")


if __name__ == "__main__":
    main()
