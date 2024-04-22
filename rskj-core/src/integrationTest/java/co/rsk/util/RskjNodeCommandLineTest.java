package co.rsk.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RskjNodeCommandLineTest {

    @Test
    void whenExecuteCommandConnectBlocks_thenOutputWithSuccess() throws IOException, InterruptedException {
        //given
        String integrationTestResourcesPath = getIntegrationTestResourcesFullPath() + "server_blocks.csv";
        String[] args = new String[]{};
        String[] parameters = new String[]{"-f ", integrationTestResourcesPath, "--regtest"};
        //when
        RskjCommandLineBase rskjCommandLineBase = new ConnectBlocksCommandLine(parameters, args);
        Process cliProcess = rskjCommandLineBase.executeCommand();
        String output = rskjCommandLineBase.getOutput();
        //then
        assertTrue(output.contains("ConnectBlocks finished"));
        cliProcess.destroyForcibly();
    }

    private static String getIntegrationTestResourcesFullPath() {
        String projectPath = System.getProperty("user.dir");
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources/", projectPath);
        return integrationTestResourcesPath;
    }
}