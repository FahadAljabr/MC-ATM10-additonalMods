"""Rebuild packwiz hashes after editing mods, including direct mods/*.jar files."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import tomllib
import zipfile


def refresh(root):
    root = Path(root).resolve()
    pack_path = root / 'pack.toml'
    text = pack_path.read_text(encoding='utf-8-sig')
    pack = tomllib.loads(text)
    index_path = root / pack['index'].get('file', 'index.toml')
    prior = tomllib.loads(index_path.read_text(encoding='utf-8-sig')) if index_path.exists() else {'files': []}
    paths = set()
    # Retain explicitly indexed non-mod files, excluding tooling and the updater itself.
    for old in prior.get('files', []):
        name = PurePosixPath(old['file'])
        if name.is_absolute() or '..' in name.parts or '\\' in old['file']:
            raise ValueError('Unsafe indexed path: ' + old['file'])
        if name.parts[0] not in {'mods', 'updater', 'tools', '.github', 'backups', 'downloads'} and (root / name).is_file():
            paths.add(name.as_posix())
    destinations = set()
    for p in sorted((root / 'mods').rglob('*')):
        if not p.is_file():
            continue
        if p.is_symlink():
            raise ValueError('Mod files must not be symlinks: ' + str(p))
        relative = p.relative_to(root).as_posix()
        if p.name.endswith('.pw.toml'):
            mod = tomllib.loads(p.read_text(encoding='utf-8-sig'))
            filename = mod['filename']
            if not filename.endswith('.jar') or any(c in filename for c in '/\\:') or filename.startswith('.'):
                raise ValueError('Invalid flat mod filename in ' + relative)
            if mod.get('side', 'both') not in {'both', 'client', 'server'}:
                raise ValueError('Invalid side in ' + relative)
            download = mod['download']
            if download.get('url') and not download['url'].startswith('https://'):
                raise ValueError('Use an HTTPS download URL in ' + relative)
            if filename.lower() in destinations:
                raise ValueError('Duplicate destination mod filename: ' + filename)
            destinations.add(filename.lower())
            paths.add(relative)
        elif p.suffix == '.jar':
            if p.parent != root / 'mods':
                raise ValueError('Direct JARs must be directly inside mods: ' + relative)
            if not zipfile.is_zipfile(p):
                raise ValueError('Invalid JAR: ' + relative)
            if p.name.lower() in destinations:
                raise ValueError('Duplicate destination mod filename: ' + p.name)
            destinations.add(p.name.lower())
            paths.add(relative)
    index = 'hash-format = "sha256"\n'
    for name in sorted(paths):
        index += '\n[[files]]\nfile = ' + json.dumps(name) + '\nhash = "' + hashlib.sha256((root / name).read_bytes()).hexdigest() + '"\n'
        if name.endswith('.pw.toml'):
            index += 'metafile = true\n'
    index_path.write_text(index, encoding='utf-8', newline='\n')
    digest = hashlib.sha256(index_path.read_bytes()).hexdigest()
    match = re.search(r'(?ms)^\[index\]\s*\n(.*?)(?=^\[|\Z)', text)
    if not match:
        raise ValueError('pack.toml has no [index] section')
    section = match.group(0)
    section, count = re.subn(r'(?m)^hash\s*=.*$', 'hash = "' + digest + '"', section)
    if count != 1:
        raise ValueError('Expected exactly one index hash in pack.toml')
    section, count = re.subn(r'(?m)^hash-format\s*=.*$', 'hash-format = "sha256"', section)
    if count != 1:
        raise ValueError('Expected an index hash-format in pack.toml')
    updated = text[:match.start()] + section + text[match.end():]
    pack_path.write_text(updated, encoding='utf-8', newline='\n')
    print(f'Refreshed {len(paths)} indexed files. Commit pack.toml, index.toml and mod changes together.')


if __name__ == '__main__':
    refresh(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
