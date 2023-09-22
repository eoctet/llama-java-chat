package chat.octet.utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Platform {
    public static final int UNSPECIFIED = -1;
    public static final int MAC = 0;
    public static final int LINUX = 1;
    public static final int WINDOWS = 2;
    public static final int SOLARIS = 3;
    public static final int FREEBSD = 4;
    public static final int OPENBSD = 5;
    public static final int WINDOWSCE = 6;
    public static final int AIX = 7;
    public static final int ANDROID = 8;
    public static final int GNU = 9;
    public static final int KFREEBSD = 10;
    public static final int NETBSD = 11;
    public static final String LIB_RESOURCE_PATH;
    private static final int osType;
    public static final String ARCH;

    private Platform() {
    }

    public static int getOSType() {
        return osType;
    }

    public static boolean isMac() {
        return osType == 0;
    }

    public static boolean isLinux() {
        return osType == 1;
    }

    public static boolean isWindows() {
        return osType == 2 || osType == 6;
    }

    public static String getLibraryResourceFilePath() {
        String name = getLibraryResourcePrefix(getOSType(), System.getProperty("os.arch"), System.getProperty("os.name"));
        if (isMac()) {
            return name + File.separator + "libllama.dylib";
        } else if (isLinux()) {
            return name + File.separator + "libllama.so";
        } else if (isWindows()) {
            return name + File.separator + "libllama.dll";
        } else {
            throw new RuntimeException("Unsupported operating system");
        }
    }

    private static String getCanonicalArchitecture(String arch, int platform) {
        arch = arch.toLowerCase().trim();
        if ("powerpc".equals(arch)) {
            arch = "ppc";
        } else if ("powerpc64".equals(arch)) {
            arch = "ppc64";
        } else if (!"i386".equals(arch) && !"i686".equals(arch)) {
            if (!"x86_64".equals(arch) && !"amd64".equals(arch)) {
                if ("zarch_64".equals(arch)) {
                    arch = "s390x";
                }
            } else {
                arch = "x86-64";
            }
        } else {
            arch = "x86";
        }
        if ("ppc64".equals(arch) && "little".equals(System.getProperty("sun.cpu.endian"))) {
            arch = "ppc64le";
        }
        if ("arm".equals(arch) && platform == 1) {
            arch = "armel";
        }
        return arch;
    }

    private static String getLibraryResourcePrefix(int osType, String arch, String name) {
        arch = getCanonicalArchitecture(arch, osType);
        String osPrefix;
        switch (osType) {
            case 0:
                osPrefix = "darwin-" + arch;
                break;
            case 1:
                osPrefix = "linux-" + arch;
                break;
            case 2:
                osPrefix = "win32-" + arch;
                break;
            case 3:
                osPrefix = "sunos-" + arch;
                break;
            case 4:
                osPrefix = "freebsd-" + arch;
                break;
            case 5:
                osPrefix = "openbsd-" + arch;
                break;
            case 6:
                osPrefix = "w32ce-" + arch;
                break;
            case 7:
            case 9:
            default:
                osPrefix = name.toLowerCase();
                int space = osPrefix.indexOf(" ");
                if (space != -1) {
                    osPrefix = osPrefix.substring(0, space);
                }

                osPrefix = osPrefix + "-" + arch;
                break;
            case 8:
                if (arch.startsWith("arm")) {
                    arch = "arm";
                }
                osPrefix = "android-" + arch;
                break;
            case 10:
                osPrefix = "kfreebsd-" + arch;
                break;
            case 11:
                osPrefix = "netbsd-" + arch;
                break;
        }
        return osPrefix;
    }

    static {
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Linux")) {
            if ("dalvik".equalsIgnoreCase(System.getProperty("java.vm.name"))) {
                osType = 8;
            } else {
                osType = 1;
            }
        } else if (osName.startsWith("AIX")) {
            osType = 7;
        } else if (!osName.startsWith("Mac") && !osName.startsWith("Darwin")) {
            if (osName.startsWith("Windows CE")) {
                osType = 6;
            } else if (osName.startsWith("Windows")) {
                osType = 2;
            } else if (!osName.startsWith("Solaris") && !osName.startsWith("SunOS")) {
                if (osName.startsWith("FreeBSD")) {
                    osType = 4;
                } else if (osName.startsWith("OpenBSD")) {
                    osType = 5;
                } else if (osName.equalsIgnoreCase("gnu")) {
                    osType = 9;
                } else if (osName.equalsIgnoreCase("gnu/kfreebsd")) {
                    osType = 10;
                } else if (osName.equalsIgnoreCase("netbsd")) {
                    osType = 11;
                } else {
                    osType = -1;
                }
            } else {
                osType = 3;
            }
        } else {
            osType = 0;
        }

        ARCH = getCanonicalArchitecture(System.getProperty("os.arch"), osType);
        Path path = Paths.get("");
        String projectPath = path.toAbsolutePath().toString();
        LIB_RESOURCE_PATH = projectPath + File.separator + "libs" + File.separator + getLibraryResourceFilePath();
    }
}
