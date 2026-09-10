package com.rockstargames.oswrapper;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Instalador remoto da data. O fluxo é propositalmente simples:
 * download completo -> SHA-256 -> extração em staging -> promoção atômica.
 */
public final class DataInstaller {
    private static final String TAG = "DataInstaller";
    private static final String MANIFEST_URL =
            "https://crest-api-l5qrupbr.manus.space/api/manifest?v=20260818-data2";
    private static final int CONNECT_TIMEOUT_MS = 45_000;
    private static final int READ_TIMEOUT_MS = 180_000;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final long MAX_RETRY_DELAY_MS = 15_000L;
    private static final int MAX_HTTP_REDIRECTS = 5;
    private static final long MAX_ALLOWED_EXTRACTED_BYTES = 3_500_000_000L;
    private static final long STORAGE_MARGIN_BYTES = 96L * 1024L * 1024L;
    private static final String[] MANAGED_ROOTS = {
            "anim", "audio", "data", "models", "texdb", "text", "textures", "SAMP",
            "CINFO.BIN", "GTASAsf10.b", "fonts", "gta_sa.set", "stream.ini"
    };
    private static final String[] REQUIRED_FILES = {
            "anim/ped.ifp", "audio/STREAMS/AA.osw", "data/gta.dat", "data/default.dat",
            "SAMP/main.scm", "SAMP/script.img", "texdb/gta3.img", "texdb/player.img", "text/american.gxt"
    };

    public interface Listener {
        void onProgress(int percent, String phase, long bytesDone, long bytesTotal);
        void onComplete(File root);
        void onError(String message, Throwable error);
    }

    private static volatile CountDownLatch readyLatch = new CountDownLatch(1);
    private static volatile boolean installing;
    private static volatile boolean success;
    private static volatile String errorMessage = "";
    private static volatile Listener listener;

    private DataInstaller() { }

    public static synchronized void prepare(final Context context) {
        if (installing || success) return;
        if (context == null) {
            fail(new IOException("Contexto do Launcher indisponível"));
            return;
        }
        installing = true;
        success = false;
        errorMessage = "";
        readyLatch = new CountDownLatch(1);
        new Thread(() -> install(context.getApplicationContext()), "crestwood-data-installer").start();
    }

    public static synchronized void repair(final Context context) {
        if (installing) return;
        success = false;
        prepare(context);
    }

    public static void setListener(Listener value) {
        listener = value;
        if (value != null && success) value.onComplete(null);
    }

    public static void whenReady(final Context context, final Runnable callback) {
        prepare(context);
        new Thread(() -> {
            if (!awaitReady(30L * 60L * 1000L) || callback == null) return;
            new Handler(Looper.getMainLooper()).post(callback);
        }, "crestwood-data-ready").start();
    }

