package co.rsk.util.preflight;

import co.rsk.util.SystemUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by Nazaret García on 25/01/2021
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({SystemUtils.class})
public class JavaVersionPreflightCheckTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void runChecks_nullJavaVersionReceived_exceptionIsThrown() throws PreflightCheckException {
        expectedException.expect(PreflightCheckException.class);
        expectedException.expectMessage("Unable to detect Java version");

        JavaVersionPreflightCheck javaVersionPreflightCheck = new JavaVersionPreflightCheck();

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn(null);

        javaVersionPreflightCheck.checkSupportedJavaVersion();
    }

    @Test
    public void runChecks_invalidJavaVersion_exceptionIsThrown() throws PreflightCheckException {
        expectedException.expect(PreflightCheckException.class);
        expectedException.expectMessage("Invalid Java Version '16'. Supported versions: 8 11");

        JavaVersionPreflightCheck javaVersionPreflightCheck = new JavaVersionPreflightCheck();

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("16");

        javaVersionPreflightCheck.checkSupportedJavaVersion();
    }

    @Test
    public void runChecks_currentJavaVersionIs1dot8_OK() throws PreflightCheckException {
        JavaVersionPreflightCheck javaVersionPreflightCheck = new JavaVersionPreflightCheck();

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("1.8");

        javaVersionPreflightCheck.checkSupportedJavaVersion();
    }

    @Test
    public void runChecks_currentJavaVersionIs11_OK() throws PreflightCheckException {
        JavaVersionPreflightCheck javaVersionPreflightCheck = new JavaVersionPreflightCheck();

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("11");

        javaVersionPreflightCheck.checkSupportedJavaVersion();
    }

    @Test
    public void runChecks_currentJavaVersionIsValidMajorVersion_OK() throws PreflightCheckException {
        JavaVersionPreflightCheck javaVersionPreflightCheck = new JavaVersionPreflightCheck();

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("11.0.9.1");

        javaVersionPreflightCheck.checkSupportedJavaVersion();
    }

    @Test
    public void runChecks_borderCase_I_exceptionIsThrown() throws PreflightCheckException {
        expectedException.expect(PreflightCheckException.class);
        expectedException.expectMessage("Invalid Java Version '111'. Supported versions: 8 11");

        JavaVersionPreflightCheck javaVersionPreflightCheck = new JavaVersionPreflightCheck();

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("111");

        javaVersionPreflightCheck.checkSupportedJavaVersion();
    }

    @Test
    public void runChecks_borderCase_II_exceptionIsThrown() throws PreflightCheckException {
        expectedException.expect(PreflightCheckException.class);
        expectedException.expectMessage("Invalid Java version number");

        JavaVersionPreflightCheck javaVersionPreflightCheck = new JavaVersionPreflightCheck();

        mockStatic(SystemUtils.class);
        when(SystemUtils.getPropertyValue("java.version")).thenReturn("1");

        javaVersionPreflightCheck.checkSupportedJavaVersion();
    }

}
