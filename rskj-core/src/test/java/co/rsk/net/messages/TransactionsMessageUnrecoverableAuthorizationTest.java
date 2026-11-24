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
package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.sync.SnapProcessor;
import co.rsk.net.SyncProcessor;
import co.rsk.net.BlockProcessor;
import co.rsk.net.TransactionGateway;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.db.TransactionInfo;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RskTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A peer sends a transactions message holding a legacy tx, a type-4 tx whose only authorization has an r that is not
 * the x coordinate of any secp256k1 point (so no authority can be recovered from it), and another legacy tx. The
 * signature of the tuple is only checked when the tx runs, so the message decodes, the peer is not punished, the pool
 * admits the type-4 tx, and in a block the tx succeeds while its tuple is skipped.
 */
class TransactionsMessageUnrecoverableAuthorizationTest {

    // 5^3 + 7 is not a square mod p, so x = 5 has no point on secp256k1; r = 5 is still in [1, n)
    private static final BigInteger R_WITH_NO_CURVE_POINT = BigInteger.valueOf(5);
    private static final RskAddress DELEGATE = Rskip545TestSupport.DEFAULT_DELEGATE;
    private static final RskAddress RECEIVER = new RskAddress("0x00000000000000000000000000000000000000aa");
    private static final String BALANCE_IN_WEI = "1000000000000000000";

    @TempDir
    public Path tempDir;

    private final Account firstLegacySender = new AccountBuilder().name("firstLegacySender").build();
    private final Account type4Sender = new AccountBuilder().name("type4Sender").build();
    private final Account secondLegacySender = new AccountBuilder().name("secondLegacySender").build();
    private final SetCodeAuthorization unrecoverable = unrecoverableAuthorization();

    private RskTestContext context;
    private TransactionPoolImpl pool;
    private Block genesis;
    private List<Transaction> sent;

