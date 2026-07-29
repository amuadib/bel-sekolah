package com.belsekolah;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AutoStartManager {

    private static final Logger LOGGER = Logger.getLogger(AutoStartManager.class.getName());
    private static final String APP_NAME = "BelSekolah";

    /**
     * Memeriksa apakah OS saat ini adalah Windows.
     */
    public static boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    /**
     * Mendapatkan path folder Startup Windows pengguna saat ini.
     */
    private static File getStartupFolder() {
        String appData = System.getenv("APPDATA");
        if (appData == null) return null;
        return new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup");
    }

    /**
     * Mendapatkan file shortcut (.lnk) aplikasi di folder Startup.
     */
    private static File getShortcutFile() {
        File startupFolder = getStartupFolder();
        if (startupFolder == null) return null;
        return new File(startupFolder, APP_NAME + ".lnk");
    }

    /**
     * Cek apakah aplikasi sudah di-set auto-start.
     */
    public static boolean isAutoStartEnabled() {
        if (!isWindows()) return false;
        File shortcut = getShortcutFile();
        return shortcut != null && shortcut.exists();
    }

    /**
     * Mengaktifkan Auto-Start dengan membuat file .lnk menggunakan VBScript bawaan Windows.
     */
    public static boolean enableAutoStart() {
        if (!isWindows()) return false;

        File shortcut = getShortcutFile();
        if (shortcut == null) return false;

        // Dapatkan lokasi executable / JAR / script aplikasi saat ini
        String currentPath;
        try {
            currentPath = new File(MainApp.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .getAbsolutePath();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Gagal mendapatkan lokasi aplikasi", e);
            return false;
        }

        // Tentukan target yang dijalankan (misal .exe atau java/javaw -jar)
        File currentFile = new File(currentPath);
        String targetPath;
        String workingDir = currentFile.getParent();

        if (currentPath.toLowerCase().endsWith(".jar")) {
            // Jika dijalankan dari JAR
            targetPath = "javaw.exe";
            currentPath = "-jar \"" + currentPath + "\"";
        } else {
            // Jika dikemas menjadi .exe (jpackage / Launch4j / exe wrapper)
            targetPath = currentPath;
            currentPath = "";
        }

        // Buat file VBS temporary untuk membuat shortcut .lnk
        try {
            File vbsFile = File.createTempFile("create_shortcut", ".vbs");
            vbsFile.deleteOnExit();

            try (FileWriter writer = new FileWriter(vbsFile)) {
                writer.write("Set WshShell = CreateObject(\"WScript.Shell\")\n");
                writer.write("Set shortcut = WshShell.CreateShortcut(\"" + shortcut.getAbsolutePath().replace("\\", "\\\\") + "\")\n");
                writer.write("shortcut.TargetPath = \"" + targetPath.replace("\\", "\\\\") + "\"\n");
                if (!currentPath.isEmpty()) {
                    writer.write("shortcut.Arguments = \"" + currentPath.replace("\\", "\\\\") + "\"\n");
                }
                writer.write("shortcut.WorkingDirectory = \"" + workingDir.replace("\\", "\\\\") + "\"\n");
                writer.write("shortcut.WindowStyle = 1\n");
                writer.write("shortcut.Description = \"Auto-start Bel Sekolah\"\n");
                writer.write("shortcut.Save\n");
            }

            Process process = Runtime.getRuntime().exec("wscript " + vbsFile.getAbsolutePath());
            process.waitFor();
            return isAutoStartEnabled();
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Gagal membuat shortcut Auto-Start", e);
            return false;
        }
    }

    /**
     * Menghapus Auto-Start.
     */
    public static boolean disableAutoStart() {
        if (!isWindows()) return false;
        File shortcut = getShortcutFile();
        if (shortcut != null && shortcut.exists()) {
            return shortcut.delete();
        }
        return true;
    }
}