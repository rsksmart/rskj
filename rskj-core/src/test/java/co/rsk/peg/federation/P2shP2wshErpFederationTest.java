package co.rsk.peg.federation;

import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.signTxInputWithKey;
import static co.rsk.peg.bitcoin.BitcoinUtils.*;
import static co.rsk.peg.federation.ErpFederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;
import static co.rsk.peg.federation.ErpFederationCreationException.Reason.REDEEM_SCRIPT_CREATION_FAILED;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.*;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegUtils;
import co.rsk.peg.ReleaseTransactionBuilder;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class P2shP2wshErpFederationTest {
    private static final ErpFederation federation = P2shP2wshErpFederationBuilder.builder().build();

    @Test
    void createFederation_withNullErpKeys_throwsErpFederationCreationException() {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        P2shP2wshErpFederationBuilder federationBuilderWithNullErpKeys = federationBuilder.withErpPublicKeys(null);

        ErpFederationCreationException exception =
            assertThrows(ErpFederationCreationException.class, federationBuilderWithNullErpKeys::build);
        assertEquals(NULL_OR_EMPTY_EMERGENCY_KEYS, exception.getReason());
    }

    @Test
    void createFederation_withEmptyErpKeys_throwsErpFederationCreationException() {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        P2shP2wshErpFederationBuilder federationBuilderWithEmptyErpKeys = federationBuilder.withErpPublicKeys(new ArrayList<>());

        ErpFederationCreationException exception =
            assertThrows(ErpFederationCreationException.class, federationBuilderWithEmptyErpKeys::build);
        assertEquals(NULL_OR_EMPTY_EMERGENCY_KEYS, exception.getReason());
    }

    @Test
    void createFederation_withOneErpKey_valid() {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        List<BtcECKey> oneErpKey = Collections.singletonList(federation.getErpPubKeys().get(0));
        P2shP2wshErpFederationBuilder federationBuilderWithOneErpKey = federationBuilder.withErpPublicKeys(oneErpKey);

        assertDoesNotThrow(federationBuilderWithOneErpKey::build);
    }

    @ParameterizedTest
    @ValueSource(longs = {20L, 130L, 500L, 33_000L, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE})
    void createFederation_withValidCsvValues_valid(long csvValue) {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        P2shP2wshErpFederationBuilder federationBuilderWithCsvValue = federationBuilder.withErpActivationDelay(csvValue);

        assertDoesNotThrow(federationBuilderWithCsvValue::build);
    }

    @ParameterizedTest
    @ValueSource(longs = {-100L, 0L, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE + 1, 100_000L, 8_400_000L})
    void createFederation_invalidCsvValues_throwsErpFederationCreationException(long csvValue) {
        P2shP2wshErpFederationBuilder p2shP2wshErpFederationBuilder = P2shP2wshErpFederationBuilder.builder()
            .withErpActivationDelay(csvValue);
        ErpFederationCreationException fedException =
            assertThrows(ErpFederationCreationException.class, p2shP2wshErpFederationBuilder::build);

        assertEquals(REDEEM_SCRIPT_CREATION_FAILED, fedException.getReason());
    }

    @Test
    void getP2SHScript_hasExpectedStructure() {
        // expected structure is op hash160, op pushbytes20 + script, op equal
        Script outputScript = federation.getP2SHScript();
        List<ScriptChunk> chunks = outputScript.getChunks();

        int opHash160 = chunks.get(0).opcode;
        int expectedOpHash160 = ScriptOpCodes.OP_HASH160;
        assertEquals(expectedOpHash160, opHash160);

        ScriptChunk p2shScriptChunk = chunks.get(1);
        int expectedPushDataLength = 20;
        assertEquals(expectedPushDataLength, p2shScriptChunk.opcode);
        String expectedP2hScript = "ef29a598f5b514f1b6f6d546c5e611c8f2cf02f3";
        assertArrayEquals(Hex.decode(expectedP2hScript), p2shScriptChunk.data);

        int opEqual = chunks.get(2).opcode;
        int expectedOpEqual = ScriptOpCodes.OP_EQUAL;
        assertEquals(expectedOpEqual, opEqual);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("test segwit tx build using real txs")
    class SegwitTxBuild {
        private final List<BtcECKey> btcMembersKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10",
                "fed11", "fed12", "fed13", "fed14", "fed15", "fed16", "fed17", "fed18", "fed19", "fed20"
            },
            true
        );
        private final List<BtcECKey> erpPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "erp1", "erp2", "erp3", "erp4","erp5", "erp6", "erp7", "erp8","erp9", "erp10",
                "erp11", "erp12", "erp13", "erp14","erp15", "erp16", "erp17", "erp18","erp19", "erp20"
            },
            true
        );

        private final byte[] emptyByte = new byte[] {};
        private final int inputIndex = 0;

        private NetworkParameters networkParameters;

        private Coin prevTxSentValue;
        private Coin fee;

        private Federation federation;
        private int federationFormatVersion;
        private Script redeemScript;
        private Script outputScript;
        private Sha256Hash prevTxHash;
        private Address receiver;
        private BtcTransaction tx;


        private void setUpFederation() {
            long erpActivationDelay = 30;
            federation = P2shP2wshErpFederationBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withMembersBtcPublicKeys(btcMembersKeys)
                .withErpActivationDelay(erpActivationDelay)
                .withErpPublicKeys(erpPublicKeys)
                .build();

            federationFormatVersion = federation.getFormatVersion();
        }

        private void arrangeSignedTx() {
            setUpTx();
            signTx();
        }

        private void setUpTx() {
            tx = new BtcTransaction(networkParameters);
            int outputIndex = 0;
            tx.addInput(prevTxHash, outputIndex, new Script(new byte[]{}));
            tx.addOutput(prevTxSentValue.minus(fee), receiver);
            tx.setVersion(ReleaseTransactionBuilder.BTC_TX_VERSION_2);
        }

        private void signTx() {
            addSpendingFederationBaseScript(tx, inputIndex, redeemScript, federationFormatVersion);
            Sha256Hash sigHash = generateSigHashForSegwitTransactionInput(tx, inputIndex, prevTxSentValue);

            for (int i = 0; i < federation.getNumberOfSignaturesRequired(); i++) {
                BtcECKey signingKey = btcMembersKeys.get(i);
                signTxInputWithKey(tx, inputIndex, sigHash, signingKey, outputScript);
            }
        }


        private static Stream<Arguments> spendFromP2shP2wshErpFedArgsProvider() {
            BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
            BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();

            return Stream.of(
                Arguments.of(
                    bridgeMainNetConstants
                ),
                Arguments.of(
                    bridgeTestNetConstants
                )
            );
        }

        @ParameterizedTest()
        @MethodSource("spendFromP2shP2wshErpFedArgsProvider")
        void spendFromP2shP2wshErpFed(
            BridgeConstants bridgeConstants) {
            // Arrange
            List<BtcECKey> erpKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"erp1", "erp2", "erp3", "erp4"},
                true
            );

            FederationConstants federationConstants = bridgeConstants.getFederationConstants();
            long erpActivationDelay = federationConstants.getErpFedActivationDelay();
            NetworkParameters networkParameters = bridgeConstants.getBtcParams();

            ErpFederation p2wshP2shErpFederation = P2shP2wshErpFederationBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withErpActivationDelay(erpActivationDelay)
                .withErpPublicKeys(erpKeys)
                .build();

            Coin value = Coin.valueOf(1_000_000);

            Address receiver = BitcoinTestUtils.createP2PKHAddress(
                networkParameters,
                "destination"
            );

            BtcTransaction fundTx = new BtcTransaction(networkParameters);
            fundTx.addOutput(value, p2wshP2shErpFederation.getAddress());

            BtcTransaction spendTx = new BtcTransaction(networkParameters);
            Sha256Hash fundTxHash = fundTx.getHash();
            int outputIndex = 0;
            spendTx.addInput(fundTxHash, outputIndex, new Script(new byte[]{}));
            spendTx.addOutput(value, receiver);

            spendTx.setVersion(BTC_TX_VERSION_2);
            int inputIndex = 0;
            spendTx.getInput(inputIndex).setSequenceNumber(erpActivationDelay);

            // Create signatures
            Script redeemScript = p2wshP2shErpFederation.getRedeemScript();
            Sha256Hash sigHash = spendTx.hashForWitnessSignature(
                inputIndex,
                redeemScript,
                value,
                BtcTransaction.SigHash.ALL,
                false
            );

            int numberOfSignaturesRequired = p2wshP2shErpFederation.getNumberOfEmergencySignaturesRequired();
            List<TransactionSignature> signatures = new ArrayList<>();
            for (int i = 0; i < numberOfSignaturesRequired; i++) {
                BtcECKey keyToSign = erpKeys.get(i);
                BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
                keyToSign.verify(sigHash, signature);
                TransactionSignature txSignature = new TransactionSignature(
                    signature,
                    BtcTransaction.SigHash.ALL,
                    false
                );
                signatures.add(txSignature);
            }

            TransactionWitness witness = FederationTestUtils.createBaseWitnessThatSpendsFromEmergencyKeys(redeemScript, signatures, numberOfSignaturesRequired);
            TransactionWitness inputWitnessWithSignature = updateWitnessWithEmergencySignatures(witness, signatures);
            spendTx.setWitness(inputIndex, inputWitnessWithSignature);
            Script segwitScriptSig = buildSegwitScriptSig(redeemScript);
            TransactionInput input = spendTx.getInput(inputIndex);
            input.setScriptSig(segwitScriptSig);

            Script inputScript = spendTx.getInput(inputIndex).getScriptSig();

            // Act & Assert
            assertDoesNotThrow(() -> inputScript.correctlySpends(
                spendTx,
                0,
                p2wshP2shErpFederation.getP2SHScript(),
                Script.ALL_VERIFY_FLAGS
            ));
        }

        @Test
        void fedData_forRealP2shP2wshFed_testnet_shouldReturnExpectedData() {
            // data from https://mempool.space/testnet/tx/3ba64656d3c87f1c284492c1a7e2dbbceebe7ac851e8f0f477d92f9796af1f2d
            // arrange
            prevTxSentValue = Coin.valueOf(10_000);
            fee = Coin.valueOf(1_000);
            networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
            setUpFederation();
            redeemScript = federation.getRedeemScript();
            outputScript = federation.getP2SHScript();

            // act
            // recreate signed tx
            prevTxHash = Sha256Hash.wrap("8bd31413fef1d5d509572f329075d61606fa0fc515e74bc0e48abfa42ebe619f");
            receiver = Address.fromBase58(networkParameters,"msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP");
            arrangeSignedTx();

            // assert
            byte[] expectedP2SHScript = Hex.decode("f31a91b8c03d066bdb9c6b2089adf3a64026765e");
            assertP2shScript(expectedP2SHScript);

            String expectedAddress = "2NFQe1xgfZutFkG25oVEiqUzH5rTaj7VpoU";
            assertAddress(expectedAddress, federation.getAddress());

            byte[] expectedHashedRedeemScriptProgram = Hex.decode("f42e529b3f6c374fc38f69ab7f5ee8e363201166fc0119bf3065349afeedea06");
            assertScriptSig(expectedHashedRedeemScriptProgram);

            // redeem script
            byte[] expectedRedeemScriptProgram = Hex.decode("645b210211310637a4062844098b46278059298cc948e1ff314ca9ec75c82e0d0b8ad22c210238de69e208565fd82e4b76c4eff5d817a51679b8a90c41709e49660ba23501c521024b120731b26ec7165cddd214fc8e3f0c844a03dc0e533fb0cf9f89ad2f68a881210274564db76110474ac0d7e09080c182855b22a864cc201ed55217b23301f52f222102867f0e693a2553bf2bc13a5efa0b516b28e66317fbe8e484dd3f375bcb48ec592102881af2910c909f224557353dd28e3729363cf5c24232f26d25c92dac72a3dcdb21029c75b3257e0842c4be48e57e39bf2735c74a76c4b1c0b08d1cc66bf5b8748cc12102a46cbe93287cb51a398a157de2b428f21a94f46affdd916ce921bd10db6520332102d335ef4eeb74330c3a53f529f9741fa096412c7982ed681fcf69763894f34f892102d3f5fd6e107cf68b1be8dce0e16a0a8afb8dcef9a76c851d7eaf6d51c46a35752103163b86a62b4eeeb52f67cb16ce13a8622a066f2a063280749b956a97705dfc3d21033267e382e076cbaa199d49ea7362535f95b135de181caf66b391f541bf39ab0e210343e106d90183e2eef7d5cb7538a634439bf1301d731787c6736922ff19e750ed21034461d4263b907cfc5ebb468f19d6a133b567f3cc4855e8725faaf60c6e388bca21036e92e6555d2e70af4f5a4f888145356e60bb1a5bc00786a8e9f50152090b2f692103ab54da6b69407dcaaa85f6904687052c93f1f9dd0633f1321b3e624fcd30144b2103bd5b51b1c5d799da190285c8078a2712b8e5dc6f73c799751e6256bb89a4bd042103be060191c9632184f2a0ab2638eeed04399372f37fc7a3cff5291cfd6426cf352103e6def9ef0597336eb58d24f955b6b63756cf7b3885322f9d0cf5a2a12f7e459b2103ef03253b7b4f33d68c39141eb016df15fafbb1d0fa4a2e7f208c94ea154ab8c30114ae67011eb2755b21021a560245f78312588f600315d75d493420bed65873b63d0d4bb8ca1b9163a35b2102218e9dc07ac4190a1d7df94fc75953b36671129f12668a94f1f504fe47399ead210272ed6e14e70f6b4757d412729730837bc63b6313276be8308a5a96afd63af9942102872f69892a74d60f6185c2908414dcddb24951c035a1a8466c6c56f55043e7602102886d7d8e865f75dfda3ddf94619af87ad8aa71e8ef393e1e57593576b7d7af1621028e59462fb53ba31186a353b7ea77ebefda9097392e45b7ca7a216168230d05af21028f5a88b08d75765b36951254e68060759de5be7e559972c37c67fc8cedafeb262102c9ced4bbc468af9ace1645df2fd50182d5822cb4c68aae0e50ae1d45da260d2a2102deba35a96add157b6de58f48bb6e23bcb0a17037bed1beb8ba98de6b0a0d71d62102f2e00fefa5868e2c56405e188ec1d97557a7c77fb6a448352cc091c2ae9d50492102fb8c06c723d4e59792e36e6226087fcfac65c1d8a0d5c5726a64102a551528442103077c62a45ea1a679e54c9f7ad800d8e40eaf6012657c8dccd3b61d5e070d9a432103616959a72dd302043e9db2dbd7827944ecb2d555a8f72a48bb8f916ec5aac6ec210362f9c79cd0586704d6a9ea863573f3b123d90a31faaa5a1d9a69bf9631c78ae321036899d94ad9d3f24152dd4fa79b9cb8dddbd26d18297be4facb295f57c9de60bd210376e4cb35baa8c46b0dcffaf303785c5f7aadf457df30ac956234cc8114e2f47d2103a587256beec4e167aebc478e1d6502bb277a596ae9574ccb646da11fffbf36502103bb9da162c3f581ced93167f86d7e0e5962762a1188f5bd1f8b5d08fed46ef73d2103c34fcd05cef2733ea7337c37f50ae26245646aba124948c6ff8dcdf8212849982103f8ac768e683a07ac4063f72a6d856aedeae109f844abcfa34ac9519d715177460114ae68");
            assertRedeemScript(expectedRedeemScriptProgram);

            // witness script
            TransactionWitness witness = tx.getWitness(inputIndex);

            List<byte[]> expectedSigs = Arrays.asList(
                Hex.decode("3045022100d36d7d5878013c7a337ba85e4b8197584a7ab263a30c97ae5e7b5fa8d4bb42b202204c5dd728a4864aba1dc09be1a27148ae7809408c1414acfef64169ed787f614d01"),
                Hex.decode("3043021f105fa4abe84f9f7617d97529a12b202f91c45a32621ee9a6a93a83beb4f80c02204fee1e395c2a2e3ec914f8eccbe77a4247936a256f843248b94d92af7d787a0c01"),
                Hex.decode("3044022054aaadeae847f398915fb12829a9666e0305ef59606002304533027096084084022011b712333fd2a935b4dfa741474e84b82dffac2f27fcc46263be9c639b8d871801"),
                Hex.decode("304402206e06e1539a654bb75ec599399f7d16beb6b26b9c83bf8d4b75d3beafe655fdcd02204e311b438b3b13d28495045f1d3e6016998b9f01bc34213d041e569dead054fc01"),
                Hex.decode("3045022100e82640227a5a72a5a0fce7e62bf136aca3b827fdcfe2e8448bc805354fa1af000220571cba68410c4e8943c95eb854570e8f2dc65f92d75bc24cd3ae817920c2933701"),
                Hex.decode("304402200e0d47a222990625be86d175edc65d2c8a7d3473d602d7781ab4c1166431c92902207f5e6e3652faad6e99b93e132ac25de3bc2e6f93c90861062faedd9c177e7c0e01"),
                Hex.decode("3045022100f9dd3370feec1d848f8d57ffc147c50d5d5a0216c5d4a183a44f24400340f771022050e22b4cafb1f0e76599856a028c82e207152ad5bd612d65ac826b285c5abefc01"),
                Hex.decode("3045022100f03156c442bbf510e945c457384e316a91feee6519dd977eb26d0fa6ad067c6202205d690f2ca004f5146f8a920abaed8edeb958083cf77c9639e539a4c60561cfa801"),
                Hex.decode("3045022100948bf500c336f00146111deb2731cb1b0a289ba1e2b0ee05149d25a5eb92fdff022050765b58146819079e84d0db3b00a8448020c93bc2c43739a27546db4da2820701"),
                Hex.decode("304502210096bcbfbf4613e10b7f773fcdfd69708d4d8cfa50c8c3c520791164d67f8734b402201c62ce26b28d5dc9044163a9ce2dd630d4a6f3cb90b295934d07c62674a74d7401"),
                Hex.decode("3045022100c666b38e8c31bdbb7e208c27901ce507aaad3c10ba1ed92eab54cab1d77cba8102202424404e060a8748ea1b9ab129489c194a9a6f0a0c99241c9f3aa7073f4c932601")
            );
            assertWitnessScript(witness, expectedRedeemScriptProgram, expectedSigs);

            Sha256Hash expectedTxHash = Sha256Hash.wrap("3ba64656d3c87f1c284492c1a7e2dbbceebe7ac851e8f0f477d92f9796af1f2d");
            assertTxHash(expectedTxHash);
        }

        @Test
        void fedData_forRealP2shP2wshFlyoverFed_mainnet_shouldReturnExpectedData() {
            // data from https://mempool.space/tx/a4d76b6211b078cbc1d2079002437fcf018cc85cd40dd6195bb0f6b42930b96b
            // arrange
            prevTxSentValue = Coin.valueOf(100_000);
            fee = Coin.valueOf(10_000);
            networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
            setUpFederation();

            Keccak256 flyoverDerivationHash = new Keccak256("0100000000000000000000000000000000000000000000000000000000000000");
            redeemScript = PegUtils.getFlyoverFederationRedeemScript(flyoverDerivationHash, federation.getRedeemScript());
            outputScript = PegUtils.getFlyoverFederationOutputScript(redeemScript, federationFormatVersion);

            // act
            // recreate signed tx
            prevTxHash = Sha256Hash.wrap("ca9c3ff2685d65a0adf571a30d77c6d12857af9edfbf8f3a19ca4c1fc1eb47f5");
            receiver = Address.fromBase58(networkParameters,"12MXsCtte9onzqaHwN5VcnwZKGd7oDSsQq");
            arrangeSignedTx();

            // assert
            byte[] expectedP2SHScript = Hex.decode("412e8c6fb9691820b826d423111e12a2590c363d");
            assertP2shScript(expectedP2SHScript);

            String expectedAddress = "37dfcGJtPJjRUcPkPgqfstPB5umEea3rWZ";
            assertAddress(expectedAddress, PegUtils.getFlyoverFederationAddress(networkParameters, flyoverDerivationHash, federation));

            byte[] expectedHashedRedeemScriptProgram = Hex.decode("431ef0e7fb92b803aa735278649879a4ffe79f79eca7733a046d1d97698cac4f");
            assertScriptSig(expectedHashedRedeemScriptProgram);

            // redeem script
            byte[] expectedRedeemScriptProgram = Hex.decode("20010000000000000000000000000000000000000000000000000000000000000075645b210211310637a4062844098b46278059298cc948e1ff314ca9ec75c82e0d0b8ad22c210238de69e208565fd82e4b76c4eff5d817a51679b8a90c41709e49660ba23501c521024b120731b26ec7165cddd214fc8e3f0c844a03dc0e533fb0cf9f89ad2f68a881210274564db76110474ac0d7e09080c182855b22a864cc201ed55217b23301f52f222102867f0e693a2553bf2bc13a5efa0b516b28e66317fbe8e484dd3f375bcb48ec592102881af2910c909f224557353dd28e3729363cf5c24232f26d25c92dac72a3dcdb21029c75b3257e0842c4be48e57e39bf2735c74a76c4b1c0b08d1cc66bf5b8748cc12102a46cbe93287cb51a398a157de2b428f21a94f46affdd916ce921bd10db6520332102d335ef4eeb74330c3a53f529f9741fa096412c7982ed681fcf69763894f34f892102d3f5fd6e107cf68b1be8dce0e16a0a8afb8dcef9a76c851d7eaf6d51c46a35752103163b86a62b4eeeb52f67cb16ce13a8622a066f2a063280749b956a97705dfc3d21033267e382e076cbaa199d49ea7362535f95b135de181caf66b391f541bf39ab0e210343e106d90183e2eef7d5cb7538a634439bf1301d731787c6736922ff19e750ed21034461d4263b907cfc5ebb468f19d6a133b567f3cc4855e8725faaf60c6e388bca21036e92e6555d2e70af4f5a4f888145356e60bb1a5bc00786a8e9f50152090b2f692103ab54da6b69407dcaaa85f6904687052c93f1f9dd0633f1321b3e624fcd30144b2103bd5b51b1c5d799da190285c8078a2712b8e5dc6f73c799751e6256bb89a4bd042103be060191c9632184f2a0ab2638eeed04399372f37fc7a3cff5291cfd6426cf352103e6def9ef0597336eb58d24f955b6b63756cf7b3885322f9d0cf5a2a12f7e459b2103ef03253b7b4f33d68c39141eb016df15fafbb1d0fa4a2e7f208c94ea154ab8c30114ae67011eb2755b21021a560245f78312588f600315d75d493420bed65873b63d0d4bb8ca1b9163a35b2102218e9dc07ac4190a1d7df94fc75953b36671129f12668a94f1f504fe47399ead210272ed6e14e70f6b4757d412729730837bc63b6313276be8308a5a96afd63af9942102872f69892a74d60f6185c2908414dcddb24951c035a1a8466c6c56f55043e7602102886d7d8e865f75dfda3ddf94619af87ad8aa71e8ef393e1e57593576b7d7af1621028e59462fb53ba31186a353b7ea77ebefda9097392e45b7ca7a216168230d05af21028f5a88b08d75765b36951254e68060759de5be7e559972c37c67fc8cedafeb262102c9ced4bbc468af9ace1645df2fd50182d5822cb4c68aae0e50ae1d45da260d2a2102deba35a96add157b6de58f48bb6e23bcb0a17037bed1beb8ba98de6b0a0d71d62102f2e00fefa5868e2c56405e188ec1d97557a7c77fb6a448352cc091c2ae9d50492102fb8c06c723d4e59792e36e6226087fcfac65c1d8a0d5c5726a64102a551528442103077c62a45ea1a679e54c9f7ad800d8e40eaf6012657c8dccd3b61d5e070d9a432103616959a72dd302043e9db2dbd7827944ecb2d555a8f72a48bb8f916ec5aac6ec210362f9c79cd0586704d6a9ea863573f3b123d90a31faaa5a1d9a69bf9631c78ae321036899d94ad9d3f24152dd4fa79b9cb8dddbd26d18297be4facb295f57c9de60bd210376e4cb35baa8c46b0dcffaf303785c5f7aadf457df30ac956234cc8114e2f47d2103a587256beec4e167aebc478e1d6502bb277a596ae9574ccb646da11fffbf36502103bb9da162c3f581ced93167f86d7e0e5962762a1188f5bd1f8b5d08fed46ef73d2103c34fcd05cef2733ea7337c37f50ae26245646aba124948c6ff8dcdf8212849982103f8ac768e683a07ac4063f72a6d856aedeae109f844abcfa34ac9519d715177460114ae68");
            assertRedeemScript(expectedRedeemScriptProgram);

            // witness script
            TransactionWitness witness = tx.getWitness(inputIndex);
            List<byte[]> expectedSigs = Arrays.asList(
                Hex.decode("30440220375d5ddad1d329105d5bb2453fd4a57f93e8b864b11519cea4c6932d414236d3022056e9567d5e8fea093cab9d85432007add04eed9019790159f3b644c3b3e6909301"),
                Hex.decode("304502210095201c22ed71453c89288bbb87e98425e59f90523ffbf8669cf6739cb4d98868022017ba3a6903c6aa4d770643717ed74edcddfda54f60f8825f5cd4ed12d265db6401"),
                Hex.decode("30450221009d8e509f6f9b22e74401f3aa06df9e212af0708e798d9b8ae9badc725a7f3d890220592d4ac99a951408f5d49a76015f9c5c8e54e34ff32bba2bfeb73ea3b4ebd75d01"),
                Hex.decode("3045022100a960302593ecae2aba3f41bcc4cda98e2fcf54de4c479440abf002c444b98bb0022055440ae8f2b425e7b2f47847794789c769645af002e8a1084dd59e693a5e04c201"),
                Hex.decode("304402200d11ffb6808f6b426aff02e603abfc8eac5b3e74e3b2fb4318d47640692c7b0d0220274929b2f6e583c43358adbde3465ea1254de0abd3787db88039d00dd3d3015e01"),
                Hex.decode("30450221009681ba08b0c826fff6499c86fd0f38216d0ea36b24d440e4aa3f5598c385370602203e9e0dae5141c1fd8598d0cb42bc44a40dda55a0577e458a22d6843e536857b401"),
                Hex.decode("3045022100da95b59e4aac7451b5fd9efb1fa0df16ef44d8e82070daf28c90a16de50491920220637241a243cf6b7d84b3be0dfe4f08c485dfe193bd97b1290632d190f584311b01"),
                Hex.decode("3045022100916a09eef76b47165b99e77c9a55592bcdaecd22d4932df2366a92c9304cf80502207cd0b6d85a757952c0fd56d0ee7426a7a14ecddf068707ffab3ec06af87b4a1801"),
                Hex.decode("3045022100b4b990d471ce70de4be19aa241466b214a27b428333dcccbe885092eec4d71d302200c4e3e50aa4c2b1417ee3f174e7332fc045c304114445e05616e032aa812e9aa01"),
                Hex.decode("3045022100af9a30d639fc333387ebf77945b4397b85f93ff6a9d8f8aeee8cca22e3383c9e02207460a2adb4264a2ecffa2eb43e59ae78e33b6f9cee44989dbec56281e5e2c1c001"),
                Hex.decode("3045022100afc850cec037c459bbf2e8b559c863f3fa43f5ae01984d7516051a1995133917022063f04ab8d398825ad9e22a37628cf69af19dda26e6e793a3f2e797350eca6d4b01")
            );
            assertWitnessScript(witness, expectedRedeemScriptProgram, expectedSigs);

            Sha256Hash expectedTxHash = Sha256Hash.wrap("a4d76b6211b078cbc1d2079002437fcf018cc85cd40dd6195bb0f6b42930b96b");
            assertTxHash(expectedTxHash);
        }

        private void assertP2shScript(byte[] expectedP2SHScript) {
            // p2sh script should be op hash160, op pushbytes20 + script, op equal
            int p2shScriptIndex = 1;
            byte[] p2shScript = outputScript.getChunks().get(p2shScriptIndex).data;
            assertArrayEquals(expectedP2SHScript, p2shScript);
        }

        private void assertAddress(String expectedAddressString, Address address) {
            Address expectedAddress = Address.fromBase58(networkParameters, expectedAddressString);
            assertEquals(expectedAddress, address);
        }

        private void assertScriptSig(byte[] expectedHashedRedeemScriptProgram) {
            // the script sig should be op 0, op pushbytes32 + script
            Script segwitScriptSig = buildSegwitScriptSig(redeemScript);
            byte[] hashedRedeemScriptProgram = extractHashedRedeemScriptProgramFromSegwitScriptSig(segwitScriptSig);
            assertArrayEquals(expectedHashedRedeemScriptProgram, hashedRedeemScriptProgram);
        }

        private void assertRedeemScript(byte[] expectedRedeemScriptProgram) {
            Script expectedRedeemScript = new Script(expectedRedeemScriptProgram);
            assertEquals(expectedRedeemScript, redeemScript);
        }

        private void assertWitnessScript(TransactionWitness witness, byte[] expectedRedeemScriptProgram, List<byte[]> expectedSigs) {
            assertOpCheckMultisigBugPush(witness);
            assertRedeemScriptPush(witness, expectedRedeemScriptProgram);
            assertOpNotifPush(witness);
            assertSigsPushes(witness, expectedSigs);
        }

        private void assertOpCheckMultisigBugPush(TransactionWitness witness) {
            int opCheckMultiSigBugPushIndex = 0;
            byte[] opCheckMultiSigBugPush = witness.getPush(opCheckMultiSigBugPushIndex);
            assertArrayEquals(emptyByte, opCheckMultiSigBugPush);
        }

        private void assertRedeemScriptPush(TransactionWitness witness, byte[] expectedRedeemScriptProgram) {
            int redeemScriptPushIndex = witness.getPushCount() - 1;
            byte[] redeemScriptPush = witness.getPush(redeemScriptPushIndex);
            assertArrayEquals(expectedRedeemScriptProgram, redeemScriptPush);
        }

        private void assertOpNotifPush(TransactionWitness witness) {
            int opNotifPushIndex = witness.getPushCount() - 2;
            byte[] opNotifPush = witness.getPush(opNotifPushIndex);
            assertArrayEquals(emptyByte, opNotifPush);
        }

        private void assertSigsPushes(TransactionWitness witness, List<byte[]> expectedSigs) {
            int opNotifPushIndex = witness.getPushCount() - 2;

            List<byte[]> sigPushes = new ArrayList<>();
            for (int i = 1; i < opNotifPushIndex ; i++) {
                byte[] sigPush = witness.getPush(i);
                sigPushes.add(sigPush);
            }

            for (int i = 0; i < expectedSigs.size(); i++) {
                byte[] actualSig = sigPushes.get(i);
                byte[] expectedSig = expectedSigs.get(i);
                assertArrayEquals(expectedSig, actualSig);
            }
        }

        private void assertTxHash(Sha256Hash expectedTxHash) {
            assertEquals(expectedTxHash, tx.getHash());
        }
    }

    private static TransactionWitness updateWitnessWithEmergencySignatures(TransactionWitness witness, List<TransactionSignature> signatures) {
        List<byte[]> updatedPushes = new ArrayList<>();
        for (TransactionSignature transactionSignature : signatures) {
            updatedPushes.add(transactionSignature.encodeToBitcoin());
        }

        for (int i = signatures.size(); i < witness.getPushCount(); i++) {
            updatedPushes.add(witness.getPush(i));
        }

        return TransactionWitness.of(updatedPushes);
    }

    @Test
    void getErpRedeemScriptBuilder() {
        assertEquals(P2shErpRedeemScriptBuilder.class, federation.getErpRedeemScriptBuilder().getClass());
    }

    @Test
    void getErpPubKeys() {
        List<BtcECKey> expectedEmergencyKeys = FederationMainNetConstants.getInstance().getErpFedPubKeysList();
        assertEquals(expectedEmergencyKeys, federation.getErpPubKeys());
    }

    @Test
    void getThreshold() {
        int federationSize = federation.getSize();
        int expectedThreshold = federationSize / 2 + 1;

        assertEquals(expectedThreshold, federation.getNumberOfSignaturesRequired());
    }

    @Test
    void getEmergencyThreshold() {
        int emergencyKeysSize = federation.getErpPubKeys().size();
        int expectedEmergencyThreshold = emergencyKeysSize / 2 + 1;

        assertEquals(expectedEmergencyThreshold, federation.getNumberOfEmergencySignaturesRequired());
    }

    @Test
    void getActivationDelay() {
        long expectedActivationDelayValue = FederationMainNetConstants.getInstance().getErpFedActivationDelay();
        assertEquals(expectedActivationDelayValue, federation.getActivationDelay());
    }

    @Test
    void getDefaultRedeemScript() {
        Script expectedDefaultRedeemScript =
            ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());

        assertEquals(expectedDefaultRedeemScript, federation.getDefaultRedeemScript());
    }

    @Test
    void p2shP2wshErpFederationRedeemScript_hasExpectedRedeemScriptFormat() {
        Script redeemScript = federation.getRedeemScript();
        List<BtcECKey> defaultPublicKeys = federation.getBtcPublicKeys();
        List<BtcECKey> emergencyPublicKeys = federation.getErpPubKeys();
        long activationDelayValue = federation.getActivationDelay();

        /*
         * Expected structure:
         * OP_NOTIF
         *  OP_M
         *  PUBKEYS...N
         *  OP_N
         *  OP_CHECKMULTISIG
         * OP_ELSE
         *  OP_PUSHBYTES
         *  CSV_VALUE
         *  OP_CHECKSEQUENCEVERIFY
         *  OP_DROP
         *  OP_M
         *  PUBKEYS...N
         *  OP_PUSHDATA1 N
         *  OP_CHECKMULTISIG
         * OP_ENDIF
         */

        // Keys are sorted when added to the redeem script, so we need them sorted in order to validate
        List<BtcECKey> sortedDefaultPublicKeys = new ArrayList<>(defaultPublicKeys);
        sortedDefaultPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<BtcECKey> sortedEmergencyPublicKeys = new ArrayList<>(emergencyPublicKeys);
        sortedEmergencyPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(activationDelayValue);

        byte[] script = redeemScript.getProgram();
        Assertions.assertTrue(script.length > 0);

        int index = 0;

        // First byte should equal OP_NOTIF
        assertEquals(ScriptOpCodes.OP_NOTIF, script[index++]);

        // Next byte should equal M, from an M/N multisig
        int m = sortedDefaultPublicKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        // Assert public keys
        for (BtcECKey key: sortedDefaultPublicKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(pubkey.length, script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        int n = sortedDefaultPublicKeys.size();
        int bytesForN = 1;
        assertEquals(bytesForN, script[index++]);
        assertEquals(n, script[index++]);

        // Next byte should equal OP_CHECKMULTISIG
        assertEquals((byte) ScriptOpCodes.OP_CHECKMULTISIG, script[index++]);

        // Next byte should equal OP_ELSE
        assertEquals(ScriptOpCodes.OP_ELSE, script[index++]);

        // Next byte should equal csv value length
        assertEquals(serializedCsvValue.length, script[index++]);

        // Next bytes should equal the csv value in bytes
        for (byte value : serializedCsvValue) {
            assertEquals(value, script[index++]);
        }

        assertEquals((byte) ScriptOpCodes.OP_CHECKSEQUENCEVERIFY, script[index++]);
        assertEquals(ScriptOpCodes.OP_DROP, script[index++]);

        // Next byte should equal M, from an M/N multisig
        m = sortedEmergencyPublicKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        for (BtcECKey key: sortedEmergencyPublicKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(pubkey.length, script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        n = sortedEmergencyPublicKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_CHECKMULTISIG
        assertEquals((byte) ScriptOpCodes.OP_CHECKMULTISIG, script[index++]);

        assertEquals(ScriptOpCodes.OP_ENDIF, script[index]);
    }

    @Test
    void getP2SHScript_equalsScriptFromP2shP2wshOutputScript() {
        Script expectedP2SHScript = ScriptBuilder.createP2SHP2WSHOutputScript(federation.getRedeemScript());
        assertEquals(expectedP2SHScript, federation.getP2SHScript());
    }

    @Test
    void getP2SHScript_doesNotEqualScriptFromP2shOutputScript() {
        Script wrongP2SHScript = ScriptBuilder.createP2SHOutputScript(federation.getRedeemScript());
        assertNotEquals(wrongP2SHScript, federation.getP2SHScript());
    }

    @Test
    void getDefaultP2SHScript_equalsScriptFromP2shP2wshOutputScript() {
        Script expectedDefaultP2SHScript = ScriptBuilder.createP2SHP2WSHOutputScript(federation.getDefaultRedeemScript());
        assertEquals(expectedDefaultP2SHScript, federation.getDefaultP2SHScript());
    }

    @Test
    void getDefaultP2SHScript_doesNotEqualScriptFromP2shOutputScript() {
        Script wrongDefaultP2SHScript = ScriptBuilder.createP2SHOutputScript(federation.getDefaultRedeemScript());
        assertNotEquals(wrongDefaultP2SHScript, federation.getDefaultP2SHScript());
    }

    @Test
    void testEquals_same() {
        FederationArgs federationArgs = new FederationArgs(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams()
        );
        ErpFederation otherFederation = FederationFactory.buildP2shP2wshErpFederation(
            federationArgs,
            federation.getErpPubKeys(),
            federation.getActivationDelay()
        );

        assertEquals(federation, otherFederation);
    }

    @Test
    void createFederationArgs_fromFederationValues_createsExpectedFederationArgs() {
        // arrange
        List<FederationMember> federationMembers = federation.getMembers();
        Instant creationTime = federation.getCreationTime();
        long creationBlockNumber = federation.getCreationBlockNumber();
        NetworkParameters btcParams = federation.getBtcParams();

        // act
        FederationArgs federationArgsFromValues =
            new FederationArgs(federationMembers, creationTime, creationBlockNumber, btcParams);

        // assert
        assertEquals(federation.getArgs(), federationArgsFromValues);
    }
}
