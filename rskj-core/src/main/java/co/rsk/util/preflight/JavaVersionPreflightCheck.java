package co.rsk.util.preflight;

import co.rsk.util.SystemUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Created by Nazaret García on 25/01/2021
 */

public class JavaVersionPreflightCheck {
    private static final Logger logger = LoggerFactory.getLogger(JavaVersionPreflightCheck.class);

    private static final int[] SUPPORTED_JAVA_VERSIONS = {8, 11};

    public void checkSupportedJavaVersion() throws PreflightCheckException {
        String javaVersion = SystemUtils.getPropertyValue("java.version");

        if (javaVersion == null) {
            throw new PreflightCheckException("Unable to detect Java version");
        }

        int intJavaVersion = getIntJavaVersion(javaVersion);

        if (intJavaVersion == -1) {
            throw new PreflightCheckException("Invalid Java version number");
        }

        if (Arrays.stream(SUPPORTED_JAVA_VERSIONS).noneMatch(v -> intJavaVersion == v)) {
            String errorMessage = String.format("Invalid Java Version '%s'. Supported versions: %s", intJavaVersion, StringUtils.join(SUPPORTED_JAVA_VERSIONS, ' '));
            logger.error(errorMessage);
            throw new PreflightCheckException(errorMessage);
        }
    }

    /**
     * Returns the Java version as an int value.
     * Formats allowed: 1.8.0_72-ea, 9-ea, 9, 9.0.1, 11, 11.0, etc.
     * @return the Java version as an int value (8, 9, etc.)
     * @link https://stackoverflow.com/a/49512420
     */
    private int getIntJavaVersion(String version) {
        String[] splitVersion = version.replace(".", " ").replace("-", " ").replace("_", " ").split(" ");
        if ("1".equals(splitVersion[0])) {
            if (splitVersion.length > 1) {
                return Integer.parseInt(splitVersion[1]);
            }
            return -1;
        }
        return Integer.parseInt(splitVersion[0]);
    }

}
