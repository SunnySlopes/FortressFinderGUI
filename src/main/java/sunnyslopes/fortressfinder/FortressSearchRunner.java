package sunnyslopes.fortressfinder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Fortress search via JNI ({@link FortressFinderBridge}).
 */
public class FortressSearchRunner {

    public static final long MAX_SEARCH_AREA_BLOCKS = 30_000_000L * 30_000_000L;

    private static final long PROGRESS_POLL_MS = 100L;

    public record FortressSearchParams(
            int mode,
            long seed,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int mc,
            int crossFilter,
            int minLong,
            int minShort,
            int threadCount) {
    }

    public record ProgressInfo(
            long processed,
            long total,
            double percentage,
            long elapsedMs,
            long remainingMs,
            int currentSeedIndex,
            int totalSeeds,
            boolean done,
            boolean nativeStopRequested) {

        public static ProgressInfo of(long processed, long total, long elapsedMs, long remainingMs) {
            return of(processed, total, elapsedMs, remainingMs, false, false);
        }

        public static ProgressInfo of(
                long processed,
                long total,
                long elapsedMs,
                long remainingMs,
                boolean done,
                boolean nativeStopRequested) {
            double pct = total > 0 ? (processed * 100.0 / total) : 0;
            return new ProgressInfo(
                    processed,
                    total,
                    pct,
                    elapsedMs,
                    remainingMs,
                    0,
                    0,
                    done,
                    nativeStopRequested);
        }

        public static ProgressInfo terminal(long elapsedMs, long processed, long total) {
            long t = Math.max(1, total);
            long p = Math.min(processed, t);
            double pct = t > 0 ? (p * 100.0 / t) : 100.0;
            return new ProgressInfo(p, t, pct, elapsedMs, 0, 0, 0, true, false);
        }
    }

    public static int[] rectToCenterAndRadius(int minX, int maxX, int minZ, int maxZ) {
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int r = Math.max(
                Math.max(centerX - minX, maxX - centerX),
                Math.max(centerZ - minZ, maxZ - centerZ));
        return new int[] { centerX, centerZ, r };
    }

