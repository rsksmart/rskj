package co.rsk.util;

import co.rsk.RskContext;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.util.RskTestContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Created by Nazaret García on 22/01/2021
 */

class PreflightChecksUtilsTest {

    @TempDir
    public Path tempDir;

    @Test
    void runChecks_receivesSkipJavaCheck_skipsJavaChecks() throws Exception {
        String[] args = {"--skip-java-check"};

        RskContext rskContext = new RskTestContext(tempDir, args);
        PreflightChecksUtils preflightChecksUtilsSpy = spy(new PreflightChecksUtils(rskContext));

        when(preflightChecksUtilsSpy.getJavaVersion()).thenReturn(null);

        preflightChecksUtilsSpy.runChecks();

        verify(preflightChecksUtilsSpy, times(0)).getJavaVersion();

        rskContext.close();
    }

    @Test
    void getIntJavaVersion_OK() {
        RskContext rskContext = new RskTestContext(tempDir);
        PreflightChecksUtils preflightChecksUtils = new PreflightChecksUtils(rskContext);

        Assertions.assertEquals(8, preflightChecksUtils.getMajorJavaVersion("1.8.0_275"));
        Assertions.assertEquals(8, preflightChecksUtils.getMajorJavaVersion("1.8.0_72-ea"));
        Assertions.assertEquals(11, preflightChecksUtils.getMajorJavaVersion("11.8.0_71-ea"));
        Assertions.assertEquals(11, preflightChecksUtils.getMajorJavaVersion("11.0"));
        Assertions.assertEquals(9, preflightChecksUtils.getMajorJavaVersion("9"));
        Assertions.assertEquals(11, preflightChecksUtils.getMajorJavaVersion("11"));
        Assertions.assertEquals(333, preflightChecksUtils.getMajorJavaVersion("333"));
        Assertions.assertEquals(9, preflightChecksUtils.getMajorJavaVersion("9-ea"));

        rskContext.close();
    }

    @Test
    void runChecks_invalidJavaVersion_exceptionIsThrown() {
        RskContext rskContext = new RskTestContext(tempDir);
        PreflightChecksUtils preflightChecksUtilsSpy = spy(new PreflightChecksUtils(rskContext));

        when(preflightChecksUtilsSpy.getJavaVersion()).thenReturn("16");

        Exception exception = assertThrows(PreflightCheckException.class, preflightChecksUtilsSpy::runChecks);
        String expectedMsg = "Invalid Java Version '16'. Supported versions: " + StringUtils.join(PreflightChecksUtils.SUPPORTED_JAVA_VERSIONS, ", ");
        Assertions.assertEquals(expectedMsg, exception.getMessage());

        rskContext.close();
    }

    @Test
    void runChecks_runAllChecks_OK() throws Exception {
        for (String ver : Arrays.asList("17.0.3")) {
            try (RskContext rskContext = new RskTestContext(tempDir)) {
                PreflightChecksUtils preflightChecksUtilsSpy = spy(new PreflightChecksUtils(rskContext));

                when(preflightChecksUtilsSpy.getJavaVersion()).thenReturn(ver);

                preflightChecksUtilsSpy.runChecks();

                verify(preflightChecksUtilsSpy, times(1)).getJavaVersion();
                verify(preflightChecksUtilsSpy, times(1)).getMajorJavaVersion(ver);
                verify(preflightChecksUtilsSpy, times(1)).checkSupportedJavaVersion();
            }
        }
    }

    @Test
    void runChecks_nextLTS_NotSupportedYet() throws Exception {
        try (RskContext rskContext = new RskTestContext(tempDir)) {
            PreflightChecksUtils preflightChecksUtilsSpy = spy(new PreflightChecksUtils(rskContext));

            when(preflightChecksUtilsSpy.getJavaVersion()).thenReturn("25.0.2");

            assertThrows(PreflightCheckException.class, preflightChecksUtilsSpy::runChecks);

            verify(preflightChecksUtilsSpy, times(1)).getJavaVersion();
            verify(preflightChecksUtilsSpy, times(1)).getMajorJavaVersion("25.0.2");
            verify(preflightChecksUtilsSpy, times(1)).checkSupportedJavaVersion();
        }
    }
}
