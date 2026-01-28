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
package co.rsk.snap;

import co.rsk.util.*;
import co.rsk.util.cli.NodeIntegrationTestCommandLine;
import com.fasterxml.jackson.databind.JsonNode;
import com.squareup.okhttp.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static co.rsk.util.FilesHelper.readBytesFromFile;
import static co.rsk.util.OkHttpClientTestFixture.FromToAddressPair.of;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotSyncIntegrationTest {
    private static final int TEN_MINUTES_IN_MILLISECONDS = 600000;
    private static final String TAG_TO_REPLACE_SERVER_RPC_PORT = "<SERVER_NODE_RPC_PORT>";
    private static final String TAG_TO_REPLACE_SERVER_PORT = "<SERVER_NODE_PORT>";
    private static final String TAG_TO_REPLACE_SERVER_DATABASE_PATH = "<SERVER_NODE_DATABASE_PATH>";
    private static final String TAG_TO_REPLACE_NODE_ID = "<SERVER_NODE_ID>";
    private static final String TAG_TO_REPLACE_CLIENT_DATABASE_PATH = "<CLIENT_NODE_DATABASE_PATH>";
    private static final String TAG_TO_REPLACE_CLIENT_PORT = "<CLIENT_PORT>";
    private static final String TAG_TO_REPLACE_CLIENT_RPC_HTTP_PORT = "<CLIENT_RPC_HTTP_PORT>";

    private static final String RSKJ_SERVER_CONF_FILE_NAME = "snap-sync-server-rskj.conf";
    private static final String RSKJ_CLIENT_CONF_FILE_NAME = "snap-sync-client-rskj.conf";

    private final int portServer = 50555;
    private final int portServerRpc = portServer + 1;
    private final int portClient = portServerRpc + 1;
    private final int portClientRpc = portClient + 1;

    @TempDir
    public Path tempDirectory;

    private NodeIntegrationTestCommandLine serverNode;
    private NodeIntegrationTestCommandLine clientNode;

    @AfterEach
    void tearDown() throws InterruptedException {
        for (NodeIntegrationTestCommandLine node : Stream.of(clientNode, serverNode).filter(Objects::nonNull).collect(Collectors.toList())) {
            node.killNode();
        }
    }

    @Test
    void whenStartTheServerAndClientNodes_thenTheClientWillSynchWithServer() throws IOException, InterruptedException {
        //given
        Path serverDbDir = tempDirectory.resolve("server/database");
        Path clientDbDir = tempDirectory.resolve("client/database");

        String rskConfFileChangedServer = configureServerWithGeneratedInformation(serverDbDir);
        serverNode = new NodeIntegrationTestCommandLine(rskConfFileChangedServer, "--regtest");
        serverNode.startNode();
        ThreadTimerHelper.waitForSeconds(60);
        generateBlocks();

        JsonNode serverBestBlockResponse = OkHttpClientTestFixture.getJsonResponseForGetBestBlockMessage(portServerRpc, "latest");
        String serverBestBlockNumber = serverBestBlockResponse.get(0).get("result").get("number").asText();
        assertTrue(HexUtils.jsonHexToLong(serverBestBlockNumber) > 10000);

        //when
        String rskConfFileChangedClient = configureClientConfWithGeneratedInformation(serverDbDir, clientDbDir.toString());
        clientNode = new NodeIntegrationTestCommandLine(rskConfFileChangedClient, "--regtest");
        clientNode.startNode();

        //then
        long startTime = System.currentTimeMillis();
        long endTime = startTime + TEN_MINUTES_IN_MILLISECONDS;
        boolean isClientSynced = false;

        while (System.currentTimeMillis() < endTime) {
            if (clientNode.getOutput().contains("Starting Snap sync") && clientNode.getOutput().contains("Snap sync finished successfully")) {
                try {
                    JsonNode jsonResponse = OkHttpClientTestFixture.getJsonResponseForGetBestBlockMessage(portClientRpc, serverBestBlockNumber);
                    JsonNode jsonResult = jsonResponse.get(0).get("result");
                    if (jsonResult.isObject()) {
                        String bestBlockNumber = jsonResult.get("number").asText();
                        if (bestBlockNumber.equals(serverBestBlockNumber)) { // We reached the tip of the test database imported on server on the client
                            isClientSynced = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error while trying to get the best block number from the client: " + e.getMessage());
                    System.out.println("We will try again in 20 seconds.");
                }
            }
            ThreadTimerHelper.waitForSeconds(20);
        }

        assertTrue(isClientSynced);
    }

    private String configureServerWithGeneratedInformation(Path tempDirDatabaseServerPath) throws IOException {
        String originRskConfFileServer = FilesHelper.getAbsolutPathFromResourceFile(getClass(), RSKJ_SERVER_CONF_FILE_NAME);
        Path rskConfFileServer = tempDirectory.resolve("server/" + RSKJ_SERVER_CONF_FILE_NAME);
        rskConfFileServer.getParent().toFile().mkdirs();
        Files.copy(Paths.get(originRskConfFileServer), rskConfFileServer);

        List<Pair<String, String>> tagsWithValues = new ArrayList<>();
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_DATABASE_PATH, tempDirDatabaseServerPath.toString()));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_PORT, String.valueOf(portServer)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_RPC_PORT, String.valueOf(portServerRpc)));

        RskjConfigurationFileFixture.substituteTagsOnRskjConfFile(rskConfFileServer.toString(), tagsWithValues);

        return rskConfFileServer.toString();
    }

    private String configureClientConfWithGeneratedInformation(Path tempDirDatabaseServerPath, String tempDirDatabasePath) throws IOException {
        String nodeId = readServerNodeId(tempDirDatabaseServerPath);
        String originRskConfFileClient = FilesHelper.getAbsolutPathFromResourceFile(getClass(), RSKJ_CLIENT_CONF_FILE_NAME);
        Path rskConfFileClient = tempDirectory.resolve("client/" + RSKJ_CLIENT_CONF_FILE_NAME);
        rskConfFileClient.getParent().toFile().mkdirs();
        Files.copy(Paths.get(originRskConfFileClient), rskConfFileClient);

        List<Pair<String, String>> tagsWithValues = new ArrayList<>();
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_NODE_ID, nodeId));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_PORT, String.valueOf(portServer)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_CLIENT_PORT, String.valueOf(portClient)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_CLIENT_RPC_HTTP_PORT, String.valueOf(portClientRpc)));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_CLIENT_DATABASE_PATH, tempDirDatabasePath));

        RskjConfigurationFileFixture.substituteTagsOnRskjConfFile(rskConfFileClient.toString(), tagsWithValues);

        return rskConfFileClient.toString();
    }

    private String readServerNodeId(Path serverDatabasePath) throws IOException {
        byte[] fileBytes = readBytesFromFile(String.format("%s/nodeId.properties", serverDatabasePath));
        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        return StringUtils.substringAfter(fileContent, "nodeId=").trim();
    }

    private void generateBlocks() throws IOException {
        List<String> accounts = OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS;
        Random rand = new Random(111);

        for (int i = 0; i < 1001; i++) {
            OkHttpClientTestFixture.FromToAddressPair[] pairs = IntStream.range(0, 10)
                    .mapToObj(n -> of(accounts.get(rand.nextInt(accounts.size())), accounts.get(rand.nextInt(accounts.size()))))
                    .toArray(OkHttpClientTestFixture.FromToAddressPair[]::new);
            Response response = OkHttpClientTestFixture.sendBulkTransactions(portServerRpc, pairs);
            assertTrue(response.isSuccessful());
            response.body().close();
        }
    }
}
