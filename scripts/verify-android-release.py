"""Check signed public artifacts before replacing a GitHub/Play release."""
import argparse
import hashlib
import json
import pathlib
import re
import struct
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--sdk", type=pathlib.Path, required=True)
parser.add_argument("--apk", type=pathlib.Path, required=True)
parser.add_argument("--aab", type=pathlib.Path, required=True)
parser.add_argument("--build-config", type=pathlib.Path, required=True)
parser.add_argument("--version-code", type=int, required=True)
parser.add_argument("--version-name", required=True)
parser.add_argument("--certificate", required=True)
args = parser.parse_args()


def run(*command):
    return subprocess.run(list(map(str, command)), check=True, capture_output=True, text=True).stdout


certificate = args.certificate.lower().replace(":", "")
signatures = run(args.sdk / "apksigner.bat", "verify", "--print-certs", args.apk)
assert certificate in signatures.lower(), "APK signing key changed"
bundle_cert = run("keytool", "-printcert", "-jarfile", args.aab)
assert certificate in bundle_cert.lower().replace(":", ""), "AAB signing key changed"
run("jarsigner", "-verify", args.aab)
run(args.sdk / "zipalign.exe", "-c", "-P", "16", "4", args.apk)
manifest = run(args.sdk / "aapt.exe", "dump", "badging", args.apk)
assert "name='com.arvio.tv'" in manifest
assert f"versionCode='{args.version_code}'" in manifest
assert f"versionName='{args.version_name}'" in manifest
assert "application-debuggable" not in manifest
assert "targetSdkVersion:'36'" in manifest

config = args.build_config.read_text(encoding="utf-8")
api_id = re.search(r'TELEGRAM_API_ID = "([1-9][0-9]+)"', config)
api_hash = re.search(r'TELEGRAM_API_HASH = "([a-fA-F0-9]{32})"', config)
assert api_id and api_hash, "Telegram release configuration missing"
mdblist_client_id = re.search(r'MDBLIST_CLIENT_ID = "([^\"]+)"', config)
assert mdblist_client_id and not mdblist_client_id[1].startswith("your-"), "MDBList OAuth release configuration missing"
report = {"versionName": args.version_name, "versionCode": args.version_code,
    "signerSha256": certificate, "files": []}
for path in (args.apk, args.aab):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        libs = [name for name in names if name.endswith(".so")]
        assert not any("/x86" in name for name in libs), "Emulator ABI in public release"
        for abi in ("arm64-v8a", "armeabi-v7a"):
            for library in ("libtdjni.so", "libdiscord_partner_sdk.so", "libarvio_native.so"):
                assert any(name.endswith(f"/{abi}/{library}") for name in libs), f"Missing {abi}/{library}"
        dex = [name for name in names if name.endswith(".dex")]
        assert any(api_hash[1].encode() in archive.read(name) for name in dex), "Telegram configuration absent from binary"
        assert any(mdblist_client_id[1].encode() in archive.read(name) for name in dex), "MDBList OAuth configuration absent from binary"
        for name in libs:
            data = archive.read(name)
            assert data[:4] == b"\x7fELF"
            if data[4] != 2:
                continue
            endian = "<" if data[5] == 1 else ">"
            phoff = struct.unpack_from(endian + "Q", data, 32)[0]
            entsize, count = struct.unpack_from(endian + "HH", data, 54)
            for i in range(count):
                offset = phoff + i * entsize
                segment = struct.unpack_from(endian + "I", data, offset)[0]
                alignment = struct.unpack_from(endian + "Q", data, offset + 48)[0]
                assert segment != 1 or alignment >= 16384, f"16 KB alignment failed: {name}"
    report["files"].append({"name": path.name, "bytes": path.stat().st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "telegramConfigured": True, "mdblistOAuthConfigured": True,
        "nativeLibraries": len(libs), "elf64bit16kb": True})
print(json.dumps(report, indent=2))
