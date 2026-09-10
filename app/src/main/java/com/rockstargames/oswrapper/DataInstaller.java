package com.rockstargames.oswrapper;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.downloader.Progress;

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
    private static final boolean USE_STATIC_ARCHIVE_SOURCE = true;
    private static final String STATIC_DOWNLOAD_URL = "https://archive.org/download/Crestwood/Crestwood.zip";
    private static final String STATIC_VERSION = "static-archive-crestwood-1";
    private static final int CONNECT_TIMEOUT_MS = 45_000;
    private static final int READ_TIMEOUT_MS = 180_000;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final long MAX_RETRY_DELAY_MS = 15_000L;
    private static final int MAX_HTTP_REDIRECTS = 5;
    private static final long MAX_ALLOWED_EXTRACTED_BYTES = 12_000_000_000L;
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
    private static volatile boolean prDownloaderInitialized;

    private DataInstaller() { }

    private static void ensurePRDownloaderInitialized(Context context) {
        if (prDownloaderInitialized) return;
        synchronized (DataInstaller.class) {
            if (prDownloaderInitialized) return;
            PRDownloader.initialize(context.getApplicationContext(),
                    PRDownloaderConfig.newBuilder()
                            .setDatabaseEnabled(true)
                            .setConnectTimeout(CONNECT_TIMEOUT_MS)
                            .setReadTimeout(READ_TIMEOUT_MS)
                            .build());
            prDownloaderInitialized = true;
        }
    }

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
        ensurePRDownloaderInitialized(context);
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

            // Se a pasta GTA já contém todos os arquivos obrigatórios (copiada manualmente,
            // instalação anterior, outro launcher, etc.), não baixa nada de novo — só grava
            // o marcador para acelerar a próxima verificação.
            if (isComplete(target)) {
                Log.i(TAG, "Data já presente em " + target + "; pulando download");
                cleanupLegacyPrivateData(context, target);
                if (!marker.isFile() || !markerValue.equals(readSmallFile(marker))) {
                    writeSmallFile(marker, markerValue);
                }
                complete(target, manifest.extractedBytes);
                return;
            }

            report(0, "Liberando espaço para a instalação", 0L, manifest.compressedBytes);
            cleanupObsoleteInstallFiles(context, target, staging);
            long existingDownload = zipFile.isFile() ? zipFile.length() : (partialFile.isFile() ? partialFile.length() : 0L);
            ensureDownloadAndExtractionSpace(target, existingDownload, manifest);

            if (zipFile.isFile() && zipFile.length() == manifest.compressedBytes) {
                report(70, "Arquivo já baixado; verificando integridade", zipFile.length(), manifest.compressedBytes);
            } else {
                deleteRecursively(zipFile);
                deleteRecursively(partialFile);
                report(0, "Baixando data do servidor", 0L, manifest.compressedBytes);
                downloadWithPRDownloader(zipFile, manifest);
            }

            report(70, "Verificando integridade do arquivo", zipFile.length(), manifest.compressedBytes);
            String actualHash = sha256(zipFile);
            if (manifest.sha256 == null) {
                Log.i(TAG, "Pacote sem SHA-256 de referência (fonte estática); hash calculado: " + actualHash);
            } else if (!manifest.sha256.equalsIgnoreCase(actualHash)) {
                deleteRecursively(zipFile);
                throw new IOException("O arquivo baixado não passou na verificação de integridade");
            }

            // Tamanho do zip baixado (confiável, já verificado por SHA-256) usado como referência
            // de exibição durante a extração — evita mostrar um total "extraído" incorreto.
            long displayTotalBytes = zipFile.length();

            ensureExtractionSpace(target, manifest);
            deleteRecursively(staging);
            if (!staging.mkdirs()) throw new IOException("Não foi possível preparar a instalação");
            extractZip(zipFile, staging, displayTotalBytes);
            if (!isComplete(staging)) {
                Log.e(TAG, "Arquivos ausentes após extração. Conteúdo de " + staging + ": " + listRecursively(staging));
                throw new IOException("A extração terminou sem os arquivos obrigatórios do jogo");
            }

            deleteRecursively(target);
            if (!staging.renameTo(target)) {
                copyRecursively(staging, target);
                deleteRecursively(staging);
            }
            writeSmallFile(new File(target, ".gtasa_data_version"), markerValue);
            if (!isComplete(target)) throw new IOException("A instalação final não contém todos os arquivos necessários");
            deleteRecursively(zipFile);
            deleteRecursively(partialFile);
            complete(target, displayTotalBytes);
        } catch (Throwable error) {
            deleteRecursively(staging);
            fail(error);
        }
    }

    private static final int MAX_DOWNLOAD_ATTEMPTS = 5;

    /**
     * Baixa o arquivo usando PRDownloader (biblioteca já testada, com gerenciamento de rede
     * mais robusto que a implementação manual anterior). Roda de forma síncrona neste thread
     * de instalação via CountDownLatch, com algumas tentativas automáticas em caso de falha.
     */
    private static void downloadWithPRDownloader(File targetFile, UpdateManifest manifest) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Não foi possível preparar a pasta de download");
        }
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            final CountDownLatch latch = new CountDownLatch(1);
            final IOException[] failure = new IOException[1];
            final int downloadId = PRDownloader
                    .download(manifest.downloadUrl, parent.getAbsolutePath(), targetFile.getName())
                    .build()
                    .setOnProgressListener(progress -> {
                        long total = Math.max(progress.totalBytes, manifest.compressedBytes);
                        report(downloadPercent(progress.currentBytes, total), "Baixando data do servidor",
                                progress.currentBytes, total);
                    })
                    .start(new OnDownloadListener() {
                        @Override
                        public void onDownloadComplete() {
                            latch.countDown();
                        }

                        @Override
                        public void onError(Error error) {
                            String detail = error != null && error.getServerErrorMessage() != null
                                    ? error.getServerErrorMessage() : "erro de rede desconhecido";
                            failure[0] = new IOException("Falha no download: " + detail);
                            latch.countDown();
                        }
                    });
            boolean finished;
            try {
                finished = latch.await(READ_TIMEOUT_MS * 4L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrompido", error);
            }
            if (!finished) {
                PRDownloader.cancel(downloadId);
                lastError = new IOException("Download expirou (tempo limite excedido)");
            } else if (failure[0] != null) {
                lastError = failure[0];
            } else if (!targetFile.isFile() || targetFile.length() <= 0L) {
                lastError = new IOException("Download concluído mas o arquivo está vazio");
            } else {
                report(70, "Download concluído", targetFile.length(), manifest.compressedBytes);
                return;
            }
            Log.w(TAG, "Tentativa " + attempt + "/" + MAX_DOWNLOAD_ATTEMPTS + " de download falhou: " + lastError.getMessage());
            if (attempt >= MAX_DOWNLOAD_ATTEMPTS) break;
            report(downloadPercent(0, manifest.compressedBytes),
                    "Conexão interrompida; tentando novamente (" + attempt + "/" + MAX_DOWNLOAD_ATTEMPTS + ")", 0L, manifest.compressedBytes);
            try {
                Thread.sleep(Math.min(MAX_RETRY_DELAY_MS, RETRY_DELAY_MS * attempt));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrompido", error);
            }
        }
        throw lastError != null ? lastError : new IOException("Falha no download após várias tentativas");
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

    private static String detectCommonRootPrefix(java.util.zip.ZipFile zip) {
        String commonRoot = null;
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name == null || name.isEmpty() || name.startsWith("__MACOSX")) continue;
            int slash = name.indexOf('/');
            if (slash <= 0) return null; // entrada solta na raiz: não há wrapper único
            String top = name.substring(0, slash);
            for (String managed : MANAGED_ROOTS) {
                if (managed.equalsIgnoreCase(top)) return null; // já é a estrutura esperada
            }
            if (commonRoot == null) {
                commonRoot = top;
            } else if (!commonRoot.equals(top)) {
                return null; // múltiplas pastas na raiz: não há wrapper único
            }
        }
        return commonRoot;
    }

    private static void extractZip(File zipFile, File staging, long displayTotalBytes) throws IOException {
        long extracted = 0L;
        String rootPath = staging.getCanonicalPath() + File.separator;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            int totalEntries = Math.max(1, zip.size());
            int processedEntries = 0;
            String stripPrefix = detectCommonRootPrefix(zip);
            if (stripPrefix != null) {
                Log.i(TAG, "Zip com pasta raiz única '" + stripPrefix + "'; será ignorada na extração");
                stripPrefix = stripPrefix + "/";
            }
            byte[] buffer = new byte[128 * 1024];
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                processedEntries++;
                String name = entry.getName();
                if (name == null || name.isEmpty() || name.startsWith("__MACOSX")) continue;
                if (stripPrefix != null) {
                    if (name.equals(stripPrefix.substring(0, stripPrefix.length() - 1))) continue;
                    if (!name.startsWith(stripPrefix)) continue;
                    name = name.substring(stripPrefix.length());
                    if (name.isEmpty()) continue;
                }
                File output = safeChild(staging, rootPath, name);
                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) throw new IOException("Não foi possível criar a pasta da data");
                    continue;
                }
                File parent = output.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Não foi possível criar a pasta da data");
                try (InputStream in = new BufferedInputStream(zip.getInputStream(entry), 128 * 1024);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(output), 128 * 1024)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        extracted += read;
                        if (extracted > MAX_ALLOWED_EXTRACTED_BYTES) throw new IOException("A data excede o limite de segurança");
                        out.write(buffer, 0, read);
                    }
                }
                // Progresso baseado na contagem de entradas do zip (confiável), não no tamanho
                // "extractedBytes" declarado no zip (pode estar incorreto/desatualizado no pacote).
                int percent = 71 + (int) Math.min(28L, processedEntries * 28L / totalEntries);
                report(percent, "Instalando arquivos do jogo", Math.min(extracted, displayTotalBytes), displayTotalBytes);
            }
        }
        if (extracted <= 0L) throw new IOException("O pacote não contém arquivos para extrair");
        report(99, "Arquivos extraídos; finalizando instalação", displayTotalBytes, displayTotalBytes);
    }

    /** Lista o conteúdo de um diretório (usado só para diagnóstico em log quando a extração falha). */
    private static String listRecursively(File dir) {
        StringBuilder result = new StringBuilder();
        listRecursively(dir, dir.getPath().length() + 1, result);
        return result.length() == 0 ? "(vazio)" : result.toString();
    }

    private static void listRecursively(File dir, int prefixLength, StringBuilder result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String relative = file.getPath().length() >= prefixLength ? file.getPath().substring(prefixLength) : file.getName();
            result.append(relative).append(file.isDirectory() ? "/" : "").append(" ");
            if (file.isDirectory()) listRecursively(file, prefixLength, result);
        }
    }

    private static UpdateManifest fetchManifest() throws Exception {
        if (USE_STATIC_ARCHIVE_SOURCE) {
            HttpURLConnection connection = null;
            try {
                connection = openDownloadConnection(STATIC_DOWNLOAD_URL, 0L);
                int response = connection.getResponseCode();
                if (response != HttpURLConnection.HTTP_OK) throw new IOException("Servidor respondeu HTTP " + response + " ao verificar o pacote");
                long size = connection.getContentLengthLong();
                if (size <= 0L) throw new IOException("Não foi possível determinar o tamanho do pacote de dados");
                UpdateManifest manifest = new UpdateManifest(STATIC_VERSION, STATIC_DOWNLOAD_URL, null, size, MAX_ALLOWED_EXTRACTED_BYTES);
                manifest.validate();
                return manifest;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
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

    private static void complete(File target, long extractedBytesTotal) {
        success = true;
        installing = false;
        report(100, "Data instalada", extractedBytesTotal, extractedBytesTotal);
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
            if (sha256 != null && !sha256.matches("^[a-fA-F0-9]{64}$")) throw new IOException("SHA-256 inválido no manifesto");
            if (compressedBytes <= 0L) throw new IOException("Tamanho compactado inválido");
            if (extractedBytes <= 0L || extractedBytes > MAX_ALLOWED_EXTRACTED_BYTES) throw new IOException("Tamanho extraído inválido");
        }
    }
}
