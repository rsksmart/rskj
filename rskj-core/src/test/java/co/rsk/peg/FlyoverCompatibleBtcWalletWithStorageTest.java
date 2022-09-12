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
import co.rsk.peg.flyover.FlyoverFederationInformation;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlyoverCompatibleBtcWalletWithStorageTest {
    private static final List<BtcECKey> erpFedKeys = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    private Federation federation;
    private ErpFederation erpFederation;
    private List<Federation> federationList;
    private List<Federation> erpFederationList;

    @BeforeEach
    void setup() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        federation = new Federation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            erpFedKeys,
            5063,
            activations
        );

        federationList = Collections.singletonList(federation);
        erpFederationList = Collections.singletonList(erpFederation);
    }

    @Test
    void findRedeemDataFromScriptHash_with_no_flyoverInformation_in_storage_call_super() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFlyoverFederationInformation(any(byte[].class))).thenReturn(Optional.empty());

        FlyoverCompatibleBtcWalletWithStorage flyoverCompatibleBtcWalletWithStorage = new FlyoverCompatibleBtcWalletWithStorage(
            mock(Context.class), federationList, provider);

        RedeemData redeemData = flyoverCompatibleBtcWalletWithStorage.findRedeemDataFromScriptHash(
            federation.getP2SHScript().getPubKeyHash());

        Assertions.assertNotNull(redeemData);
        Assertions.assertEquals(federation.getRedeemScript(), redeemData.redeemScript);
    }

    @Test
    void findRedeemDataFromScriptHash_with_flyoverInformation_in_storage() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(1);

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            federation.getRedeemScript(), Sha256Hash.wrap(derivationArgumentsHash.getBytes()));

        Script p2SHOutputScript = ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript);
        byte[] flyoverFederationP2SH = p2SHOutputScript.getPubKeyHash();

        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(
                derivationArgumentsHash,
                federation.getP2SHScript().getPubKeyHash(),
                flyoverFederationP2SH);

        when(provider.getFlyoverFederationInformation(flyoverFederationP2SH))
            .thenReturn(Optional.of(flyoverFederationInformation));

        FlyoverCompatibleBtcWalletWithStorage flyoverCompatibleBtcWalletWithStorage = new FlyoverCompatibleBtcWalletWithStorage(
            mock(Context.class), federationList, provider
        );

        RedeemData redeemData = flyoverCompatibleBtcWalletWithStorage.findRedeemDataFromScriptHash(flyoverFederationP2SH);

        Assertions.assertNotNull(redeemData);
        Assertions.assertEquals(flyoverRedeemScript, redeemData.redeemScript);
    }

    @Test
    void findRedeemDataFromScriptHash_with_flyoverInformation_in_storage_and_erp_fed() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(1);

        Script flyoverRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            Sha256Hash.wrap(derivationArgumentsHash.getBytes()
            )
        );

        Script p2SHOutputScript = ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript);
        byte[] flyoverFederationP2SH = p2SHOutputScript.getPubKeyHash();

        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(
                derivationArgumentsHash,
                erpFederation.getP2SHScript().getPubKeyHash(),
                flyoverFederationP2SH);

        when(provider.getFlyoverFederationInformation(flyoverFederationP2SH))
            .thenReturn(Optional.of(flyoverFederationInformation));

        FlyoverCompatibleBtcWalletWithStorage flyoverCompatibleBtcWalletWithStorage =
            new FlyoverCompatibleBtcWalletWithStorage(
                mock(Context.class),
                erpFederationList,
                provider
        );

        RedeemData redeemData = flyoverCompatibleBtcWalletWithStorage
            .findRedeemDataFromScriptHash(flyoverFederationP2SH);

        Assertions.assertNotNull(redeemData);
        Assertions.assertEquals(flyoverRedeemScript, redeemData.redeemScript);
    }

    @Test
    void findRedeemDataFromScriptHash_null_destination_federation() {
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(2);
        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(
                derivationArgumentsHash,
                new byte[0],
                new byte[1]);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFlyoverFederationInformation(any(byte[].class))).thenReturn(
            Optional.of(flyoverFederationInformation)
        );

        FlyoverCompatibleBtcWalletWithStorage flyoverCompatibleBtcWalletWithStorage =
            new FlyoverCompatibleBtcWalletWithStorage(mock(Context.class), federationList, provider);

        Assertions.assertNull(flyoverCompatibleBtcWalletWithStorage.findRedeemDataFromScriptHash(new byte[]{1}));
    }

    @Test
    void getFlyoverFederationInformation_data_on_storage() {
        byte[] flyoverScriptHash = new byte[1];
        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(
                PegTestUtils.createHash3(2),
                new byte[0],
                flyoverScriptHash);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFlyoverFederationInformation(any(byte[].class))).thenReturn(
            Optional.of(flyoverFederationInformation)
        );

        FlyoverCompatibleBtcWalletWithStorage flyoverCompatibleBtcWalletWithStorage =
            new FlyoverCompatibleBtcWalletWithStorage(mock(Context.class), federationList, provider);

        Optional<FlyoverFederationInformation> result = flyoverCompatibleBtcWalletWithStorage.
            getFlyoverFederationInformation(flyoverScriptHash);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(flyoverFederationInformation, result.get());
    }

    @Test
    void getFlyoverFederationInformation_no_data_on_storage() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFlyoverFederationInformation(any(byte[].class))).thenReturn(
            Optional.empty()
        );

        FlyoverCompatibleBtcWalletWithStorage flyoverCompatibleBtcWalletWithStorage =
            new FlyoverCompatibleBtcWalletWithStorage(mock(Context.class), federationList, provider);

        Optional<FlyoverFederationInformation> result = flyoverCompatibleBtcWalletWithStorage.
            getFlyoverFederationInformation(new byte[1]);

        Assertions.assertEquals(Optional.empty(), result);
    }
}
