package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.bitcoin.PegoutAddressType;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.RejectedPegoutReason;
import co.rsk.test.builders.BridgeSupportBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import co.rsk.RskTestUtils;

/**
 * Covers the peg-out request method that takes a destination type.
 *
 * <p>The point of these tests is that one request path produces four different destinations, all
 * of them derived from the same recovered public key, and that they all queue and log through the
 * existing machinery.</p>
 */
class BridgeSupportReleaseBtcToTest {

    private static final BigInteger NONCE = BigInteger.ZERO;
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(100);
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(1000);
    private static final ECKey SENDER = RskTestUtils.getEcKeyFromSeed("sender");
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0L);

    private BridgeStorageProvider provider;
    private BridgeEventLogger eventLogger;
    private BridgeSupport bridgeSupport;

    @BeforeEach
    void setUp() {
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        Repository repository = RskTestUtils.createRepository();
        eventLogger = mock(BridgeEventLogger.class);
        provider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, ALL_ACTIVATIONS);

        StorageAccessor accessor = new BridgeStorageAccessorImpl(repository);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(accessor);
        federationStorageProvider.setNewFederation(P2shErpFederationBuilder.builder().build());
        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .build();

        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.valueOf(5_000L));

        bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withRepository(repository)
            .withEventLogger(eventLogger)
            .withActivations(ALL_ACTIVATIONS)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
    }

    /** The destination the requester's key derives to, which is what should end up queued. */
    private Address expectedDestination(PegoutAddressType type) {
        BtcECKey requesterKey = BtcECKey.fromPublicOnly(SENDER.getPubKey(true));

        return type.deriveAddress(requesterKey, NETWORK_PARAMETERS);
    }

    @ParameterizedTest
    @CsvSource({"legacy, P2PKH", "p2sh-segwit, P2SH_P2WPKH", "bech32, P2WPKH", "bech32m, P2TR"})
    void releaseBtcTo_queuesTheRequestedAddressType(String apiName, PegoutAddressType type)
        throws IOException {

        bridgeSupport.releaseBtcTo(buildReleaseTx(), apiName);

        List<ReleaseRequestQueue.Entry> entries = provider.getReleaseRequestQueue().getEntries();
        assertEquals(1, entries.size());
        assertEquals(expectedDestination(type), entries.get(0).getDestination());
    }

    @ParameterizedTest
    @CsvSource({"legacy, P2PKH", "p2sh-segwit, P2SH_P2WPKH", "bech32, P2WPKH", "bech32m, P2TR"})
    void releaseBtcTo_logsTheDestinationInItsOwnFormat(String apiName, PegoutAddressType type)
        throws IOException {

        bridgeSupport.releaseBtcTo(buildReleaseTx(), apiName);

        verify(eventLogger, times(1)).logReleaseBtcRequestReceivedToAddress(
            any(RskAddress.class), eq(expectedDestination(type).toString()), any(co.rsk.core.Coin.class));
    }

    @Test
    void releaseBtcTo_legacyMatchesTheFallbackMethod() throws IOException {
        bridgeSupport.releaseBtcTo(buildReleaseTx(), "legacy");
        List<ReleaseRequestQueue.Entry> viaNewMethod = provider.getReleaseRequestQueue().getEntries();

        setUp();
        bridgeSupport.releaseBtc(buildReleaseTx());
        List<ReleaseRequestQueue.Entry> viaFallback = provider.getReleaseRequestQueue().getEntries();

        assertEquals(viaFallback.get(0).getDestination(), viaNewMethod.get(0).getDestination());
    }

    @Test
    void releaseBtcTo_derivesEveryTypeFromTheSameKey() throws IOException {
        // P2PKH and P2WPKH commit to the same 20 bytes; taproot commits to 32
        assertArrayEquals(
            expectedDestination(PegoutAddressType.P2PKH).getHash(),
            expectedDestination(PegoutAddressType.P2WPKH).getHash());
        assertEquals(20, expectedDestination(PegoutAddressType.P2PKH).getHash().length);
        assertEquals(32, expectedDestination(PegoutAddressType.P2TR).getHash().length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "segwit", "taproot", "P2WPKH", "BECH32"})
    void releaseBtcTo_whenTypeIsNotSupported_rejectsAndRefunds(String apiName) throws IOException {
        bridgeSupport.releaseBtcTo(buildReleaseTx(), apiName);

        assertTrue(provider.getReleaseRequestQueue().getEntries().isEmpty());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            any(RskAddress.class), any(co.rsk.core.Coin.class),
            eq(RejectedPegoutReason.UNSUPPORTED_ADDRESS_TYPE));
        verify(eventLogger, never()).logReleaseBtcRequestReceivedToAddress(any(), any(), any());
    }

    @Test
    void releaseBtcTo_hasTheExpectedSelector() {
        // Pinned because tooling and scripts hardcode it. keccak256("releaseBtcTo(string)")[0..4]
        assertEquals("ba75bbd5",
            org.bouncycastle.util.encoders.Hex.toHexString(Bridge.RELEASE_BTC_TO.encodeSignature()));
    }

    private Transaction buildReleaseTx() {
        Transaction tx = Transaction.builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(PrecompiledContracts.BRIDGE_ADDR)
            .data(new byte[]{})
            .chainId(Constants.MAINNET_CHAIN_ID)
            .value(co.rsk.core.Coin.fromBitcoin(Coin.COIN))
            .build();
        tx.sign(SENDER.getPrivKeyBytes());

        return tx;
    }
}
