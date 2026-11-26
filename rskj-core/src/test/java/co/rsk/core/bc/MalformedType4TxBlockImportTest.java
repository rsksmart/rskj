/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.core.bc;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockBodyCodec;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Rskip546TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AuthorizationListCodec;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.bouncycastle.util.BigIntegers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block whose only transaction is a malformed or invalid type-4 transaction is not imported. Each case breaks one
 * part of an otherwise canonical type-4 envelope and puts it in the body of a real block. The block must fail to
 * decode on every path a peer can deliver it (a block message, the shared block decoder, and the body decoder used by
 * sync), so it never reaches the blockchain, and the importer's chain and state stay as they were.
 */
class MalformedType4TxBlockImportTest {

    private static final byte CHAIN_ID = Rskip545TestSupport.REGTEST_CHAIN_ID;
    private static final RskAddress RECEIVER = new RskAddress("0x00000000000000000000000000000000000000aa");
    private static final RskAddress DELEGATE = Rskip545TestSupport.DEFAULT_DELEGATE;
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(100_000);

    private static final String TRIE_ROOT_FAILURE = "Transactions trie root validation failed";
    private static final int TO_INDEX = 5;
    private static final int VALUE_INDEX = 6;
    private static final int AUTHORIZATION_LIST_INDEX = 9;

    // Named test accounts have fixed keys, so this is the same authority the worlds hold
    private static final SetCodeAuthorization AUTHORIZATION = Rskip545TestSupport.createSignedAuthorization(
            new AccountBuilder().name("authority").build().getEcKey(), DELEGATE, BigInteger.ZERO, CHAIN_ID);

    private World miner;
    private World importer;
    private Block validBlock;
    private Transaction validTx;

    @BeforeEach
    void setUp() {
        miner = newWorld();
        importer = newWorld();
        Account sender = miner.getAccountByName("sender");
        validTx = signedType4(sender, AUTHORIZATION);
        validBlock = new BlockBuilder(miner.getBlockChain(), miner.getBridgeSupportFactory(), miner.getBlockStore())
                .trieStore(miner.getTrieStore())
                .parent(miner.getBlockChain().getBestBlock())
                .transactions(Collections.singletonList(validTx))
                .build(miner.getConfig());
        assertEquals(1, validBlock.getTransactionsList().size(), "the miner must keep the type-4 tx");
        assertEquals(validBlock.getParentHash(), importer.getBlockChain().getBestBlock().getHash(), "both worlds must share the genesis");
    }

    @Test
    void rebuiltBlockWithTheValidType4Tx_isTheSameBlockAndImports() {
        byte[] rebuilt = blockWithRawTx(validTx.getEncoded());
        assertArrayEquals(validBlock.getEncoded(), rebuilt, "the test's block builder must match the node's encoding");

        Block received = factory().decodeBlock(rebuilt);

        assertEquals(ImportResult.IMPORTED_BEST, importer.getBlockChain().tryToConnect(received));
        assertEquals(validBlock.getHash(), importer.getBlockChain().getBestBlock().getHash());
    }

    @Test
    void canonicalBaseEnvelope_decodesAsABlockBodyTx() {
        // The base every malformed case starts from: it must decode, so each failure below comes from its one change
        byte[] baseTx = type4Bytes(baseFields());
        List<Transaction> decoded = BlockBodyCodec.decodeTransactions(RLP.decodeList(RLP.encodeList(RLP.encodeElement(baseTx))));

        assertEquals(1, decoded.size());
        assertEquals(TransactionType.TYPE_4, decoded.get(0).getType());
        assertEquals(List.of(AUTHORIZATION), decoded.get(0).getAuthorizationList());

        // In a block, the base tx decodes and the block decoder then stops at the header's tx trie root, which was
        // built for the signed tx. The malformed cases must fail earlier, on the tx itself.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> factory().decodeBlock(blockWithRawTx(baseTx)));
        assertTrue(e.getMessage().startsWith(TRIE_ROOT_FAILURE), e.getMessage());
    }

