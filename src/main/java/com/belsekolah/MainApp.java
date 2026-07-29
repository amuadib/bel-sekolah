package com.belsekolah;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.shape.Rectangle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class MainApp extends Application {

    // =========================================================================
    // CONSTANTS & FORMATTERS
    // =========================================================================
    private static final Locale LOCALE_ID = new Locale("id", "ID");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", LOCALE_ID);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // =========================================================================
    // FXML BINDINGS
    // =========================================================================
    @FXML private Label lblHari, lblJam, lblStatus, lblJadwalBerikutnya;
    @FXML private Button btnToggleJadwal, btnCustomPlay, btnCustomPause, btnCustomStop;
    @FXML private ComboBox<String> cbCustomAudio;
    @FXML private VBox panelJadwal, containerLaguHariIni;
    @FXML private TableView<Jadwal> tableJadwal;
    @FXML private TableColumn<Jadwal, String> colJam, colJadwal;
    @FXML private Pane paneRunningText;
    @FXML private Label lblRunningText;
    @FXML private Button btnCentralPlay, btnCentralPause, btnCentralStop;
    @FXML private ToggleButton btnCentralRepeat;

    private TranslateTransition runningTextAnimation;
    private boolean isRepeatEnabled = false;

    // =========================================================================
    // STATE & DATA VARIABLES
    // =========================================================================
    private final List<Jadwal> listJadwal = new ArrayList<>();
    private final List<Lagu> listLagu = new ArrayList<>();

    private MediaPlayer scheduledMediaPlayer;
    private MediaPlayer customMediaPlayer;
    private MediaPlayer laguMediaPlayer;

    private Lagu currentlyPlayingLagu = null;
    private Button currentlyPlayingButton = null;

    private TrayIcon trayIcon;
    private String lastPlayedKey = "";
    private long lastModifiedJadwal = 0;
    private long lastModifiedLagu = 0;

    private boolean customWasPausedBySchedule = false;
    private boolean laguWasPausedBySchedule = false;

    // =========================================================================
    // LIFECYCLE & INITIALIZATION
    // =========================================================================
    @Override
    public void start(Stage stage) throws Exception {
        Platform.setImplicitExit(false);

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());

        stage.setTitle("Bel Sekolah - SDI Miftahul Ulum Klemunan");
        stage.setResizable(false);
        setupSystemTray(stage);

        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });

        stage.setScene(scene);
        stage.show();
    }

    @FXML
    public void initialize() {
        // Init Kolom Tabel
        colJam.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().jam().toString()));
        colJadwal.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().label()));

        // Binding lebar kolom secara otomatis (30% untuk Jam, 70% untuk Jadwal)
        colJam.prefWidthProperty().bind(tableJadwal.widthProperty().multiply(0.3));
        colJadwal.prefWidthProperty().bind(tableJadwal.widthProperty().multiply(0.69));

        // Load Initial Data
        loadJadwal();
        loadLagu();
        loadCustomAudioFiles();
        checkAndPlaySchedule();

        // Listener untuk pilihan audio custom agar status tombol langsung ter-update
        cbCustomAudio.valueProperty().addListener((obs, oldVal, newVal) ->
                updateMediaButtonStates(isAnyPlaying(), isAnyPaused())
        );

        // Setup Timer / Clock Handler (Setiap 1 Detik)
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClockAndCheckSchedule()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        updateMediaButtonStates(false, false);
        setupRunningTextClipping();
        updateRunningText("Tidak ada audio diputar");
    }

    // =========================================================================
    // LOGIKA RUNNING TEXT & CENTRAL CONTROLLER
    // =========================================================================

    private void setupRunningTextClipping() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(paneRunningText.widthProperty());
        clip.heightProperty().bind(paneRunningText.heightProperty());
        paneRunningText.setClip(clip);
    }

    private void updateRunningText(String text) {
        Platform.runLater(() -> {
            if (runningTextAnimation != null) {
                runningTextAnimation.stop();
            }

            lblRunningText.setText(text);
            lblRunningText.setTranslateX(paneRunningText.getWidth());

            double textWidth = lblRunningText.getLayoutBounds().getWidth();
            double paneWidth = paneRunningText.getWidth() > 0 ? paneRunningText.getWidth() : 400;

            runningTextAnimation = new TranslateTransition(Duration.seconds(8), lblRunningText);
            runningTextAnimation.setFromX(paneWidth);
            runningTextAnimation.setToX(-textWidth - 20);
            runningTextAnimation.setCycleCount(TranslateTransition.INDEFINITE);
            runningTextAnimation.setInterpolator(javafx.animation.Interpolator.LINEAR);
            runningTextAnimation.play();
        });
    }

    // Helper Pengecekan Status Media
    private boolean isAnyPlaying() {
        return (customMediaPlayer != null && customMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING)
                || (laguMediaPlayer != null && laguMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING)
                || (scheduledMediaPlayer != null && scheduledMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING);
    }

    private boolean isAnyPaused() {
        return (customMediaPlayer != null && customMediaPlayer.getStatus() == MediaPlayer.Status.PAUSED)
                || (laguMediaPlayer != null && laguMediaPlayer.getStatus() == MediaPlayer.Status.PAUSED)
                || (scheduledMediaPlayer != null && scheduledMediaPlayer.getStatus() == MediaPlayer.Status.PAUSED);
    }

    // Handler Tombol Central Player
    @FXML
    private void handleCentralPlay() {
        if (customMediaPlayer != null && customMediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
            customMediaPlayer.play();
            updateMediaButtonStates(true, false);
        } else if (laguMediaPlayer != null && laguMediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
            laguMediaPlayer.play();
            updateMediaButtonStates(true, false);
        } else if (scheduledMediaPlayer != null && scheduledMediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
            scheduledMediaPlayer.play();
            updateMediaButtonStates(true, false);
        } else if (cbCustomAudio.getValue() != null) {
            handlePlay();
        }
    }

    @FXML
    private void handleCentralPause() {
        if (customMediaPlayer != null && customMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            customMediaPlayer.pause();
        } else if (laguMediaPlayer != null && laguMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            laguMediaPlayer.pause();
        } else if (scheduledMediaPlayer != null && scheduledMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            scheduledMediaPlayer.pause();
        }
        updateMediaButtonStates(false, true);
    }

    @FXML
    private void handleCentralStop() {
        stopAllAudio();
    }

    @FXML
    private void handleCentralRepeat() {
        isRepeatEnabled = btnCentralRepeat.isSelected();
        if (isRepeatEnabled) {
            btnCentralRepeat.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        } else {
            btnCentralRepeat.setStyle("");
        }
    }

    // =========================================================================
    // CLOCK & SCHEDULE SCHEDULER
    // =========================================================================
    private void updateClockAndCheckSchedule() {
        LocalDate now = LocalDate.now();
        LocalTime timeNow = LocalTime.now();

        lblHari.setText(now.format(DATE_FORMATTER));
        lblJam.setText(timeNow.format(TIME_FORMATTER));

        if (timeNow.getSecond() == 0) {
            checkFileModifications();
            checkAndPlaySchedule();
        }
    }

    private void checkAndPlaySchedule() {
        LocalDate now = LocalDate.now();
        LocalTime timeNow = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        int currentDayOfWeek = now.getDayOfWeek().getValue();

        if (panelJadwal.isVisible()) {
            renderJadwalHariIni();
        }

        Jadwal nextJadwal = null;

        for (Jadwal j : listJadwal) {
            if (j.hari() == currentDayOfWeek) {
                if (j.jam().isAfter(timeNow)) {
                    if (nextJadwal == null || j.jam().isBefore(nextJadwal.jam())) {
                        nextJadwal = j;
                    }
                }

                if (j.jam().equals(timeNow)) {
                    String currentKey = currentDayOfWeek + "-" + j.jam();
                    if (!lastPlayedKey.equals(currentKey)) {
                        lastPlayedKey = currentKey;
                        playScheduledAudio(j.mp3Path(), j.label());
                    }
                }
            }
        }

        lblJadwalBerikutnya.setText(nextJadwal != null
                ? nextJadwal.jam() + "   " + nextJadwal.label()
                : "Tidak ada jadwal lagi hari ini");
    }

    // =========================================================================
    // DATA LOADERS & FILE I/O
    // =========================================================================
    private void checkFileModifications() {
        File fileJadwal = new File("jadwal.dat");
        if (fileJadwal.exists() && fileJadwal.lastModified() != lastModifiedJadwal) {
            loadJadwal();
        }

        File fileLagu = new File("lagu.dat");
        if (fileLagu.exists() && fileLagu.lastModified() != lastModifiedLagu) {
            loadLagu();
        }
    }

    private void loadJadwal() {
        File file = new File("jadwal.dat");
        if (!file.exists()) return;

        listJadwal.clear();
        lastModifiedJadwal = file.lastModified();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String label = parts[0].trim();
                    int hari = Integer.parseInt(parts[1].trim());
                    String jamStr = parts[2].trim();
                    LocalTime jam = LocalTime.of(
                            Integer.parseInt(jamStr.substring(0, 2)),
                            Integer.parseInt(jamStr.substring(2, 4))
                    );
                    String mp3Path = parts[3].trim().replace("\\", "/");
                    listJadwal.add(new Jadwal(label, hari, jam, mp3Path));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (panelJadwal != null && panelJadwal.isVisible()) {
            renderJadwalHariIni();
        }
    }

    private void loadLagu() {
        File file = new File("lagu.dat");
        if (!file.exists()) return;

        listLagu.clear();
        lastModifiedLagu = file.lastModified();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String label = parts[0].trim();
                    List<Integer> listHari = Arrays.stream(parts[1].split(","))
                            .map(String::trim)
                            .map(Integer::parseInt)
                            .toList();
                    String mp3Path = parts[2].trim().replace("\\", "/");

                    listLagu.add(new Lagu(label, listHari, mp3Path));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        renderLaguHariIni();
    }

    private void loadCustomAudioFiles() {
        File folder = new File("mp3/custom");
        cbCustomAudio.getItems().clear();
        cbCustomAudio.getItems().addAll(scanAudioFiles(folder));
    }

    private void saveJadwalToFile() {
        File file = new File("jadwal.dat");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Jadwal j : listJadwal) {
                String jamStr = String.format("%02d%02d", j.jam().getHour(), j.jam().getMinute());
                String line = j.label() + "|" + j.hari() + "|" + jamStr + "|" + j.mp3Path();
                writer.write(line);
                writer.newLine();
            }
            lastModifiedJadwal = file.lastModified();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Error Penyimpanan", "Gagal Menulis File", "Gagal menyimpan jadwal ke file jadwal.dat");
        }
    }

    private List<String> scanAudioFiles(File baseFolder) {
        List<String> fileList = new ArrayList<>();
        if (!baseFolder.exists() || !baseFolder.isDirectory()) {
            baseFolder.mkdirs();
            return fileList;
        }

        try (var stream = Files.walk(baseFolder.toPath())) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a");
                    })
                    .forEach(p -> {
                        String relativePath = baseFolder.toPath().relativize(p).toString().replace("\\", "/");
                        fileList.add(relativePath);
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(fileList);
        return fileList;
    }

    // =========================================================================
    // UI RENDERING & HANDLERS
    // =========================================================================
    private void renderJadwalHariIni() {
        int today = LocalDate.now().getDayOfWeek().getValue();
        List<Jadwal> hariIniList = listJadwal.stream()
                .filter(j -> j.hari() == today)
                .sorted(Comparator.comparing(Jadwal::jam))
                .toList();

        tableJadwal.getItems().setAll(hariIniList);
    }

    private void renderLaguHariIni() {
        containerLaguHariIni.getChildren().clear();
        int today = LocalDate.now().getDayOfWeek().getValue();

        for (Lagu lagu : listLagu) {
            if (lagu.hariList().contains(today)) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);

                Label lblNama = new Label(lagu.label());
                HBox.setHgrow(lblNama, Priority.ALWAYS);
                lblNama.setMaxWidth(Double.MAX_VALUE);

                Button btnPlayStop = new Button(currentlyPlayingLagu == lagu ? "⏹" : "▶");
                btnPlayStop.setOnAction(e -> handlePlayStopLagu(lagu, btnPlayStop));

                row.getChildren().addAll(lblNama, btnPlayStop);
                containerLaguHariIni.getChildren().add(row);
            }
        }
    }

    @FXML
    private void toggleJadwalPanel() {
        boolean isVisible = !panelJadwal.isVisible();
        panelJadwal.setVisible(isVisible);
        panelJadwal.setManaged(isVisible);

        btnToggleJadwal.setText(isVisible ? "Sembunyikan Jadwal" : "Tampilkan Jadwal");
        if (isVisible) renderJadwalHariIni();

        Stage stage = (Stage) panelJadwal.getScene().getWindow();
        if (stage != null) stage.sizeToScene();
    }

    @FXML
    private void handleTambahJadwal() {
        showJadwalDialog(null);
    }

    @FXML
    private void handleEditJadwal() {
        Jadwal selected = tableJadwal.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorAlert("Peringatan", "Pilihan Tidak Valid", "Pilih baris jadwal yang ingin diubah terlebih dahulu!");
            return;
        }
        showJadwalDialog(selected);
    }

    @FXML
    private void handleHapusJadwal() {
        Jadwal selected = tableJadwal.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorAlert("Peringatan", "Pilihan Tidak Valid", "Pilih baris jadwal yang ingin dihapus terlebih dahulu!");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Apakah Anda yakin ingin menghapus jadwal \"" + selected.label() + "\"?",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Konfirmasi Hapus");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                listJadwal.remove(selected);
                saveJadwalToFile();
                renderJadwalHariIni();
                checkAndPlaySchedule();
            }
        });
    }

    private void showJadwalDialog(Jadwal existing) {
        Dialog<Jadwal> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Tambah Jadwal Baru" : "Edit Jadwal");
        dialog.setHeaderText(null);

        ButtonType btnSaveType = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSaveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField txtLabel = new TextField(existing != null ? existing.label() : "");
        txtLabel.setPromptText("Contoh: Bel Masuk");

        TextField txtJam = new TextField(existing != null ? existing.jam().toString() : "07:00");
        txtJam.setPromptText("HH:mm (Contoh: 07:30)");

        ComboBox<String> cbMp3 = new ComboBox<>();
        File folderMp3 = new File("mp3");
        cbMp3.getItems().addAll(scanAudioFiles(folderMp3));

        if (existing != null) {
            cbMp3.setValue(existing.mp3Path());
        }

        grid.add(new Label("Nama Jadwal:"), 0, 0);
        grid.add(txtLabel, 1, 0);
        grid.add(new Label("Jam (HH:mm):"), 0, 1);
        grid.add(txtJam, 1, 1);
        grid.add(new Label("File Suara:"), 0, 2);
        grid.add(cbMp3, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnSaveType) {
                try {
                    String label = txtLabel.getText().trim();
                    String jamText = txtJam.getText().trim();
                    String mp3 = cbMp3.getValue();

                    if (label.isEmpty() || jamText.isEmpty() || mp3 == null) {
                        showErrorAlert("Input Tidak Lengkap", "Data Belum Lengkap", "Semua kolom harus diisi!");
                        return null;
                    }

                    LocalTime jam;
                    try {
                        jam = LocalTime.parse(jamText);
                    } catch (DateTimeParseException ex) {
                        String[] parts = jamText.split(":");
                        jam = LocalTime.of(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                    }

                    int hari = existing != null ? existing.hari() : LocalDate.now().getDayOfWeek().getValue();
                    return new Jadwal(label, hari, jam, mp3);
                } catch (Exception e) {
                    showErrorAlert("Format Waktu Salah", "Format Jam Salah", "Gunakan format jam HH:mm (contoh: 08:30)");
                    return null;
                }
            }
            return null;
        });

        Optional<Jadwal> result = dialog.showAndWait();
        result.ifPresent(newJadwal -> {
            if (existing != null) {
                int index = listJadwal.indexOf(existing);
                if (index != -1) listJadwal.set(index, newJadwal);
            } else {
                listJadwal.add(newJadwal);
            }

            saveJadwalToFile();
            renderJadwalHariIni();
            checkAndPlaySchedule();
        });
    }

    @FXML
    private void openGithub() {
        try {
            String githubUrl = "https://github.com/amuadib/bel-sekolah";
            getHostServices().showDocument(githubUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // AUDIO CONTROLLERS
    // =========================================================================
    private void playScheduledAudio(String path, String label) {
        pauseMediaForSchedule();

        File mainAudioFile = new File("mp3/" + path);

        if (!mainAudioFile.exists()) {
            String msg = "File MP3 untuk jadwal \"" + label + "\" tidak ditemukan:\n" + mainAudioFile.getAbsolutePath();
            setStatusText("Error: File MP3 tidak ditemukan");
            showErrorAlert("Error Pemutaran Bel", "File Tidak Ditemukan", msg);
            resumeMediaAfterSchedule();
            return;
        }

        if (path.trim().equalsIgnoreCase("BEL.mp3")) {
            scheduledMediaPlayer = new MediaPlayer(new Media(mainAudioFile.toURI().toString()));
            setStatusText("Status: Memutar " + label);
            updateRunningText("JADWAL BEL: " + label);
            attachEndOfMediaHandler(scheduledMediaPlayer, this::resumeMediaAfterSchedule);
            scheduledMediaPlayer.play();
            updateMediaButtonStates(true, false);
            return;
        }

        File pembukaFile = new File("mp3/pembuka.wav");
        File penutupFile = new File("mp3/penutup.wav");

        Runnable playPenutupAction = () -> {
            if (penutupFile.exists()) {
                scheduledMediaPlayer = new MediaPlayer(new Media(penutupFile.toURI().toString()));
                attachEndOfMediaHandler(scheduledMediaPlayer, this::resumeMediaAfterSchedule);
                scheduledMediaPlayer.play();
                updateMediaButtonStates(true, false);
            } else {
                resumeMediaAfterSchedule();
            }
        };

        Runnable playMainAudioAction = () -> {
            scheduledMediaPlayer = new MediaPlayer(new Media(mainAudioFile.toURI().toString()));
            setStatusText("Status: Memutar " + label);
            attachEndOfMediaHandler(scheduledMediaPlayer, playPenutupAction);
            scheduledMediaPlayer.play();
            updateMediaButtonStates(true, false);
        };

        if (pembukaFile.exists()) {
            scheduledMediaPlayer = new MediaPlayer(new Media(pembukaFile.toURI().toString()));
            setStatusText("Status: Memutar " + label);
            attachEndOfMediaHandler(scheduledMediaPlayer, playMainAudioAction);
            scheduledMediaPlayer.play();
            updateMediaButtonStates(true, false);
        } else {
            playMainAudioAction.run();
        }
    }

    private void attachEndOfMediaHandler(MediaPlayer player, Runnable defaultOnEnd) {
        if (player == null) return;
        player.setOnEndOfMedia(() -> {
            if (isRepeatEnabled) {
                player.seek(Duration.ZERO);
                player.play();
            } else {
                defaultOnEnd.run();
            }
        });
    }

    private void handlePlayStopLagu(Lagu lagu, Button btn) {
        if (currentlyPlayingLagu == lagu) {
            stopAllAudio();
            return;
        }

        stopAllAudio();

        File audioFile = new File("mp3/" + lagu.mp3Path());
        if (audioFile.exists()) {
            try {
                laguMediaPlayer = new MediaPlayer(new Media(audioFile.toURI().toString()));
                currentlyPlayingLagu = lagu;
                currentlyPlayingButton = btn;

                btn.setText("⏹");
                setStatusText("Status: Memutar " + lagu.label());
                updateRunningText("Lagu Hari Ini: " + lagu.label());
                attachEndOfMediaHandler(laguMediaPlayer, this::stopAllAudio);
                laguMediaPlayer.play();
                updateMediaButtonStates(true, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            String msg = "File lagu \"" + lagu.label() + "\" tidak ditemukan:\n" + audioFile.getAbsolutePath();
            setStatusText("Error: File tidak ditemukan");
            showErrorAlert("Error Pemutaran Lagu", "File Tidak Ditemukan", msg);
        }
    }

    @FXML
    private void handlePlay() {
        String selected = cbCustomAudio.getValue();
        if (selected == null) return;

        if (customMediaPlayer != null && customMediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
            customMediaPlayer.play();
            updateMediaButtonStates(true, false);
            return;
        }

        stopAllAudio();

        File audioFile = new File("mp3/custom/" + selected);
        if (audioFile.exists()) {
            try {
                customMediaPlayer = new MediaPlayer(new Media(audioFile.toURI().toString()));
                attachEndOfMediaHandler(customMediaPlayer, this::stopAllAudio);
                customMediaPlayer.play();
                updateMediaButtonStates(true, false);
                setStatusText("Status: Memutar " + selected);
                updateRunningText("Custom Audio: " + selected);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            String msg = "File audio custom tidak ditemukan:\n" + audioFile.getAbsolutePath();
            setStatusText("Error: File tidak ditemukan");
            showErrorAlert("Error Pemutaran Audio", "File Tidak Ditemukan", msg);
        }
    }

    @FXML
    private void handleStop() {
        stopAllAudio();
    }

    @FXML
    private void handlePause() {
        if (customMediaPlayer != null && customMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            customMediaPlayer.pause();
        } else if (laguMediaPlayer != null && laguMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            laguMediaPlayer.pause();
        } else if (scheduledMediaPlayer != null && scheduledMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            scheduledMediaPlayer.pause();
        }
        updateMediaButtonStates(false, true);
    }

    private void pauseMediaForSchedule() {
        customWasPausedBySchedule = false;
        laguWasPausedBySchedule = false;

        if (customMediaPlayer != null && customMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            customMediaPlayer.pause();
            customWasPausedBySchedule = true;
        }

        if (laguMediaPlayer != null && laguMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            laguMediaPlayer.pause();
            laguWasPausedBySchedule = true;
        }

        scheduledMediaPlayer = disposeAndNull(scheduledMediaPlayer);
        updateMediaButtonStates(false, true);
    }

    private void resumeMediaAfterSchedule() {
        if (customWasPausedBySchedule) {
            customWasPausedBySchedule = false;
            if (customMediaPlayer != null) {
                customMediaPlayer.play();
                updateMediaButtonStates(true, false);
                if (cbCustomAudio.getValue() != null) {
                    setStatusText("Status: Memutar " + cbCustomAudio.getValue());
                }
                return;
            }
        }

        if (laguWasPausedBySchedule) {
            laguWasPausedBySchedule = false;
            if (laguMediaPlayer != null) {
                laguMediaPlayer.play();
                updateMediaButtonStates(true, false);
                if (currentlyPlayingLagu != null) {
                    setStatusText("Status: Memutar " + currentlyPlayingLagu.label());
                }
                return;
            }
        }

        stopAllAudio();
    }

    private void stopAllAudio() {
        customWasPausedBySchedule = false;
        laguWasPausedBySchedule = false;

        customMediaPlayer = disposeAndNull(customMediaPlayer);
        scheduledMediaPlayer = disposeAndNull(scheduledMediaPlayer);
        laguMediaPlayer = disposeAndNull(laguMediaPlayer);

        if (currentlyPlayingButton != null) {
            currentlyPlayingButton.setText("▶");
            currentlyPlayingButton = null;
        }

        currentlyPlayingLagu = null;
        updateMediaButtonStates(false, false);
        setStatusText("Status: Menunggu jadwal berikutnya");

        // Reset running text ke kondisi default
        updateRunningText("Tidak ada audio diputar");
    }

    private MediaPlayer disposeAndNull(MediaPlayer player) {
        if (player != null) {
            try {
                player.stop();
                player.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void setStatusText(String text) {
        if (lblStatus != null) {
            lblStatus.setText(text);
        }
    }

    // =========================================================================
    // LOGIKA DI-ENABLE / DI-DISABLE TOMBOL KONTROL
    // =========================================================================
    private void updateMediaButtonStates(boolean isPlaying, boolean isPaused) {
        boolean hasSelectedCustomAudio = cbCustomAudio != null && cbCustomAudio.getValue() != null && !cbCustomAudio.getValue().isEmpty();
        boolean canPlayOrResume = isPaused || hasSelectedCustomAudio;

        // Custom Buttons
        if (btnCustomPlay != null) btnCustomPlay.setDisable(isPlaying || !hasSelectedCustomAudio);
        if (btnCustomPause != null) btnCustomPause.setDisable(!isPlaying);
        if (btnCustomStop != null) btnCustomStop.setDisable(!isPlaying && !isPaused);

        // Central Player Buttons
        if (btnCentralPlay != null) btnCentralPlay.setDisable(isPlaying || !canPlayOrResume);
        if (btnCentralPause != null) btnCentralPause.setDisable(!isPlaying);
        if (btnCentralStop != null) btnCentralStop.setDisable(!isPlaying && !isPaused);
    }

    // =========================================================================
    // SYSTEM TRAY, ALERTS & APP EXIT
    // =========================================================================
    private void setupSystemTray(Stage stage) {
        if (!SystemTray.isSupported()) return;

        PopupMenu popup = new PopupMenu();
        MenuItem itemTampilkan = new MenuItem("Tampilkan");
        itemTampilkan.addActionListener(e -> Platform.runLater(() -> { stage.show(); stage.toFront(); }));

        MenuItem itemKeluar = new MenuItem("Keluar");
        itemKeluar.addActionListener(e -> Platform.runLater(() -> tampilkanKonfirmasiKeluar(stage)));

        popup.add(itemTampilkan);
        popup.addSeparator();
        popup.add(itemKeluar);

        trayIcon = new TrayIcon(createTrayIconImage(), "Bel Sekolah", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(() -> { stage.show(); stage.toFront(); }));

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private java.awt.Image createTrayIconImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(107, 201, 13));
        g.fillOval(0, 0, 16, 16);
        g.dispose();
        return image;
    }

    private void tampilkanKonfirmasiKeluar(Stage stage) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Apakah Anda yakin ingin menutup aplikasi?", ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Konfirmasi Keluar");
        alert.setHeaderText("Keluar dari Aplikasi Bel Sekolah");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                stopAllAudio();
                if (trayIcon != null && SystemTray.isSupported()) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
                stage.close();
                Platform.exit();
                System.exit(0);
            }
        });
    }

    private void showErrorAlert(String title, String header, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }

    // =========================================================================
    // DATA MODELS (Java Record)
    // =========================================================================
    public record Jadwal(String label, int hari, LocalTime jam, String mp3Path) {}
    public record Lagu(String label, List<Integer> hariList, String mp3Path) {}
}