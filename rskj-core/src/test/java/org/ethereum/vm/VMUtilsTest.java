package org.ethereum.vm;

import co.rsk.config.TestSystemProperties;
import org.ethereum.vm.trace.DetailedProgramTrace;
import org.ethereum.vm.trace.Serializers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.core.Is.is;

public class VMUtilsTest {

    private final TestSystemProperties config = new TestSystemProperties();

    @TempDir
    private Path tempDir;

    @Test
    public void savePlainProgramTraceFile() throws Exception {
        DetailedProgramTrace mockTrace = new DetailedProgramTrace(config.getVmConfig(), null);
        String mockTxHash = "1234";

        VMUtils.saveProgramTraceFile(
            tempDir,
            mockTxHash,
            false,
            mockTrace
        );

        String trace = new String(Files.readAllBytes(tempDir.resolve(mockTxHash + ".json")));
        MatcherAssert.assertThat(trace, is(Serializers.serializeFieldsOnly(mockTrace, true)));
    }

    @Test
    public void saveZippedProgramTraceFile() throws Exception {
        Path traceFilePath = tempDir.resolve(UUID.randomUUID().toString());
        traceFilePath.toFile().mkdir();

        DetailedProgramTrace mockTrace = new DetailedProgramTrace(config.getVmConfig(), null);
        String mockTxHash = "1234";

        VMUtils.saveProgramTraceFile(
                traceFilePath,
                mockTxHash,
                true,
                mockTrace
        );

        ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(traceFilePath.resolve(mockTxHash + ".zip")));
        ZipEntry zippedTrace = zipIn.getNextEntry();
        MatcherAssert.assertThat(zippedTrace.getName(), is(mockTxHash + ".json"));
        ByteArrayOutputStream unzippedTrace = new ByteArrayOutputStream();
        byte[] traceBuffer = new byte[2048];
        int len;
        while ((len = zipIn.read(traceBuffer)) > 0) {
            unzippedTrace.write(traceBuffer, 0, len);
        }
        MatcherAssert.assertThat(new String(unzippedTrace.toByteArray()), is(Serializers.serializeFieldsOnly(mockTrace, true)));
    }
}
