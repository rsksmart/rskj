package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.stream.Stream;

import static co.rsk.peg.BridgeStorageIndexKey.BRIDGE_BTC_TX_SIG_HASH_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeStorageProviderBridgeTxsIndexTests {

    private static final String DUPLICATED_INSERTION_ERROR_MESSAGE = "Given bridge btc tx sigHash %s already exists in the index. Index entries are considered unique.";
    private static final byte TRUE_VALUE = (byte) 1;
    private final BridgeConstants bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();

    private static Stream<Arguments> null_sigHash_parameters() {
        return Stream.of(
            Arguments.of(false, null),
            Arguments.of(true, null)
        );
    }

    private static Stream<Arguments> non_null_sigHash_parameters() {
        return Stream.of(
            Arguments.of(false, Sha256Hash.ZERO_HASH),
            Arguments.of(false, PegTestUtils.createHash(10)),
            Arguments.of(true, Sha256Hash.ZERO_HASH),
            Arguments.of(true, PegTestUtils.createHash(20))
        );
    }

    @ParameterizedTest
    @MethodSource("null_sigHash_parameters")
    void hasBtcTxSigHash_null_sigHash(boolean isRskip379Active, Sha256Hash sigHash) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP379)).thenReturn(isRskip379Active);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        boolean result = provider.hasBridgeBtcTxSigHash(sigHash);

        // Assert
        Assertions.assertFalse(result);

        verify(repository, never()).getStorageBytes(
            any(),
            any()
        );

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    @ParameterizedTest
    @MethodSource("non_null_sigHash_parameters")
    void hasBtcTxSigHash_non_null_sigHash(boolean isRskip379Active, Sha256Hash sigHash) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP379)).thenReturn(isRskip379Active);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        boolean result = provider.hasBridgeBtcTxSigHash(sigHash);

        // Assert
        Assertions.assertFalse(result);
        if (isRskip379Active){
            verify(repository, times(1)).getStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
            );
        } else {
            verify(repository, never()).getStorageBytes(
                any(),
                any()
            );
        }

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    @Test
    void hasBtcTxSigHash_passing_existing_sigHash() throws IOException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP379)).thenReturn(true);

        Sha256Hash sigHash = PegTestUtils.createHash(15);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Check if sigHash exists when there are no entries in the index
        boolean result = provider.hasBridgeBtcTxSigHash(sigHash);
        Assertions.assertFalse(result);

        // Verify the method check if the given sigHash exists in the index
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
        );

        // Verify sigHash is not persisted into the index when calling hasBtcTxSigHash
        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
        // reset calls counter
        Mockito.reset(repository);

        // Let's set the sigHash and then call hasBtcTxSigHash, it should return false.
        provider.setBridgeBtcTxSigHash(sigHash);
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
        );
        // reset calls counter
        Mockito.reset(repository);

        boolean sigHashShouldNotExists = provider.hasBridgeBtcTxSigHash(sigHash);
        Assertions.assertFalse(sigHashShouldNotExists);
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
        );
        Mockito.reset(repository);

        // Let's save pending sigHash into the repository
        provider.save();
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString()),
            new byte[]{(byte)1}
        );
        verify(repository, never()).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
        );
        Mockito.reset(repository);

        // Let's create a stub for the just saved sigHash
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())))
            .thenReturn(new byte[]{TRUE_VALUE});

        // Check if saved sigHash exists
        boolean shouldFoundSigHash = provider.hasBridgeBtcTxSigHash(sigHash);
        Assertions.assertTrue(shouldFoundSigHash);
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
        );
    }

    @ParameterizedTest
    @MethodSource("null_sigHash_parameters")
    void setBridgeBtcTxSigHash_null_sigHash(boolean isRskip379Active, Sha256Hash sigHash) throws IOException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP379)).thenReturn(isRskip379Active);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        provider.setBridgeBtcTxSigHash(sigHash);
        provider.save();

        // Assert
        verify(repository, never()).getStorageBytes(
            any(),
            any()
        );

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    @ParameterizedTest
    @MethodSource("non_null_sigHash_parameters")
    void setBridgeBtcTxSigHash_non_null_sigHash(boolean isRskip379Active, Sha256Hash sigHash) throws IOException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP379)).thenReturn(isRskip379Active);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        provider.setBridgeBtcTxSigHash(sigHash);
        provider.save();

        // Assert
        if (isRskip379Active){
            verify(repository, times(1)).getStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
            );

            verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString()),
                new byte[]{(byte)1}
            );
        } else {
            verify(repository, never()).getStorageBytes(
                any(),
                any()
            );

            verify(repository, never()).addStorageBytes(
                any(),
                any(),
                any()
            );
        }
    }

    @Test
    void setBridgeBtcTxSigHash_passing_existing_sigHash() throws IOException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP379)).thenReturn(true);

        Sha256Hash sigHash = PegTestUtils.createHash(15);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Add a sigHash when index is empty
        provider.setBridgeBtcTxSigHash(sigHash);

        // Verify the method check if the given sigHash already exists in the index
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
        );
        // Verify sigHash is not persisted into the index when save has not been called.
        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
        Mockito.reset(repository);

        // Try to set same sigHash, it should allow it to do it.
        provider.setBridgeBtcTxSigHash(sigHash);
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", sigHash.toString())
        );

        // Try to set a different sigHash. It should allow it as well.
        Sha256Hash newSigHash = PegTestUtils.createHash(7);
        provider.setBridgeBtcTxSigHash(newSigHash);

        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", newSigHash.toString())
        );
        // Verify no sigHash is persisted yet
        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
        Mockito.reset(repository);

        // Now let's persist the pending to save sigHash
        provider.save();

        // Check the persisted sigHash is the newSigHash
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", newSigHash.toString()),
            new byte[]{TRUE_VALUE}
        );
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, BRIDGE_BTC_TX_SIG_HASH_KEY.getCompoundKey("-", newSigHash.toString())))
            .thenReturn(new byte[]{TRUE_VALUE});

        // Try to set again the new sigHash that was persisted into the repository
        Assertions.assertThrows(IllegalStateException.class, () -> {
            provider.setBridgeBtcTxSigHash(newSigHash);

        }, String.format(DUPLICATED_INSERTION_ERROR_MESSAGE, newSigHash));
    }
}
