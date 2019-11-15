package org.ethereum.core;

import co.rsk.RskContext;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.RskTestContext;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Created by mraof on 2019 November 14 at 2:52 PM.
 */
public class MultikeyTransactionTest {

    private TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test
    public void testNoEnveloper() throws Exception {

        // cat --> 79b08ad8787060333663d19704909ee7b1903e58
        // cow --> cd2a3d9f938e13cd947ec05abc7fe734df8dd826

        ECKey ecKey = ECKey.fromPrivate(HashUtil.keccak256("cat".getBytes()));
        byte[] cowPrivkey = HashUtil.keccak256("cow".getBytes());
        byte[] elfPrivkey = HashUtil.keccak256("elf".getBytes());
        byte[] gnomePrivkey = HashUtil.keccak256("gnome".getBytes());

        byte[][] nonce = {{0x01}, {0x03}, {0x04}};
        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gasLimit = Hex.decode("4255");
        BigInteger value = new BigInteger("1000000000000000000000000");

        Transaction tx = new Transaction(
                Arrays.asList(nonce),
                gasPrice,
                gasLimit,
                ecKey.getAddress(),
                value.toByteArray(),
                null,
                nonce.length
        );

        tx.sign(Arrays.asList(cowPrivkey, elfPrivkey, gnomePrivkey));

        System.out.println("Signatures: {");
        for (ECKey.ECDSASignature signature : tx.getSignatures()) {
            System.out.println("v\t\t\t: " + Hex.toHexString(new byte[]{signature.v}));
            System.out.println("r\t\t\t: " + Hex.toHexString(BigIntegers.asUnsignedByteArray(signature.r)));
            System.out.println("s\t\t\t: " + Hex.toHexString(BigIntegers.asUnsignedByteArray(signature.s)));
        }
        System.out.println("}");

        System.out.println("RLP encoded tx\t\t: " + Hex.toHexString(tx.getEncoded()));

        System.out.println("Tx unsigned RLP\t\t: " + Hex.toHexString(tx.getEncodedRaw()));
        System.out.println("Tx signed   RLP\t\t: " + Hex.toHexString(tx.getEncoded()));

        // retrieve the signer/sender of the transaction
        List<ECKey> keys = new ArrayList<>();
        List<ECKey.ECDSASignature> signatures = tx.getSignatures();
        for (int i = 0; i < signatures.size(); i++) {
            ECKey.ECDSASignature signature = signatures.get(i);
            keys.add(ECKey.signatureToKey(tx.getRawHash(i).getBytes(), signature));
        }

        for (ECKey key : keys) {
            System.out.println("Signature public key\t: " + Hex.toHexString(key.getPubKey()));
            System.out.println("Sender is\t\t: " + Hex.toHexString(key.getAddress()));
        }

        assertEquals(
                Hex.toHexString(ECKey.fromPrivate(cowPrivkey).getAddress()),
                Hex.toHexString(keys.get(0).getAddress())
        );

        assertEquals(
                Hex.toHexString(ECKey.fromPrivate(elfPrivkey).getAddress()),
                Hex.toHexString(keys.get(1).getAddress())
        );

        assertEquals(
                Hex.toHexString(ECKey.fromPrivate(gnomePrivkey).getAddress()),
                Hex.toHexString(keys.get(2).getAddress())
        );

        assertEquals(Hex.toHexString(tx.getSender().getBytes()), Hex.toHexString(tx.getChargedSender().getBytes()));
    }

    @Test
    public void testEnveloper() throws Exception {

        // cat --> 79b08ad8787060333663d19704909ee7b1903e58
        // cow --> cd2a3d9f938e13cd947ec05abc7fe734df8dd826

        ECKey ecKey = ECKey.fromPrivate(HashUtil.keccak256("cat".getBytes()));
        byte[] cowPrivkey = HashUtil.keccak256("cow".getBytes());
        byte[] elfPrivkey = HashUtil.keccak256("elf".getBytes());
        byte[] gnomePrivkey = HashUtil.keccak256("gnome".getBytes());

        byte[][] nonce = {{0x01}, {0x03}, {0x04}};
        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gasLimit = Hex.decode("4255");
        BigInteger value = new BigInteger("1000000000000000000000000");

        Transaction tx = new Transaction(
                Arrays.asList(nonce),
                gasPrice,
                gasLimit,
                ecKey.getAddress(),
                value.toByteArray(),
                null,
                nonce.length - 1
        );

        tx.sign(Arrays.asList(cowPrivkey, elfPrivkey, gnomePrivkey));

        //Sender should be made from all public keys, but charged sender should be the enveloper, the last signer
        assertNotEquals(Hex.toHexString(tx.getSender().getBytes()), Hex.toHexString(tx.getChargedSender().getBytes()));
        assertEquals(Hex.toHexString(tx.getSenders().get(2).getBytes()), Hex.toHexString(tx.getChargedSender().getBytes()));
    }

