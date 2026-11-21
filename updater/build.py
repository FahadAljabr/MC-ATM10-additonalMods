"""Rebuild the single updater JAR with Python 3 and JDK 17 or newer."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
ENGINE_URL = 'https://github.com/packwiz/packwiz-installer/releases/download/v0.5.14/packwiz-installer.jar'
ENGINE_SHA256 = 'c9f646908d340d84773948a9a7d98bc1dae250d35e1016dc6e2b8459760b5598'
vendor = ROOT / '.vendor'
vendor.mkdir(exist_ok=True)
engine = vendor / 'packwiz-installer.jar'
if not engine.exists():
    request = urllib.request.Request(ENGINE_URL, headers={'User-Agent': 'Fahad-Addon-Updater-Builder/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response:
        engine.write_bytes(response.read())
assert hashlib.sha256(engine.read_bytes()).hexdigest() == ENGINE_SHA256, 'packwiz engine checksum mismatch'
javac = shutil.which('javac')
if not javac:
    raise SystemExit('Install JDK 17 or newer, and add its bin folder to PATH.')
classes = ROOT / '.build' / 'classes'
classes.mkdir(parents=True, exist_ok=True)
sources = sorted((ROOT / 'src').rglob('*.java'))
subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-cp', str(engine), '-d', str(classes),
                *map(str, sources)], check=True)
destination = ROOT / 'fahad-auto-update.jar'
with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/MANIFEST.MF',
              'Manifest-Version: 1.0\r\nMain-Class: com.fahad.updater.Updater\r\n'
              'Premain-Class: com.fahad.updater.Updater\r\nImplementation-Version: 1.0.0\r\n\r\n')
    for f in sorted(classes.rglob('*.class')):
        z.write(f, f.relative_to(classes).as_posix())
    z.write(engine, 'vendor/packwiz-installer.jar')
    for f in sorted((ROOT / 'licenses').glob('*')):
        z.write(f, 'META-INF/licenses/' + f.name)
    z.writestr('META-INF/UPDATER-SOURCE.txt',
              'Original updater source is in the updater/src folder of\n'
              'https://github.com/FahadAljabr/MC-ATM10-additonalMods\n\n'
              'Includes unmodified packwiz-installer v0.5.14 (MIT).\n'
              'Upstream source: https://github.com/packwiz/packwiz-installer/tree/v0.5.14\n'
              'Engine SHA256: ' + ENGINE_SHA256 + '\n')
print(f'Built {destination} ({destination.stat().st_size:,} bytes)')
print('SHA256:', hashlib.sha256(destination.read_bytes()).hexdigest())
