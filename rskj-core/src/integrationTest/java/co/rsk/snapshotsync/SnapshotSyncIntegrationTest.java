package co.rsk.snapshotsync;

import co.rsk.util.cli.NodeIntegrationTestCommandLine;
import co.rsk.util.cli.ConnectBlocksCommandLine;
import co.rsk.util.FilesHelper;
import co.rsk.util.cli.RskjCommandLineBase;
import co.rsk.util.ThreadTimerHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static co.rsk.util.FilesHelper.readBytesFromFile;
import static co.rsk.util.FilesHelper.writeBytesToFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnapshotSyncIntegrationTest {
    private static final String TAG_TO_REPLACE_SERVER_RPC_PORT = "<SERVER_NODE_RPC_PORT>";
    private static final String TAG_TO_REPLACE_SERVER_PORT = "<SERVER_NODE_PORT>";
    private static final String TAG_TO_REPLACE_SERVER_DATABASE_PATH = "<SERVER_NODE_DATABASE_PATH>";
    private static final String TAG_TO_REPLACE_NODE_ID = "<SERVER_NODE_ID>";
    private static final String TAG_TO_REPLACE_CLIENT_DATABASE_PATH = "<CLIENT_NODE_DATABASE_PATH>";
    private static final String TAG_TO_REPLACE_CLIENT_PORT = "<CLIENT_PORT>";
    public static final String TMP_RSKJ_INTEGRATION_TEST_FOLDER = "rskj-integration-test-";
    private final String rskConfFileServerName = "snap-sync-server-rskj.conf";
    private final String rskConfFileClientName = "snap-sync-client-rskj.conf";
    private final int portServer = RandomUtils.nextInt(1, 5000);
    private final int portClient = RandomUtils.nextInt(5001, 9999);
    private final String pathDatabaseServer = "/.rsk/regtest/database";
    private final String randomPathDatabaseClient = RandomStringUtils.randomAlphanumeric(10);

    @Test
    public void whenStartTheServerAndClientNodes_thenTheClientWillSynchWithServer() throws IOException, InterruptedException {
        //given
        Path tempDirectory = Files.createTempDirectory(TMP_RSKJ_INTEGRATION_TEST_FOLDER);
        Path dirDatabaseServer = Paths.get(System.getProperty("user.home") + pathDatabaseServer);
        FilesHelper.deleteContents(dirDatabaseServer.toFile());
        Path tempDirDatabaseClient = Files.createTempDirectory(tempDirectory, randomPathDatabaseClient);

        importTheExportedBlocksToRegtestNode();
        String rskConfFileChangedServer = configureServerWithGeneratedInformation(dirDatabaseServer);
        NodeIntegrationTestCommandLine serverNode = new NodeIntegrationTestCommandLine(rskConfFileChangedServer, "--regtest");

        //when
        serverNode.startNode();
        ThreadTimerHelper.waitForSeconds(30);

        String rskConfFileChangedClient = configureClientConfWithGeneratedInformation(dirDatabaseServer, tempDirDatabaseClient.toString());
        NodeIntegrationTestCommandLine clientNode = new NodeIntegrationTestCommandLine(rskConfFileChangedClient, "--regtest");
        clientNode.startNode();

        ThreadTimerHelper.waitForSeconds(20);

        //then
        boolean snapshotNotAsserted = true;
        while (snapshotNotAsserted) {
            if(clientNode.getOutput().contains("CLIENT - Starting Snapshot sync.") && clientNode.getOutput().contains("CLIENT - Snapshot sync finished!")) {
                snapshotNotAsserted = false;
            }
        }
        serverNode.killNode();
        clientNode.killNode();
    }

    private void importTheExportedBlocksToRegtestNode() throws IOException, InterruptedException {
        String exportedBlocksCsv = FilesHelper.getIntegrationTestResourcesFullPath("server_blocks.csv");
        RskjCommandLineBase rskjCommandLineBase = new ConnectBlocksCommandLine(exportedBlocksCsv);
        rskjCommandLineBase.executeCommand();
        String output = rskjCommandLineBase.getOutput();
        assertTrue(output.contains("ConnectBlocks finished"));
    }

    private String configureServerWithGeneratedInformation(Path tempDirDatabaseServerPath) throws IOException {
        String rskConfFileServer = FilesHelper.getAbsolutPathFromResourceFile(getClass(), rskConfFileServerName);
        substituteTagByValueOnConfigurationFile(TAG_TO_REPLACE_SERVER_DATABASE_PATH, tempDirDatabaseServerPath.toString(), rskConfFileServer);
        substituteTagByValueOnConfigurationFile(TAG_TO_REPLACE_SERVER_PORT, String.valueOf(portServer), rskConfFileServer);
        substituteTagByValueOnConfigurationFile(TAG_TO_REPLACE_SERVER_RPC_PORT, String.valueOf(portServer + 1), rskConfFileServer);

        return rskConfFileServer;
    }

    private String configureClientConfWithGeneratedInformation(Path tempDirDatabaseServerPath, String tempDirDatabasePath) throws IOException {
        String nodeId = readServerNodeId(tempDirDatabaseServerPath);
        String rskConfFileClient = FilesHelper.getAbsolutPathFromResourceFile(getClass(), rskConfFileClientName);
        substituteTagByValueOnConfigurationFile(TAG_TO_REPLACE_NODE_ID, nodeId, rskConfFileClient);
        substituteTagByValueOnConfigurationFile(TAG_TO_REPLACE_SERVER_PORT, String.valueOf(portServer), rskConfFileClient);
        substituteTagByValueOnConfigurationFile(TAG_TO_REPLACE_CLIENT_PORT, String.valueOf(portClient), rskConfFileClient);
        substituteTagByValueOnConfigurationFile(TAG_TO_REPLACE_CLIENT_DATABASE_PATH, tempDirDatabasePath, rskConfFileClient);

        return rskConfFileClient;
    }

    private void substituteTagByValueOnConfigurationFile(String tag, String replacement, String configurationFilePath) throws IOException {
        byte[] fileBytes = readBytesFromFile(configurationFilePath);

        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        fileContent = StringUtils.replace(fileContent, tag, replacement);

        writeBytesToFile(fileContent.getBytes(StandardCharsets.UTF_8), configurationFilePath);
    }

    private String readServerNodeId(Path serverDatabasePath) throws IOException {
        byte[] fileBytes = readBytesFromFile(String.format("%s/nodeId.properties", serverDatabasePath));
        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        return StringUtils.substringAfter(fileContent, "nodeId=").trim();
    }
}
