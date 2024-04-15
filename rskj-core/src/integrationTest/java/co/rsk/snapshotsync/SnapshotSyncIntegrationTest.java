package co.rsk.snapshotsync;

import co.rsk.NodeIntegrationTestCommandLine;
import co.rsk.util.CommandLineFixture;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SnapshotSyncIntegrationTest {

    private static final String TAG_TO_REPLACE_NODE_ID = "<SERVER_NODE_ID>";
    public static final String TMP_RSKJ_INTEGRATION_TEST_FOLDER = "rskj-integration-test-";
    private String rskConfFileServerName = "snap-sync-server-rskj.conf";
    private String rskConfFileClientName = "snap-sync-client-rskj.conf";
    private int portServer = 3333;
    private int portClient = RandomUtils.nextInt(5001, 9999);
    private String randomPathDatabaseServer = RandomStringUtils.randomAlphanumeric(10);
    private String randomPathDatabaseClient = RandomStringUtils.randomAlphanumeric(10);

    @Test
    public void whenStartTheServerAndClientNodes_thenTheClientWillSynchWithServer() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        //given
        Path tempDirectory = Files.createTempDirectory(TMP_RSKJ_INTEGRATION_TEST_FOLDER);
        Path tempDirDatabaseServer = Files.createTempDirectory(tempDirectory, randomPathDatabaseServer);
        Path tempDirDatabaseClient = Files.createTempDirectory(tempDirectory, randomPathDatabaseClient);

        NodeIntegrationTestCommandLine serverNode = new NodeIntegrationTestCommandLine(portServer, tempDirDatabaseServer, getAbsolutPathFromResourceFile(rskConfFileServerName), "--regtest");

        //when
        Process nodeServer = serverNode.startNode();
        nodeServer.waitFor(2, TimeUnit.MINUTES);

        String rskConfFileChanged = configureClientConfWithServerInformation(tempDirDatabaseServer);
        NodeIntegrationTestCommandLine clientNode = new NodeIntegrationTestCommandLine(portClient, tempDirDatabaseClient, rskConfFileChanged, "--regtest");
        Process nodeClient = clientNode.startNode();

        nodeClient.waitFor(2, TimeUnit.MINUTES);

        //then
        assertNotNull(serverNode.appendLinesToProcessOutput());
        assertNotNull(clientNode.appendLinesToProcessOutput());
        serverNode.killNode();
        clientNode.killNode();
    }

    private String configureClientConfWithServerInformation(Path tempDirDatabaseServerPath) throws IOException, InterruptedException {
        String nodeId = readServerNodeId(tempDirDatabaseServerPath);
        String rskConfFileClient = getAbsolutPathFromResourceFile(rskConfFileClientName);
        substituteTagByValueOnConfigurationFile(TAG_TO_REPLACE_NODE_ID, nodeId, rskConfFileClient);
        return rskConfFileClient;
    }

    private String getAbsolutPathFromResourceFile(String resourceFile) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(resourceFile).getFile());
        String rskConfFileClient = file.getAbsolutePath();
        return rskConfFileClient;
    }

    private void substituteTagByValueOnConfigurationFile(String tag, String replacement, String configurationFilePath) throws IOException {
        // Read the file
        byte[] fileBytes = readBytesFromFile(configurationFilePath);

        // Replace the tag
        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        fileContent = StringUtils.replace(fileContent, tag, replacement);

        // Write the updated contents back to the file
        writeBytesToFile(fileContent.getBytes(StandardCharsets.UTF_8), configurationFilePath);
    }

    private String readServerNodeId(Path serverDatabasePath) throws IOException {
        byte[] fileBytes = readBytesFromFile(String.format("%s/database/nodeId.properties", serverDatabasePath));
        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        return StringUtils.substringAfter(fileContent, "nodeId=").trim();
    }

    private static byte[] readBytesFromFile(String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    private static void writeBytesToFile(byte[] bytes, String filePath) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(filePath)) {
            outputStream.write(bytes);
        }
    }
}
