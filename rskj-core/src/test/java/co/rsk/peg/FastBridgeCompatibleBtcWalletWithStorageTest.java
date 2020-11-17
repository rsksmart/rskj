package co.rsk.peg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class FastBridgeCompatibleBtcWalletWithStorageTest {
    private final Federation federation = new Federation(
        FederationTestUtils.getFederationMembers(3),
        Instant.ofEpochMilli(1000),
        0L,
        NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
    );

    private final List<Federation> federationList = Collections.singletonList(federation);

    @Test
    public void findRedeemDataFromScriptHash_with_no_fastBridgeInformation_in_storage_call_super() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFastBridgeFederationInformation(any(byte[].class))).thenReturn(Optional.empty());

        FastBridgeCompatibleBtcWalletWithStorage fastBridgeCompatibleBtcWalletWithStorage = new FastBridgeCompatibleBtcWalletWithStorage(
            mock(Context.class), federationList, provider);

        RedeemData redeemData = fastBridgeCompatibleBtcWalletWithStorage.findRedeemDataFromScriptHash(
            federation.getP2SHScript().getPubKeyHash());

        Assert.assertNotNull(redeemData);
        Assert.assertEquals(federation.getRedeemScript(), redeemData.redeemScript);
    }

    @Test
    public void findRedeemDataFromScriptHash_with_fastBridgeInformation_in_storage() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Sha256Hash derivationArgumentsHash = Sha256Hash.of(new byte[]{1});

        Script fastBridgeRedeemScript = RedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            federation.getRedeemScript(), derivationArgumentsHash);

        Script p2SHOutputScript = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
        byte[] fastBridgeFederationP2SH = p2SHOutputScript.getPubKeyHash();

        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(
                derivationArgumentsHash,
                federation.getP2SHScript().getPubKeyHash(),
                fastBridgeFederationP2SH);

        when(provider.getFastBridgeFederationInformation(fastBridgeFederationP2SH))
            .thenReturn(Optional.of(fastBridgeFederationInformation));

        FastBridgeCompatibleBtcWalletWithStorage fastBridgeCompatibleBtcWalletWithStorage = new FastBridgeCompatibleBtcWalletWithStorage(
            mock(Context.class), federationList, provider
        );

        RedeemData redeemData = fastBridgeCompatibleBtcWalletWithStorage.findRedeemDataFromScriptHash(fastBridgeFederationP2SH);

        Assert.assertNotNull(redeemData);
        Assert.assertEquals(fastBridgeRedeemScript, redeemData.redeemScript);
    }

    @Test
    public void findRedeemDataFromScriptHash_null_destination_federation() {
        Sha256Hash derivationArgumentsHash = Sha256Hash.of(new byte[]{2});
        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(
                derivationArgumentsHash,
                new byte[0],
                new byte[1]);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFastBridgeFederationInformation(any(byte[].class))).thenReturn(
            Optional.of(fastBridgeFederationInformation)
        );

        FastBridgeCompatibleBtcWalletWithStorage fastBridgeCompatibleBtcWalletWithStorage =
            new FastBridgeCompatibleBtcWalletWithStorage(mock(Context.class), federationList, provider);

        Assert.assertNull(fastBridgeCompatibleBtcWalletWithStorage.findRedeemDataFromScriptHash(new byte[]{1}));
    }

    @Test
    public void getFastBridgeFederationInformation_data_on_storage() {
        byte[] fastBridgeScriptHash = new byte[1];
        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(
                Sha256Hash.of(new byte[]{2}),
                new byte[0],
                fastBridgeScriptHash);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFastBridgeFederationInformation(any(byte[].class))).thenReturn(
            Optional.of(fastBridgeFederationInformation)
        );

        FastBridgeCompatibleBtcWalletWithStorage fastBridgeCompatibleBtcWalletWithStorage =
            new FastBridgeCompatibleBtcWalletWithStorage(mock(Context.class), federationList, provider);

        Optional<FastBridgeFederationInformation> result = fastBridgeCompatibleBtcWalletWithStorage.
            getFastBridgeFederationInformation(fastBridgeScriptHash);

        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(fastBridgeFederationInformation, result.get());
    }

    @Test
    public void getFastBridgeFederationInformation_no_data_on_storage() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFastBridgeFederationInformation(any(byte[].class))).thenReturn(
            Optional.empty()
        );

        FastBridgeCompatibleBtcWalletWithStorage fastBridgeCompatibleBtcWalletWithStorage =
            new FastBridgeCompatibleBtcWalletWithStorage(mock(Context.class), federationList, provider);

        Optional<FastBridgeFederationInformation> result = fastBridgeCompatibleBtcWalletWithStorage.
            getFastBridgeFederationInformation(new byte[1]);

        Assert.assertEquals(Optional.empty(), result);
    }
}
