package co.rsk.peg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.FastBridgeErpRedeemScriptParser;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import co.rsk.peg.utils.ScriptBuilderWrapper;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FastBridgeCompatibleBtcWalletWithStorageTest {
    private static final List<BtcECKey> erpFedKeys = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    private Federation federation;
    private ErpFederation erpFederation;
    private List<Federation> federationList;
    private List<Federation> erpFederationList;

    @Before
    public void setup() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        ScriptBuilderWrapper scriptBuilderWrapper = ScriptBuilderWrapper.getInstance();

        federation = new Federation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                scriptBuilderWrapper
        );

        erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            erpFedKeys,
            5063,
            activations,
                scriptBuilderWrapper
        );

        federationList = Collections.singletonList(federation);
        erpFederationList = Collections.singletonList(erpFederation);
    }

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
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(1);

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            federation.getRedeemScript(), Sha256Hash.wrap(derivationArgumentsHash.getBytes()));

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
    public void findRedeemDataFromScriptHash_with_fastBridgeInformation_in_storage_and_erp_fed() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(1);

        Script fastBridgeRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            Sha256Hash.wrap(derivationArgumentsHash.getBytes()
            )
        );

        Script p2SHOutputScript = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
        byte[] fastBridgeFederationP2SH = p2SHOutputScript.getPubKeyHash();

        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(
                derivationArgumentsHash,
                erpFederation.getP2SHScript().getPubKeyHash(),
                fastBridgeFederationP2SH);

        when(provider.getFastBridgeFederationInformation(fastBridgeFederationP2SH))
            .thenReturn(Optional.of(fastBridgeFederationInformation));

        FastBridgeCompatibleBtcWalletWithStorage fastBridgeCompatibleBtcWalletWithStorage =
            new FastBridgeCompatibleBtcWalletWithStorage(
                mock(Context.class),
                erpFederationList,
                provider
        );

        RedeemData redeemData = fastBridgeCompatibleBtcWalletWithStorage
            .findRedeemDataFromScriptHash(fastBridgeFederationP2SH);

        Assert.assertNotNull(redeemData);
        Assert.assertEquals(fastBridgeRedeemScript, redeemData.redeemScript);
    }

    @Test
    public void findRedeemDataFromScriptHash_null_destination_federation() {
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(2);
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
                PegTestUtils.createHash3(2),
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
