package co.rsk.peg.whitelist;

import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_ONE_OFF;
import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_UNLIMITED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.whitelist.constants.WhitelistConstants;
import co.rsk.peg.whitelist.constants.WhitelistMainNetConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

class WhitelistStorageProviderImplTest {

    private final WhitelistConstants whitelistConstants = WhitelistMainNetConstants.getInstance();
    private final NetworkParameters networkParameters = whitelistConstants.getBtcParams();
    private WhitelistStorageProvider whitelistStorageProvider;
    private ActivationConfig.ForBlock activationConfig;

    @BeforeEach
    void setUp() {
        StorageAccessor inMemoryStorage = new InMemoryStorage();
        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        activationConfig = mock(ActivationConfig.ForBlock.class);
    }

    @Test
    void getLockWhitelist_nonNullBytes() {
        List<Integer> calls = new ArrayList<>();
        LockWhitelist whitelistMock = new LockWhitelist(new HashMap<>());
        LockWhitelistEntry oneOffEntry = new OneOffWhiteListEntry(getBtcAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Coin.COIN);
        LockWhitelistEntry unlimitedEntry = new UnlimitedWhiteListEntry(getBtcAddress("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        whitelistMock.put(oneOffEntry.address(), oneOffEntry);
        whitelistMock.put(unlimitedEntry.address(), unlimitedEntry);
        Repository repositoryMock = mock(Repository.class);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(LOCK_ONE_OFF.getKey())))
            .then((InvocationOnMock invocation) -> {
                calls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                // Make sure the bytes are got from the correct address in the repo
                assertArrayEquals(new byte[]{(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd}, contractAddress.getBytes());
                assertEquals(LOCK_ONE_OFF.getKey(), address);
                return new byte[]{(byte) 0xaa};
            });
        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(LOCK_UNLIMITED.getKey())))
            .then((InvocationOnMock invocation) -> {
                calls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                // Make sure the bytes are got from the correct address in the repo
                assertArrayEquals(new byte[]{(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd}, contractAddress.getBytes());
                assertEquals(LOCK_UNLIMITED.getKey(), address);
                return new byte[]{(byte) 0xbb};
            });
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked
                .when(() -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    byte[] data = invocation.getArgument(0);
                    NetworkParameters parameters = invocation.getArgument(1);
                    assertEquals(NetworkParameters.fromID(NetworkParameters.ID_TESTNET), parameters);
                    // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
                    assertArrayEquals(new byte[]{(byte) 0xaa}, data);
                    HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                    map.put(oneOffEntry.address(), oneOffEntry);
                    return Pair.of(map, 0);
                });
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    byte[] unlimitedData = invocation.getArgument(0);
                    NetworkParameters parameters = invocation.getArgument(1);
                    assertEquals(NetworkParameters.fromID(NetworkParameters.ID_TESTNET), parameters);
                    // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
                    assertArrayEquals(new byte[]{(byte) 0xbb}, unlimitedData);
                    HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                    map.put(unlimitedEntry.address(), unlimitedEntry);
                    return map;
                });

            assertEquals(whitelistMock.getAll(), whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters).getAll());
            assertEquals(4, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes (we call getStorageBytes twice)
        }
    }

    @Test
    void getLockWhitelist_nullBytes() {
        List<Integer> calls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class)))
            .then((InvocationOnMock invocation) -> {
                calls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                // Make sure the bytes are got from the correct address in the repo
                assertArrayEquals(new byte[]{(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd}, contractAddress.getBytes());
                assertEquals(LOCK_ONE_OFF.getKey(), address);
                return new byte[]{(byte) 0xee};
            });
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    return null;
                });
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0); // THIS ONE WON'T BE CALLED BECAUSE ONEOFF IS EMPTY
                    Assertions.fail(
                        "As we don't have data for one-off, we shouldn't have called deserialize unlimited");
                    return null;
                });

            LockWhitelist result = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
            Assertions.assertNotNull(result);
            assertEquals(0, result.getSize().intValue());
            assertEquals(2, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes
        }
    }

    @Test
    void saveLockWhitelist() {
        LockWhitelist whitelistMock = mock(LockWhitelist.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            // Mock the One-Off serialization
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeOneOffLockWhitelist(any(Pair.class)))
                .then((InvocationOnMock invocation) -> {
                    Pair<List<OneOffWhiteListEntry>, Integer> data = invocation.getArgument(0);
                    assertEquals(whitelistMock.getAll(OneOffWhiteListEntry.class), data.getLeft());
                    Assertions.assertSame(whitelistMock.getDisableBlockHeight(), data.getRight());
                    serializeCalls.add(0);
                    return Hex.decode("ccdd");
                });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                    storageBytesCalls.add(0);
                    RskAddress contractAddress = invocation.getArgument(0);
                    DataWord address = invocation.getArgument(1);
                    byte[] data = invocation.getArgument(2);
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                    assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                    assertEquals(LOCK_ONE_OFF.getKey(), address);
                    assertArrayEquals(Hex.decode("ccdd"), data);
                    return null;
                })
                .when(repositoryMock)
                .addStorageBytes(any(RskAddress.class), eq(LOCK_ONE_OFF.getKey()), any(byte[].class));

            // Mock the Unlimited serialization
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeUnlimitedLockWhitelist(any(List.class)))
                .then((InvocationOnMock invocation) -> {
                    List<UnlimitedWhiteListEntry> unlimitedWhiteListEntries = invocation.getArgument(0);
                    assertEquals(whitelistMock.getAll(UnlimitedWhiteListEntry.class), unlimitedWhiteListEntries);
                    serializeCalls.add(0);
                    return Hex.decode("bbcc");
                });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                    storageBytesCalls.add(0);
                    RskAddress contractAddress = invocation.getArgument(0);
                    DataWord address = invocation.getArgument(1);
                    byte[] data = invocation.getArgument(2);
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                    assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                    assertEquals(LOCK_UNLIMITED.getKey(), address);
                    assertArrayEquals(Hex.decode("bbcc"), data);
                    return null;
                })
                .when(repositoryMock)
                .addStorageBytes(any(RskAddress.class), eq(LOCK_UNLIMITED.getKey()), any(byte[].class));

            whitelistStorageProvider.save(activationConfig);
            // Shouldn't have tried to save nor serialize anything
            assertEquals(0, storageBytesCalls.size());
            assertEquals(0, serializeCalls.size());
            TestUtils.setInternalState(whitelistStorageProvider, "lockWhitelist", whitelistMock);
            whitelistStorageProvider.save(activationConfig);
            assertEquals(2, storageBytesCalls.size());
            assertEquals(2, serializeCalls.size());
        }
    }

    @Test
    void saveLockWhiteListAfterGetWithData() {
        AtomicReference<Boolean> storageCalled = new AtomicReference<>();
        storageCalled.set(Boolean.FALSE);
        Repository repositoryMock = mock(Repository.class);
        OneOffWhiteListEntry oneOffEntry = new OneOffWhiteListEntry(getBtcAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Coin.COIN);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(LOCK_ONE_OFF.getKey())))
            .then((InvocationOnMock invocation) -> new byte[]{(byte) 0xaa});

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                    map.put(oneOffEntry.address(), oneOffEntry);
                    return Pair.of(map, 0);
                });

            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeOneOffLockWhitelist(any(Pair.class)))
                .thenReturn(new byte[]{(byte) 0xee});

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                    storageCalled.set(Boolean.TRUE);
                    return null;
                })
                .when(repositoryMock)
                .addStorageBytes(any(RskAddress.class), eq(LOCK_ONE_OFF.getKey()), eq(new byte[]{(byte) 0xee}));

            Assertions.assertTrue(whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters).getSize() > 0);

            whitelistStorageProvider.save(activationConfig);

            Assertions.assertTrue(storageCalled.get());
        }
    }

    private Address getBtcAddress(String address) {
        return new Address(networkParameters, Hex.decode(address));
    }
}
