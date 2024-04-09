package co.rsk.snapshotsync;

import co.rsk.NodeIntegrationTestCommandLine;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SnapshotSyncIntegrationTest {

    private String rskConfFileServer = "integration-test-rskj.conf";

    @Test
    public void whenSpinUpServer_theNodeStartsOnThePortConfigured() throws IOException, InterruptedException {
        //given
        int portServer = new Random().nextInt(9999);
        Path tempDirDatabaseServer = Files.createTempDirectory(Paths.get("/tmp/rskj-integration-test"), RandomStringUtils.randomAlphanumeric(10));
        int portClient = new Random().nextInt(9999);
        Path tempDirDatabaseClient = Files.createTempDirectory(Paths.get("/tmp/rskj-integration-test"), RandomStringUtils.randomAlphanumeric(10));
        NodeIntegrationTestCommandLine serverNode = new NodeIntegrationTestCommandLine(portServer, tempDirDatabaseServer,rskConfFileServer, 2);
        NodeIntegrationTestCommandLine clientNode = new NodeIntegrationTestCommandLine(portClient, tempDirDatabaseClient,rskConfFileServer, 2);
        //when
        String outputServer = serverNode.startNode();
        String outputClient = clientNode.startNode();
        //then
        assertNotNull(outputServer);
        assertNotNull(outputClient);
    }
}
