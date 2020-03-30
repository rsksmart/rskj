/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light.message;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static co.rsk.net.light.LightClientMessageCodes.STATUS;
import static org.ethereum.crypto.HashUtil.*;
import static org.junit.Assert.*;


public class StatusMessageTest {

    private long id;
    private byte protocolVersion;
    private int networkId;
    private Keccak256 bestHash;
    private long bestNumber;
    private byte[] genesisHash;
    private BlockDifficulty totalDifficulty;
    private StatusMessage testMessage;

    @Before
    public void setUp() {
        id = 1L;
        protocolVersion = (byte) 2;
        networkId = 2;
        bestHash = new Keccak256(randomHash());
        bestNumber = 10L;
        genesisHash = randomHash();
        totalDifficulty = new BlockDifficulty(BigInteger.ONE);
        testMessage = new StatusMessage(id, protocolVersion, networkId,
                totalDifficulty, bestHash.getBytes(), bestNumber, genesisHash);
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        assertEquals(STATUS, testMessage.getCommand());
        assertEquals(id, testMessage.getId());
        assertEquals(protocolVersion, testMessage.getProtocolVersion());
        assertEquals(networkId, testMessage.getNetworkId());
        assertArrayEquals(totalDifficulty.getBytes(), testMessage.getTotalDifficulty().getBytes());
        assertArrayEquals(bestHash.getBytes(), testMessage.getBestHash());
        assertArrayEquals(genesisHash, testMessage.getGenesisHash());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        StatusMessage decodedMessage = new StatusMessage(testMessage.getEncoded());
        assertArrayEquals(testMessage.getEncoded(), decodedMessage.getEncoded());
    }
}
