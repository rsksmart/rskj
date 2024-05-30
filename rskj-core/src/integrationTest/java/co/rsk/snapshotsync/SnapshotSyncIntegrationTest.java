/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.snapshotsync;

import co.rsk.util.*;
import co.rsk.util.cli.ConnectBlocksCommandLine;
import co.rsk.util.cli.NodeIntegrationTestCommandLine;
import co.rsk.util.cli.RskjCommandLineBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static co.rsk.util.FilesHelper.readBytesFromFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnapshotSyncIntegrationTest {
    private static final int TEN_MINUTES_IN_MILLISECONDS = 600000;
    private static final String TAG_TO_REPLACE_SERVER_RPC_PORT = "<SERVER_NODE_RPC_PORT>";
    private static final String TAG_TO_REPLACE_SERVER_PORT = "<SERVER_NODE_PORT>";
    private static final String TAG_TO_REPLACE_SERVER_DATABASE_PATH = "<SERVER_NODE_DATABASE_PATH>";
    private static final String TAG_TO_REPLACE_NODE_ID = "<SERVER_NODE_ID>";
    private static final String TAG_TO_REPLACE_CLIENT_DATABASE_PATH = "<CLIENT_NODE_DATABASE_PATH>";
    private static final String TAG_TO_REPLACE_CLIENT_PORT = "<CLIENT_PORT>";
    private static final String TAG_TO_REPLACE_CLIENT_RPC_HTTP_PORT = "<CLIENT_RPC_HTTP_PORT>";
    private static final String TAG_TO_REPLACE_CLIENT_RPC_WS_PORT = "<CLIENT_RPC_WS_PORT>";
    private static final String TMP_RSKJ_INTEGRATION_TEST_FOLDER = "rskj-integration-test-";

    private static final String RSKJ_SERVER_CONF_FILE_NAME = "snap-sync-server-rskj.conf";
    private static final String RSKJ_CLIENT_CONF_FILE_NAME = "snap-sync-client-rskj.conf";

    private final int portServer = RandomUtils.nextInt(1, 5000);
    private final int portClient = RandomUtils.nextInt(5001, 9999);
    private final int portClientHttp = portClient + 1;
    private final String randomPathDatabaseClient = RandomStringUtils.randomAlphanumeric(10);

    @Test
    public void whenStartTheServerAndClientNodes_thenTheClientWillSynchWithServer() throws IOException, InterruptedException {
        //given
        Path tempDirectory = Files.createTempDirectory(TMP_RSKJ_INTEGRATION_TEST_FOLDER);
        String pathDatabaseServer = "/.rsk/regtest/database";
        Path dirDatabaseServer = Paths.get(System.getProperty("user.home") + pathDatabaseServer);
        FilesHelper.deleteContents(dirDatabaseServer.toFile());
        Path tempDirDatabaseClient = Files.createTempDirectory(tempDirectory, randomPathDatabaseClient);

        importTheExportedBlocksToRegtestNode();
        String rskConfFileChangedServer = configureServerWithGeneratedInformation(dirDatabaseServer);
        NodeIntegrationTestCommandLine serverNode = new NodeIntegrationTestCommandLine(rskConfFileChangedServer, "--regtest");

        serverNode.startNode();
        ThreadTimerHelper.waitForSeconds(30);

        //when
        String rskConfFileChangedClient = configureClientConfWithGeneratedInformation(dirDatabaseServer, tempDirDatabaseClient.toString());
        NodeIntegrationTestCommandLine clientNode = new NodeIntegrationTestCommandLine(rskConfFileChangedClient, "--regtest");
        clientNode.startNode();

        ThreadTimerHelper.waitForSeconds(20);

        //then
        long startTime = System.currentTimeMillis();
        long endTime = startTime + TEN_MINUTES_IN_MILLISECONDS;
        boolean isClientSynced = false;

        while (System.currentTimeMillis() < endTime) {
            if(clientNode.getOutput().contains("CLIENT - Starting Snapshot sync.") && clientNode.getOutput().contains("CLIENT - Snapshot sync finished!")) {
                try {
                    JsonNode jsonResponse = OkHttpClientTestFixture.getJsonResponseForGetBestBlockMessage(portClientHttp);
                    String bestBlockNumber = jsonResponse.get(0).get("result").get("transactions").get(0).get("blockNumber").asText();
                    if(bestBlockNumber.equals("0x1770")) { // We reached the tip of the test database imported on server on the client
                        isClientSynced = true;
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Error while trying to get the best block number from the client: " + e.getMessage());
                    System.out.println("We will try again in 10 seconds.");
                    ThreadTimerHelper.waitForSeconds(10);
                }
            }
        }

        serverNode.killNode();
        clientNode.killNode();

        assertTrue(isClientSynced);
        FilesHelper.deleteContents(Paths.get("./build/resources/integrationTest/" + RSKJ_SERVER_CONF_FILE_NAME ).toFile());
        FilesHelper.deleteContents(Paths.get("./build/resources/integrationTest/" + RSKJ_CLIENT_CONF_FILE_NAME).toFile());

    }

    private void importTheExportedBlocksToRegtestNode() throws IOException, InterruptedException {
        String exportedBlocksCsv = FilesHelper.getIntegrationTestResourcesFullPath("server_blocks.csv");
        RskjCommandLineBase rskjCommandLineBase = new ConnectBlocksCommandLine(exportedBlocksCsv);
        rskjCommandLineBase.executeCommand();
    }

    private String configureServerWithGeneratedInformation(Path tempDirDatabaseServerPath) throws IOException {
        String rskConfFileServer = FilesHelper.getAbsolutPathFromResourceFile(getClass(), RSKJ_SERVER_CONF_FILE_NAME);
        List<Pair<String, String>> tagsWithValues = new ArrayList<>();
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_DATABASE_PATH, tempDirDatabaseServerPath.toString()));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_PORT, String.valueOf(portServer)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_RPC_PORT, String.valueOf(portServer + 1)));

        RskjConfigurationFileFixture.substituteTagsOnRskjConfFile(rskConfFileServer, tagsWithValues);

        return rskConfFileServer;
    }

    private String configureClientConfWithGeneratedInformation(Path tempDirDatabaseServerPath, String tempDirDatabasePath) throws IOException {
        String nodeId = readServerNodeId(tempDirDatabaseServerPath);
        String rskConfFileClient = FilesHelper.getAbsolutPathFromResourceFile(getClass(), RSKJ_CLIENT_CONF_FILE_NAME);

        List<Pair<String, String>> tagsWithValues = new ArrayList<>();
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_NODE_ID, nodeId));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_PORT, String.valueOf(portServer)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_CLIENT_PORT, String.valueOf(portClient)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_CLIENT_RPC_HTTP_PORT, String.valueOf(portClientHttp)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_CLIENT_RPC_WS_PORT, String.valueOf(portClient + 2)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_CLIENT_DATABASE_PATH, tempDirDatabasePath));

        RskjConfigurationFileFixture.substituteTagsOnRskjConfFile(rskConfFileClient, tagsWithValues);

        return rskConfFileClient;
    }

    private String readServerNodeId(Path serverDatabasePath) throws IOException {
        byte[] fileBytes = readBytesFromFile(String.format("%s/nodeId.properties", serverDatabasePath));
        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        return StringUtils.substringAfter(fileContent, "nodeId=").trim();
    }
}