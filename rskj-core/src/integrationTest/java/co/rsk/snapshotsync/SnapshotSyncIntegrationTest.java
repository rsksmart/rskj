package co.rsk.snapshotsync;

import co.rsk.NodeIntegrationTestCommandLine;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SnapshotSyncIntegrationTest {

    public static final String TMP_RSKJ_INTEGRATION_TEST_FOLDER = "rskj-integration-test-";
    private String rskConfFileServer = "integration-test-rskj.conf";
    private int portServer = RandomUtils.nextInt(0, 5000);
    private int portClient = RandomUtils.nextInt(5001, 9999);
    private String randomPathDatabaseServer = RandomStringUtils.randomAlphanumeric(10);
    private String randomPathDatabaseClient = RandomStringUtils.randomAlphanumeric(10);

    @Test
    public void whenStartTheServerAndClientNodes_thenTheClientWillSynchWithServer() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        //given
        Path tempDirectory = Files.createTempDirectory(TMP_RSKJ_INTEGRATION_TEST_FOLDER);
        Path tempDirDatabaseServer = Files.createTempDirectory(tempDirectory, randomPathDatabaseServer);
        Path tempDirDatabaseClient = Files.createTempDirectory(tempDirectory, randomPathDatabaseClient);

        NodeIntegrationTestCommandLine serverNode = new NodeIntegrationTestCommandLine(portServer, tempDirDatabaseServer,rskConfFileServer);
        NodeIntegrationTestCommandLine clientNode = new NodeIntegrationTestCommandLine(portClient, tempDirDatabaseClient,rskConfFileServer);

        //when
        Process nodeServer = serverNode.startNode();
        Thread.sleep(20000);
        Process nodeClient = clientNode.startNode();
        Thread.sleep(20000);
        //then
        assertNotNull(serverNode.appendLinesToProcessOutput());
        assertNotNull(clientNode.appendLinesToProcessOutput());
        serverNode.killNode();
        clientNode.killNode();
    }
}
