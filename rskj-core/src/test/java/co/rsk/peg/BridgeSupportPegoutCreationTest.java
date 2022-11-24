package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeSupportPegoutCreationTest extends BridgeSupportTestBase {

    @BeforeEach
    void setUp() {
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeSupportBuilder = new BridgeSupportBuilder();
    }

    @Test
    void getPegoutCreationRskTxHashByBtcTxHash_before_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(false);

        Sha256Hash btcTxHash = PegTestUtils.createHash(15);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activations)
            .build();

        byte[] pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);

        assertEquals(ByteUtil.EMPTY_BYTE_ARRAY, pegoutRskTxHash);
    }

    @Test
    void getPegoutCreationRskTxHashByBtcTxHash_ok_after_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(true);

        Sha256Hash btcTxHash = PegTestUtils.createHash(3);
        Keccak256 rskTxHash = PegTestUtils.createHash3(6);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash)).thenReturn(Optional.of(rskTxHash));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .build();

        byte[] pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);

        assertArrayEquals(rskTxHash.getBytes(), pegoutRskTxHash);
    }

    @Test
    void getPegoutCreationRskTxHashByBtcTxHash_not_found_after_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(true);

        Sha256Hash btcTxHash = PegTestUtils.createHash(9);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash)).thenReturn(Optional.empty());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .build();

        byte[] pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);

        assertEquals(ByteUtil.EMPTY_BYTE_ARRAY, pegoutRskTxHash);
    }

    @Test
    void getPegoutCreationRskTxHashByBtcTxHash_by_zero_hash_after_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(true);

        Sha256Hash btcTxHash = Sha256Hash.ZERO_HASH;
        Keccak256 rskTxHash = PegTestUtils.createHash3(5);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash)).thenReturn(Optional.of(rskTxHash));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .build();

        byte[] pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);

        assertArrayEquals(rskTxHash.getBytes(), pegoutRskTxHash);
    }

    @Test
    void getPegoutCreationRskTxHashByBtcTxHash_btcTxHash_is_null_after_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getPegoutCreationRskTxHashByBtcTxHash(isNull())).thenReturn(Optional.empty());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .build();

        byte[] pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(null);

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, pegoutRskTxHash);
    }
}