    public static String formatResults(int[] raw, int mode) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        int stride = mode == FortressFinderBridge.MODE_SPAN
                ? FortressFinderBridge.SPAN_STRIDE
                : FortressFinderBridge.CROSS_STRIDE;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + stride <= raw.length; i += stride) {
            if (mode == FortressFinderBridge.MODE_SPAN) {
                int outX = raw[i + 2];
                int outY = raw[i + 3];
                int outZ = raw[i + 4];
                int longEdge = raw[i + 5];
                int shortEdge = raw[i + 6];
                sb.append(String.format("/tp %d %d %d %d*%d", outX, outY, outZ, longEdge, shortEdge))
                        .append('\n');
            } else {
                int outX = raw[i + 2];
                int outY = raw[i + 3];
                int outZ = raw[i + 4];
                int shapeCode = raw[i + 5];
                int n = shapeCode == FortressFinderBridge.SHAPE_QUAD ? 4
                        : shapeCode == FortressFinderBridge.SHAPE_TRIPLE ? 3 : 2;
                sb.append(String.format("/tp %d %d %d %dCrossings", outX, outY, outZ, n))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile long runStartTimeMs = 0;
    private volatile long totalPausedMs = 0;
    private volatile long pauseStartMs = 0;

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            pauseStartMs = System.currentTimeMillis();
            try {
                FortressFinderBridge.pause();
            } catch (Throwable ignored) {
            }
        }
    }

    public void resume() {
        if (isPaused) {
            if (pauseStartMs > 0) {
                totalPausedMs += System.currentTimeMillis() - pauseStartMs;
                pauseStartMs = 0;
            }
            isPaused = false;
            try {
                FortressFinderBridge.resume();
            } catch (Throwable ignored) {
            }
        }
    }

    public void stop() {
        try {
            FortressFinderBridge.stop();
        } catch (Throwable ignored) {
        }
    }

    private long getElapsedMs() {
        long base = System.currentTimeMillis() - runStartTimeMs;
        long paused = totalPausedMs;
        if (pauseStartMs > 0) {
            paused += System.currentTimeMillis() - pauseStartMs;
        }
        return Math.max(0, base - paused);
    }

    public void startFortressSearch(
            FortressSearchParams params,
            Consumer<ProgressInfo> progressCallback,
            Consumer<String> resultCallback) {
        if (isRunning) return;
        Thread t = new Thread(
                () -> runFortressSearch(params, progressCallback, resultCallback),
                "fortressfinder-search");
        t.setDaemon(true);
        t.start();
    }

    public void runFortressSearchBlocking(
            FortressSearchParams params,
            Consumer<ProgressInfo> progressCallback,
            Consumer<String> resultCallback) {
        if (isRunning) return;
        runFortressSearch(params, progressCallback, resultCallback);
    }

    private void runFortressSearch(
            FortressSearchParams params,
            Consumer<ProgressInfo> progressCallback,
            Consumer<String> resultCallback) {
        isRunning = true;
        isPaused = false;
        totalPausedMs = 0;
        pauseStartMs = 0;
        runStartTimeMs = System.currentTimeMillis();
        try {
            FortressFinderBridge.resetSearchState();
        } catch (Throwable ignored) {
        }
        int threads = Math.max(1, Math.min(params.threadCount(), Runtime.getRuntime().availableProcessors()));

        long rectArea = (long) (params.maxX() - params.minX() + 1) * (params.maxZ() - params.minZ() + 1);
        if (rectArea <= 0 || rectArea > MAX_SEARCH_AREA_BLOCKS) {
            isRunning = false;
            if (resultCallback != null && rectArea > MAX_SEARCH_AREA_BLOCKS) {
                resultCallback.accept("[Error] Search area too large (" + rectArea + " blocks).");
            }
            if (progressCallback != null) {
                progressCallback.accept(ProgressInfo.terminal(getElapsedMs(), 0, 1));
            }
            return;
        }

        int[] centerR = rectToCenterAndRadius(params.minX(), params.maxX(), params.minZ(), params.maxZ());
        int centerX = centerR[0];
        int centerZ = centerR[1];
        int r = centerR[2];
        long searchArea = (long) (2L * r + 1) * (2L * r + 1);
        if (searchArea > MAX_SEARCH_AREA_BLOCKS) {
            isRunning = false;
            if (resultCallback != null) {
                resultCallback.accept("[Error] Search area too large after center/r conversion.");
            }
            if (progressCallback != null) {
                progressCallback.accept(ProgressInfo.terminal(getElapsedMs(), 0, 1));
            }
            return;
        }

        int minShort = params.minShort() <= 0 ? 0 : params.minShort();

        AtomicLong lastProcessed = new AtomicLong(0);
        AtomicLong lastTotal = new AtomicLong(1);

        ScheduledExecutorService progressScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread t = new Thread(task, "fortressfinder-progress");
            t.setDaemon(true);
            return t;
        });

        if (progressCallback != null) {
            progressCallback.accept(ProgressInfo.of(0, 1, getElapsedMs(), 0));
        }

        progressScheduler.scheduleAtFixedRate(() -> {
            if (progressCallback == null || !isRunning) {
                return;
            }
            try {
                int[] arr = FortressFinderBridge.getSearchProgress();
                if (arr == null || arr.length < 4) {
                    return;
                }
                int cur = arr[0];
                int tot = arr[1];
                long processed = cur;
                long total = Math.max(tot, 1L);
                lastProcessed.set(processed);
                lastTotal.set(total);

                long elapsed = getElapsedMs();
                long remaining = 0L;
                if (!isPaused && total > processed && processed > 0) {
                    remaining = elapsed * (total - processed) / processed;
                }
                progressCallback.accept(
                        ProgressInfo.of(processed, total, elapsed, remaining, false, false));
            } catch (Throwable ignored) {
            }
        }, 0, PROGRESS_POLL_MS, TimeUnit.MILLISECONDS);

        try {
            int[] raw = FortressFinderBridge.fortressSearch(
                    params.mode(),
                    params.seed(),
                    centerX,
                    centerZ,
                    r,
                    params.mc(),
                    params.crossFilter(),
                    params.minLong(),
                    minShort,
                    threads);

            if (raw != null && resultCallback != null) {
                String formatted = formatResults(raw, params.mode());
                if (!formatted.isEmpty()) {
                    resultCallback.accept(formatted);
                }
            }
        } catch (UnsatisfiedLinkError e) {
            if (resultCallback != null) {
                resultCallback.accept(
                        "[Error] Native library not loaded. Build with: gradle buildNative. " + e.getMessage());
            }
        } finally {
            progressScheduler.shutdown();
            try {
                if (!progressScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    progressScheduler.shutdownNow();
                    progressScheduler.awaitTermination(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                progressScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            isRunning = false;
            if (pauseStartMs > 0) {
                totalPausedMs += System.currentTimeMillis() - pauseStartMs;
                pauseStartMs = 0;
            }
            isPaused = false;
            if (progressCallback != null) {
                long p = lastProcessed.get();
                long t = lastTotal.get();
                if (t <= 0) {
                    t = 1;
                }
                if (p < t) {
                    p = t;
                }
                progressCallback.accept(ProgressInfo.terminal(getElapsedMs(), p, t));
            }
        }
    }
}
