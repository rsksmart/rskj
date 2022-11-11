/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net;

import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

class BlockProcessResultTest {
    @Test
    void buildSimpleLogMessage() {
        String blockHash = "0x01020304";
        Duration processingTime = Duration.ofNanos(123_000_000L);

        String result = BlockProcessResult.buildLogMessage(blockHash, processingTime, null);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("[MESSAGE PROCESS] Block[0x01020304] After[0.123000] seconds, process result. No block connections were made", result);
    }

    @Test
    void buildLogMessageWithConnections() {
        String blockHash = "0x01020304";
        Duration processingTime = Duration.ofNanos(123_000_000L);
        Map<Keccak256, ImportResult> connections = new HashMap<>();

        Keccak256 hash = TestUtils.randomHash("blockHash");

        connections.put(hash, ImportResult.IMPORTED_BEST);

        String result = BlockProcessResult.buildLogMessage(blockHash, processingTime, connections);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("[MESSAGE PROCESS] Block[0x01020304] After[0.123000] seconds, process result. Connections attempts: 1 | " + hash.toHexString() + " - IMPORTED_BEST | ", result);
    }

    @Test
    void factoryMethodForIgnoredBlock() {
        Block mock = Mockito.mock(Block.class);
        BlockProcessResult result = BlockProcessResult.ignoreBlockResult(
                mock, Instant.now()
        );
        MatcherAssert.assertThat(result.wasBlockAdded(mock), Matchers.is(false));
    }

    @Test
    void factoryMethodForValidBlock() {
        Block mock = Mockito.mock(Block.class);
        Map<Keccak256, ImportResult> connectionsResult = new HashMap<>();
        connectionsResult.put(mock.getHash(), ImportResult.IMPORTED_BEST);
        BlockProcessResult result = BlockProcessResult.connectResult(
                mock, Instant.now(), connectionsResult
        );
        MatcherAssert.assertThat(result.wasBlockAdded(mock), Matchers.is(true));
    }
}

