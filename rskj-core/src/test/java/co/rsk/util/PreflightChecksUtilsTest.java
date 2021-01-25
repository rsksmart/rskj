package co.rsk.util;

import org.ethereum.util.RskTestContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.powermock.api.mockito.PowerMockito.*;

/**
 * Created by Nazaret García on 22/01/2021
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({SystemUtils.class})
public class PreflightChecksUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void runChecks_receivesSkipJavaCheck_skipsJavaChecks() {
        String[] args = {"--skip-java-check"};

        RskTestContext rskContext = new RskTestContext(args);

        PreflightChecksUtils.runChecks(rskContext);
    }

    @Test
    public void runChecks_nullJavaVersionReceived_exceptionIsThrown() {
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Unable to detect Java version");

        RskTestContext rskContext = new RskTestContext(new String[0]);

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn(null);

        PreflightChecksUtils.runChecks(rskContext);
    }

    @Test
    public void runChecks_invalidJavaVersion_exceptionIsThrown() {
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Invalid Java Version '16'. Supported versions: 1.8, 11");

        RskTestContext rskContext = new RskTestContext(new String[0]);

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("16");

        PreflightChecksUtils.runChecks(rskContext);
    }

    @Test
    public void runChecks_currentJavaVersionIs1dot8_OK() {
        RskTestContext rskContext = new RskTestContext(new String[0]);

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("1.8");

        PreflightChecksUtils.runChecks(rskContext);
    }

    @Test
    public void runChecks_currentJavaVersionIs11_OK() {
        RskTestContext rskContext = new RskTestContext(new String[0]);

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("11");

        PreflightChecksUtils.runChecks(rskContext);
    }

    @Test
    public void runChecks_currentJavaVersionIsValidMajorVersion_OK() {
        RskTestContext rskContext = new RskTestContext(new String[0]);

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("11.0.9.1");

        PreflightChecksUtils.runChecks(rskContext);
    }

    @Test
    public void runChecks_borderCase_supportedMajorVersionContainedInCurrentVersion_exceptionIsThrown() {
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Invalid Java Version '111'. Supported versions: 1.8, 11");

        RskTestContext rskContext = new RskTestContext(new String[0]);

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("111");

        PreflightChecksUtils.runChecks(rskContext);
    }

}
