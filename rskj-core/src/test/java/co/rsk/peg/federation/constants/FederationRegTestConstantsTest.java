package co.rsk.peg.federation.constants;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FederationRegTestConstantsTest {

    @BeforeEach
    void setUp() throws Exception {
        resetInstance();
    }

    @AfterAll
    static void cleanUp() throws Exception {
        resetInstance();
    }

    @Test
    void getInstance_whenKeysAreNull_shouldThrowIllegalArgumentException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            FederationRegTestConstants.getInstance(null)
        );
    }

    @Test
    void getInstance_whenKeysAreEmpty_shouldThrowIllegalArgumentException() {
        // Arrange
        List<BtcECKey> emptyList = List.of();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            FederationRegTestConstants.getInstance(emptyList)
        );
    }

    @Test
    void getInstance_whenCalledWithValidKeys_shouldCreateInstance() {
        // Arrange
        var key1 = BitcoinTestUtils.getBtcEcKeyFromSeed("1");
        var key2 = BitcoinTestUtils.getBtcEcKeyFromSeed("2");
        var key3 = BitcoinTestUtils.getBtcEcKeyFromSeed("3");
        var validKeys = List.of(key1, key2, key3);

        // Act
        var instance = FederationRegTestConstants.getInstance(validKeys);
      
        // Assert
        assertNotNull(instance);
    }

    @Test
    void getInstance_whenCalledTwiceWithSameKeys_shouldReturnSameInstance() {
        // Arrange
        var key1 = BitcoinTestUtils.getBtcEcKeyFromSeed("1");
        var key2 = BitcoinTestUtils.getBtcEcKeyFromSeed("2");
        var key3 = BitcoinTestUtils.getBtcEcKeyFromSeed("3");
        var validKeys = List.of(key1, key2, key3);
      
        // Act
        var instance1 = FederationRegTestConstants.getInstance(validKeys);
        var instance2 = FederationRegTestConstants.getInstance(validKeys);

        // Assert
        assertSame(instance1, instance2);
    }

    @Test
    void getInstance_whenCalledWithDifferentKeysAfterInitialization_shouldThrowIllegalStateException() {
        // Arrange
        var key1 = BitcoinTestUtils.getBtcEcKeyFromSeed("1");
        var key2 = BitcoinTestUtils.getBtcEcKeyFromSeed("2");
        var key3 = BitcoinTestUtils.getBtcEcKeyFromSeed("3");
        var validKeys = List.of(key1, key2, key3);
        var differentKeys = List.of(key1);

        // Act
        FederationRegTestConstants.getInstance(validKeys);
     
        // Assert
        assertThrows(IllegalStateException.class, () ->
            FederationRegTestConstants.getInstance(differentKeys)
        );
    }

    @Test
    void getInstanceNoArgs_whenCalledBeforeInitialization_shouldThrowIllegalStateException() {
        // Act & Assert
        assertThrows(IllegalStateException.class, FederationRegTestConstants::getInstance);
    }

    @Test
    void getInstanceNoArgs_whenCalledAfterInitialization_shouldReturnSameInstance() {
        // Arrange
        var key1 = BitcoinTestUtils.getBtcEcKeyFromSeed("1");
        var key2 = BitcoinTestUtils.getBtcEcKeyFromSeed("2");
        var key3 = BitcoinTestUtils.getBtcEcKeyFromSeed("3");
        var validKeys = List.of(key1, key2, key3);

        // Act
        var expectedInstance = FederationRegTestConstants.getInstance(validKeys);
        var actualInstance = FederationRegTestConstants.getInstance();

        // Assert
        assertSame(expectedInstance, actualInstance);
    }

    private static void resetInstance() throws Exception {
        // reset singleton instance
        Field instanceField = FederationRegTestConstants.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
