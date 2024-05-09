package co.rsk.util.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RskjNodeCommandLineTest {

    @Test
    void whenExecuteCommandConnectBlocks_thenOutputWithSuccess() throws IOException, InterruptedException {
        //given
        String filePath = getIntegrationTestResourcesFullPath() + "server_blocks.csv";
        //when
        RskjCommandLineBase rskjCommandLineBase = new ConnectBlocksCommandLine(filePath);
        Process cliProcess = rskjCommandLineBase.executeCommand();
        String output = rskjCommandLineBase.getOutput();
        //then
        assertTrue(output.contains("ConnectBlocks finished"));
        cliProcess.destroyForcibly();
    }

    private static String getIntegrationTestResourcesFullPath() {
        String projectPath = System.getProperty("user.dir");
        return String.format("%s/src/integrationTest/resources/", projectPath);
    }
}