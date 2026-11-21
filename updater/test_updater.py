"""Integration tests against a local HTTP repo and actual updater subprocesses."""
import hashlib
import http.server
import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
import zipfile

ROOT = Path(__file__).resolve().parent
JAR = ROOT / 'fahad-auto-update.jar'


def jar_bytes(label):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as z:
        z.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n\n')
        z.writestr('test-fixture.txt', label)
    return out.getvalue()


class Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='fahad-tests-', dir=ROOT / '.build')
        cls.root = Path(cls.temp.name)
        cls.repo = cls.root / 'repo'
        cls.repo.mkdir()
        cls.requests = []

        class Handler(http.server.SimpleHTTPRequestHandler):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, directory=str(cls.repo), **kwargs)

            def log_message(self, *args):
                pass

            def do_GET(self):
                cls.requests.append(self.path)
                super().do_GET()

        cls.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.source = f'http://127.0.0.1:{cls.server.server_port}/pack.toml'
        cls.game_source = cls.root / 'GameProbe.java'
        cls.game_source.write_text('public class GameProbe { public static void main(String[] args) throws Exception { System.out.println("GAME_STARTED"); if(args.length>0) Thread.sleep(10000); } }')
        subprocess.run(['javac', '--release', '17', str(cls.game_source)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.temp.cleanup()

    def publish(self, entries, bad=False):
        files = []
        for slug, filename, side, content in entries:
            artifact = self.repo / 'artifacts' / filename
            artifact.parent.mkdir(exist_ok=True)
            artifact.write_bytes(content)
            pw = self.repo / 'mods' / (slug + '.pw.toml')
            pw.parent.mkdir(exist_ok=True)
            digest = '0' * 128 if bad else hashlib.sha512(content).hexdigest()
            url = self.source.rsplit('/', 1)[0] + '/artifacts/' + filename
            pw.write_text(f'name = "{slug}"\nfilename = "{filename}"\nside = "{side}"\n\n'
                          f'[download]\nurl = "{url}"\nhash-format = "sha512"\nhash = "{digest}"\n', encoding='utf-8')
            files.append(f'\n[[files]]\nfile = "mods/{pw.name}"\nhash = "{hashlib.sha256(pw.read_bytes()).hexdigest()}"\nmetafile = true\n')
        index = self.repo / 'index.toml'
        index.write_text('hash-format = "sha256"\n' + ''.join(files), encoding='utf-8')
        (self.repo / 'pack.toml').write_text('name = "Fixture"\npack-format = "packwiz:1.1.0"\n\n[index]\nfile = "index.toml"\nhash-format = "sha256"\nhash = "'
            + hashlib.sha256(index.read_bytes()).hexdigest() + '"\n\n[versions]\nminecraft = "26.1.2"\nneoforge = "26.1.2.126"\n', encoding='utf-8')

    def game(self, name):
        root = self.root / name
        (root / 'mods').mkdir(parents=True)
        (root / 'mods' / 'base-pack.jar').write_bytes(jar_bytes('BASE PACK - DO NOT CHANGE'))
        return root

    def update(self, root, side='client', success=True):
        cmd = ['java', '-Dfahad.updater.source=' + self.source, '-jar', str(JAR), '--update', side, str(root)]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=90)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def test_sync_transactions_and_agent(self):
        client = self.game('client with spaces')
        server = self.game('server')
        base = (client / 'mods/base-pack.jar').read_bytes()
        common = ('shared', 'shared-v1.jar', 'both', jar_bytes('common-v1'))
        clientmod = ('client', 'client.jar', 'client', jar_bytes('client'))
        servermod = ('server', 'server.jar', 'server', jar_bytes('server'))
        self.publish([common, clientmod, servermod])
        self.update(client)
        self.update(server, 'server')
        self.assertEqual({p.name for p in (client / 'mods').glob('*.jar')}, {'base-pack.jar', 'shared-v1.jar', 'client.jar'})
        self.assertEqual({p.name for p in (server / 'mods').glob('*.jar')}, {'base-pack.jar', 'shared-v1.jar', 'server.jar'})
        before = sum('/artifacts/' in p for p in self.requests)
        self.update(client)
        self.assertEqual(before, sum('/artifacts/' in p for p in self.requests))
        (client / 'mods/user-extra.jar').write_bytes(jar_bytes('user extra'))
        common2 = ('shared', 'shared-v2.jar', 'both', jar_bytes('common-v2'))
        self.publish([common2, servermod])
        self.update(client)
        self.assertFalse((client / 'mods/shared-v1.jar').exists())
        self.assertFalse((client / 'mods/client.jar').exists())
        self.assertTrue((client / 'mods/shared-v2.jar').exists())
        self.assertTrue((client / 'mods/user-extra.jar').exists())
        self.assertEqual(base, (client / 'mods/base-pack.jar').read_bytes())
        snapshot = {p.name: p.read_bytes() for p in (client / 'mods').iterdir()}
        common3 = ('shared', 'shared-v3.jar', 'both', jar_bytes('common-v3'))
        self.publish([common3], bad=True)
        self.update(client, success=False)
        self.assertEqual(snapshot, {p.name: p.read_bytes() for p in (client / 'mods').iterdir()})
        shutil.copy2(JAR, client / 'fahad-auto-update.jar')
        command = ['java', '-Dfahad.updater.source=' + self.source,
                   '-javaagent:' + str(client / 'fahad-auto-update.jar') + '=client', '-cp', str(self.root), 'GameProbe']
        fail = subprocess.run(command, capture_output=True, text=True, timeout=90)
        self.assertNotEqual(fail.returncode, 0)
        self.assertNotIn('GAME_STARTED', fail.stdout)
        self.publish([common3])
        ok = subprocess.run(command, capture_output=True, text=True, timeout=90)
        self.assertEqual(ok.returncode, 0, ok.stdout + ok.stderr)
        self.assertIn('GAME_STARTED', ok.stdout)
        self.assertLess(ok.stdout.index('Up to date.'), ok.stdout.index('GAME_STARTED'))
        self.assertFalse((client / 'mods/shared-v2.jar').exists())
        self.assertTrue((client / 'mods/shared-v3.jar').exists())
        tx = client / '.fahad-updater/transaction'
        (tx / 'old-mods').mkdir(parents=True)
        shutil.copy2(client / 'mods/shared-v3.jar', tx / 'old-mods/shared-v3.jar')
        shutil.copy2(client / '.fahad-updater/managed.properties', tx / 'old-managed.properties')
        (tx / 'pending.properties').write_text('shared-v3.jar=true\nghost.jar=false\n')
        (client / 'mods/shared-v3.jar').write_bytes(jar_bytes('INTERRUPTED'))
        (client / 'mods/ghost.jar').write_bytes(jar_bytes('NEW BUT UNCOMMITTED'))
        recovered = self.update(client)
        self.assertIn('Recovered the previous mod set', recovered.stdout)
        self.assertEqual((client / 'mods/shared-v3.jar').read_bytes(), common3[3])
        self.assertFalse((client / 'mods/ghost.jar').exists())
        running = subprocess.Popen(command + ['hold'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        try:
            for line in running.stdout:
                if 'GAME_STARTED' in line:
                    break
            locked = self.update(client, success=False)
            self.assertIn('already running or updating', locked.stderr)
        finally:
            running.terminate()
            running.wait(timeout=10)
            running.stdout.close()
        self.publish([('collision', 'base-pack.jar', 'both', jar_bytes('BAD REPLACEMENT'))])
        self.update(client, success=False)
        self.assertEqual(base, (client / 'mods/base-pack.jar').read_bytes())
        self.assertTrue((client / 'mods/shared-v3.jar').exists())

    def test_installers_keep_existing_settings(self):
        prism = self.root / 'Prism/instances/Test'
        (prism / '.minecraft/mods').mkdir(parents=True)
        (prism.parent.parent / 'prismlauncher.cfg').write_text('[General]\nJvmArgs=-Dglobal=kept\n')
        original = '[General]\nConfigVersion=1.3\nname=FTB\nOverrideJavaArgs=false\n[Other]\nuntouched=yes\n'
        (prism / 'instance.cfg').write_text(original)
        at = self.game('ATLauncher/instances/Test')
        config = {'id': '26.1.2', 'launcher': {'name': 'FTB', 'javaArguments': '-Dexisting=kept'}, 'unrelated': [1, 2]}
        (at / 'instance.json').write_text(json.dumps(config))
        for path in [prism, at]:
            for _ in range(2):
                p = subprocess.run(['java', '-Djava.awt.headless=true', '-jar', str(JAR), '--install-client', str(path)], capture_output=True, text=True, timeout=30)
                self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        value = (prism / 'instance.cfg').read_text()
        self.assertIn('OverrideJavaArgs=true', value)
        self.assertIn('-Dglobal=kept', value)
        self.assertEqual(value.count('-javaagent:'), 1)
        self.assertIn('[Other]\nuntouched=yes', value)
        self.assertTrue((prism / '.minecraft/fahad-auto-update.jar').exists())
        value = json.loads((at / 'instance.json').read_text())
        self.assertEqual(value['unrelated'], [1, 2])
        self.assertIn('-Dexisting=kept', value['launcher']['javaArguments'])
        self.assertEqual(value['launcher']['javaArguments'].count('-javaagent:'), 1)
        server = self.game('server-install')
        (server / 'user_jvm_args.txt').write_text('-Xmx8G\n')
        for _ in range(2):
            p = subprocess.run(['java', '-jar', str(JAR), '--install-server', str(server)], capture_output=True, text=True, timeout=30)
            self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        self.assertEqual((server / 'user_jvm_args.txt').read_text().count('-javaagent:'), 1)
        self.assertIn('-Xmx8G', (server / 'user_jvm_args.txt').read_text())


if __name__ == '__main__':
    unittest.main(verbosity=2)
