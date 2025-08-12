package co.rsk.util;

import co.rsk.RskContext;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by Nazaret García on 21/01/2021
 *
 * This class exposes a method to run a variety of checks.
 * If any given check fails, then a PreflightCheckException exception is thrown.
 *
 * Flags, as command-line arguments, can be used to skip or configure any available check.
 *
 * Current Supported Checks:
 *
 * - Supported Java Version Check: (can be skipped by setting the --skip-java-check flag)
 */

public class PreflightChecksUtils {
    private static final Logger logger = LoggerFactory.getLogger(PreflightChecksUtils.class);

    public static final Set<Integer> SUPPORTED_JAVA_VERSIONS = Collections.unmodifiableSet(
            new TreeSet<>(Arrays.asList(17, 21))
    );

    private final RskContext rskContext;

    public PreflightChecksUtils(RskContext rskContext) {
        this.rskContext = rskContext;
    }

    /**
     * Checks if current Java Version is supported
     * @throws PreflightCheckException if current Java Version is not supported
     */
    @VisibleForTesting
    void checkSupportedJavaVersion() throws PreflightCheckException {
        String javaVersion = getJavaVersion();

        int majorJavaVersion = getMajorJavaVersion(javaVersion);

        if (!SUPPORTED_JAVA_VERSIONS.contains(majorJavaVersion)) {
            String errorMessage = String.format("Invalid Java Version '%s'. Supported versions: %s", majorJavaVersion, StringUtils.join(SUPPORTED_JAVA_VERSIONS, ", "));
            logger.error(errorMessage);
            throw new PreflightCheckException(errorMessage);
        }
    }

    @VisibleForTesting
    String getJavaVersion() {
        return System.getProperty("java.version");
    }

    /**
     * Returns the Java version as an int value.
     * Formats allowed: 1.8.0_72-ea, 9-ea, 9, 9.0.1, 11, 11.0, etc.
     * Based on @link https://stackoverflow.com/a/49512420
     * @param version The java version as String
     * @return the Java version as an int value (8, 9, etc.)
     */
    @VisibleForTesting
    int getMajorJavaVersion(String version) {
        if (version.startsWith("1.")) {
            version = version.substring(2);
        }

        int dotPos = version.indexOf('.');
        int dashPos = version.indexOf('-');

        int endIndex;

        if (dotPos > -1) {
            endIndex = dotPos;
        } else {
            endIndex = dashPos > -1 ? dashPos : version.length();
        }

        return Integer.parseInt(version.substring(0, endIndex));
    }

    public void runChecks() throws PreflightCheckException {
        if (rskContext.getRskSystemProperties().shouldCheckJavaVersion()) {
            checkSupportedJavaVersion();
        }
    }

}
