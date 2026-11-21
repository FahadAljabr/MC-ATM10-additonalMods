FTB EVOLUTION 2 1.9.0 - FAHAD'S ADDITIONS
Prepared: 22 November 2026

These are ADD-ON files. First install the official FTB Evolution 2 version
1.9.0 on every computer and the server. These downloads do not contain the
431-mod base pack and cannot launch Minecraft on their own.

Exact base: Minecraft 26.1.2, NeoForge 26.1.2.126, Java 25.
Install the same base pack version on the server and every client.

Included (official NeoForge-compatible releases):
- Distant Horizons 3.3.3 for Minecraft 26.1.2
- Simple Voice Chat 2.6.22 for Minecraft 26.1.2
  This is the latest version tagged Release in the checked Modrinth metadata;
  newer 2.6.23-2.6.27 versions are tagged Beta.
No additional required dependency mods are declared for these two files.
The base pack already supplies the optional Cloth Config dependency.

MINECOLONIES IS NOT INCLUDED: its official files currently support Minecraft
1.21.1 and older, not Minecraft 26.1.2. Installing that JAR here would be
incompatible. Adding MineColonies requires choosing a different base pack
on a supported Minecraft version for everyone, or waiting for an official port.

Carry On is already in FTB Evolution 2 1.9.0. Do not install the old 1.21.1
Carry On or Simple Voice Chat JARs from the previous ATM10 add-on setup.

The two mods must stay as separate JARs; do not combine or rename them into
one server.jar. The server starts with the official FTB/NeoForge launch script.

Validation: downloaded from the official Modrinth CDN; sizes and SHA512
checksums match the official metadata; JAR integrity and NeoForge mod metadata
checked against the FTB 1.9.0 manifest. No full Minecraft launch or multiplayer
test was possible: C:\Minecraft contains only packwiz add-on metadata, not
an installed FTB client/server. Test in a copy before applying to a live world.

Official sources:
FTB version: https://www.feed-the-beast.com/modpacks/135-ftb-evolution-2/versions
FTB exact manifest: https://api.feed-the-beast.com/v1/modpacks/public/modpack/135/100614
MineColonies files: https://www.curseforge.com/minecraft/mc-mods/minecolonies/files/all
Distant Horizons: https://modrinth.com/mod/distanthorizons/version/QHxoccsN
Simple Voice Chat: https://modrinth.com/plugin/simple-voice-chat/version/rVZ2FMgU
Voice chat setup: https://modrepo.de/minecraft/voicechat/wiki/server_setup

CLIENT INSTALLATION (YOUR PC AND FRIENDS)
1. Close Minecraft. Install FTB Evolution 2 1.9.0 in your launcher.
2. Open that instance's folder from the launcher. Back it up.
3. Copy both JARs from this ZIP's mods folder into the instance's mods folder.
   Keep the base pack's existing mods. Remove older copies of these same two
   mods if present, so there is only one version of each.
4. Launch the pack with Java 25 and join the matching server.
5. Open the Simple Voice Chat menu with V (default key) and select your mic.
6. Configure Distant Horizons in its settings. Shader compatibility depends
   on the selected shader; start with shaders off while testing.

Send this client ZIP to your friends. Each friend needs the official base pack.

SERVER INSTALLATION (UPLOAD TO YOUR HOST)
1. Stop the server and back up its files and world.
2. Install the official FTB Evolution 2 server version 1.9.0 through your host
   or FTB's version-specific server installer (pack 135, version 100614).
   Use Java 25 and the pack's NeoForge 26.1.2.126 launch script.
3. Upload the two JARs from this ZIP's mods folder into the server's mods folder.
   Keep existing base pack mods; replace only older copies of these same mods.
   These are NeoForge MODS; do not upload to plugins or replace server.jar.
4. Start the server so the mods create their configuration files.
5. Allocate/open UDP port 24454 in the hosting panel/firewall/router as needed.
   Voice chat uses UDP; opening only a TCP port will not work.
   If your host gives you another UDP port, stop the server and set port=YOURPORT
   in config/voicechat/voicechat-server.properties, then restart.
6. Give your friends the client ZIP. Test with /voicechat test PLAYERNAME
   as an operator after they join. Each player needs the voice chat client mod.

Distant Horizons on the server is optional for basic client-side LOD caching;
it is included here for server-side LOD support. Start with default settings
and monitor CPU/disk load. The base pack includes Chunky: avoid running heavy
terrain pregeneration while assessing performance.

This is an add-on upload ZIP, not a complete standalone server installation.

PACKWIZ SOURCE
This folder describes only the two add-on mods; it is not a full FTB modpack.
The index intentionally lists only the add-on metadata and index.md.
The Minecraft/NeoForge versions match the official FTB 1.9.0 manifest.
Do not point a packwiz installer at this source inside a managed base pack
without first checking that it will preserve the base pack's files.

The previous tracked setup is preserved in backups and in the delivered ZIP.
The original packwiz.exe and Git repository are unchanged. No changes were
committed or pushed. Existing hosted copies are unchanged until you publish.
