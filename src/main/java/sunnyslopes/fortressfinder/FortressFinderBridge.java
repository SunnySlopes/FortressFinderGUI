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
 * JNI bridge for fortress search (cubiomes).
 */
public final class FortressFinderBridge {

    private static boolean loaded = false;

    /** cubiomes MCVersion enum values (1.18+) */
    public static final int MC_1_18 = 22;
    public static final int MC_1_19 = 24;
    public static final int MC_1_20 = 25;
    public static final int MC_1_21 = 27;

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
    /** ints per hit in span mode results */
    public static final int SPAN_STRIDE = 7;

    private static void loadNativeLibrary() {
        if (loaded) return;
        try {
            System.loadLibrary("fortressFinderLibJ");
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError e) {
            UnsatisfiedLinkError firstError = e;
            if (tryLoadFromBundledResources()) {
                loaded = true;
                return;
            }
            Path base = Paths.get(System.getProperty("user.dir", "."));
            List<String> libNames = getNativeLibNamesForCurrentOs();
            Path[] candidates = {
                base.resolve("native"),
                base.resolve("build").resolve("native"),
                base.resolve("build"),
            };
            for (Path dir : candidates) {
                for (String libName : libNames) {
                    File f = dir.resolve(libName).toFile();
                    if (f.exists()) {
                        System.load(f.getAbsolutePath());
                        loaded = true;
                        return;
                    }
                }
            }
            throw new UnsatisfiedLinkError(
                "fortressFinderLibJ native library not found. Build with: gradle buildNative. "
                    + firstError.getMessage());
        }
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
        } else {
            names.add("libfortressFinderLibJ.so");
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
