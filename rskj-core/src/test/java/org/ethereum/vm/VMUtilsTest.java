package org.ethereum.vm;

import co.rsk.config.TestSystemProperties;
import org.ethereum.vm.trace.ProgramTrace;
import org.ethereum.vm.trace.Serializers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.core.Is.is;

public class VMUtilsTest {

    private final TestSystemProperties config = new TestSystemProperties();
    @Rule
    public TemporaryFolder tempRule = new TemporaryFolder();

    @Test
    public void savePlainProgramTraceFile() throws Exception {
        Path traceFilePath = tempRule.newFolder().toPath();
        ProgramTrace mockTrace = new ProgramTrace(config.getVmConfig(), null);
        String mockTxHash = "1234";

        VMUtils.saveProgramTraceFile(
            traceFilePath,
            mockTxHash,
            false,
            mockTrace
        );

        String trace = new String(Files.readAllBytes(traceFilePath.resolve(mockTxHash + ".json")));
        Assert.assertThat(trace, is(Serializers.serializeFieldsOnly(mockTrace, true)));
    }

    @Test
    public void saveZippedProgramTraceFile() throws Exception {
        Path traceFilePath = tempRule.newFolder().toPath();
        ProgramTrace mockTrace = new ProgramTrace(config.getVmConfig(), null);
        String mockTxHash = "1234";

        VMUtils.saveProgramTraceFile(
                traceFilePath,
                mockTxHash,
                true,
                mockTrace
        );

        ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(traceFilePath.resolve(mockTxHash + ".zip")));
        ZipEntry zippedTrace = zipIn.getNextEntry();
        Assert.assertThat(zippedTrace.getName(), is(mockTxHash + ".json"));
        ByteArrayOutputStream unzippedTrace = new ByteArrayOutputStream();
        byte[] traceBuffer = new byte[2048];
        int len;
        while ((len = zipIn.read(traceBuffer)) > 0) {
            unzippedTrace.write(traceBuffer, 0, len);
        }
        Assert.assertThat(new String(unzippedTrace.toByteArray()), is(Serializers.serializeFieldsOnly(mockTrace, true)));
    }
}