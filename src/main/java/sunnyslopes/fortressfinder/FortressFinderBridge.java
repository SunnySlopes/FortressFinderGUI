package sunnyslopes.fortressfinder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * JNI bridge for fortress search.
 *
 * <p>All C++/cubiomes computation lives in the {@code FortressFinderBase}
 * submodule (Gradle project {@code :fortressfinderbase}). This class is the
 * Java side of the native contract:
 * <ul>
 *   <li>{@link #fortressSearch} returns 6 ints per hit in cross mode
 *       ({@code chunkX, chunkZ, x, y, z, shapeCode}) and 8 ints per hit in
 *       span mode ({@code chunkX, chunkZ, x, y, z, spanX, spanY, spanZ});</li>
 *   <li>{@link #getSearchProgress} returns {@code [current, total, phase1, status]};</li>
 *   <li>{@link #getNowResult} uses the same flat layout as {@link #fortressSearch}.</li>
 * </ul>
 * The native header checked into
 * {@code FortressFinderBase/jni/generated/sunnyslopes_fortressfinder_FortressFinderBridge.h}
 * must match this file; the main module's {@code verifyJniContract} task
 * enforces that.
 */
public final class FortressFinderBridge {

    private static boolean loaded = false;

    /**
     * cubiomes MCVersion enum values (xpple biomes.h).
     *
     * <p>The GUI exposes two generation groups: 1.16~1.17 searches with
     * {@link #MC_1_17}, and 1.18~26.3+ searches with {@link #MC_26_3}.
     */
    public static final int MC_1_16 = 20;
    public static final int MC_1_17 = 21;
    public static final int MC_1_18 = 22;
    public static final int MC_1_19 = 24;
    public static final int MC_1_20 = 25;
    public static final int MC_1_21 = 26;
    public static final int MC_26_1 = 33;
    public static final int MC_26_2 = 34;
    /** 1.18 ~ 26.3+ share one fortress generation; this is the group's map. */
    public static final int MC_26_3 = 35;

    static {
        loadNativeLibrary();
    }

    private FortressFinderBridge() {}

    /** cross mode: double/triple/quad */
    public static final int MODE_CROSS = 0;
    /** span mode: long x short edge filter */
    public static final int MODE_SPAN = 1;

    public static final int CROSS_FILTER_ALL = 2;
    public static final int CROSS_FILTER_TRIPLE_QUAD = 3;
    public static final int CROSS_FILTER_QUAD_ONLY = 4;

    public static final int SHAPE_DOUBLE = 1;
    public static final int SHAPE_TRIPLE = 2;
    public static final int SHAPE_QUAD = 3;

    /** ints per hit in cross mode results */
    public static final int CROSS_STRIDE = 6;
    /** ints per hit in span mode: chunkX,chunkZ,outX,outY,outZ,spanX,spanY,spanZ */
    public static final int SPAN_STRIDE = 8;

    private static void loadNativeLibrary() {
        if (loaded) return;
        UnsatisfiedLinkError firstError = null;
        // Prefer freshly built libs on disk over stale JAR/classpath resources.
        if (tryLoadFromProjectNativeDirs()) {
            loaded = true;
            return;
        }
        try {
            System.loadLibrary("fortressFinderLibJ");
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError e) {
            firstError = e;
        }
        if (tryLoadFromBundledResources()) {
            loaded = true;
            return;
        }
        throw new UnsatisfiedLinkError(
            "fortressFinderLibJ native library not found. Build with: gradlew buildNative "
                + "(which delegates to :fortressfinderbase:copyNativeArtifacts). "
                + (firstError != null ? firstError.getMessage() : ""));
    }

    private static boolean tryLoadFromProjectNativeDirs() {
        Path base = Paths.get(System.getProperty("user.dir", "."));
        List<String> libNames = getNativeLibNamesForCurrentOs();
        Path[] candidates = {
            base.resolve("native"),
            base.resolve("build").resolve("native"),
            // FortressFinderBase submodule build output.
            base.resolve("FortressFinderBase").resolve("build").resolve("native"),
            base.resolve("build").resolve("generated").resolve("native-resources")
                .resolve("native").resolve(getOsResourceFolder() != null ? getOsResourceFolder() : "windows"),
            base.resolve("build"),
        };
        for (Path dir : candidates) {
            for (String libName : libNames) {
                File f = dir.resolve(libName).toFile();
                if (f.isFile()) {
                    try {
                        System.load(f.getAbsolutePath());
                        return true;
                    } catch (UnsatisfiedLinkError ignored) {
                        // try next candidate
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryLoadFromBundledResources() {
        String osFolder = getOsResourceFolder();
        if (osFolder == null) return false;
        for (String libName : getNativeLibNamesForCurrentOs()) {
            String resourcePath = "/native/" + osFolder + "/" + libName;
            if (tryLoadSingleBundledResource(resourcePath, libName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryLoadSingleBundledResource(String resourcePath, String libName) {
        try (InputStream is = FortressFinderBridge.class.getResourceAsStream(resourcePath)) {
            if (is == null) return false;
            Path tempDir = Files.createTempDirectory("fortressfinder-native-");
            Path tempLib = tempDir.resolve(libName);
            Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
            tempLib.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            System.load(tempLib.toAbsolutePath().toString());
            return true;
        } catch (IOException | UnsatisfiedLinkError ex) {
            return false;
        }
    }

    private static String getOsResourceFolder() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        if (os.contains("nix") || os.contains("nux") || os.contains("aix") || os.contains("linux")) return "linux";
        return null;
    }

    private static List<String> getNativeLibNamesForCurrentOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> names = new ArrayList<>();
        if (os.contains("win")) {
            names.add("fortressFinderLibJ.dll");
            names.add("libfortressFinderLibJ.dll");
        } else if (os.contains("mac")) {
            names.add("libfortressFinderLibJ.dylib");
            names.add("fortressFinderLibJ.dylib");
        } else {
            names.add("libfortressFinderLibJ.so");
            names.add("fortressFinderLibJ.so");
        }
        return names;
    }

    public static native int[] fortressSearch(
            int mode,
            long seed,
            int centerX,
            int centerZ,
            int r,
            int mc,
            int crossFilter,
            int minLong,
            int minShort,
            int minHeight,
            int numThreads);

    /** @return [current, total, phase1, status] or null */
    public static native int[] getSearchProgress();

    public static native int[] getNowResult();

    public static native boolean pause();

    public static native boolean resume();

    public static native boolean stop();

    /** Clears native pause/stop flags before a new search. */
    public static native void resetSearchState();
}
