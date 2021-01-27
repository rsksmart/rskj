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
import java.time.Instant;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.mockito.Mockito;

public class BlockProcessResultTest {
    @Test
    public void buildSimpleLogMessage() {
        String blockHash = "0x01020304";
        Duration processingTime = Duration.ofNanos(123_000_000L);

        String result = BlockProcessResult.buildLogMessage(blockHash, processingTime, null);

        Assert.assertNotNull(result);
        Assert.assertEquals("[MESSAGE PROCESS] Block[0x01020304] After[0.123000] seconds, process result. No block connections were made", result);
    }

    @Test
    public void buildLogMessageWithConnections() {
        String blockHash = "0x01020304";
        Duration processingTime = Duration.ofNanos(123_000_000L);
        Map<Keccak256, ImportResult> connections = new HashMap<>();

        Random random = new Random();

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        Keccak256 hash = new Keccak256(bytes);

        connections.put(hash, ImportResult.IMPORTED_BEST);

        String result = BlockProcessResult.buildLogMessage(blockHash, processingTime, connections);

        Assert.assertNotNull(result);
        Assert.assertEquals("[MESSAGE PROCESS] Block[0x01020304] After[0.123000] seconds, process result. Connections attempts: 1 | " + hash.toHexString() + " - IMPORTED_BEST | ", result);
    }

    @Test
    public void factoryMethodForIgnoredBlock() {
        Block mock = Mockito.mock(Block.class);
        BlockProcessResult result = BlockProcessResult.ignoreBlockResult(
                mock, Instant.now()
        );
        MatcherAssert.assertThat(result.wasBlockAdded(mock), Matchers.is(false));
    }

    @Test
    public void factoryMethodForValidBlock() {
        Block mock = Mockito.mock(Block.class);
        Map<Keccak256, ImportResult> connectionsResult = new HashMap<>();
        connectionsResult.put(mock.getHash(), ImportResult.IMPORTED_BEST);
        BlockProcessResult result = BlockProcessResult.connectResult(
                mock, Instant.now(), connectionsResult
        );
        MatcherAssert.assertThat(result.wasBlockAdded(mock), Matchers.is(true));
    }
}

