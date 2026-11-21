package com.fahad.updater;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarFile;

/** A small launcher agent. The packwiz engine runs in an isolated subprocess. */
public final class Updater {
    public static final String NAME = "fahad-auto-update.jar";
    public static final String SOURCE = "https://raw.githubusercontent.com/FahadAljabr/MC-ATM10-additonalMods/main/pack.toml";
    private static final String ENGINE_HASH = "c9f646908d340d84773948a9a7d98bc1dae250d35e1016dc6e2b8459760b5598";
    private static FileChannel activeChannel;
    private static FileLock activeLock;

    private Updater() {}

    public static Path self() throws Exception {
        return Paths.get(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
    }

    public static void premain(String side, Instrumentation unused) {
        // Prism runs a separate Java capability probe before launching the game.
        // Never download mods during that probe; the real game process runs again.
        String command = System.getProperty("sun.java.command", "");
        if (command.startsWith("org.multimc.EntryPoint") || command.startsWith("org.prismlauncher.EntryPoint")) {
            // The launcher entry points are real game launches and must be updated.
        } else if (command.contains("JavaCheck") || command.contains("JavaInfo")) {
            return;
        }
        try {
            update(side == null || side.isBlank() ? "client" : side, self().getParent(), true);
        } catch (Exception e) {
            System.err.println("[Fahad Updater] UPDATE FAILED. Minecraft was not started: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0 || args[0].equals("--install-client")) {
                runSetup(args);
            } else if (args[0].equals("--install-server")) {
                if (args.length != 2) throw new IOException("Usage: --install-server SERVER_FOLDER");
                installServer(Paths.get(args[1]));
            } else if (args[0].equals("--update")) {
                if (args.length < 2 || args.length > 3) throw new IOException("Usage: --update client|server [GAME_FOLDER]");
                update(args[1], args.length == 3 ? Paths.get(args[2]) : self().getParent(), false);
            } else {
                System.out.println("Fahad's GitHub Add-on Updater 1.0.0\nRepository: " + SOURCE
                    + "\nDouble-click this JAR for one-time Prism/ATLauncher setup."
                    + "\njava -jar " + NAME + " --install-client INSTANCE_FOLDER"
                    + "\njava -jar " + NAME + " --install-server SERVER_FOLDER"
                    + "\njava -jar " + NAME + " --update client|server [GAME_FOLDER]"
                    + "\nManual JVM argument: -javaagent:" + NAME + "=client (or =server)"
                    + "\nThe JAR belongs beside mods, not inside mods. Base FTB pack required.");
            }
        } catch (Exception e) {
            System.err.println("[Fahad Updater] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSetup(String[] args) throws Exception {
        // java -jar is the interactive installer; no Gson/Kotlin classes enter the game JVM.
        Path temp = Files.createTempDirectory("fahad-updater-setup-");
        try {
            Path engine = extractEngine(temp);
            List<String> cmd = new ArrayList<>(List.of(javaBinary(), "-cp", self() + File.pathSeparator + engine,
                "com.fahad.updater.LauncherSetup"));
            cmd.addAll(Arrays.asList(args));
            Process p = new ProcessBuilder(cmd).inheritIO().start();
            int result = p.waitFor();
            if (result != 0) throw new IOException("Client setup failed (exit " + result + ").");
        } finally { deleteTree(temp); }
    }

    static String javaBinary() {
        return Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    static Path extractEngine(Path folder) throws Exception {
        Files.createDirectories(folder);
        Path out = folder.resolve("packwiz-installer-0.5.14.jar");
        if (!Files.exists(out) || !hash(out, "SHA-256").equals(ENGINE_HASH)) {
            try (InputStream in = Updater.class.getResourceAsStream("/vendor/packwiz-installer.jar")) {
                if (in == null) throw new IOException("Bundled packwiz engine missing.");
                atomicCopy(in, out);
            }
        }
        if (!hash(out, "SHA-256").equals(ENGINE_HASH)) throw new IOException("Bundled engine integrity check failed.");
        return out;
    }

    public static void installServer(Path folder) throws Exception {
        Path root = folder.toRealPath();
        Path config = root.resolve("user_jvm_args.txt");
        if (!Files.isDirectory(root.resolve("mods")) || !Files.isRegularFile(config)) {
            throw new IOException("Choose an installed NeoForge server folder containing mods and user_jvm_args.txt. "
                + "If your host uses a custom startup command, upload the JAR beside mods and add -javaagent:"
                + NAME + "=server to that command's JVM options.");
        }
        String old = Files.readString(config);
        if (old.contains(NAME)) {
            copySelf(root);
            System.out.println("Server updater already configured; refreshed its JAR.");
            return;
        }
        Path backup = backupConfig(root, config);
        copySelf(root);
        atomicWrite(config, old.stripTrailing() + "\n\n# Fahad GitHub add-on updater\n-javaagent:" + NAME + "=server\n");
        System.out.println("Server installed. Start with its usual run.bat/run.sh. Backup: " + backup);
    }

    static void copySelf(Path root) throws Exception {
        Path source = self();
        Path target = root.resolve(NAME);
        if (Files.exists(target) && Files.isSameFile(source, target)) return;
        if (Files.isSymbolicLink(target)) throw new IOException("Updater destination must not be a symlink.");
        try (InputStream in = Files.newInputStream(source)) { atomicCopy(in, target); }
    }

    static Path backupConfig(Path root, Path config) throws Exception {
        Path dir = root.resolve(".fahad-updater/setup-backups");
        Files.createDirectories(dir);
        Path backup = dir.resolve(config.getFileName() + "." + UUID.randomUUID() + ".bak");
        Files.copy(config, backup);
        return backup;
    }

    public static void update(String side, Path requestedRoot, boolean holdUntilGameExit) throws Exception {
        if (!side.equals("client") && !side.equals("server")) throw new IOException("Side must be client or server.");
        Path root = requestedRoot.toRealPath();
        if (root.getFileName() != null && root.getFileName().toString().equalsIgnoreCase("mods"))
            throw new IOException("Move " + NAME + " OUT of mods, into the folder containing mods.");
        Path liveMods = root.resolve("mods");
        if (!Files.isDirectory(liveMods) || Files.isSymbolicLink(liveMods))
            throw new IOException("No regular mods folder at " + liveMods + ". Install FTB Evolution 2 1.9.0 first.");
        Path work = root.resolve(".fahad-updater");
        if (Files.isSymbolicLink(work)) throw new IOException("Updater working folder must not be a symlink.");
        Files.createDirectories(work);
        FileChannel channel = FileChannel.open(work.resolve("update.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.tryLock();
        if (lock == null) {
            channel.close();
            throw new IOException("This instance is already running or updating. Close it before trying again.");
        }
        boolean keep = false;
        try {
            recover(root, work);
            Path engine = extractEngine(work.resolve("engine"));
            Path staging = work.resolve("staged-" + side);
            Files.createDirectories(staging);
            System.out.println("[Fahad Updater] Checking GitHub for " + side + " additions...");
            // The system property is for controlled integration tests or an explicit alternative source.
            String source = System.getProperty("fahad.updater.source", SOURCE);
            URI uri = URI.create(source);
            if (!"https".equals(uri.getScheme()) && !"file".equals(uri.getScheme())
                && !("http".equals(uri.getScheme()) && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())))
                throw new IOException("Manifest source must use HTTPS (or a local test source).");
            List<String> command = List.of(javaBinary(), "-Djava.awt.headless=true", "-cp",
                self() + File.pathSeparator + engine, "com.fahad.updater.EngineRunner",
                "-g", "-s", side, "--pack-folder", staging.toString(),
                "--multimc-folder", work.resolve("no-launcher-changes").toString(),
                "--meta-file", "packwiz-state.json", source);
            ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
            // Never propagate environment-injected agents into the updater child JVM.
            for (String env : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) builder.environment().remove(env);
            Process child = builder.start();
            Thread cleanup = new Thread(child::destroyForcibly, "fahad-updater-child-cleanup");
            Runtime.getRuntime().addShutdownHook(cleanup);
            try (BufferedReader output = child.inputReader();
                 BufferedWriter log = Files.newBufferedWriter(work.resolve("latest-update.log"))) {
                String line;
                while ((line = output.readLine()) != null) {
                    System.out.println("[Fahad Updater] " + line);
                    log.write(line); log.newLine(); log.flush();
                }
                int result = child.waitFor();
                if (result != 0) throw new IOException("Download/validation failed (exit " + result
                    + "). Installed mods were not changed. See " + work.resolve("latest-update.log"));
            } finally {
                child.destroy();
                Runtime.getRuntime().removeShutdownHook(cleanup);
            }
            apply(root, work, staging);
            System.out.println("[Fahad Updater] Up to date. Starting Minecraft.");
            if (holdUntilGameExit) {
                activeChannel = channel; activeLock = lock; keep = true;
                // Keep the lock for the lifetime of the game, including while mods are in use.
            }
        } finally {
            if (!keep) { lock.release(); channel.close(); }
        }
    }

    private static void apply(Path root, Path work, Path stage) throws Exception {
        Properties installed = loadProperties(work.resolve("managed.properties"));
        Properties wanted = new Properties();
        Path stagedMods = stage.resolve("mods");
        if (Files.isDirectory(stagedMods)) {
            try (var files = Files.walk(stagedMods)) {
                for (Path p : files.filter(Files::isRegularFile).toList()) {
                    if (!p.getFileName().toString().endsWith(".jar")) continue;
                    if (!p.getParent().equals(stagedMods) || Files.isSymbolicLink(p))
                        throw new IOException("Only flat, regular mods/*.jar files are supported: " + p);
                    String name = validName(p.getFileName().toString());
                    try (JarFile jar = new JarFile(p.toFile(), true)) {
                        if (!jar.entries().hasMoreElements()) throw new IOException("Empty mod JAR: " + name);
                    }
                    wanted.setProperty(name, hash(p, "SHA-512"));
                }
            }
        }
        Set<String> names = new TreeSet<>();
        names.addAll(installed.stringPropertyNames()); names.addAll(wanted.stringPropertyNames());
        List<String> changes = new ArrayList<>();
        for (String name : names) {
            Path target = root.resolve("mods").resolve(validName(name));
            if (Files.isSymbolicLink(target)) throw new IOException("Refusing a symlink mod file: " + name);
            String current = Files.exists(target) ? hash(target, "SHA-512") : null;
            String next = wanted.getProperty(name);
            if (next != null && current != null && !installed.containsKey(name) && !next.equals(current))
                throw new IOException("Unmanaged mod already uses filename " + name
                    + ". Resolve the filename conflict before updating; the existing mod was preserved.");
            if (!Objects.equals(current, next)) changes.add(name);
        }
        if (changes.isEmpty()) {
            saveProperties(work.resolve("managed.properties"), wanted);
            return;
        }
        Path tx = work.resolve("transaction");
        if (Files.exists(tx)) throw new IOException("An unfinished update transaction could not be recovered.");
        Files.createDirectories(tx.resolve("old-mods"));
        Properties journal = new Properties();
        for (String name : changes) {
            Path live = root.resolve("mods").resolve(name);
            boolean exists = Files.exists(live);
            journal.setProperty(name, Boolean.toString(exists));
            if (exists) Files.copy(live, tx.resolve("old-mods").resolve(name));
        }
        if (Files.exists(work.resolve("managed.properties")))
            Files.copy(work.resolve("managed.properties"), tx.resolve("old-managed.properties"));
        saveProperties(tx.resolve("pending.properties"), journal); // Durable before changing live mods.
        try {
            for (String name : changes) {
                Path live = root.resolve("mods").resolve(name);
                if (wanted.containsKey(name)) {
                    try (InputStream in = Files.newInputStream(stagedMods.resolve(name))) { atomicCopy(in, live); }
                    if (!hash(live, "SHA-512").equals(wanted.getProperty(name))) throw new IOException("Copy verification failed: " + name);
                } else { Files.deleteIfExists(live); }
            }
            saveProperties(work.resolve("managed.properties"), wanted);
            atomicWrite(tx.resolve("committed"), "ok\n");
        } catch (Exception e) {
            recover(root, work);
            throw e;
        }
        finishTransaction(work, tx);
        System.out.println("[Fahad Updater] Applied " + changes.size() + " mod file changes; backups saved in " + work.resolve("backups"));
    }

    static void recover(Path root, Path work) throws Exception {
        Path tx = work.resolve("transaction");
        if (!Files.exists(tx)) return;
        if (Files.exists(tx.resolve("committed"))) { finishTransaction(work, tx); return; }
        Path pending = tx.resolve("pending.properties");
        if (Files.exists(pending)) {
            Properties journal = loadProperties(pending);
            for (String name : journal.stringPropertyNames()) {
                Path live = root.resolve("mods").resolve(validName(name));
                if (Files.isSymbolicLink(live)) throw new IOException("Cannot recover symlink: " + name);
                if (Boolean.parseBoolean(journal.getProperty(name))) {
                    try (InputStream in = Files.newInputStream(tx.resolve("old-mods").resolve(name))) { atomicCopy(in, live); }
                } else { Files.deleteIfExists(live); }
            }
            Path old = tx.resolve("old-managed.properties");
            if (Files.exists(old)) {
                try (InputStream in = Files.newInputStream(old)) { atomicCopy(in, work.resolve("managed.properties")); }
            } else Files.deleteIfExists(work.resolve("managed.properties"));
            System.out.println("[Fahad Updater] Recovered the previous mod set after an interrupted update.");
        }
        // A directory without pending.properties means backup preparation never changed live files.
        finishTransaction(work, tx);
    }

    private static void finishTransaction(Path work, Path tx) throws Exception {
        Path backups = work.resolve("backups");
        Files.createDirectories(backups);
        Files.move(tx, backups.resolve(Instant.now().toEpochMilli() + "-" + UUID.randomUUID()));
    }

    private static String validName(String name) throws IOException {
        if (!name.endsWith(".jar") || name.contains("/") || name.contains("\\") || name.contains(":") || name.startsWith("."))
            throw new IOException("Invalid mod filename: " + name);
        return name;
    }

    static Properties loadProperties(Path path) throws IOException {
        Properties p = new Properties();
        if (Files.exists(path)) try (Reader r = Files.newBufferedReader(path)) { p.load(r); }
        return p;
    }

    static void saveProperties(Path path, Properties value) throws IOException {
        StringWriter out = new StringWriter(); value.store(out, "Fahad GitHub add-on updater");
        atomicWrite(path, out.toString());
    }

    static String hash(Path path, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void atomicWrite(Path path, String text) throws IOException {
        atomicCopy(new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)), path);
    }

    static void atomicCopy(InputStream in, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".fahad-", ".tmp");
        try {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel c = FileChannel.open(tmp, StandardOpenOption.WRITE)) { c.force(true); }
            try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }

    static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
