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
import co.rsk.net.light.LightStatus;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.core.BlockFactory;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static co.rsk.net.light.LightClientMessageCodes.STATUS;
import static org.ethereum.crypto.HashUtil.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;


public class StatusMessageTest {

    private Keccak256 bestHash;
    private byte[] genesisHash;

    @Before
    public void setUp() {
        bestHash = new Keccak256(randomHash());
        genesisHash = randomHash();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        BlockDifficulty totalDifficulty = new BlockDifficulty(BigInteger.ONE);
        byte protocolVersion = (byte) 2;
        int networkId = 2;
        long bestNumber = 10L;
        LightStatus lightStatus = new LightStatus(protocolVersion, networkId,
                totalDifficulty, bestHash.getBytes(), bestNumber, genesisHash);

        long id = 1L;
        StatusMessage testMessage = new StatusMessage(id, lightStatus);

        assertEquals(STATUS, testMessage.getCommand());
        assertEquals(id, testMessage.getId());
        assertArrayEquals(lightStatus.getEncoded(), testMessage.getStatus().getEncoded());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        long id = 1L;
        BlockDifficulty totalDifficulty = new BlockDifficulty(BigInteger.ONE);
        byte protocolVersion = (byte) 2;
        int networkId = 2;
        long bestNumber = 10L;

        createMessageAndAssertEncodeDecode(id, protocolVersion, networkId, totalDifficulty, bestNumber);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrectWithZeroParameters() {
        long id = 0;
        BlockDifficulty totalDifficulty = new BlockDifficulty(BigInteger.ZERO);
        byte protocolVersion = (byte) 0;
        int networkId = 0;
        long bestNumber = 0;

        createMessageAndAssertEncodeDecode(id, protocolVersion, networkId, totalDifficulty, bestNumber);
    }

    private void createMessageAndAssertEncodeDecode(long id, byte protocolVersion, int networkId, BlockDifficulty totalDifficulty, long bestNumber) {
        LightStatus lightStatus = new LightStatus(protocolVersion, networkId,
                totalDifficulty, bestHash.getBytes(), bestNumber, genesisHash);
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        StatusMessage testMessage = new StatusMessage(id, lightStatus);
        StatusMessage decodedMessage = (StatusMessage) lcMessageFactory.create(STATUS.asByte(), testMessage.getEncoded());
        assertArrayEquals(testMessage.getEncoded(), decodedMessage.getEncoded());
    }
}
