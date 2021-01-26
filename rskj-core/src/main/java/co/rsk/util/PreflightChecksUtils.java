package co.rsk.util;

import co.rsk.RskContext;
import co.rsk.config.NodeCliFlags;
import co.rsk.util.preflight.JavaVersionPreflightCheck;

/**
 * Created by Nazaret García on 21/01/2021
 */

public class PreflightChecksUtils {

    private final RskContext rskContext;
    private final JavaVersionPreflightCheck javaVersionPreflightCheck;

    public PreflightChecksUtils(RskContext rskContext) {
        this.rskContext = rskContext;
        this.javaVersionPreflightCheck = new JavaVersionPreflightCheck();
    }

    public void runChecks() {
        if (!rskContext.getCliArgs().getFlags().contains(NodeCliFlags.SKIP_JAVA_CHECK)) {
            javaVersionPreflightCheck.checkSupportedJavaVersion();
        }
    }

}