    public static boolean awaitReady(long timeoutMs) {
        try {
            readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        return success;
    }

    public static boolean isReady() { return success; }
    public static boolean isInstalling() { return installing; }
    public static String getErrorMessage() { return errorMessage; }
    public static String getRemoteDataUrl() { return MANIFEST_URL; }

    public static File getTargetDirectory(Context context) {
        @SuppressWarnings("deprecation")
        File sharedRoot = Environment.getExternalStorageDirectory();
        return sharedRoot == null ? null : new File(sharedRoot, "GTA");
    }

    public static boolean hasSharedStorageAccess(Context context) {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                (context != null && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean isInstallReady(Context context) {
        if (!success || context == null) return false;
        File target = getTargetDirectory(context);
        if (target == null) return false;
        try {
            return new File(target, ".gtasa_data_version").isFile() && isComplete(target);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void install(Context context) {
        File target = getTargetDirectory(context);
        File cacheDirectory = context.getExternalCacheDir();
        if (cacheDirectory == null) cacheDirectory = context.getCacheDir();
        File zipFile = new File(cacheDirectory, "crestwood_gtasa_data.zip");
        File partialFile = new File(cacheDirectory, "crestwood_gtasa_data.zip.part");
        File staging = target == null ? null : new File(target.getParentFile(), target.getName() + ".staging");

        try {
            if (target == null || staging == null) throw new IOException("Diretório de instalação indisponível");
            if (!hasSharedStorageAccess(context)) throw new IOException("Autorize o acesso aos arquivos para instalar em /storage/emulated/0/GTA");
            if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) throw new IOException("Não foi possível criar o cache de atualização");
            if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) throw new IOException("Não foi possível criar a pasta do jogo");

            UpdateManifest manifest = fetchManifestWithRetry();
            String markerValue = manifest.version + ":" + manifest.sha256;
            File marker = new File(target, ".gtasa_data_version");
            if (marker.isFile() && markerValue.equals(readSmallFile(marker)) && isComplete(target)) {
                cleanupLegacyPrivateData(context, target);
                complete(target, manifest);
                return;
            }

            report(0, "Liberando espaço para a instalação", 0L, manifest.compressedBytes);
            cleanupObsoleteInstallFiles(context, target, staging);
            long existingDownload = zipFile.isFile() ? zipFile.length() : (partialFile.isFile() ? partialFile.length() : 0L);
            ensureDownloadAndExtractionSpace(target, existingDownload, manifest);

            if (zipFile.isFile() && zipFile.length() == manifest.compressedBytes) {
                report(70, "Arquivo já baixado; verificando integridade", zipFile.length(), manifest.compressedBytes);
            } else {
                if (zipFile.exists()) deleteRecursively(zipFile);
                report(0, "Baixando data do servidor", partialFile.isFile() ? partialFile.length() : 0L, manifest.compressedBytes);
                downloadToFile(partialFile, manifest);
                if (!partialFile.renameTo(zipFile)) {
                    copyFile(partialFile, zipFile);
                    deleteRecursively(partialFile);
                }
            }

            report(70, "Verificando integridade do arquivo", zipFile.length(), manifest.compressedBytes);
            String actualHash = sha256(zipFile);
            if (!manifest.sha256.equalsIgnoreCase(actualHash)) {
                deleteRecursively(zipFile);
                throw new IOException("O arquivo baixado não passou na verificação de integridade");
            }

            ensureExtractionSpace(target, manifest);
            deleteRecursively(staging);
            if (!staging.mkdirs()) throw new IOException("Não foi possível preparar a instalação");
            extractZip(zipFile, staging, manifest);
            if (!isComplete(staging)) throw new IOException("A extração terminou sem os arquivos obrigatórios do jogo");

            deleteRecursively(target);
            if (!staging.renameTo(target)) {
                copyRecursively(staging, target);
                deleteRecursively(staging);
            }
            writeSmallFile(new File(target, ".gtasa_data_version"), markerValue);
            if (!isComplete(target)) throw new IOException("A instalação final não contém todos os arquivos necessários");
            deleteRecursively(zipFile);
            deleteRecursively(partialFile);
            complete(target, manifest);
        } catch (Throwable error) {
            deleteRecursively(staging);
            fail(error);
        }
    }

    private static final int MAX_STALLED_ATTEMPTS = 15;

    private static void downloadToFile(File partialFile, UpdateManifest manifest) throws IOException {
        int failuresWithoutProgress = 0;
        while (partialFile.length() < manifest.compressedBytes) {
            long before = partialFile.length();
            try {
                downloadAttempt(partialFile, manifest);
            } catch (IOException error) {
                if (!isRecoverableDownloadError(error)) throw error;
            }
            long after = partialFile.length();
            if (after >= manifest.compressedBytes) break;
            if (after > before) failuresWithoutProgress = 0;
            else failuresWithoutProgress++;
            if (failuresWithoutProgress >= MAX_STALLED_ATTEMPTS) {
                throw new IOException("Download travado sem progresso após várias tentativas. " +
                        "Verifique sua conexão ou tente novamente mais tarde.");
            }
            long waitMs = Math.min(MAX_RETRY_DELAY_MS, RETRY_DELAY_MS * Math.max(1L, failuresWithoutProgress));
            report(downloadPercent(after, manifest.compressedBytes),
                    "Conexão interrompida; retomando automaticamente", after, manifest.compressedBytes);
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrompido", error);
            }
        }
        if (partialFile.length() != manifest.compressedBytes) throw new IOException("Download incompleto");
        report(70, "Download concluído", partialFile.length(), manifest.compressedBytes);
    }

    private static void downloadAttempt(File partialFile, UpdateManifest manifest) throws IOException {
        long existing = partialFile.isFile() ? partialFile.length() : 0L;
        HttpURLConnection connection = null;
        try {
            connection = openDownloadConnection(manifest.downloadUrl, existing);
            int response = connection.getResponseCode();
            boolean append = existing > 0L && response == HttpURLConnection.HTTP_PARTIAL;
            if (response != HttpURLConnection.HTTP_OK && !append) throw new IOException("Servidor respondeu HTTP " + response);
            if (!append) {
                existing = 0L;
                deleteRecursively(partialFile);
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream(), 128 * 1024);
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(partialFile, append), 128 * 1024)) {
                byte[] buffer = new byte[128 * 1024];
                long done = existing;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    long remaining = manifest.compressedBytes - done;
                    if (remaining <= 0L) throw new IOException("O servidor enviou bytes além do tamanho esperado");
                    int accepted = (int) Math.min((long) read, remaining);
                    output.write(buffer, 0, accepted);
                    done += accepted;
                    report(downloadPercent(done, manifest.compressedBytes), "Baixando data do servidor", done, manifest.compressedBytes);
                    if (accepted != read) break;
                }
                output.flush();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection openDownloadConnection(String urlString, long existing) throws IOException {
        URL current = new URL(urlString);
        for (int redirects = 0; redirects <= MAX_HTTP_REDIRECTS; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "CrestwoodRoleplayLauncher/2.0");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Cache-Control", "no-cache");
            if (existing > 0L) connection.setRequestProperty("Range", "bytes=" + existing + "-");
            int response = connection.getResponseCode();
            if (response != HttpURLConnection.HTTP_MOVED_PERM && response != HttpURLConnection.HTTP_MOVED_TEMP &&
                    response != HttpURLConnection.HTTP_SEE_OTHER && response != 307 && response != 308) {
                return connection;
            }
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.trim().isEmpty()) throw new IOException("Redirecionamento sem endereço de download");
            current = new URL(current, location);
            if (!"https".equalsIgnoreCase(current.getProtocol())) throw new IOException("Redirecionamento de download inseguro");
        }
        throw new IOException("Muitos redirecionamentos no download");
    }

