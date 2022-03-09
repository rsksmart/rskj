package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsVersion1;
import co.rsk.peg.simples.SimpleRskTransaction;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class BridgeSupportTestBase {
    protected static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    protected static final BigInteger DUST_AMOUNT = new BigInteger("1");
    protected static final BigInteger NONCE = new BigInteger("0");
    protected static final BigInteger GAS_PRICE = new BigInteger("100");
    protected static final BigInteger GAS_LIMIT = new BigInteger("1000");
    protected static final String DATA = "80af2871";
    protected static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));
    protected static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;
    protected static final List<BtcECKey> REGTEST_OLD_FEDERATION_PRIVATE_KEYS = Arrays.asList(
        BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
        BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
        BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
    );

    protected BridgeConstants bridgeConstants;
    protected NetworkParameters btcParams;
    protected BridgeSupportBuilder bridgeSupportBuilder;
    protected ActivationConfig.ForBlock activations;

    protected BtcTransaction createBtcTransactionWithOutputToAddress(Coin amount, Address btcAddress) {
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(amount, btcAddress);
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1),
            0, ScriptBuilder.createInputScript(null, srcKey));
        return tx;
    }

    protected BtcLockSenderProvider getBtcLockSenderProvider(BtcLockSender.TxSenderAddressType txSenderAddressType, Address btcAddress, RskAddress rskAddress) {
        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getTxSenderAddressType()).thenReturn(txSenderAddressType);
        when(btcLockSender.getBTCAddress()).thenReturn(btcAddress);
        when(btcLockSender.getRskAddress()).thenReturn(rskAddress);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        return btcLockSenderProvider;
    }

    protected PeginInstructionsProvider getPeginInstructionsProviderForVersion1(RskAddress rskDestinationAddress, Optional<Address> btcRefundAddress)
        throws PeginInstructionsException {
        PeginInstructionsVersion1 peginInstructions = mock(PeginInstructionsVersion1.class);
        when(peginInstructions.getProtocolVersion()).thenReturn(1);
        when(peginInstructions.getRskDestinationAddress()).thenReturn(rskDestinationAddress);
        when(peginInstructions.getBtcRefundAddress()).thenReturn(btcRefundAddress);

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.of(peginInstructions));

        return peginInstructionsProvider;
    }

    protected UTXO createUTXO(Coin value, Address address) {
        return new UTXO(
            PegTestUtils.createHash(),
            1,
            value,
            0,
            false,
            ScriptBuilder.createOutputScript(address));
    }

    protected Transaction getMockedRskTxWithHash(String s) {
        byte[] hash = Keccak256Helper.keccak256(s);
        return new SimpleRskTransaction(hash);
    }

    protected static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    protected static void mockChainOfStoredBlocks(BtcBlockStoreWithCache btcBlockStore, BtcBlock targetHeader, int headHeight, int targetHeight) throws BlockStoreException {
        // Simulate that the block is in there by mocking the getter by height,
        // and then simulate that the txs have enough confirmations by setting a high head.
        when(btcBlockStore.getStoredBlockAtMainChainHeight(targetHeight)).thenReturn(new StoredBlock(targetHeader, BigInteger.ONE, targetHeight));
        // Mock current pointer's header
        StoredBlock currentStored = mock(StoredBlock.class);
        BtcBlock currentBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(currentBlock).getHash();
        doReturn(currentBlock).when(currentStored).getHeader();
        when(currentStored.getHeader()).thenReturn(currentBlock);
        when(btcBlockStore.getChainHead()).thenReturn(currentStored);
        when(currentStored.getHeight()).thenReturn(headHeight);
    }
}
