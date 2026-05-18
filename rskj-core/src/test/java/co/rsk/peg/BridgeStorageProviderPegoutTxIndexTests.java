package co.rsk.peg;

import static co.rsk.peg.BridgeStorageIndexKey.PEGOUT_TX_SIG_HASH;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.RskAddress;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class BridgeStorageProviderPegoutTxIndexTests {
    private static final String DUPLICATED_INSERTION_ERROR_MESSAGE = "Given pegout tx sigHash %s already exists in the index. Index entries are considered unique.";
    private static final byte TRUE_VALUE = (byte) 1;

    private final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters mainnetBtcParams = bridgeMainnetConstants.getBtcParams();
    private final RskAddress bridgeAddress = PrecompiledContracts.BRIDGE_ADDR;

    private static Stream<Arguments> null_sigHash_parameters() {
        return Stream.of(
            Arguments.of(false),
            Arguments.of(true)
        );
    }

    private static Stream<Arguments> invalid_entry_values() {
        return Stream.of(
            Arguments.of(new byte[]{1, 2}),
            Arguments.of(new byte[]{0})
        );
    }

    private static Stream<Arguments> valid_sigHash_parameters() {
        return Stream.of(
            Arguments.of(false, Sha256Hash.ZERO_HASH),
            Arguments.of(false, BitcoinTestUtils.createHash(10)),
            Arguments.of(true, Sha256Hash.ZERO_HASH),
            Arguments.of(true, BitcoinTestUtils.createHash(20))
        );
    }

    @ParameterizedTest
    @MethodSource("null_sigHash_parameters")
    void hasPegoutSigHash_null_sigHash(boolean isRskip379HardForkActive) {
        // Arrange
        ActivationConfig.ForBlock activations = isRskip379HardForkActive ?
                                                    ActivationConfigsForTest.arrowhead600().forBlock(0) :
                                                    ActivationConfigsForTest.fingerroot500().forBlock(0);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);

        // Act
        boolean result = bridgeStorageProvider.hasPegoutTxSigHash(null);

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

    @Test
    void hasPegoutTxSigHash_null_stored_value() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);
        Sha256Hash sigHash = BitcoinTestUtils.createHash(5);
        DataWord entryKey = PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString());
        when(repository.getStorageBytes(bridgeAddress, entryKey)).thenReturn(null);

        // Act
        boolean result = bridgeStorageProvider.hasPegoutTxSigHash(sigHash);

        // Assert
        Assertions.assertFalse(result);

        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            entryKey
        );

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    @ParameterizedTest
    @MethodSource("invalid_entry_values")
    void hasPegoutTxSigHash_invalid_stored_data(byte[] invalidStoredValue) {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);
        Sha256Hash sigHash = BitcoinTestUtils.createHash(5);
        DataWord entryKey = PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString());
        when(repository.getStorageBytes(bridgeAddress, entryKey)).thenReturn(invalidStoredValue);

        // Act
        boolean result = bridgeStorageProvider.hasPegoutTxSigHash(sigHash);

        // Assert
        Assertions.assertFalse(result);

        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            entryKey
        );

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    @ParameterizedTest
    @MethodSource("valid_sigHash_parameters")
    void hasPegoutTxSigHash_non_null_sigHash(boolean isRskip379HardForkActive, Sha256Hash sigHash) {
        // Arrange
        ActivationConfig.ForBlock activations = isRskip379HardForkActive ?
                                                    ActivationConfigsForTest.arrowhead600().forBlock(0) :
                                                    ActivationConfigsForTest.fingerroot500().forBlock(0);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);

        // Act
        boolean result = bridgeStorageProvider.hasPegoutTxSigHash(sigHash);

        // Assert
        Assertions.assertFalse(result);
        if (isRskip379HardForkActive) {
            verify(repository, times(1)).getStorageBytes(
                bridgeAddress,
                PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
            );
        } else {
            verify(repository, never()).getStorageBytes(
                any(RskAddress.class),
                any(DataWord.class)
            );
        }

        verify(repository, never()).addStorageBytes(
            any(RskAddress.class),
            any(DataWord.class),
            any(byte[].class)
        );
    }

    @Test
    void hasPegoutTxSigHash_passing_existing_sigHash() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Sha256Hash sigHash = BitcoinTestUtils.createHash(15);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);

        // Check if sigHash exists when there are no entries in the index
        boolean result = bridgeStorageProvider.hasPegoutTxSigHash(sigHash);
        Assertions.assertFalse(result);

        // Verify the method check if the given sigHash exists in the index
        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
        );

        // Verify sigHash is not persisted into the index when calling hasPegoutTxSigHash
        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
        // reset calls counter
        Mockito.reset(repository);

        // Let's set the sigHash and then call hasPegoutTxSigHash, it should return false.
        bridgeStorageProvider.setPegoutTxSigHash(sigHash);
        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
        );
        // reset calls counter
        Mockito.reset(repository);

        boolean sigHashShouldNotExist = bridgeStorageProvider.hasPegoutTxSigHash(sigHash);
        Assertions.assertFalse(sigHashShouldNotExist);
        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
        );
        Mockito.reset(repository);

        // Let's save pending sigHash into the repository
        bridgeStorageProvider.save();
        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString()),
            new byte[]{(byte) 1}
        );
        verify(repository, never()).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
        );
        Mockito.reset(repository);

        // Let's create a stub for the just saved sigHash
        when(repository.getStorageBytes(bridgeAddress, PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())))
            .thenReturn(new byte[]{TRUE_VALUE});

        // Check if saved sigHash exists
        boolean shouldFindSigHash = bridgeStorageProvider.hasPegoutTxSigHash(sigHash);
        Assertions.assertTrue(shouldFindSigHash);
        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
        );
    }

    @ParameterizedTest
    @MethodSource("null_sigHash_parameters")
    void setPegoutTxSigHash_null(boolean isRskip379HardForkActive) {
        // Arrange
        ActivationConfig.ForBlock activations = isRskip379HardForkActive ?
                                                    ActivationConfigsForTest.arrowhead600().forBlock(0) :
                                                    ActivationConfigsForTest.fingerroot500().forBlock(0);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);

        // Act
        bridgeStorageProvider.setPegoutTxSigHash(null);
        bridgeStorageProvider.savePegoutTxSigHashes();

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
    @MethodSource("valid_sigHash_parameters")
    void setPegoutTxSigHash_non_null(boolean isRskip379HardForkActive, Sha256Hash sigHash) {
        // Arrange
        ActivationConfig.ForBlock activations = isRskip379HardForkActive ?
                                                    ActivationConfigsForTest.arrowhead600().forBlock(0) :
                                                    ActivationConfigsForTest.fingerroot500().forBlock(0);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);

        // Act
        bridgeStorageProvider.setPegoutTxSigHash(sigHash);
        bridgeStorageProvider.savePegoutTxSigHashes();

        // Assert
        if (isRskip379HardForkActive) {
            verify(repository, times(1)).getStorageBytes(
                bridgeAddress,
                PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
            );

            verify(repository, times(1)).addStorageBytes(
                bridgeAddress,
                PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString()),
                new byte[]{(byte) 1}
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
    void setPegoutTxSigHash_passing_existing() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Sha256Hash sigHash = BitcoinTestUtils.createHash(15);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);

        // Add a sigHash when index is empty
        bridgeStorageProvider.setPegoutTxSigHash(sigHash);

        // Verify the method check if the given sigHash already exists in the index
        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
        );
        // Verify sigHash is not persisted into the index when save has not been called.
        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
        Mockito.reset(repository);

        // Try to set same sigHash, it should allow it to do it.
        bridgeStorageProvider.setPegoutTxSigHash(sigHash);
        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
        );

        // Try to set a different sigHash. It should allow it as well.
        Sha256Hash newSigHash = BitcoinTestUtils.createHash(7);
        bridgeStorageProvider.setPegoutTxSigHash(newSigHash);

        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", newSigHash.toString())
        );
        // Verify no sigHash is persisted yet
        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
        Mockito.reset(repository);

        // Now let's persist the pending to save sigHash
        bridgeStorageProvider.save();

        // Check the persisted sigHash is the newSigHash
        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", newSigHash.toString()),
            new byte[]{TRUE_VALUE}
        );
        when(repository.getStorageBytes(bridgeAddress, PEGOUT_TX_SIG_HASH.getCompoundKey("-", newSigHash.toString())))
            .thenReturn(new byte[]{TRUE_VALUE});

        // Try to set again the new sigHash that was persisted into the repository
        assertThrows(
            IllegalStateException.class,
            () -> bridgeStorageProvider.setPegoutTxSigHash(newSigHash),
            String.format(DUPLICATED_INSERTION_ERROR_MESSAGE, newSigHash)
        );
    }

    @Test
    void setPegoutTxSigHash_multiple_sigHash_in_a_single_rsk_tx() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Sha256Hash sigHash = BitcoinTestUtils.createHash(15);
        Sha256Hash sigHash2 = BitcoinTestUtils.createHash(25);
        Sha256Hash sigHash3 = BitcoinTestUtils.createHash(35);

        Repository repository = mock(Repository.class);
        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, activations);

        // Set multiple sighash when index is empty
        bridgeStorageProvider.setPegoutTxSigHash(sigHash);
        bridgeStorageProvider.setPegoutTxSigHash(sigHash2);
        bridgeStorageProvider.setPegoutTxSigHash(sigHash3);

        // Verify the method check if the given sigHash already exists in the index
        verify(repository, times(3)).getStorageBytes(
            any(RskAddress.class),
            any(DataWord.class)
        );
        // Verify sigHash is not persisted into the index when save has not been called.
        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
        reset(repository);

        // Try to set same sigHash, it should allow it to do it.
        bridgeStorageProvider.setPegoutTxSigHash(sigHash);
        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString())
        );

        // Try to set a different sigHash. It should allow it as well.
        Sha256Hash sigHash4 = BitcoinTestUtils.createHash(7);
        bridgeStorageProvider.setPegoutTxSigHash(sigHash4);

        verify(repository, times(1)).getStorageBytes(
            bridgeAddress,
            PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash4.toString())
        );
        // Verify no sigHash is persisted yet
        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
        reset(repository);

        // Now let's persist pending sigHashes
        bridgeStorageProvider.save();

        // Check the persisted sigHash is the sigHash4
        verify(repository, times(4)).addStorageBytes(
            any(RskAddress.class),
            any(DataWord.class),
            any(byte[].class)
        );
        when(repository.getStorageBytes(bridgeAddress, PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash4.toString())))
            .thenReturn(new byte[]{TRUE_VALUE});

        // Try to set again the new sigHash that was persisted into the repository
        assertThrows(
            IllegalStateException.class,
            () -> bridgeStorageProvider.setPegoutTxSigHash(sigHash4),
            String.format(DUPLICATED_INSERTION_ERROR_MESSAGE, sigHash4)
        );
    }
    
    private BridgeStorageProvider createBridgeStorageProvider(Repository repository, ActivationConfig.ForBlock activations) {
        return new BridgeStorageProvider(repository, mainnetBtcParams, activations);
    }
}
