/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.core;

import co.rsk.config.RskMiningConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegTestUtils;
import com.google.common.primitives.Bytes;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.System.arraycopy;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class BlockHeaderTest {

    @Test
    void getHashForMergedMiningWithForkDetectionDataAndIncludedOnAndMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true, new byte[0], new short[0]);

        byte[] encodedBlock = header.getEncoded(false, false, false);
        byte[] hashForMergedMiningPrefix = Arrays.copyOfRange(HashUtil.keccak256(encodedBlock), 0, 20);
        byte[] forkDetectionData = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] hashForMergedMining = concatenate(hashForMergedMiningPrefix, forkDetectionData);
        byte[] coinbase = concatenate(RskMiningConstants.RSK_TAG, hashForMergedMining);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        byte[] hashForMergedMiningResult = header.getHashForMergedMining();

        MatcherAssert.assertThat(hashForMergedMining, is(hashForMergedMiningResult));
    }

    @Test
    void getHashForMergedMiningWithNoForkDetectionDataAndIncludedOffAndMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0], new short[0]);

        Keccak256 hash = header.getHash();
        byte[] hashForMergedMining = header.getHashForMergedMining();

        Assertions.assertNotEquals(hashForMergedMining, hash.getBytes());
    }

    @Test
    void getHashForMergedMiningWithForkDetectionDataAndIncludedOn() {
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(forkDetectionData, true, new byte[0], new short[0]);

        byte[] hash = header.getHash().getBytes();
        byte[] hashFirst20Elements = Arrays.copyOfRange(hash, 0, 20);
        byte[] expectedHash = Bytes.concat(hashFirst20Elements, forkDetectionData);

        byte[] hashForMergedMining = header.getHashForMergedMining();

        MatcherAssert.assertThat(expectedHash, is(hashForMergedMining));
    }

    @Test
    void getHashForMergedMiningWithForkDetectionDataAndIncludedOff() {
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(forkDetectionData, false, new byte[0], new short[0]);

        byte[] hash = header.getHash().getBytes();
        byte[] hashForMergedMining = header.getHashForMergedMining();

        MatcherAssert.assertThat(hash, is(hashForMergedMining));
    }


    @Test
    void getEncodedWithUmmRootWithMergedMiningFields() {
        byte[] ummRoot = TestUtils.randomBytes(20);
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, ummRoot, new short[0]);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(21));
    }

    @Test
    void getEncodedWithUmmRootWithoutMergedMiningFields() {
        byte[] ummRoot = TestUtils.randomBytes(20);
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, ummRoot, new short[0]);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(18));
    }

    @Test
    void getEncodedNullUmmRootWithMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, null, new short[0]);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(20));
    }

    @Test
    void getEncodedNullUmmRootWithoutMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, null, new short[0]);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(17));
    }

    @Test
    void getEncodedEmptyUmmRootWithMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0], new short[0]);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(21));
    }

    @Test
    void getEncodedEmptyUmmRootWithoutMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, new byte[0], new short[0]);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(18));
    }

    @Test
    void getEncodedWithEdgesWithMergedMiningFields() {
        short[] edges = TestUtils.randomShortArray(4);
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0], edges);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(21));
    }

    @Test
    void getEncodedWithEdgesWithoutMergedMiningFields() {
        short[] edges = TestUtils.randomShortArray(4);
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, new byte[0], edges);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(18));
    }

    @Test
    void getEncodedNullEdgesWithMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0], null);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(20));
    }

    @Test
    void getEncodedNullEdgesWithoutMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, new byte[0], null);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(17));
    }

    @Test
    void getEncodedWithEdgesAndUmmRootWithMergedMiningFields() {
        byte[] ummRoot = TestUtils.randomBytes(20);
        short[] edges = TestUtils.randomShortArray(4);
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, ummRoot, edges);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(21));
    }

    @Test
    void getEncodedWithEdgesAndUmmRootWithoutMergedMiningFields() {
        byte[] ummRoot = TestUtils.randomBytes(20);
        short[] edges = TestUtils.randomShortArray(4);
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, ummRoot, edges);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        MatcherAssert.assertThat(headerRLP.size(), is(18));
    }


    @Test
    void getMiningForkDetectionData() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true, new byte[0], new short[0]);

        byte[] encodedBlock = header.getEncoded(false, false, false);
        byte[] hashForMergedMining = Arrays.copyOfRange(HashUtil.keccak256(encodedBlock), 0, 20);
        byte[] forkDetectionData = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] coinbase = concatenate(hashForMergedMining, forkDetectionData);
        coinbase = concatenate(RskMiningConstants.RSK_TAG, coinbase);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        MatcherAssert.assertThat(forkDetectionData, is(header.getMiningForkDetectionData()));
    }

    @Test
    void getUmmRoot() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        MatcherAssert.assertThat(header.getUmmRoot(), is(ummRoot));
    }

    @Test
    void isUMMBlockWhenUmmRoot() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        MatcherAssert.assertThat(header.isUMMBlock(), is(true));
    }

    @Test
    void isUMMBlockWhenNullUmmRoot() {
        byte[] ummRoot = null;
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        MatcherAssert.assertThat(header.isUMMBlock(), is(false));
    }

    @Test
    void isUMMBlockWhenEmptyUmmRoot() {
        byte[] ummRoot = new byte[0];
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        MatcherAssert.assertThat(header.isUMMBlock(), is(false));
    }

    @Test
    void getHashForMergedMiningWhenUmmRoot() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        byte[] forkDetectionData = new byte[]{20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot, forkDetectionData);

        byte[] encodedBlock = header.getEncoded(false, false, false);
        byte[] oldHashForMergedMining = HashUtil.keccak256(encodedBlock);
        byte[] leftHash = Arrays.copyOf(oldHashForMergedMining, 20);
        byte[] hashRoot = HashUtil.keccak256(concatenate(leftHash, ummRoot));
        byte[] newHashForMergedMining = concatenate(
                java.util.Arrays.copyOfRange(hashRoot, 0, 20),
                forkDetectionData
        );

        MatcherAssert.assertThat(header.getHashForMergedMining(), is(newHashForMergedMining));
    }

    @Test
    void getHashForMergedMiningWhenEmptyUmmRoot() {
        byte[] ummRoot = new byte[0];

        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        byte[] encodedBlock = header.getEncoded(false, false, false);
        byte[] hashForMergedMining = HashUtil.keccak256(encodedBlock);

        MatcherAssert.assertThat(header.getHashForMergedMining(), is(hashForMergedMining));
    }

    @Test
    void getHashForMergedMiningWhenUmmRootWithLengthUnder20() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        Assertions.assertThrows(IllegalStateException.class, header::getHashForMergedMining);
    }

    @Test
    void getHashForMergedMiningWhenUmmRootWithLengthOver20() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        Assertions.assertThrows(IllegalStateException.class, header::getHashForMergedMining);
    }

    /**
     * This case is an error and should never happen in production
     */
    @Test
    void getMiningForkDetectionDataNoDataCanBeFound() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true, new byte[0], new short[0]);

        byte[] forkDetectionData = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] coinbase = concatenate(RskMiningConstants.RSK_TAG, forkDetectionData);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        Assertions.assertThrows(IllegalStateException.class, header::getMiningForkDetectionData);
    }

    @Test
    void getMiningForkDetectionDataNoDataMustBeIncluded() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0], new short[0]);

        MatcherAssert.assertThat(new byte[0], is(header.getMiningForkDetectionData()));
    }

    @Test
    void getHashShouldReuseCalculatedValue() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0], new short[0]);
        byte[] headerEncoded = header.getEncoded();

        try (MockedStatic<HashUtil> hashUtilMocked = mockStatic(HashUtil.class)) {
            hashUtilMocked.when(() -> HashUtil.keccak256(headerEncoded)).thenReturn(Keccak256.ZERO_HASH.getBytes());

            for (int i = 0; i < 5; i++) {
                header.getHash();
            }

            hashUtilMocked.verify(() -> HashUtil.keccak256(ArgumentMatchers.any()));
        }
    }

    @Test
    void verifyRecalculatedHashForAmendedBlocks() {
        BlockHeader header = createBlockHeader(new byte[80], new byte[32], new byte[128], new byte[0],
                false, new byte[0], new short[0], false, false);

        assertArrayEquals(HashUtil.keccak256(header.getEncodedForHash()), header.getHash().getBytes());

        List<Consumer<BlockHeader>> stateModifiers = Arrays.asList(
                h -> h.setBitcoinMergedMiningCoinbaseTransaction(HashUtil.keccak256("BitcoinMergedMiningCoinbaseTransaction".getBytes())),
                h -> h.setBitcoinMergedMiningHeader(HashUtil.keccak256("BitcoinMergedMiningHeader".getBytes())),
                h -> h.setBitcoinMergedMiningMerkleProof(HashUtil.keccak256("BitcoinMergedMiningMerkleProof".getBytes())),
                h -> h.setDifficulty(header.getDifficulty().add(BlockDifficulty.ONE)),
                h -> h.setGasUsed(header.getGasUsed() + 10),
                h -> h.setLogsBloom(HashUtil.keccak256("LogsBloom".getBytes())),
                h -> h.setPaidFees(header.getPaidFees().add(Coin.valueOf(10))),
                h -> h.setReceiptsRoot(HashUtil.keccak256("ReceiptsRoot".getBytes())),
                h -> h.setStateRoot(HashUtil.keccak256("StateRoot".getBytes())),
                h -> h.setTransactionsRoot(HashUtil.keccak256("TransactionsRoot".getBytes())),
                BlockHeader::seal
        );

        stateModifiers.forEach(sm -> {
            sm.accept(header);
            assertArrayEquals(HashUtil.keccak256(header.getEncodedForHash()),
                    header.getHash().getBytes(),
                    "Block header returned invalid hash after modification");
        });
    }

    private void testHeaderVersion(byte version) {
        BlockHeader header = createBlockHeaderWithVersion(version);
        assertEquals(version, header.getVersion());
    }

    @Test
    public void getVersion0() { this.testHeaderVersion((byte) 0x0); }

    @Test
    public void getVersion1() {
        this.testHeaderVersion((byte) 0x1);
    }

    private static byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
    private static short[] edges = new short[]{ 1, 2, 3, 4 };

    @BeforeAll
    static void setupLogsBloom() {
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;
    }

    @Test
    public void encodeForLogsBloomField() {
        BlockHeaderV1 header = (BlockHeaderV1) createBlockHeaderWithVersion((byte) 0x1);
        header.setLogsBloom(logsBloom);
        header.setTxExecutionSublistsEdges(edges);

        RLPList extensionDataRLP = RLP.decodeList(header.getExtensionData());
        byte version = extensionDataRLP.get(0).getRLPData()[0];
        byte[] extensionHash = extensionDataRLP.get(1).getRLPData();

        Assertions.assertEquals((byte) 0x1, version);
        Assertions.assertArrayEquals(header.getExtension().getHash(), extensionHash);
    }

    @Test
    public void encodeForLogsBloomFieldIsNotLogsBloomSize() {
        // this test is added to assert the blockchain is still serializable
        // it is still possible to serialize wether logs bloom field is the
        // actual logs bloom or the extension data by reading its length != 256
        BlockHeaderV1 header = (BlockHeaderV1) createBlockHeaderWithVersion((byte) 0x1);
        header.setLogsBloom(logsBloom);
        header.setTxExecutionSublistsEdges(edges);

        byte[] extensionData = header.getExtensionData();

        Assertions.assertNotEquals(Bloom.BLOOM_BYTES, extensionData.length);
    }

    private BlockHeaderV1 createV1FromV0(BlockHeaderV0 headerV0) {
        return new BlockHeaderV1(
                headerV0.getParentHash().getBytes(), headerV0.getUnclesHash(), headerV0.getCoinbase(), headerV0.getStateRoot(),
                headerV0.getTxTrieRoot(), headerV0.getReceiptsRoot(), headerV0.getLogsBloom(), headerV0.getDifficulty(),
                headerV0.getNumber(), headerV0.getGasLimit(), headerV0.getGasUsed(), headerV0.getTimestamp(), headerV0.getExtraData(),
                headerV0.getPaidFees(), headerV0.getBitcoinMergedMiningHeader(), headerV0.getBitcoinMergedMiningMerkleProof(),
                headerV0.getBitcoinMergedMiningCoinbaseTransaction(), headerV0.getMiningForkDetectionData(),
                headerV0.getMinimumGasPrice(), headerV0.getUncleCount(), headerV0.isSealed(),
                false, false, headerV0.getUmmRoot(), headerV0.getTxExecutionSublistsEdges()
        );
    }

    private void testEncodingButVersion(BlockHeaderV0 headerV0, BlockHeaderV1 headerV1) {
        RLPList rlpHeaderV0 = RLP.decodeList(headerV0.getEncoded());
        RLPList rlpHeaderV1 = RLP.decodeList(headerV1.getEncoded());

        for (int i = 0; i < rlpHeaderV0.size(); i++) {
            // jump version field
            Assertions.assertArrayEquals(rlpHeaderV0.get(i).getRLPData(), rlpHeaderV1.get(i > 15 ? i + 1 : i).getRLPData());
        }
    }

    @Test
    public void encodedV0IsTheSameForV0andV1 () {
        BlockHeaderV0 headerV0 = (BlockHeaderV0) createBlockHeaderWithVersion((byte) 0x0);
        headerV0.setLogsBloom(logsBloom);

        BlockHeaderV1 headerV1 = createV1FromV0(headerV0);

        testEncodingButVersion(headerV0, headerV1);
    }

    @Test
    public void fullEncodedV0IsTheSameForV0andV1 () {
        BlockHeaderV0 headerV0 = (BlockHeaderV0) createBlockHeaderWithVersion((byte) 0x0);
        headerV0.setLogsBloom(logsBloom);

        BlockHeaderV1 headerV1 = createV1FromV0(headerV0);

        testEncodingButVersion(headerV0, headerV1);
    }

    @Test
    public void fullEncodedV0IsTheSameAsEncodedForHeaderMessage () {
        BlockHeaderV0 headerV0 = (BlockHeaderV0) createBlockHeaderWithVersion((byte) 0x0);
        headerV0.setLogsBloom(logsBloom);

        Assertions.assertArrayEquals(headerV0.getFullEncoded(), headerV0.getEncodedCompressed());
    }

    @Test
    public void fullEncodedV1IsTheSameAsCompressedButLogsBloomEdgesAndVersion () {
        // this test is added to assert that there were no changes in the rest of the elements
        BlockHeaderV1 headerV1 = (BlockHeaderV1) createBlockHeaderWithVersion((byte) 0x1);
        headerV1.setLogsBloom(logsBloom);

        RLPList fullEncoded = RLP.decodeList(headerV1.getFullEncoded());
        RLPList encodedCompressed = RLP.decodeList(headerV1.getEncodedCompressed());

        // extension data takes the element of logs bloom
        // version element is not existent, is inside the rlp list od the extension data
        // edges is not existent
        int sizeDifference = 2;
        Assertions.assertEquals(fullEncoded.size() - sizeDifference, encodedCompressed.size());

        for (int i = 0; i < encodedCompressed.size(); i++) {
            int j = i < 16 ? i : i + sizeDifference; // keep comparing elements jumping version and edges
            if (i != 6) {
                // logs bloom field
                Assertions.assertArrayEquals(fullEncoded.get(j).getRLPData(), encodedCompressed.get(i).getRLPData());
            }
        }
    }

    @Test
    public void compressedEncodingV1HasSameRLPSizeAsFullEncodedV0WithoutEdges () {
        // this test is added to assert that the rlp header size does not change
        // in the hard fork, assuming both RSKIP 351 and RSKIP 144 are activated
        // together
        BlockHeaderV0 headerV0 = (BlockHeaderV0) createBlockHeaderWithVersion((byte) 0x0);
        headerV0.setLogsBloom(logsBloom);
        headerV0.setTxExecutionSublistsEdges(null);

        BlockHeaderV1 headerV1 = (BlockHeaderV1) createBlockHeaderWithVersion((byte) 0x1);
        headerV1.setLogsBloom(logsBloom);
        headerV1.setTxExecutionSublistsEdges(null);

        RLPList compressedEncodingV1 = RLP.decodeList(headerV1.getEncodedCompressed());
        RLPList fullEncodedV0 = RLP.decodeList(headerV0.getFullEncoded());

        Assertions.assertEquals(fullEncodedV0.size(), compressedEncodingV1.size());
    }

    @Test
    public void hashOfV1IncludesLogsBloom() {
        BlockHeaderV1 headerV1 = (BlockHeaderV1) createBlockHeaderWithVersion((byte) 0x1);

        headerV1.setLogsBloom(logsBloom);
        byte[] hash = headerV1.getHash().getBytes();

        byte[] otherLogsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x05;
        headerV1.setLogsBloom(otherLogsBloom);

        Assertions.assertFalse(Arrays.equals(hash, headerV1.getHash().getBytes()));
    }

    @Test
    public void hashOfV1IncludesEdges() {
        BlockHeaderV1 headerV1 = (BlockHeaderV1) createBlockHeaderWithVersion((byte) 0x1);

        headerV1.setTxExecutionSublistsEdges(edges);
        byte[] hash = headerV1.getHash().getBytes();


        short[] otherEdges = new short[]{ 1, 2, 3, 5};
        headerV1.setTxExecutionSublistsEdges(otherEdges);

        Assertions.assertFalse(Arrays.equals(hash, headerV1.getHash().getBytes()));
    }

    private BlockHeader createBlockHeaderWithMergedMiningFields(
            byte[] forkDetectionData,
            boolean includeForkDetectionData, byte[] ummRoot, short[] edges){
        return createBlockHeader(new byte[80], new byte[32], new byte[128],
                forkDetectionData, includeForkDetectionData, ummRoot, edges, false);
    }

    private BlockHeader createBlockHeaderWithNoMergedMiningFields(
            byte[] forkDetectionData,
            boolean includeForkDetectionData, byte[] ummRoot, short[] edges) {
        return createBlockHeader(null, null, null,
                forkDetectionData, includeForkDetectionData, ummRoot, edges, true);
    }

    private BlockHeader createBlockHeaderWithUmmRoot(byte[] ummRoot) {
        return createBlockHeader(null, null, null,
                new byte[0], false, ummRoot, new short[0], true);
    }

    private BlockHeader createBlockHeaderWithUmmRoot(byte[] ummRoot, byte[] forkDetectionData) {
        return createBlockHeader(null, null, null,
                                 forkDetectionData, true, ummRoot, new short[0], true);
    }

    private BlockHeader createBlockHeader(byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                                          byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] forkDetectionData,
                                          boolean includeForkDetectionData, byte[] ummRoot, short[] edges, boolean sealed) {
        return createBlockHeader(bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                forkDetectionData, includeForkDetectionData, ummRoot, edges, true, sealed);
    }

    private BlockHeader createBlockHeader(byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                                          byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] forkDetectionData,
                                          boolean includeForkDetectionData, byte[] ummRoot, short[] edges,
                                          boolean useRskip92Encoding,
                                          boolean sealed) {
        return createBlockHeader((byte) 0x0, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                forkDetectionData, includeForkDetectionData, ummRoot, edges, useRskip92Encoding, sealed);
    }

    private BlockHeader createBlockHeaderWithVersion(byte version) {
        return createBlockHeader(version, new byte[80], new byte[32], new byte[128],
                new byte[0], false, null, new short[0], false, false);
    }

    private BlockHeader createBlockHeader(byte version,
                                          byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                                          byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] forkDetectionData,
                                          boolean includeForkDetectionData, byte[] ummRoot, short[] edges, boolean useRskip92Encoding,
                                          boolean sealed) {
        BlockDifficulty difficulty = new BlockDifficulty(BigInteger.ONE);
        long number = 1;
        BigInteger gasLimit = BigInteger.valueOf(6800000);
        long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

        if (version == 0x1) return new BlockHeaderV1(
                PegTestUtils.createHash3().getBytes(),
                HashUtil.keccak256(RLP.encodeList()),
                new RskAddress(TestUtils.randomAddress().getBytes()),
                HashUtil.EMPTY_TRIE_HASH,
                "tx_trie_root".getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                new Bloom().getData(),
                difficulty,
                number,
                gasLimit.toByteArray(),
                3000000,
                timestamp,
                new byte[0],
                Coin.ZERO,
                bitcoinMergedMiningHeader,
                bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction,
                forkDetectionData,
                Coin.valueOf(10L),
                0,
                sealed,
                useRskip92Encoding,
                includeForkDetectionData,
                ummRoot,
                edges);

        return new BlockHeaderV0(
                PegTestUtils.createHash3().getBytes(),
                HashUtil.keccak256(RLP.encodeList()),
                new RskAddress(TestUtils.randomAddress().getBytes()),
                HashUtil.EMPTY_TRIE_HASH,
                "tx_trie_root".getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                new Bloom().getData(),
                difficulty,
                number,
                gasLimit.toByteArray(),
                3000000,
                timestamp,
                new byte[0],
                Coin.ZERO,
                bitcoinMergedMiningHeader,
                bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction,
                forkDetectionData,
                Coin.valueOf(10L),
                0,
                sealed,
                useRskip92Encoding,
                includeForkDetectionData,
                ummRoot,
                edges);
    }

    private byte[] concatenate(byte[] left, byte[] right) {
        byte[] leftRight = Arrays.copyOf(left, left.length + right.length);
        arraycopy(right, 0, leftRight, left.length, right.length);
        return leftRight;
    }
}