    @BeforeEach
    void setUp() {
        context = new RskTestContext(tempDir, "--regtest") {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), genesisJson(), BigInteger.ZERO, true, true, true);
            }

            @Override
            protected BlockValidator buildBlockValidator() {
                // header and proof of work rules are out of scope; blocks are still executed and their state root checked
                return new DummyBlockValidator();
            }
        };
        pool = (TransactionPoolImpl) context.getTransactionPool();
        genesis = context.getBlockchain().getBestBlock();
        // not calling start() to avoid creating cleaner threads
        pool.processBest(genesis);
        assertTrue(context.getRskSystemProperties().getActivationConfig().isActive(ConsensusRule.RSKIP545, 1));

        sent = List.of(legacyTx(firstLegacySender), type4Tx(), legacyTx(secondLegacySender));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void tupleR_isNotACurveXCoordinate_andNoAuthorityCanBeRecovered() {
        byte[] compressed = ByteUtil.merge(new byte[]{0x02}, ByteUtil.bigIntegerToBytes(R_WITH_NO_CURVE_POINT, 32));
        assertThrows(IllegalArgumentException.class, () -> ECKey.CURVE.getCurve().decodePoint(compressed));

        ECKey recovered;
        try {
            recovered = Secp256k1.getInstance().signatureToKey(unrecoverable.getSigningHash(), unrecoverable.getSignature());
        } catch (Exception e) {
            recovered = null;
        }
        assertEquals(null, recovered);
    }

    @Test
    void transactionsMessage_decodesAllThreeTransactions() {
        List<Transaction> received = receive();

        assertEquals(3, received.size());
        assertEquals(hashes(sent), hashes(received));
        assertEquals(TransactionType.LEGACY, received.get(0).getType());
        assertEquals(TransactionType.TYPE_4, received.get(1).getType());
        assertEquals(TransactionType.LEGACY, received.get(2).getType());
        assertEquals(List.of(unrecoverable), received.get(1).getAuthorizationList());
        assertEquals(type4Sender.getAddress(), received.get(1).getSender());
    }

    @Test
    void messageVisitor_passesAllThreeToTheGateway_andScoresEachAsValid() {
        RskSystemProperties config = mock(RskSystemProperties.class);
        when(config.getNetworkConstants()).thenReturn(Constants.regtest());
        TransactionGateway gateway = mock(TransactionGateway.class);
        PeerScoringManager scoring = mock(PeerScoringManager.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);
        Peer peer = mock(Peer.class);
        when(peer.getPeerNodeID()).thenReturn(new NodeID(new byte[]{0x01}));
        when(peer.getAddress()).thenReturn(InetAddress.getLoopbackAddress());

        MessageVisitor visitor = new MessageVisitor(config, blockProcessor, mock(SyncProcessor.class),
                mock(SnapProcessor.class), gateway, scoring, mock(ChannelManager.class), peer);
        visitor.apply(new TransactionsMessage(receive()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> passed = ArgumentCaptor.forClass(List.class);
        verify(gateway).receiveTransactionsFrom(passed.capture(), any());
        assertEquals(hashes(sent), hashes(passed.getValue()));

        List<Object> events = mockingDetails(scoring).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("recordEvent"))
                .map(i -> i.getArgument(2))
                .collect(Collectors.toList());
        assertEquals(List.of(EventType.VALID_TRANSACTION, EventType.VALID_TRANSACTION, EventType.VALID_TRANSACTION), events);
    }

    @Test
    void pool_admitsTheType4TransactionAndBothLegacyOnes() {
        for (Transaction tx : receive()) {
            TransactionPoolAddResult result = pool.addTransaction(tx);
            assertTrue(result.pendingTransactionsWereAdded(), tx.getType() + " tx must be admitted: " + result.getErrorMessage());
        }

        assertEquals(hashes(sent).stream().sorted().collect(Collectors.toList()),
                pool.getPendingTransactions().stream().map(Transaction::getHash).sorted().collect(Collectors.toList()));
    }

    @Test
    void blockWithTheThreeTransactions_imports_andOnlyTheTupleIsSkipped() {
        List<Transaction> received = receive();
        Block block = new BlockGenerator().createChildBlock(genesis, received);
        context.getBlockExecutor().executeAndFillAll(block, genesis.getHeader());
        block.seal();

        assertEquals(ImportResult.IMPORTED_BEST, context.getBlockchain().tryToConnect(block));
        assertEquals(3, block.getTransactionsList().size());
        for (Transaction tx : received) {
            TransactionInfo info = context.getReceiptStore().get(tx.getHash().getBytes(), block.getHash().getBytes()).orElseThrow();
            assertTrue(info.getReceipt().isSuccessful(), tx.getType() + " tx must succeed");
        }

        var state = context.getRepositoryLocator().snapshotAt(block.getHeader());
        assertEquals(BigInteger.ONE, state.getNonce(type4Sender.getAddress()));
        assertFalse(state.isActiveDelegatedEOA(type4Sender.getAddress()));
        assertEquals(Coin.valueOf(3), state.getBalance(RECEIVER));
    }

    private List<Transaction> receive() {
        byte[] wire = new TransactionsMessage(sent).getEncoded();
        BlockFactory factory = new BlockFactory(context.getRskSystemProperties().getActivationConfig());
        return ((TransactionsMessage) Message.create(factory, wire)).getTransactions();
    }

    private static List<Keccak256> hashes(Collection<Transaction> txs) {
        return txs.stream().map(Transaction::getHash).collect(Collectors.toList());
    }

    private static SetCodeAuthorization unrecoverableAuthorization() {
        ECDSASignature signature = ECDSASignature.fromComponents(
                ByteUtil.bigIntegerToBytes(R_WITH_NO_CURVE_POINT),
                ByteUtil.bigIntegerToBytes(BigInteger.ONE),
                Transaction.LOWER_REAL_V);
        return new SetCodeAuthorization(BigInteger.valueOf(Constants.REGTEST_CHAIN_ID), DELEGATE, new byte[0], signature);
    }

    private Transaction legacyTx(Account sender) {
        return new TransactionBuilder()
                .sender(sender)
                .receiverAddress(RECEIVER.getBytes())
                .nonce(0)
                .value(BigInteger.ONE)
                .gasPrice(BigInteger.ONE)
                .build();
    }

    private Transaction type4Tx() {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(100_000))
                .receiveAddress(RECEIVER)
                .value(Coin.valueOf(1))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(1))
                .authorizationList(List.of(unrecoverable))
                .build();
        tx.sign(type4Sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private InputStream genesisJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/genesis/rsk-unittests.json"));
            ObjectNode alloc = (ObjectNode) root.get("alloc");
            for (Account account : List.of(firstLegacySender, type4Sender, secondLegacySender)) {
                alloc.putObject(account.getAddress().toString()).put("balance", BALANCE_IN_WEI);
            }
            return new ByteArrayInputStream(mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
