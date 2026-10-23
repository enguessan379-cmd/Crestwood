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
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final String STATIC_DOWNLOAD_URL = "https://github.com/enguessan379-cmd/Crestwood/releases/download/Samp/files.zip";
    private static final String STATIC_VERSION = "static-github-data-lite-1";
    private static final int CONNECT_TIMEOUT_MS = 45_000;
    private static final int READ_TIMEOUT_MS = 180_000;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final long MAX_RETRY_DELAY_MS = 15_000L;
    private static final int MAX_HTTP_REDIRECTS = 5;
    // Telechargement parallele : N connexions HTTP Range en meme temps, par blocs de 4 Mo.
    private static final int PARALLEL_CONNECTIONS = 8;
    private static final long CHUNK_SIZE = 4L * 1024L * 1024L;
    private static final int MAX_CHUNK_ATTEMPTS = 6;
    private static final int COPY_BUFFER_BYTES = 128 * 1024;
    private static final long PROGRESS_INTERVAL_MS = 250L;
    private static final long MAX_ALLOWED_EXTRACTED_BYTES = 12_000_000_000L;
    private static final long STORAGE_MARGIN_BYTES = 96L * 1024L * 1024L;
    private static final String[] MANAGED_ROOTS = {
            "anim", "audio", "data", "models", "texdb", "SAMP",
            "CINFO.BIN", "GTASAMP10.b", "fonts", "gta_sa.set", "stream.ini"
    };
    // Structure texdb : chaque base de textures peut exister en 3 formats (dxt / etc / pvr) et le moteur
    // choisit UN format selon le GPU du telephone (voir TextureFormatProbe). On n'exige donc que le(s)
    // format(s) dont CE telephone a besoin : un pack "lite" qui n'a que le dxt est valide sur un GPU dxt,
    // mais pas sur un GPU Mali sans S3TC (qui veut de l'etc). Un format absent = crash natif dans
    // TextureDatabaseRuntime::LoadEPK au clic sur JOGAR, d'ou le message clair plutot qu'un crash.
    private static final String[] TEXDB_BASES = { "gta3", "gta_int", "mobile", "txd", "menu", "samp" };
    private static final String[] TEXDB_FORMATS = { "dxt", "etc", "pvr" };
    private static final String[] TEXDB_PARTS = { "dat", "tmb", "toc" };
    private static final String[] BASE_REQUIRED_FILES = buildBaseRequiredFiles();

    private static String[] buildBaseRequiredFiles() {
        java.util.List<String> files = new java.util.ArrayList<>(java.util.Arrays.asList(
                "anim/ped.ifp", "audio/STREAMS/AA.osw", "data/gta.dat", "data/default.dat",
                "SAMP/main.scm", "SAMP/script.img", "texdb/gta3.img", "texdb/player.img",
                "fonts/arial_bold.ttf"));
        // player / playerhi sont toujours charges en PVR par le moteur.
        for (String part : TEXDB_PARTS) {
            files.add("texdb/player/player.pvr." + part);
            files.add("texdb/playerhi/playerhi.pvr." + part);
        }
        return files.toArray(new String[0]);
    }

    /** Vue "existe / n'existe pas" d'une arborescence : dossier sur disque ou contenu d'un ZIP. */
    private interface FileIndex {
        boolean hasRoot(String name);
        boolean hasFile(String name);
    }

    private static FileIndex diskIndex(final File root) {
        return new FileIndex() {
            @Override public boolean hasRoot(String name) { return new File(root, name).exists(); }
            @Override public boolean hasFile(String name) {
                File file = new File(root, name);
                return file.isFile() && file.length() > 0L;
            }
        };
    }

    /** Liste le contenu du ZIP (apres retrait des dossiers wrapper) sans rien extraire. */
    private static FileIndex zipIndex(File zipFile) throws IOException {
        final java.util.Set<String> roots = new java.util.HashSet<>();
        final java.util.Map<String, Long> files = new java.util.HashMap<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            int stripDepth = detectStripDepth(zip);
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = stripWrapper(entry.getName(), stripDepth);
                if (name == null) continue;
                String clean = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
                if (clean.isEmpty()) continue;
                clean = clean.toLowerCase(java.util.Locale.ROOT);
                int slash = clean.indexOf('/');
                roots.add(slash < 0 ? clean : clean.substring(0, slash));
                if (!entry.isDirectory()) files.put(clean, entry.getSize());
            }
        }
        return new FileIndex() {
            @Override public boolean hasRoot(String name) { return roots.contains(name.toLowerCase(java.util.Locale.ROOT)); }
            @Override public boolean hasFile(String name) {
                Long size = files.get(name.toLowerCase(java.util.Locale.ROOT));
                return size != null && size.longValue() != 0L;
            }
        };
    }

    private static String texdbPath(String base, String format, String part) {
        return "texdb/" + base + "/" + base + "." + format + "." + part;
    }

    /**
     * Formats texdb requis pour CE telephone (ex. {"dxt"} sur Adreno, {"etc"} sur Mali sans S3TC),
     * ou null si le GPU n'a pas pu etre interroge (dans ce cas : au moins un format complet par base).
     */
    private static String[] neededTexdbFormats() {
        TextureFormatProbe.Result probe = TextureFormatProbe.get();
        return probe == null ? null : probe.formats;
    }

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
        return sharedRoot == null ? null : new File(sharedRoot, "data");
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
            if (!hasSharedStorageAccess(context)) throw new IOException("Autorize o acesso aos arquivos para instalar em /storage/emulated/0/data");
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
            long existingDownload = zipFile.isFile() ? zipFile.length() : resumableBytes(partialFile, manifest.compressedBytes);
            ensureDownloadAndExtractionSpace(target, existingDownload, manifest);

            if (zipFile.isFile() && zipFile.length() == manifest.compressedBytes && isValidZipArchive(zipFile)) {
                report(70, "Arquivo já baixado; verificando integridade", zipFile.length(), manifest.compressedBytes);
            } else {
                if (zipFile.isFile() && zipFile.length() == manifest.compressedBytes) {
                    Log.w(TAG, "Arquivo em cache tinha o tamanho esperado mas não é um ZIP válido; baixando novamente");
                }
                deleteRecursively(zipFile);
                report(0, "Baixando data do servidor", 0L, manifest.compressedBytes);
                downloadFast(zipFile, partialFile, manifest);
            }

            report(70, "Verificando integridade do arquivo", zipFile.length(), manifest.compressedBytes);
            if (!isValidZipArchive(zipFile)) {
                deleteRecursively(zipFile);
                deleteRecursively(partialFile);
                throw new IOException("O link de download não retornou um arquivo ZIP válido (provável expiração/bloqueio do link de origem). Tente novamente mais tarde.");
            }
            if (manifest.sha256 == null) {
                // Source statique sans SHA-256 de reference : inutile de relire tout le zip pour le hasher.
                // On controle la taille exacte + la validite du zip (deja faite ci-dessus).
                if (zipFile.length() != manifest.compressedBytes) {
                    deleteRecursively(zipFile);
                    deleteRecursively(partialFile);
                    throw new IOException("O arquivo baixado está incompleto. Tente novamente.");
                }
            } else if (!manifest.sha256.equalsIgnoreCase(sha256(zipFile))) {
                deleteRecursively(zipFile);
                deleteRecursively(partialFile);
                throw new IOException("O arquivo baixado não passou na verificação de integridade");
            }

            // Tamanho do zip baixado (confiável, já verificado: taille + zip valide) usado como referência
            // de exibição durante a extração — evita mostrar um total "extraído" incorreto.
            long displayTotalBytes = zipFile.length();

            // Confere o conteúdo do ZIP ANTES de extrair: se o pacote não serve para este celular,
            // avisa já (com a lista exata do que falta) em vez de extrair tudo para depois apagar.
            java.util.Map<String, java.util.Set<String>> zipMissingFormats = new java.util.LinkedHashMap<>();
            java.util.List<String> zipMissing = describeMissing(zipIndex(zipFile), zipMissingFormats);
            if (!zipMissing.isEmpty()) {
                Log.e(TAG, "Pacote incompleto para este celular. Faltam: " + String.join(", ", zipMissing));
                throw new IOException(buildMissingMessage(zipMissing, zipMissingFormats));
            }

            ensureExtractionSpace(target, manifest);
            deleteRecursively(staging);
            if (!staging.mkdirs()) throw new IOException("Não foi possível preparar a instalação");
            extractZip(zipFile, staging, displayTotalBytes);
            java.util.Map<String, java.util.Set<String>> stagingMissingFormats = new java.util.LinkedHashMap<>();
            java.util.List<String> stagingMissing = describeMissing(diskIndex(staging), stagingMissingFormats);
            if (!stagingMissing.isEmpty()) {
                Log.e(TAG, "Arquivos ausentes após extração. Conteúdo de " + staging + ": " + listRecursively(staging));
                throw new IOException(buildMissingMessage(stagingMissing, stagingMissingFormats));
            }

            deleteRecursively(target);
            if (!staging.renameTo(target)) {
                copyRecursively(staging, target);
                deleteRecursively(staging);
            }
            writeSmallFile(new File(target, ".gtasa_data_version"), markerValue);
            if (!isComplete(target)) {
                throw new IOException("Faltam após mover para o destino final: " + String.join(", ", describeMissing(diskIndex(target), null)));
            }
            deleteRecursively(zipFile);
            deleteRecursively(partialFile);
            deleteRecursively(chunkStateFile(partialFile));
            complete(target, displayTotalBytes);
        } catch (Throwable error) {
            deleteRecursively(staging);
            fail(error);
        }
    }

    private static final class RangeUnsupportedException extends IOException {
        RangeUnsupportedException(String message) { super(message); }
    }

    private static File chunkStateFile(File partialFile) {
        return new File(partialFile.getParentFile(), partialFile.getName() + ".chunks");
    }

    /** Lit l'etat de reprise : en-tete "total:chunkSize" puis un index de bloc termine par ligne. */
    private static java.util.Set<Integer> readCompletedChunks(File partialFile, long total) {
        java.util.Set<Integer> done = new java.util.HashSet<>();
        File state = chunkStateFile(partialFile);
        if (!partialFile.isFile() || !state.isFile()) return done;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new FileInputStream(state), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!(total + ":" + CHUNK_SIZE).equals(header)) return new java.util.HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) done.add(Integer.parseInt(line));
            }
        } catch (Exception error) {
            return new java.util.HashSet<>();
        }
        return done;
    }

    private static long chunkLength(int index, long total) {
        long start = index * CHUNK_SIZE;
        return Math.min(CHUNK_SIZE, total - start);
    }

    private static long resumableBytes(File partialFile, long total) {
        long bytes = 0L;
        for (int index : readCompletedChunks(partialFile, total)) bytes += Math.max(0L, chunkLength(index, total));
        return bytes;
    }

    /** Ouvre une connexion HTTP Range [start, end] en suivant les redirections depuis l'URL d'origine. */
    private static HttpURLConnection openRangeConnection(String urlString, long start, long end) throws IOException {
        URL current = new URL(urlString);
        for (int redirects = 0; redirects <= MAX_HTTP_REDIRECTS; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(60_000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "CrestwoodRoleplayLauncher/2.0");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
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

    /** Le "Content-Range: bytes a-b/total" et retourne total, ou -1. */
    private static long parseContentRangeTotal(HttpURLConnection connection) {
        String header = connection.getHeaderField("Content-Range");
        if (header == null) return -1L;
        int slash = header.lastIndexOf('/');
        if (slash < 0) return -1L;
        try {
            return Long.parseLong(header.substring(slash + 1).trim());
        } catch (NumberFormatException error) {
            return -1L;
        }
    }

    private static boolean serverSupportsRanges(String url, long total) {
        HttpURLConnection connection = null;
        try {
            connection = openRangeConnection(url, 0L, 0L);
            return connection.getResponseCode() == 206 && parseContentRangeTotal(connection) == total;
        } catch (IOException error) {
            Log.w(TAG, "Teste Range échoué: " + error.getMessage());
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Telechargement rapide : plusieurs connexions Range en parallele (reprise possible par bloc).
     * Si le serveur ne supporte pas Range, retombe sur PRDownloader (connexion unique).
     */
    private static void downloadFast(File zipFile, File partialFile, UpdateManifest manifest) throws IOException {
        File parent = zipFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Não foi possível preparar a pasta de download");
        }
        if (!serverSupportsRanges(manifest.downloadUrl, manifest.compressedBytes)) {
            Log.w(TAG, "Serveur sans support Range (ou taille différente) ; repli sur PRDownloader");
            deleteRecursively(partialFile);
            deleteRecursively(chunkStateFile(partialFile));
            downloadWithPRDownloader(zipFile, manifest);
            return;
        }
        try {
            downloadParallel(zipFile, partialFile, manifest);
        } catch (RangeUnsupportedException error) {
            Log.w(TAG, "Range refusé pendant le téléchargement ; repli sur PRDownloader", error);
            deleteRecursively(partialFile);
            deleteRecursively(chunkStateFile(partialFile));
            downloadWithPRDownloader(zipFile, manifest);
        }
    }

    private static void downloadParallel(File zipFile, File partialFile, UpdateManifest manifest) throws IOException {
        final long total = manifest.compressedBytes;
        final int chunkCount = (int) ((total + CHUNK_SIZE - 1L) / CHUNK_SIZE);
        final File stateFile = chunkStateFile(partialFile);

        java.util.Set<Integer> completed = readCompletedChunks(partialFile, total);
        if (completed.isEmpty() || !partialFile.isFile() || partialFile.length() != total) {
            // Pas de reprise possible : on repart de zero et on preallocue le fichier.
            completed = new java.util.HashSet<>();
            deleteRecursively(partialFile);
            deleteRecursively(stateFile);
            try (RandomAccessFile allocate = new RandomAccessFile(partialFile, "rw")) {
                allocate.setLength(total);
            }
            writeSmallFile(stateFile, total + ":" + CHUNK_SIZE + "\n");
        } else {
            Log.i(TAG, "Reprise du téléchargement : " + completed.size() + "/" + chunkCount + " blocs déjà présents");
        }

        final java.util.concurrent.ConcurrentLinkedQueue<Integer> queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        long alreadyDone = 0L;
        for (int index = 0; index < chunkCount; index++) {
            if (completed.contains(index)) alreadyDone += chunkLength(index, total);
            else queue.add(index);
        }
        if (queue.isEmpty()) {
            if (!partialFile.renameTo(zipFile)) throw new IOException("Não foi possível finalizar o download");
            deleteRecursively(stateFile);
            return;
        }

        final AtomicLong downloaded = new AtomicLong(alreadyDone);
        final AtomicBoolean abort = new AtomicBoolean(false);
        final AtomicInteger activeWorkers = new AtomicInteger(0);
        final IOException[] failure = new IOException[1];
        final Object stateLock = new Object();
        final int workers = Math.max(1, Math.min(PARALLEL_CONNECTIONS, queue.size()));
        final CountDownLatch finished = new CountDownLatch(workers);
        final String url = manifest.downloadUrl;

        report(downloadPercent(alreadyDone, total), "Baixando data do servidor", alreadyDone, total);

        for (int w = 0; w < workers; w++) {
            activeWorkers.incrementAndGet();
            Thread worker = new Thread(() -> {
                byte[] buffer = new byte[COPY_BUFFER_BYTES];
                try (RandomAccessFile output = new RandomAccessFile(partialFile, "rw")) {
                    Integer next;
                    while (!abort.get() && (next = queue.poll()) != null) {
                        int index = next;
                        long start = index * CHUNK_SIZE;
                        long end = start + chunkLength(index, total) - 1L;
                        downloadChunk(url, output, buffer, start, end, downloaded, abort, total);
                        synchronized (stateLock) {
                            try (FileOutputStream state = new FileOutputStream(stateFile, true)) {
                                state.write((index + "\n").getBytes(StandardCharsets.UTF_8));
                            }
                        }
                    }
                } catch (IOException error) {
                    synchronized (failure) { if (failure[0] == null) failure[0] = error; }
                    abort.set(true);
                } catch (Throwable error) {
                    synchronized (failure) { if (failure[0] == null) failure[0] = new IOException("Falha no download: " + error); }
                    abort.set(true);
                } finally {
                    activeWorkers.decrementAndGet();
                    finished.countDown();
                }
            }, "crestwood-dl-" + w);
            worker.setDaemon(true);
            worker.start();
        }

        try {
            finished.await();
        } catch (InterruptedException error) {
            abort.set(true);
            Thread.currentThread().interrupt();
            throw new IOException("Download interrompido", error);
        }
        if (failure[0] != null) throw failure[0]; // le .part + .chunks restent pour reprendre au prochain essai
        if (downloaded.get() < total) throw new IOException("Download incompleto");
        if (!partialFile.renameTo(zipFile)) {
            copyFile(partialFile, zipFile);
            deleteRecursively(partialFile);
        }
        deleteRecursively(stateFile);
        report(70, "Download concluído", total, total);
    }

    /** Telecharge [start, end] dans output avec retries + reprise dans le bloc. */
    private static void downloadChunk(String url, RandomAccessFile output, byte[] buffer, long start, long end,
                                      AtomicLong downloaded, AtomicBoolean abort, long total) throws IOException {
        long position = start;
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_CHUNK_ATTEMPTS && position <= end; attempt++) {
            if (abort.get()) throw new IOException("Download interrompido");
            HttpURLConnection connection = null;
            try {
                connection = openRangeConnection(url, position, end);
                int code = connection.getResponseCode();
                if (code == 200) throw new RangeUnsupportedException("Servidor ignorou o cabeçalho Range");
                if (code != 206) throw new IOException("Servidor respondeu HTTP " + code + " durante o download");
                output.seek(position);
                try (InputStream input = connection.getInputStream()) {
                    int read;
                    while (position <= end && !abort.get() && (read = input.read(buffer, 0, (int) Math.min(buffer.length, end - position + 1L))) != -1) {
                        output.write(buffer, 0, read);
                        position += read;
                        long now = downloaded.addAndGet(read);
                        report(downloadPercent(now, total), "Baixando data do servidor", now, total);
                    }
                }
                if (position <= end && !abort.get()) throw new IOException("Conexão encerrada antes do fim do bloco");
                lastError = null;
                break;
            } catch (RangeUnsupportedException error) {
                throw error;
            } catch (IOException error) {
                lastError = error;
                Log.w(TAG, "Bloc " + start + "-" + end + " tentative " + attempt + "/" + MAX_CHUNK_ATTEMPTS + " : " + error.getMessage());
                if (attempt < MAX_CHUNK_ATTEMPTS) {
                    try {
                        Thread.sleep(Math.min(MAX_RETRY_DELAY_MS, RETRY_DELAY_MS * attempt));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrompido", interrupted);
                    }
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        if (abort.get() && position <= end) throw lastError != null ? lastError : new IOException("Download interrompido");
        if (position <= end) throw lastError != null ? lastError : new IOException("Falha no download do bloco");
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

    private static int detectStripDepth(java.util.zip.ZipFile zip) {
        // Compte, pour chaque profondeur de dossier, combien d'entrées correspondent
        // à un des dossiers/fichiers attendus (MANAGED_ROOTS). Gère n'importe quel
        // nombre de dossiers wrapper (0, 1, 2...) autour de la vraie structure du jeu.
        java.util.Map<Integer, Integer> depthVotes = new java.util.HashMap<>();
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name == null || name.isEmpty() || name.startsWith("__MACOSX")) continue;
            String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
            if (normalized.isEmpty()) continue;
            String[] segments = normalized.split("/");
            for (int i = 0; i < segments.length; i++) {
                boolean hit = false;
                for (String managed : MANAGED_ROOTS) {
                    if (managed.equalsIgnoreCase(segments[i])) { hit = true; break; }
                }
                if (hit) {
                    depthVotes.merge(i, 1, Integer::sum);
                    break; // une seule correspondance comptée par entrée, la plus haute dans l'arbo
                }
            }
        }
        if (depthVotes.isEmpty()) return 0;
        int bestDepth = 0, bestVotes = -1;
        for (java.util.Map.Entry<Integer, Integer> e : depthVotes.entrySet()) {
            if (e.getValue() > bestVotes) { bestVotes = e.getValue(); bestDepth = e.getKey(); }
        }
        return bestDepth;
    }

    /**
     * Verifica rapidamente se o arquivo baixado é mesmo um ZIP (assinatura "PK") e se o
     * java.util.zip consegue abrir seu diretório central. Evita cair no meio da extração
     * com "zip END header not found" quando o servidor devolveu uma página HTML (link
     * expirado, bloqueio de anti-bot, redirecionamento inesperado, etc.) em vez do pacote.
     */
    private static boolean isValidZipArchive(File file) {
        if (file == null || !file.isFile() || file.length() < 4L) return false;
        try (InputStream input = new FileInputStream(file)) {
            byte[] signature = new byte[4];
            if (input.read(signature) != 4) return false;
            if (signature[0] != 'P' || signature[1] != 'K') return false;
        } catch (IOException error) {
            return false;
        }
        try (java.util.zip.ZipFile probe = new java.util.zip.ZipFile(file)) {
            return probe.size() > 0;
        } catch (Exception error) {
            Log.w(TAG, "Arquivo baixado não é um ZIP válido: " + error.getMessage());
            return false;
        }
    }

    /**
     * Retire les dossiers "wrapper" du debut du chemin ZIP. Retourne null si l'entree doit etre
     * ignoree (entree __MACOSX, ou dossier wrapper lui-meme).
     */
    private static String stripWrapper(String name, int stripDepth) {
        if (name == null || name.isEmpty() || name.startsWith("__MACOSX")) return null;
        if (stripDepth <= 0) return name;
        String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        String[] segments = normalized.isEmpty() ? new String[0] : normalized.split("/");
        if (segments.length <= stripDepth) return null; // entrée du/des dossier(s) wrapper eux-mêmes
        StringBuilder sb = new StringBuilder();
        for (int i = stripDepth; i < segments.length; i++) {
            if (sb.length() > 0) sb.append('/');
            sb.append(segments[i]);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void extractZip(File zipFile, File staging, long displayTotalBytes) throws IOException {
        long extracted = 0L;
        String rootPath = staging.getCanonicalPath() + File.separator;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            int totalEntries = Math.max(1, zip.size());
            int processedEntries = 0;
            int stripDepth = detectStripDepth(zip);
            if (stripDepth > 0) {
                Log.i(TAG, "Zip avec " + stripDepth + " niveau(x) de dossier(s) wrapper détecté(s); ignoré(s) à l'extraction");
            }
            byte[] buffer = new byte[128 * 1024];
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                processedEntries++;
                String name = stripWrapper(entry.getName(), stripDepth);
                if (name == null) continue;
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
        return describeMissing(diskIndex(root), null).isEmpty();
    }

    /**
     * Retourne la liste des dossiers/fichiers obligatoires absents (ou vides) dans l'arborescence.
     * Si missingFormats n'est pas null, y range aussi "format de texture -> bases qui n'en ont pas"
     * (pour un message lisible). Les fichiers texdb ne sont exiges que pour le(s) format(s) dont ce
     * telephone a besoin ; si le GPU est inconnu, il suffit d'un format complet par base.
     */
    private static java.util.List<String> describeMissing(FileIndex index, java.util.Map<String, java.util.Set<String>> missingFormats) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String name : MANAGED_ROOTS) if (!index.hasRoot(name)) missing.add(name + "/");
        for (String name : BASE_REQUIRED_FILES) if (!index.hasFile(name)) missing.add(name);

        String[] needed = neededTexdbFormats();
        for (String base : TEXDB_BASES) {
            if (needed != null) {
                for (String format : needed) {
                    boolean formatMissing = false;
                    for (String part : TEXDB_PARTS) {
                        String path = texdbPath(base, format, part);
                        if (!index.hasFile(path)) { missing.add(path); formatMissing = true; }
                    }
                    if (formatMissing) noteMissingFormat(missingFormats, format, base);
                }
            } else {
                boolean anyComplete = false;
                for (String format : TEXDB_FORMATS) {
                    boolean all = true;
                    for (String part : TEXDB_PARTS) if (!index.hasFile(texdbPath(base, format, part))) { all = false; break; }
                    if (all) { anyComplete = true; break; }
                }
                if (!anyComplete) {
                    missing.add("texdb/" + base + "/" + base + ".(dxt|etc|pvr).(dat|tmb|toc)");
                    noteMissingFormat(missingFormats, "dxt/etc/pvr", base);
                }
            }
        }
        return missing;
    }

    private static void noteMissingFormat(java.util.Map<String, java.util.Set<String>> missingFormats, String format, String base) {
        if (missingFormats == null) return;
        java.util.Set<String> bases = missingFormats.get(format);
        if (bases == null) {
            bases = new java.util.LinkedHashSet<>();
            missingFormats.put(format, bases);
        }
        bases.add(base);
    }

    /** Message court et lisible pour l'ecran "Instalação pausada" (la liste complete va dans logcat). */
    private static String buildMissingMessage(java.util.List<String> missing, java.util.Map<String, java.util.Set<String>> missingFormats) {
        StringBuilder message = new StringBuilder();
        java.util.Set<String> formatItems = new java.util.HashSet<>();
        if (missingFormats != null && !missingFormats.isEmpty()) {
            TextureFormatProbe.Result probe = TextureFormatProbe.get();
            message.append("O pacote de dados não tem as texturas que este celular usa");
            if (probe != null && !probe.renderer.isEmpty()) message.append(" (GPU ").append(probe.renderer).append(")");
            message.append(". Faltam texturas no formato ");
            boolean firstFormat = true;
            for (java.util.Map.Entry<String, java.util.Set<String>> entry : missingFormats.entrySet()) {
                if (!firstFormat) message.append("; ");
                firstFormat = false;
                message.append(entry.getKey().toUpperCase(java.util.Locale.ROOT)).append(" em ")
                        .append(String.join(", ", entry.getValue()));
            }
            message.append(". Hospede um pacote completo com os arquivos texdb/<base>/<base>.<formato>.dat, .tmb e .toc.");
            for (String format : TEXDB_FORMATS) {
                for (String base : TEXDB_BASES) for (String part : TEXDB_PARTS) formatItems.add(texdbPath(base, format, part));
            }
        }
        java.util.List<String> others = new java.util.ArrayList<>();
        for (String item : missing) {
            if (formatItems.contains(item) || item.contains("(dxt|etc|pvr)")) continue;
            others.add(item);
        }
        if (!others.isEmpty()) {
            if (message.length() > 0) message.append(' ');
            message.append("Faltam no pacote: ");
            int shown = Math.min(others.size(), 8);
            message.append(String.join(", ", others.subList(0, shown)));
            if (others.size() > shown) message.append(" … (+").append(others.size() - shown).append(")");
        }
        if (message.length() == 0) message.append("Faltam arquivos obrigatórios no pacote de dados.");
        return message.toString();
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

    private static final Object REPORT_LOCK = new Object();
    private static long lastReportAtMs;
    private static String lastReportPhase = "";

    /** Progression limitee a ~4 fois/s (sauf changement de phase / 100%) pour ne pas inonder l'UI (WebView). */
    private static void report(int percent, String phase, long done, long total) {
        Listener current = listener;
        if (current == null) return;
        int clamped = Math.max(0, Math.min(100, percent));
        synchronized (REPORT_LOCK) {
            long now = System.currentTimeMillis();
            boolean phaseChanged = phase == null ? lastReportPhase != null : !phase.equals(lastReportPhase);
            boolean isFinal = clamped >= 100 || (total > 0L && done >= total);
            if (!phaseChanged && !isFinal && now - lastReportAtMs < PROGRESS_INTERVAL_MS) return;
            lastReportAtMs = now;
            lastReportPhase = phase;
            current.onProgress(clamped, phase, done, total);
        }
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
