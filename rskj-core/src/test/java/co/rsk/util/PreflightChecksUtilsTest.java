package co.rsk.util;

import co.rsk.RskContext;
import co.rsk.util.preflight.JavaVersionPreflightCheck;
import co.rsk.util.preflight.PreflightCheckException;
import org.ethereum.util.RskTestContext;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.*;

/**
 * Created by Nazaret García on 22/01/2021
 */

public class PreflightChecksUtilsTest {

    @Test
    public void runChecks_receivesSkipJavaCheck_skipsJavaChecks() throws PreflightCheckException {
        String[] args = {"--skip-java-check"};

        RskContext rskContext = new RskTestContext(args);
        PreflightChecksUtils preflightChecksUtils = new PreflightChecksUtils(rskContext);

        JavaVersionPreflightCheck javaVersionPreflightCheckMock = mock(JavaVersionPreflightCheck.class);
        doNothing().when(javaVersionPreflightCheckMock).checkSupportedJavaVersion();

        Whitebox.setInternalState(preflightChecksUtils, "javaVersionPreflightCheck", javaVersionPreflightCheckMock);

        preflightChecksUtils.runChecks();

        verify(javaVersionPreflightCheckMock, times(0)).checkSupportedJavaVersion();
    }

    @Test
    public void runChecks_noSkipFlags_OK() throws PreflightCheckException {
        RskContext rskContext = new RskTestContext(new String[0]);
        PreflightChecksUtils preflightChecksUtils = new PreflightChecksUtils(rskContext);

        JavaVersionPreflightCheck javaVersionPreflightCheckMock = mock(JavaVersionPreflightCheck.class);
        doNothing().when(javaVersionPreflightCheckMock).checkSupportedJavaVersion();

        Whitebox.setInternalState(preflightChecksUtils, "javaVersionPreflightCheck", javaVersionPreflightCheckMock);

        preflightChecksUtils.runChecks();

        verify(javaVersionPreflightCheckMock, times(1)).checkSupportedJavaVersion();
    }

}
