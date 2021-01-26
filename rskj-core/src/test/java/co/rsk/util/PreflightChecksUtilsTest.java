package co.rsk.util;

import co.rsk.RskContext;
import org.ethereum.util.RskTestContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.verifyPrivate;

/**
 * Created by Nazaret García on 22/01/2021
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({PreflightChecksUtils.class})
public class PreflightChecksUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void runChecks_receivesSkipJavaCheck_skipsJavaChecks() throws Exception {
        String[] args = {"--skip-java-check"};

        RskContext rskContext = new RskTestContext(args);
        PreflightChecksUtils preflightChecksUtilsSpy = PowerMockito.spy(new PreflightChecksUtils(rskContext));

        PowerMockito.when(preflightChecksUtilsSpy, "getJavaVersion").thenReturn(null);

        preflightChecksUtilsSpy.runChecks();

        verifyPrivate(preflightChecksUtilsSpy, times(0)).invoke("getJavaVersion");
    }

    @Test
    public void getIntJavaVersion_OK() {
        RskContext rskContext = new RskContext(new String[0]);
        PreflightChecksUtils preflightChecksUtils = new PreflightChecksUtils(rskContext);

        assertEquals(preflightChecksUtils.getIntJavaVersion("1.8.0_275"), 8);
        assertEquals(preflightChecksUtils.getIntJavaVersion("1.8.0_72-ea"), 8);
        assertEquals(preflightChecksUtils.getIntJavaVersion("11.8.0_71-ea"), 11);
        assertEquals(preflightChecksUtils.getIntJavaVersion("11.0"), 11);
        assertEquals(preflightChecksUtils.getIntJavaVersion("9"), 9);
        assertEquals(preflightChecksUtils.getIntJavaVersion("11"), 11);
        assertEquals(preflightChecksUtils.getIntJavaVersion("333"), 333);
        assertEquals(preflightChecksUtils.getIntJavaVersion("9-ea"), 9);
    }

    @Test
    public void runChecks_nullJavaVersionReceived_exceptionIsThrown() throws Exception {
        expectedException.expect(PreflightCheckException.class);
        expectedException.expectMessage("Unable to detect Java version");

        RskContext rskContext = new RskTestContext(new String[0]);
        PreflightChecksUtils preflightChecksUtilsSpy = PowerMockito.spy(new PreflightChecksUtils(rskContext));

        PowerMockito.when(preflightChecksUtilsSpy, "getJavaVersion").thenReturn(null);

        preflightChecksUtilsSpy.runChecks();

        verifyPrivate(preflightChecksUtilsSpy, times(0)).invoke("getJavaVersion");
    }

    @Test
    public void runChecks_invalidJavaVersion_exceptionIsThrown() throws Exception {
        expectedException.expect(PreflightCheckException.class);
        expectedException.expectMessage("Invalid Java Version '16'. Supported versions: 8 11");

        RskContext rskContext = new RskTestContext(new String[0]);
        PreflightChecksUtils preflightChecksUtilsSpy = PowerMockito.spy(new PreflightChecksUtils(rskContext));

        PowerMockito.when(preflightChecksUtilsSpy, "getJavaVersion").thenReturn("16");

        preflightChecksUtilsSpy.runChecks();
    }

    @Test
    public void runChecks_currentJavaVersionIs1dot8_OK() throws Exception {
        RskContext rskContext = new RskTestContext(new String[0]);
        PreflightChecksUtils preflightChecksUtilsSpy = PowerMockito.spy(new PreflightChecksUtils(rskContext));

        PowerMockito.when(preflightChecksUtilsSpy, "getJavaVersion").thenReturn("1.8");

        preflightChecksUtilsSpy.runChecks();

        verifyPrivate(preflightChecksUtilsSpy, times(1)).invoke("getJavaVersion");
        verifyPrivate(preflightChecksUtilsSpy, times(1)).invoke("getIntJavaVersion", "1.8");
    }

    @Test
    public void runChecks_currentJavaVersionIs11_OK() throws Exception {
        RskContext rskContext = new RskTestContext(new String[0]);
        PreflightChecksUtils preflightChecksUtilsSpy = PowerMockito.spy(new PreflightChecksUtils(rskContext));

        PowerMockito.when(preflightChecksUtilsSpy, "getJavaVersion").thenReturn("11");

        preflightChecksUtilsSpy.runChecks();

        verifyPrivate(preflightChecksUtilsSpy, times(1)).invoke("getJavaVersion");
        verifyPrivate(preflightChecksUtilsSpy, times(1)).invoke("getIntJavaVersion", "11");
    }

    @Test
    public void runChecks_currentJavaVersionIsValidMajorVersion_OK() throws Exception {
        RskContext rskContext = new RskTestContext(new String[0]);
        PreflightChecksUtils preflightChecksUtilsSpy = PowerMockito.spy(new PreflightChecksUtils(rskContext));

        PowerMockito.when(preflightChecksUtilsSpy, "getJavaVersion").thenReturn("11.0.9.1");

        preflightChecksUtilsSpy.runChecks();

        verifyPrivate(preflightChecksUtilsSpy, times(1)).invoke("getJavaVersion");
        verifyPrivate(preflightChecksUtilsSpy, times(1)).invoke("getIntJavaVersion", "11.0.9.1");
    }

    @Test
    public void runChecks_runAllChecks_OK() throws Exception {
        RskContext rskContext = new RskTestContext(new String[0]);
        PreflightChecksUtils preflightChecksUtilsSpy = PowerMockito.spy(new PreflightChecksUtils(rskContext));

        PowerMockito.when(preflightChecksUtilsSpy, "getJavaVersion").thenReturn("1.8.0_275");

        preflightChecksUtilsSpy.runChecks();

        verifyPrivate(preflightChecksUtilsSpy, times(1)).invoke("getJavaVersion");
        verify(preflightChecksUtilsSpy, times(1)).getIntJavaVersion("1.8.0_275");
        verifyPrivate(preflightChecksUtilsSpy, times(1)).invoke("checkSupportedJavaVersion");
    }

}
