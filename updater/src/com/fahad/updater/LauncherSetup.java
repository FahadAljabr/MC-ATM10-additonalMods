package com.fahad.updater;

import com.google.gson.*;
import java.awt.GraphicsEnvironment;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** One-time setup wizard. Uses Gson bundled with packwiz in a separate JVM. */
public final class LauncherSetup {
    public static void main(String[] args) {
        try {
            if (args.length == 2 && args[0].equals("--install-client")) {
                System.out.println(installClient(Paths.get(args[1])));
                return;
            }
            if (GraphicsEnvironment.isHeadless()) throw new Exception("Run with --install-client INSTANCE_FOLDER or --install-server SERVER_FOLDER.");
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Object[] choices = {"Prism / ATLauncher client", "NeoForge server", "Cancel"};
            int kind = JOptionPane.showOptionDialog(null,
                "Install FTB Evolution 2 1.9.0 first.\nClose Minecraft and the launcher before setup.\n\n"
                + "This enables automatic GitHub add-on updates on every launch.\n"
                + "Repository: FahadAljabr/MC-ATM10-additonalMods\n\nChoose where to install:",
                "Fahad's Automatic Mod Updater", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, choices, choices[0]);
            if (kind < 0 || kind > 1) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(kind == 0 ? "Choose the existing FTB launcher instance folder" : "Choose the installed NeoForge server folder");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
            Path folder = chooser.getSelectedFile().toPath();
            String message;
            if (kind == 0) message = installClient(folder);
            else { Updater.installServer(folder); message = "Server installed. Start with its usual run.bat / run.sh.\nYour server checks GitHub on each start."; }
            JOptionPane.showMessageDialog(null, message, "Updater installed", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            if (!GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null, e.getMessage(), "Setup failed", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    public static String installClient(Path selected) throws Exception {
        Path instance = selected.toRealPath();
        if ((instance.getFileName().toString().equals(".minecraft") || instance.getFileName().toString().equals("minecraft"))
            && Files.exists(instance.getParent().resolve("instance.cfg"))) instance = instance.getParent();
        if (Files.isRegularFile(instance.resolve("instance.cfg"))) return installPrism(instance);
        if (Files.isRegularFile(instance.resolve("instance.json"))) return installAT(instance);
        throw new Exception("Choose the Prism instance folder containing instance.cfg, or the ATLauncher instance folder containing instance.json. "
            + "Use the launcher's Open Folder button to locate it. This installer does not create the FTB base pack.");
    }

    private static String installPrism(Path instance) throws Exception {
        Path root = Files.isDirectory(instance.resolve(".minecraft")) ? instance.resolve(".minecraft") : instance.resolve("minecraft");
        requireMods(root);
        Path config = instance.resolve("instance.cfg");
        String text = Files.readString(config);
        String args = iniValue(text, "JvmArgs");
        if (!"true".equalsIgnoreCase(iniValue(text, "OverrideJavaArgs"))) {
            String global = prismGlobalArguments(instance);
            if (global != null) args = global;
        }
        args = appendAgent(args);
        String edited = iniSet(iniSet(text, "OverrideJavaArgs", "true"), "JvmArgs", iniEncode(args));
        Path backup = Updater.backupConfig(root, config);
        Updater.copySelf(root);
        Updater.atomicWrite(config, edited);
        return "Prism instance configured. Reopen Prism and press Play normally.\n"
            + "Updates run before Minecraft loads.\nJAR: " + root.resolve(Updater.NAME) + "\nSettings backup: " + backup;
    }

    private static String installAT(Path root) throws Exception {
        requireMods(root);
        Path config = root.resolve("instance.json");
        JsonObject json = JsonParser.parseReader(new StringReader(Files.readString(config))).getAsJsonObject();
        if (json.get("launcher") == null || !json.get("launcher").isJsonObject()) throw new Exception("ATLauncher instance.json has no launcher object.");
        JsonObject launcher = json.get("launcher").getAsJsonObject();
        String args = launcher.get("javaArguments") != null && !launcher.get("javaArguments").isJsonNull()
            ? launcher.get("javaArguments").getAsString() : atGlobalArguments(root);
        launcher.add("javaArguments", new JsonPrimitive(appendAgent(args)));
        Path backup = Updater.backupConfig(root, config);
        Updater.copySelf(root);
        Updater.atomicWrite(config, json.toString() + "\n");
        return "ATLauncher instance configured. Reopen ATLauncher and press Play normally.\n"
            + "Updates run before Minecraft loads.\nJAR: " + root.resolve(Updater.NAME) + "\nSettings backup: " + backup;
    }

    private static void requireMods(Path root) throws Exception {
        if (!Files.isDirectory(root.resolve("mods")) || Files.isSymbolicLink(root.resolve("mods")))
            throw new Exception("No regular mods folder at " + root + ". Install the FTB base pack first.");
    }

    private static String appendAgent(String original) {
        String args = original == null ? "" : original.trim();
        // Replace only this updater's prior argument, preserving every other JVM option.
        args = args.replaceAll("(?:^|\\s)-javaagent:fahad-auto-update\\.jar(?:=\\S*)?(?=\\s|$)", " ").trim();
        return (args.isEmpty() ? "" : args + " ") + "-javaagent:" + Updater.NAME + "=client";
    }

    private static String prismGlobalArguments(Path instance) throws Exception {
        Path p = instance.getParent();
        for (int i = 0; p != null && i < 4; i++, p = p.getParent()) {
            Path config = p.resolve("prismlauncher.cfg");
            if (Files.isRegularFile(config)) return iniValue(Files.readString(config), "JvmArgs");
        }
        // If the launcher config is elsewhere, retain an instance's existing value.
        return null;
    }

    private static String atGlobalArguments(Path root) throws Exception {
        Path p = root.getParent();
        for (int i = 0; p != null && i < 4; i++, p = p.getParent()) {
            Path settings = p.resolve("configs/ATLauncher.json");
            if (Files.isRegularFile(settings)) {
                JsonObject object = JsonParser.parseReader(new StringReader(Files.readString(settings))).getAsJsonObject();
                return object.get("javaParameters") != null && !object.get("javaParameters").isJsonNull()
                    ? object.get("javaParameters").getAsString() : "";
            }
        }
        return "";
    }

    static String iniValue(String text, String key) {
        boolean general = true;
        for (String line : text.split("\\R", -1)) {
            String s = line.trim();
            if (s.startsWith("[")) { general = s.equals("[General]"); continue; }
            if (!general || !s.startsWith(key + "=")) continue;
            String value = s.substring(key.length() + 1);
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\\' && i + 1 < value.length()) {
                    char escaped = value.charAt(++i);
                    c = escaped == 'n' ? '\n' : escaped == 't' ? '\t' : escaped;
                }
                result.append(c);
            }
            return result.toString();
        }
        return "";
    }

    static String iniEncode(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    static String iniSet(String text, String key, String value) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\R", -1)));
        boolean general = true;
        int insert = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("[")) {
                if (general && !line.equals("[General]") && insert == lines.size()) insert = i;
                general = line.equals("[General]");
            }
            if (general && line.startsWith(key + "=")) {
                lines.set(i, key + "=" + value);
                return String.join("\n", lines);
            }
        }
        // A General section is required by current Prism's QSettings reader.
        if (lines.stream().anyMatch(l -> l.trim().equals("[General]"))) {
            int generalLine = 0;
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).trim().equals("[General]")) generalLine = i;
            lines.add(generalLine + 1, key + "=" + value);
        } else { lines.add(insert, key + "=" + value); }
        return String.join("\n", lines);
    }
}
