package co.rsk.peg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.script.ErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ErpFederationTest {
    private ErpFederation federation;
    private final byte[] ERP_TESTNET_REDEEM_SCRIPT_BYTES = Hex.decode("6453210208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce210225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f42102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da210344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a0921039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9556702cd50b27552210216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d321034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f210275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f145368ae");

    // ERP federation keys
    private static final List<BtcECKey> ERP_FED_KEYS = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
        "03776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c1",
        "03ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc"
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    private static final long ACTIVATION_DELAY_VALUE = 5063;

    @Before
    public void createErpFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        federation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_FED_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );
    }

    @Test
    public void getErpPubKeys() {
        Assert.assertEquals(ERP_FED_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void getActivationDelay() {
        Assert.assertEquals(ACTIVATION_DELAY_VALUE, federation.getActivationDelay());
    }

    @Test
    public void getRedeemScript() {
        Script redeemScript = federation.getRedeemScript();
        Assert.assertEquals(19, redeemScript.getChunks().size());

        // First element: OP_0 - Belonging to the standard of BTC
        // M elements OP_0 - Belonging to M/N amount of signatures
        // OP_0 - Belonging to ERP
        // Last element: Program of redeem script
        String expectedProgram = "64522102ed3bace23c5e17652e174c835fb72bf53ee306b3406a26890221b4ce"
            + "f7500f88210385a7b790fc9d962493788317e4874a4ab07f1e9c78c773c47f2f6c96df756f052103cd5"
            + "a3be41717d65683fe7a9de8ae5b4b8feced69f26a8b55eeefbcc2e74b75fb53670213c7b2755321029c"
            + "ecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54"
            + "c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103776b1fd8f86da3c1db3d69699e8250a1"
            + "5877d286734ea9a6da8e9d8ad25d16c12103ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58"
            + "513312550ea1584bc2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b"
            + "245568ae";

        Assert.assertEquals(expectedProgram, Hex.toHexString(redeemScript.getProgram()));
    }

    @Test
    public void getP2SHScript() {
        Script p2shs = federation.getP2SHScript();
        String expectedProgram = "a914bbb7b7942d0fb850bd619b399e96d8b8b36ff89187";

        Assert.assertEquals(expectedProgram, Hex.toHexString(p2shs.getProgram()));
        Assert.assertEquals(3, p2shs.getChunks().size());
        Assert.assertEquals(
            federation.getAddress(),
            p2shs.getToAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
        );
    }

    @Test
    public void getAddress() {
        String fedAddress = federation.getAddress().toBase58();
        String expectedAddress = "2NAMnS3XpcWw1KrYkszRw7gWHFkxuMYrU2Z";

        Assert.assertEquals(expectedAddress, fedAddress);
    }

    @Test
    public void getErpPubKeys_compressed_public_keys() {
        Assert.assertEquals(ERP_FED_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void getErpPubKeys_uncompressed_public_keys() {
        // Public keys used for creating federation, but uncompressed format now
        List<BtcECKey> erpPubKeysList = Arrays.stream(new String[]{
            "04b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b9"
                + "67e6b243635dfd897d936044b05344860cd5494283aad8508d52a784eb6a1f4527e2c9f",
            "049cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301b069"
                + "dfae714467c15649fbdb61c70e367fb43f326dc807691923cd16698af99e",
            "04284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd4076b8bb"
                + "c11b4a3f559c8041b03a903d7d7efacc4dd3796a27df324c7aa3bc5d",
            "04776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c118424627ece3cba0"
                + "028fcbd4a0372485641a02383f4cdcee932542efd60d1029",
            "04ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc08b4554783b4960c6a"
                + "bb761979d24d76a08ac38e775d72b960cd5644e1a54f01"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        // Recreate federation
        federation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            erpPubKeysList,
            ACTIVATION_DELAY_VALUE,
            mock(ActivationConfig.ForBlock.class)
        );

        Assert.assertEquals(ERP_FED_KEYS, federation.getErpPubKeys());
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_negativeCsvValue() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_FED_KEYS,
            -100L,
            activations
        );
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_csvValueAboveMax() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_FED_KEYS,
            ErpFederationRedeemScriptParser.MAX_CSV_VALUE + 1,
            activations
        );
    }

    @Test
    public void getRedeemScript_before_RSKIP_284_testnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_FED_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Script testnetRedeemScript = new Script(ERP_TESTNET_REDEEM_SCRIPT_BYTES);
        Assert.assertEquals(testnetRedeemScript, erpFederation.getRedeemScript());
    }

    @Test
    public void getRedeemScript_before_RSKIP_284_mainnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            ERP_FED_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Script testnetRedeemScript = new Script(ERP_TESTNET_REDEEM_SCRIPT_BYTES);
        Assert.assertNotEquals(testnetRedeemScript, erpFederation.getRedeemScript());
    }

    @Test
    public void getRedeemScript_after_RSKIP_284_testnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_FED_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Script testnetRedeemScript = new Script(ERP_TESTNET_REDEEM_SCRIPT_BYTES);
        Assert.assertNotEquals(testnetRedeemScript, erpFederation.getRedeemScript());
    }

    @Test
    public void getRedeemScript_after_RSKIP_284_mainnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            ERP_FED_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Script testnetRedeemScript = new Script(ERP_TESTNET_REDEEM_SCRIPT_BYTES);
        Assert.assertNotEquals(testnetRedeemScript, erpFederation.getRedeemScript());
    }
}