    private static void extractZip(File zipFile, File staging, UpdateManifest manifest) throws IOException {
        long extracted = 0L;
        String rootPath = staging.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile), 128 * 1024))) {
            ZipEntry entry;
            byte[] buffer = new byte[128 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                File output = safeChild(staging, rootPath, entry.getName());
                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) throw new IOException("Não foi possível criar a pasta da data");
                    zip.closeEntry();
                    continue;
                }
                File parent = output.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Não foi possível criar a pasta da data");
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(output), 128 * 1024)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        extracted += read;
                        if (extracted > MAX_ALLOWED_EXTRACTED_BYTES) throw new IOException("A data excede o limite de segurança");
                        out.write(buffer, 0, read);
                        int percent = 71 + (int) Math.min(28L, extracted * 28L / Math.max(1L, manifest.extractedBytes));
                        report(percent, "Instalando arquivos do jogo", extracted, manifest.extractedBytes);
                    }
                }
                zip.closeEntry();
            }
        }
        if (extracted <= 0L) throw new IOException("O pacote não contém arquivos para extrair");
        report(99, "Arquivos extraídos; finalizando instalação", extracted, manifest.extractedBytes);
    }

    private static UpdateManifest fetchManifest() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("Manifesto respondeu HTTP " + connection.getResponseCode());
            StringBuilder body = new StringBuilder();
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) body.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            JSONObject json = new JSONObject(body.toString());
            UpdateManifest manifest = new UpdateManifest(
                    json.getString("version"), json.getString("downloadUrl"), json.getString("sha256"),
                    json.getLong("compressedBytes"), json.getLong("extractedBytes"));
            manifest.validate();
            return manifest;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static final int MAX_MANIFEST_ATTEMPTS = 5;

    private static UpdateManifest fetchManifestWithRetry() throws Exception {
        int failures = 0;
        IOException lastError = null;
        while (failures < MAX_MANIFEST_ATTEMPTS) {
            try {
                return fetchManifest();
            } catch (IOException error) {
                lastError = error;
                if (!isRecoverableDownloadError(error)) throw error;
                failures++;
                if (failures >= MAX_MANIFEST_ATTEMPTS) break;
                report(0, "Conectando ao servidor; tentando novamente (" + failures + "/" + MAX_MANIFEST_ATTEMPTS + ")", 0L, 0L);
                try {
                    Thread.sleep(Math.min(MAX_RETRY_DELAY_MS, RETRY_DELAY_MS * Math.max(1L, failures)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Conexão interrompida", interrupted);
                }
            }
        }
        throw new IOException("Servidor de atualização inacessível após " + MAX_MANIFEST_ATTEMPTS +
                " tentativas. Verifique sua conexão ou tente novamente mais tarde.", lastError);
    }

    private static void cleanupObsoleteInstallFiles(Context context, File target, File staging) {
        cleanupLegacyPrivateData(context, target);
        deleteRecursively(staging);
        File marker = new File(target, ".gtasa_data_version");
        if (target.exists() && !marker.isFile()) deleteRecursively(target);
    }

    private static void cleanupLegacyPrivateData(Context context, File target) {
        if (context == null) return;
        File legacyExternal = context.getExternalFilesDir(null);
        File legacyRoot = legacyExternal == null ? null : new File(legacyExternal, "GTA");
        if (legacyRoot != null && !samePath(legacyRoot, target)) deleteRecursively(legacyRoot);
        File legacyInternal = new File(context.getFilesDir(), "GTA");
        if (!samePath(legacyInternal, target)) deleteRecursively(legacyInternal);
    }

    private static boolean samePath(File first, File second) {
        if (first == null || second == null) return false;
        try {
            return first.getCanonicalPath().equals(second.getCanonicalPath());
        } catch (IOException error) {
            return first.equals(second);
        }
    }

    private static void ensureDownloadAndExtractionSpace(File target, long existingDownload, UpdateManifest manifest) throws IOException {
        long remainingDownload = Math.max(0L, manifest.compressedBytes - Math.max(0L, existingDownload));
        long required = remainingDownload + manifest.extractedBytes + STORAGE_MARGIN_BYTES;
        long available = getUsableSpaceForTarget(target);
        if (available < required) {
            throw new IOException("Espaço livre insuficiente para baixar e extrair a data. Libere ao menos " +
                    ((required - available + (1024L * 1024L - 1L)) / (1024L * 1024L)) + " MB antes de iniciar a instalação.");
        }
    }

    private static void ensureExtractionSpace(File target, UpdateManifest manifest) throws IOException {
        long required = manifest.extractedBytes + STORAGE_MARGIN_BYTES;
        long available = getUsableSpaceForTarget(target);
        if (available < required) {
            throw new IOException("Espaço livre insuficiente para extrair a data. Libere ao menos " +
                    ((required - available + (1024L * 1024L - 1L)) / (1024L * 1024L)) + " MB e tente novamente.");
        }
    }

    private static long getUsableSpaceForTarget(File target) {
        File volume = target;
        while (volume != null && !volume.exists()) volume = volume.getParentFile();
        return volume == null ? 0L : volume.getUsableSpace();
    }

    private static int downloadPercent(long done, long total) {
        return (int) Math.min(70L, done * 70L / Math.max(1L, total));
    }

    private static boolean isRecoverableDownloadError(IOException error) {
        if (error == null) return true;
        String text = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return !(text.contains("espaço") || text.contains("storage") || text.contains("permission") ||
                text.contains("permiss") || text.contains("insegura") || text.contains("além do tamanho"));
    }

    private static File safeChild(File root, String canonicalRoot, String name) throws IOException {
        if (name == null || name.length() == 0) throw new IOException("Entrada ZIP inválida");
        File output = new File(root, name);
        String outputPath = output.getCanonicalPath();
        if (!outputPath.startsWith(canonicalRoot)) throw new IOException("Entrada ZIP insegura");
        return output;
    }

    private static boolean isComplete(File root) {
        for (String name : MANAGED_ROOTS) if (!new File(root, name).exists()) return false;
        for (String name : REQUIRED_FILES) {
            File file = new File(root, name);
            if (!file.isFile() || file.length() <= 0L) return false;
        }
        return true;
    }

    private static void complete(File target, UpdateManifest manifest) {
        success = true;
        installing = false;
        report(100, "Data instalada", manifest.extractedBytes, manifest.extractedBytes);
        readyLatch.countDown();
        Listener current = listener;
        if (current != null) current.onComplete(target);
    }

    private static void fail(Throwable error) {
        errorMessage = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        success = false;
        installing = false;
        readyLatch.countDown();
        Listener current = listener;
        if (current != null) current.onError(errorMessage, error);
        Log.e(TAG, "Falha instalando a data", error);
    }

    private static void report(int percent, String phase, long done, long total) {
        Listener current = listener;
        if (current != null) current.onProgress(Math.max(0, Math.min(100, percent)), phase, done, total);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file), 128 * 1024)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private static String readSmallFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[256];
            int count = input.read(bytes);
            return count <= 0 ? "" : new String(bytes, 0, count, StandardCharsets.UTF_8).trim();
        }
    }

    private static void writeSmallFile(File file, String value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Não foi possível salvar a confirmação da data");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Não foi possível preparar o destino");
        try (InputStream input = new BufferedInputStream(new FileInputStream(source), 128 * 1024);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target), 128 * 1024)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private static void copyRecursively(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) throw new IOException("Não foi possível criar a pasta da instalação");
            File[] files = source.listFiles();
            if (files != null) for (File file : files) copyRecursively(file, new File(target, file.getName()));
        } else {
            copyFile(source, target);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] files = file.listFiles();
        if (files != null) for (File child : files) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static final class UpdateManifest {
        final String version;
        final String downloadUrl;
        final String sha256;
        final long compressedBytes;
        final long extractedBytes;

        UpdateManifest(String version, String downloadUrl, String sha256, long compressedBytes, long extractedBytes) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.compressedBytes = compressedBytes;
            this.extractedBytes = extractedBytes;
        }

        void validate() throws IOException {
            if (version == null || version.trim().isEmpty()) throw new IOException("Manifesto sem versão");
            if (downloadUrl == null || !downloadUrl.startsWith("https://")) throw new IOException("URL de download insegura");
            if (sha256 == null || !sha256.matches("^[a-fA-F0-9]{64}$")) throw new IOException("SHA-256 inválido no manifesto");
            if (compressedBytes <= 0L) throw new IOException("Tamanho compactado inválido");
            if (extractedBytes <= 0L || extractedBytes > MAX_ALLOWED_EXTRACTED_BYTES) throw new IOException("Tamanho extraído inválido");
        }
    }
}
