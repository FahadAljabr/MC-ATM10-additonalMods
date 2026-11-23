# FTB Evolution 2 - Automatic GitHub Add-ons

FAHAD'S AUTOMATIC MOD UPDATER - ONE JAR

Repository: https://github.com/FahadAljabr/MC-ATM10-additonalMods
Source branch: main. GitHub Pages is not required by this JAR.

CLIENTS: USE YOUR EXISTING PRE-LAUNCH SETUP (RECOMMENDED FOR PRISM)
1. Install FTB Evolution 2 version 1.9.0 first (Minecraft 26.1.2,
   NeoForge 26.1.2.126, Java 25). Do this on every client and the server.
2. Put fahad-auto-update.jar in the instance's Minecraft folder BESIDE mods,
   not inside mods. In Prism this is usually instance/.minecraft/.
3. Replace the old packwiz-installer-bootstrap pre-launch command with:

   "$INST_JAVA" -jar "$INST_MC_DIR/fahad-auto-update.jar" --update client "$INST_MC_DIR"

4. Press Play normally. The updater downloads and verifies GitHub's additions
   before the launcher starts Minecraft. Do not keep the old updater command.
   Never run an update while that Minecraft instance is already running.

PRISM OR ATLAUNCHER: AUTOMATIC ONE-TIME SETUP ALTERNATIVE
If you do not want to configure a pre-launch command, close Minecraft and its
launcher, then double-click the JAR. Select Prism/ATLauncher client and choose
the existing FTB instance folder. For Prism choose the folder with instance.cfg
(or its .minecraft folder); for ATLauncher choose the folder with instance.json.
The installer copies itself beside mods and adds a Java agent argument to
THAT INSTANCE. Reopen the launcher and press Play. Keep your other JVM options.
If you use this method, clear the previous bootstrap pre-launch command manually
first. Choose EITHER the new pre-launch command OR the automatic installer.
The agent method also prevents updating while that instance is running.

If double-click does not open Java, run with Java 17 or newer:
  java -jar fahad-auto-update.jar
Or configure just this instance's Java/JVM arguments manually:
  -javaagent:fahad-auto-update.jar=client
The JAR must remain beside mods. Do not set this globally for all instances.
FTB Evolution 2 itself must still launch with Java 25.

SERVER: ONE-TIME SETUP
Stop the installed FTB Evolution 2 1.9.0 server. Put the SAME JAR beside its
mods folder. In that server folder, run this once:
  java -jar fahad-auto-update.jar --install-server .
This backs up and adds the following line to user_jvm_args.txt:
  -javaagent:fahad-auto-update.jar=server
Start the server with its normal NeoForge run.bat/run.sh. Every server start
checks GitHub before NeoForge discovers mods. Restart the server to receive
new changes; it does not replace mods while players are online.

If your host's startup command ignores user_jvm_args.txt, add the same
-javaagent argument to the HOST'S JVM/startup options instead. It must appear
before the main class or -jar argument, with the JAR beside mods and the
working directory set to the server folder. This is not a server.jar replacement.
Another supported server startup sequence is:
  java -jar fahad-auto-update.jar --update server .
  [only if that succeeds] run your usual server start command

OWNER: ADD, UPDATE, OR REMOVE MODS ON GITHUB
Push the prepared C:\Minecraft repository changes to main once to publish
the updater source and the automatic index-refresh workflow. No push was
performed for you. The finished JAR already works with the current live repo.

Option A - packwiz metadata (recommended):
  packwiz.exe modrinth add MOD_SLUG
  packwiz.exe update MOD_SLUG
  packwiz.exe remove MOD_SLUG
Then refresh the index and commit/push the changed mods, index.toml, pack.toml:
  python updater/refresh_index.py .
The existing packwiz.exe refresh command also works.

Option B - edit through GitHub:
Upload/change/delete a compatible mod's .pw.toml file under mods/.
Or upload a compatible JAR directly under mods/ (less convenient for updates).
After the workflow is published, GitHub Actions refreshes the index hashes
automatically on a mods/ push. Wait for "Refresh mod update index" to finish
successfully before asking people to launch. If main is protected or Actions
cannot write to it, use the local refresh-and-push method instead.

Every .pw.toml supports side="both", side="client", or side="server".
Raw JAR files apply to both sides, so use .pw.toml for side-specific mods.
Use NeoForge mods compatible with Minecraft 26.1.2, and include all required
dependencies. Availability and download restrictions can prevent automation
for some CurseForge files; publicly downloadable Modrinth files work directly.
Do not use this add-on repo to replace existing FTB base-pack mods under new
filenames: the older base-pack files are intentionally not removed.

HOW UPDATES WORK
The JAR stages the repo's mod downloads in .fahad-updater, verifies packwiz
checksums, then applies only the add-on JARs. It replaces/removes JARs it has
previously managed. Base mods and unrelated JARs remain in place. A conflicting
unmanaged filename with different contents causes a clear error instead of
silently overwriting it. An exact existing copy can be adopted as a managed mod.
Worlds, configs, resource packs, and Minecraft/NeoForge versions are not synced.
Only the repo's mods/*.jar output is installed by this add-on updater.

Failed downloads stop launch and leave the live mod set unchanged. Update
transactions keep backups and recover interrupted changes on the next run.
Internet is required on launch; there is no silent offline bypass. GitHub's
cache/workflow completion may delay a freshly pushed update.

Diagnostics: .fahad-updater/latest-update.log
Mod rollback backups: .fahad-updater/backups/
Launcher/server configuration backups: .fahad-updater/setup-backups/
Do not delete .fahad-updater/managed.properties: it tracks owned JARs for removal.

CURRENT ADDITIONS
Simple Voice Chat 2.6.22 (both sides).
Distant Horizons was removed by request. The existing updater removes its
managed Distant Horizons JAR on the next launch/restart. Chunky stays installed.
Voice chat still requires the server's UDP port 24454, or the allocated port.
MineColonies is unavailable for Minecraft 26.1.2 and remains excluded.

VALIDATION
Tested against the real GitHub repo on client and server sides. Integration
tests cover side filters, updates/removals, unchanged-mod download caching,
base/user mod preservation, bad checksums, launch aborts, agent-before-main
ordering, process locking, interrupted-update recovery, and Prism/ATLauncher/
server setup. Tests ran with Java 21; the JAR targets Java 17+. A full FTB/Java25
game launch and host-specific server panel setup were not tested.
