package co.rsk.util;

import co.rsk.RskContext;
import co.rsk.config.NodeCliFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Created by Nazaret García on 21/01/2021
 */

public class PreflightChecksUtils {
    private static final Logger logger = LoggerFactory.getLogger(PreflightChecksUtils.class);

    private static final String[] SUPPORTED_JAVA_VERSIONS = {"1.8", "11"};

    private static void checkSupportedJavaVersion() {
        String javaVersion = SystemUtils.getPropertyValue("java.version");

        if (javaVersion == null) {
            throw new RuntimeException("Unable to detect Java version");
        }

        if (Arrays.stream(SUPPORTED_JAVA_VERSIONS).noneMatch(v -> isCompatibleVersion(javaVersion, v))) {
            logger.error("Invalid Java version detected");
            throw new RuntimeException(String.format("Invalid Java Version '%s'. Supported versions: %s", javaVersion, String.join(", ", SUPPORTED_JAVA_VERSIONS)));
        }
    }

    private static boolean isCompatibleVersion(String currentVersion, String candidate) {
        if (currentVersion.startsWith(candidate)) {
            if (currentVersion.length() != candidate.length()) {
                return currentVersion.charAt(candidate.length()) == '.';
            }
            return true;
        }
        return false;
    }

    public static void runChecks(RskContext rskContext) {
        if (!rskContext.getCliArgs().getFlags().contains(NodeCliFlags.SKIP_JAVA_CHECK)) {
            checkSupportedJavaVersion();
        }
    }

}
