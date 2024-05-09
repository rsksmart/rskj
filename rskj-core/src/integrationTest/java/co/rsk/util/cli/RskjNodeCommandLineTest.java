package co.rsk.util.cli;

import co.rsk.util.FilesHelper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RskjNodeCommandLineTest {

    @Test
    void whenExecuteCommandConnectBlocks_thenOutputWithSuccess() throws IOException, InterruptedException {
        //given
        String filePath = FilesHelper.getAbsolutPathFromResourceFile(getClass(), "server_blocks.csv");
        //when
        RskjCommandLineBase rskjCommandLineBase = new ConnectBlocksCommandLine(filePath);
        Process cliProcess = rskjCommandLineBase.executeCommand();
        String output = rskjCommandLineBase.getOutput();
        //then
        assertTrue(output.contains("ConnectBlocks finished"));
        cliProcess.destroyForcibly();
    }
}