    @Test
    public void testExecuteNoEnveloper() throws Exception {

        BigInteger initialNonce = config.getNetworkConstants().getInitialNonce();
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableRepository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        IndexedBlockStore blockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        RskContext rskContext = new RskTestContext(new String[0]);
        Blockchain blockchain = ImportLightTest.createBlockchain(
                rskContext.getGenesisLoader().load(),
                config, repository, blockStore, trieStore
        );

        List<byte[]> nonces = Arrays.asList(
                initialNonce.toByteArray(),
                initialNonce.toByteArray(),
                initialNonce.toByteArray()
        );

        ECKey ecKey = ECKey.fromPrivate(HashUtil.keccak256("cat".getBytes()));
        byte[] cowPrivkey = HashUtil.keccak256("cow".getBytes());
        byte[] elfPrivkey = HashUtil.keccak256("elf".getBytes());
        byte[] gnomePrivkey = HashUtil.keccak256("gnome".getBytes());

        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gasLimit = Hex.decode("8765");
        BigInteger value = new BigInteger("1000000000000000000000000");

        Transaction tx = new Transaction(
                nonces,
                gasPrice,
                gasLimit,
                ecKey.getAddress(),
                value.toByteArray(),
                null,
                nonces.size()
        );

        tx.sign(Arrays.asList(cowPrivkey, elfPrivkey, gnomePrivkey));

        TransactionExecutor executor = executeTransaction(blockchain, blockStore, tx, repository);
        Assert.assertNull(executor.getResult().getException());
    }

    @Test
    public void testExecuteEnveloper() throws Exception {

        BigInteger initialNonce = config.getNetworkConstants().getInitialNonce();
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableRepository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        IndexedBlockStore blockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        RskContext rskContext = new RskTestContext(new String[0]);
        Blockchain blockchain = ImportLightTest.createBlockchain(
                rskContext.getGenesisLoader().load(),
                config, repository, blockStore, trieStore
        );

        List<byte[]> nonces = Arrays.asList(
                initialNonce.toByteArray(),
                initialNonce.toByteArray(),
                initialNonce.toByteArray()
        );

        ECKey ecKey = ECKey.fromPrivate(HashUtil.keccak256("cat".getBytes()));
        byte[] cowPrivkey = HashUtil.keccak256("cow".getBytes());
        byte[] elfPrivkey = HashUtil.keccak256("elf".getBytes());
        byte[] gnomePrivkey = HashUtil.keccak256("gnome".getBytes());

        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gasLimit = Hex.decode("8765");
        BigInteger value = new BigInteger("1000000000000000000000000");

        Transaction tx = new Transaction(
                nonces,
                gasPrice,
                gasLimit,
                ecKey.getAddress(),
                value.toByteArray(),
                null,
                nonces.size() - 1
        );

        tx.sign(Arrays.asList(cowPrivkey, elfPrivkey, gnomePrivkey));

        TransactionExecutor executor = executeTransaction(blockchain, blockStore, tx, repository);
        Assert.assertNull(executor.getResult().getException());
    }

    private TransactionExecutor executeTransaction(
            Blockchain blockchain,
            BlockStore blockStore,
            Transaction tx,
            Repository repository) {
        Repository track = repository.startTracking();
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(
                        config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig());

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                blockStore,
                null,
                blockFactory,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory));
        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(tx, 0, RskAddress.nullAddress(), repository, blockchain.getBestBlock(), 0);

        executor.executeTransaction();

        track.commit();
        return executor;
    }
}