    static Stream<Arguments> malformedType4Txs() {
        return Stream.of(
                Arguments.of("empty authorization list", (UnaryOperator<byte[]>) base ->
                        type4Bytes(withField(AUTHORIZATION_LIST_INDEX, RLP.encodeList()))),
                Arguments.of("no to", (UnaryOperator<byte[]>) base ->
                        type4Bytes(withField(TO_INDEX, RLP.encodeElement(new byte[0])))),
                Arguments.of("undecodable tuple field (19-byte address)", (UnaryOperator<byte[]>) base ->
                        type4Bytes(withField(AUTHORIZATION_LIST_INDEX, RLP.encodeList(tupleWith(1, RLP.encodeElement(new byte[19])))))),
                Arguments.of("non-canonical RLP (value 1 as 0x8101)", (UnaryOperator<byte[]>) base ->
                        type4Bytes(withField(VALUE_INDEX, new byte[]{(byte) 0x81, 0x01}))),
                Arguments.of("wrong tuple shape (5 fields)", (UnaryOperator<byte[]>) base ->
                        type4Bytes(withField(AUTHORIZATION_LIST_INDEX, RLP.encodeList(tupleWithoutLastField())))),
                Arguments.of("tuple is a byte string, not a list", (UnaryOperator<byte[]>) base ->
                        type4Bytes(withField(AUTHORIZATION_LIST_INDEX, RLP.encodeList(RLP.encodeElement(new byte[]{0x01, 0x02}))))),
                Arguments.of("truncated envelope", (UnaryOperator<byte[]>) base ->
                        Arrays.copyOf(base, base.length - 1)),
                Arguments.of("trailing bytes", (UnaryOperator<byte[]>) base ->
                        ByteUtil.merge(base, new byte[]{0x00}))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedType4Txs")
    void blockWithMalformedType4Tx_failsToDecodeAndLeavesTheChainUnchanged(String name, UnaryOperator<byte[]> mutation) {
        byte[] malformedTx = mutation.apply(type4Bytes(baseFields()));
        byte[] blockBytes = blockWithRawTx(malformedTx);
        Block bestBefore = importer.getBlockChain().getBestBlock();

        RuntimeException e = assertThrows(RuntimeException.class, () -> factory().decodeBlock(blockBytes), "block decoder");
        assertFalse(String.valueOf(e.getMessage()).startsWith(TRIE_ROOT_FAILURE), "must fail on the tx, not the trie root: " + e.getMessage());
        assertThrows(RuntimeException.class, () -> Message.create(factory(), blockMessage(blockBytes)), "block message");
        assertThrows(RuntimeException.class,
                () -> BlockBodyCodec.decodeTransactions(RLP.decodeList(RLP.encodeList(RLP.encodeElement(malformedTx)))),
                "block body decoder");

        assertEquals(bestBefore.getHash(), importer.getBlockChain().getBestBlock().getHash());
        assertEquals(BigInteger.ZERO, Rskip545TestSupport.snapshotAtBest(importer)
                .getNonce(importer.getAccountByName("authority").getAddress()));
    }

    /** The well-formed message for the same block: the malformed cases fail because of the tx, not the framing. */
    @Test
    void blockMessageWithTheValidType4Tx_decodes() {
        Message message = Message.create(factory(), blockMessage(validBlock.getEncoded()));

        assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());
        assertEquals(validBlock.getHash(), ((BlockMessage) message).getBlock().getHash());
    }

    private BlockFactory factory() {
        return new BlockFactory(importer.getConfig().getActivationConfig());
    }

    private byte[] blockWithRawTx(byte[] rawTx) {
        return RLP.encodeList(
                validBlock.getHeader().getFullEncoded(),
                RLP.encodeList(RLP.encodeElement(rawTx)),
                RLP.encodeList());
    }

    private static RLPList blockMessage(byte[] blockBytes) {
        byte[] body = RLP.encodeList(RLP.encodeElement(blockBytes));
        byte[] wire = RLP.encodeList(RLP.encodeByte(MessageType.BLOCK_MESSAGE.getTypeAsByte()), RLP.encodeElement(body));
        return (RLPList) RLP.decode2(wire).get(0);
    }

    private static byte[][] baseFields() {
        return new byte[][]{
                RLP.encodeByte(CHAIN_ID),
                RLP.encodeElement(new byte[0]),
                RLP.encodeCoinNonNullZero(Coin.valueOf(1)),
                RLP.encodeCoinNonNullZero(Coin.valueOf(1)),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(GAS_LIMIT)),
                RLP.encodeRskAddress(RECEIVER),
                RLP.encodeBigInteger(BigInteger.ONE),
                RLP.encodeElement(new byte[0]),
                Rskip545TestSupport.EMPTY_ACCESS_LIST,
                AuthorizationListCodec.encodeList(List.of(AUTHORIZATION)),
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(Rskip546TestSupport.signatureWord()),
                RLP.encodeElement(Rskip546TestSupport.signatureWord())
        };
    }

    private static byte[][] withField(int index, byte[] encodedField) {
        byte[][] fields = baseFields();
        fields[index] = encodedField;
        return fields;
    }

    private static byte[] tupleWith(int index, byte[] encodedField) {
        byte[][] fields = tupleFields(AUTHORIZATION);
        fields[index] = encodedField;
        return RLP.encodeList(fields);
    }

    private static byte[] tupleWithoutLastField() {
        byte[][] fields = tupleFields(AUTHORIZATION);
        return RLP.encodeList(Arrays.copyOf(fields, fields.length - 1));
    }

    private static byte[][] tupleFields(SetCodeAuthorization auth) {
        RLPList tuple = RLP.decodeList(AuthorizationListCodec.encodeTuple(auth));
        byte[][] fields = new byte[tuple.size()][];
        for (int i = 0; i < tuple.size(); i++) {
            byte[] data = tuple.get(i).getRLPData();
            fields[i] = RLP.encodeElement(data == null ? new byte[0] : data);
        }
        return fields;
    }

    private static byte[] type4Bytes(byte[][] fields) {
        return Rskip545TestSupport.buildRawType4Bytes(fields);
    }

    private static Transaction signedType4(Account sender, SetCodeAuthorization authorization) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .nonce(BigInteger.ZERO)
                .gasLimit(GAS_LIMIT)
                .receiveAddress(RECEIVER)
                .value(Coin.valueOf(1))
                .chainId(CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(1))
                .authorizationList(List.of(authorization))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static World newWorld() {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip351", ConfigValueFactory.fromAnyRef(0)));
        World world = new World(config);
        world.saveAccount("sender", new AccountBuilder(world).name("sender").balance(Coin.valueOf(1_000_000_000_000L)).build());
        world.saveAccount("authority", new AccountBuilder(world).name("authority").build());
        return world;
    }
}
