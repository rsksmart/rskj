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
package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.db.HashMapBlocksIndex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

class SnapStatusResponseMessageTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final BlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
    private final Block block4Test = new BlockGenerator().getBlock(1);
    private final List<Block> blockList = Collections.singletonList(new BlockGenerator().getBlock(1));
    private final List<BlockDifficulty> blockDifficulties = Collections.singletonList(indexedBlockStore.getTotalDifficultyForHash(block4Test.getHash().getBytes()));
    private final long trieSize = 1L;
    private final SnapStatusResponseMessage underTest = new SnapStatusResponseMessage(1, blockList, blockDifficulties, trieSize);


    @Test
    void getMessageType_returnCorrectMessageType() {
        //given-when
        MessageType messageType = underTest.getMessageType();

        //then
        assertEquals(MessageType.SNAP_STATUS_RESPONSE_MESSAGE, messageType);
    }

    @Test
    void getEncodedMessage_returnExpectedByteArray() {
        //given default block 4 test
        byte[] expectedEncodedMessage = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.valueOf(underTest.getId())),
                RLP.encodeList(
                        RLP.encodeList(RLP.encode(block4Test.getEncoded())),
                        RLP.encodeList(RLP.encode(blockDifficulties.get(0).getBytes())),
                        RLP.encodeBigInteger(BigInteger.valueOf(this.trieSize))));
        //when
        byte[] encodedMessage = underTest.getEncodedMessage();

        //then
        assertThat(encodedMessage, equalTo(expectedEncodedMessage));
    }

    @Test
    void decodeMessage_returnExpectedMessage() {
        //given default block 4 test
        RLPList encodedRLPList = (RLPList) RLP.decode2(underTest.getEncodedMessage()).get(0);

        //when
        Message decodedMessage = SnapStatusResponseMessage.decodeMessage(blockFactory, encodedRLPList);

        //then
        assertInstanceOf(SnapStatusResponseMessage.class, decodedMessage);
        assertEquals(underTest.getId(), ((SnapStatusResponseMessage) decodedMessage).getId());
        assertEquals(1, ((SnapStatusResponseMessage) decodedMessage).getBlocks().size());
        assertEquals(underTest.getBlocks().get(0).getHash(), ((SnapStatusResponseMessage) decodedMessage).getBlocks().get(0).getHash());
        assertEquals(1, ((SnapStatusResponseMessage) decodedMessage).getDifficulties().size());
        assertEquals(underTest.getDifficulties().get(0), ((SnapStatusResponseMessage) decodedMessage).getDifficulties().get(0));
    }

    @Test
    void getDifficulties_returnTheExpectedValue() {
        //given default block 4 test

        //when
        List<BlockDifficulty> difficultiesReturned = underTest.getDifficulties();
        //then
        assertThat(difficultiesReturned, equalTo(blockDifficulties));
    }

    @Test
    void getBlocks_returnTheExpectedValue() {
        //given default block 4 test

        //when
        List<Block> blocksReturned = underTest.getBlocks();
        //then
        assertThat(blocksReturned, equalTo(blockList));
    }

    @Test
    void getTrieSize_returnTheExpectedValue() {
        //given default block 4 test

        //when
        long trieSizeReturned = underTest.getTrieSize();
        //then
        assertThat(trieSizeReturned, equalTo(trieSize));
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedForMessage() {
        //given
        SnapStatusResponseMessage message = new SnapStatusResponseMessage(1, blockList, blockDifficulties, trieSize);
        MessageVisitor visitor = mock(MessageVisitor.class);

        //when
        message.accept(visitor);

        //then
        verify(visitor, times(1)).apply(message);
    }
}