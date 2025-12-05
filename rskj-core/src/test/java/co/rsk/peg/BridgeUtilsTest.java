/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.peg;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.wallet.CoinSelector;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.FlyoverRedeemScriptBuilderImpl;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import co.rsk.peg.federation.*;
import co.rsk.peg.flyover.FlyoverTxResponseCodes;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static co.rsk.peg.BridgeUtils.calculateSignedSegwitBtcTxVirtualSize;
import static co.rsk.peg.PegUtils.getFlyoverFederationOutputScript;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.generateSignerEncodedSignatures;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.generateTransactionInputsSigHashes;
import static co.rsk.peg.bitcoin.BitcoinUtils.*;
import static co.rsk.peg.federation.FederationTestUtils.REGTEST_FEDERATION_PRIVATE_KEYS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BridgeUtilsTest {
    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger AMOUNT = new BigInteger("1");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final byte[] MISSING_SIGNATURE = new byte[0];

    private Constants constants;
    private ActivationConfig activationConfig;
    private ActivationConfig.ForBlock activations;
    private BridgeConstants bridgeConstantsRegtest;
    private BridgeConstants bridgeConstantsMainnet;
    private NetworkParameters networkParameters;

    @BeforeEach
    void setupConfig() {
        constants = Constants.regtest();
        activationConfig = spy(ActivationConfigsForTest.all());
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstantsRegtest = new BridgeRegTestConstants();
        bridgeConstantsMainnet = BridgeMainNetConstants.getInstance();
        networkParameters = bridgeConstantsRegtest.getBtcParams();
    }

    @Test
    void getAddressFromEthTransaction() {
        org.ethereum.core.Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(constants.getChainId())
            .value(AMOUNT)
            .build();
        byte[] privKey = generatePrivKey();
        tx.sign(privKey);

        Address expectedAddress = BtcECKey.fromPrivate(privKey).toAddress(RegTestParams.get());
        Address result = BridgeUtils.recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());

        assertEquals(expectedAddress, result);
    }

    @Test
    void getAddressFromEthNotSignTransaction() {
        org.ethereum.core.Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(BigIntegers.asUnsignedByteArray(GAS_LIMIT))
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(constants.getChainId())
            .value(AMOUNT)
            .build();
        Assertions.assertThrows(Exception.class, () -> BridgeUtils.recoverBtcAddressFromEthTransaction(tx, RegTestParams.get()));
    }

    @Test
    void hasEnoughSignatures_two_signatures() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 1);
        Assertions.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    void hasEnoughSignatures_one_signature() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 1);
        Assertions.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    void hasEnoughSignatures_no_signatures() {
        BtcTransaction btcTx = createPegOutTx(Collections.emptyList(), 1);
        Assertions.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    void hasEnoughSignatures_several_inputs_all_signed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 3);
        Assertions.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    void hasEnoughSignatures_several_inputs_all_signed_non_standard_erp_fed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        ErpFederation nonStandardErpFederation = createNonStandardErpFederation();
        BtcTransaction btcTx = createPegOutTx(
            Arrays.asList(sign1, sign2),
            3,
            nonStandardErpFederation,
            false
        );

        Assertions.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    void hasEnoughSignatures_several_inputs_all_signed_fast_bridge() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTxForFlyover(
            Arrays.asList(sign1, sign2),
            3,
            null
        );

        Assertions.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    void hasEnoughSignatures_several_inputs_all_signed_non_standard_erp_fast_bridge() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        ErpFederation nonStandardErpFederation = createNonStandardErpFederation();
        BtcTransaction btcTx = createPegOutTxForFlyover(
            Arrays.asList(sign1, sign2),
            3,
            nonStandardErpFederation
        );

        Assertions.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    void hasEnoughSignatures_several_inputs_one_missing_signature() {
        // Create 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 3);
        Assertions.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    void countMissingSignatures_two_signatures() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 1);
        Assertions.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    void countMissingSignatures_one_signature() {
        // Add 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 1);
        Assertions.assertEquals(1, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    void countMissingSignatures_no_signatures() {
        // As no signature was added, missing signatures is 2
        BtcTransaction btcTx = createPegOutTx(Collections.emptyList(), 1);
        Assertions.assertEquals(2, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    void countMissingSignatures_several_inputs_all_signed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 3);
        Assertions.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    void countMissingSignatures_several_inputs_all_signed_non_standard_erp_fed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        ErpFederation nonStandardErpFederation = createNonStandardErpFederation();
        BtcTransaction btcTx = createPegOutTx(
            Arrays.asList(sign1, sign2),
            3,
            nonStandardErpFederation,
            false
        );

        Assertions.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    void countMissingSignatures_several_inputs_all_signed_fast_bridge() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTxForFlyover(
            Arrays.asList(sign1, sign2),
            3,
            null
        );

        Assertions.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    void countMissingSignatures_several_inputs_all_signed_non_standard_erp_fast_bridge() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        ErpFederation nonStandardErpFederation = createNonStandardErpFederation();
        BtcTransaction btcTx = createPegOutTxForFlyover(
            Arrays.asList(sign1, sign2),
            3,
            nonStandardErpFederation
        );

        Assertions.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    void countMissingSignatures_several_inputs_one_missing_signature() {
        // Create 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 3);
        Assertions.assertEquals(1, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    void isFreeBridgeTxTrue() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();
        isFreeBridgeTx(true, PrecompiledContracts.BRIDGE_ADDR, REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
    }

    @Test
    void isFreeBridgeTxOtherContract() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();
        isFreeBridgeTx(false, PrecompiledContracts.IDENTITY_ADDR, REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
    }

    @Test
    void isFreeBridgeTxFreeTxDisabled() {
        activationConfig = ActivationConfigsForTest.only(ConsensusRule.ARE_BRIDGE_TXS_PAID);
        isFreeBridgeTx(false, PrecompiledContracts.BRIDGE_ADDR, REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
    }

    @Test
    void isFreeBridgeTxNonFederatorKey() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();
        isFreeBridgeTx(false, PrecompiledContracts.BRIDGE_ADDR, new BtcECKey().getPrivKeyBytes());
    }

    @Test
    void getFederationNoSpendWallet() {
        test_getNoSpendWallet(false);
    }

    @Test
    void getFederationNoSpendWallet_flyoverCompatible() {
        test_getNoSpendWallet(true);
    }

    @Test
    void getFederationSpendWallet() throws UTXOProviderException {
        test_getSpendWallet(false);
    }

    @Test
    void getFederationSpendWallet_flyoverCompatible() throws UTXOProviderException {
        test_getSpendWallet(true);
    }

    @Test
    void testIsContractTx() {
        Assertions.assertFalse(
            BridgeUtils.isContractTx(
                Transaction.builder().build()
            )
        );
        Assertions.assertTrue(
            BridgeUtils.isContractTx(new org.ethereum.vm.program.InternalTransaction(
                Keccak256.ZERO_HASH.getBytes(),
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ))
        );
    }

    @Test
    void testIsContractTxInvalidTx() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ImmutableTransaction(null));
    }

    @Test
    void getCoinFromBigInteger_bigger_than_long_value() {
        Assertions.assertThrows(BridgeIllegalArgumentException.class, () -> BridgeUtils.getCoinFromBigInteger(new BigInteger("9223372036854775808")));
    }

    @Test
    void getCoinFromBigInteger_null_value() {
        Assertions.assertThrows(BridgeIllegalArgumentException.class, () -> BridgeUtils.getCoinFromBigInteger(null));
    }

    @Test
    void getCoinFromBigInteger() throws BridgeIllegalArgumentException {
        Assertions.assertEquals(Coin.COIN, BridgeUtils.getCoinFromBigInteger(BigInteger.valueOf(Coin.COIN.getValue())));
    }

    @Test
    void validateHeightAndConfirmations_invalid_height() {
        Assertions.assertThrows(Exception.class, () -> Assertions.assertFalse(BridgeUtils.validateHeightAndConfirmations(-1, 0, 0, null)));
    }

    @Test
    void validateHeightAndConfirmation_insufficient_confirmations() throws Exception {
        Assertions.assertFalse(BridgeUtils.validateHeightAndConfirmations(2, 5, 10, Sha256Hash.of(Hex.decode("ab"))));
    }

    @Test
    void validateHeightAndConfirmation_enough_confirmations() throws Exception {
        Assertions.assertTrue(BridgeUtils.validateHeightAndConfirmations(
            2,
            5,
            3,
            Sha256Hash.of(Hex.decode("ab")))
        );
    }

    @Test
    void calculateMerkleRoot_invalid_pmt() {
        Assertions.assertThrows(Exception.class, () -> BridgeUtils.calculateMerkleRoot(networkParameters, Hex.decode("ab"), null));
    }

    @Test
    void calculateMerkleRoot_hashes_not_in_pmt() {
        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash(2));

        BtcTransaction tx = new BtcTransaction(networkParameters);
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, bits, hashes, 1);

        Assertions.assertNull(BridgeUtils.calculateMerkleRoot(networkParameters, pmt.bitcoinSerialize(), tx.getHash()));
    }

    @Test
    void calculateMerkleRoot_hashes_in_pmt() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        byte[] bits = new byte[1];
        bits[0] = 0x5;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(Sha256Hash.ZERO_HASH);
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, bits, hashes, 2);
        Sha256Hash merkleRoot = BridgeUtils.calculateMerkleRoot(networkParameters, pmt.bitcoinSerialize(), tx.getHash());
        Assertions.assertNotNull(merkleRoot);
    }

    @Test
    void validateInputsCount_active_rskip() {
        byte[] decode = Hex.decode("00000000000100");
        Assertions.assertThrows(VerificationException.class, () -> BridgeUtils.validateInputsCount(decode, true));
    }

    @Test
    void validateInputsCount_inactive_rskip() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        byte[] btcTxSerialized = tx.bitcoinSerialize();
        Assertions.assertThrows(VerificationException.class, () -> BridgeUtils.validateInputsCount(btcTxSerialized, false));
    }

    @Test
    void isInputSignedByThisFederator_isSigned() {
        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
            Instant.now(),
            0,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(networkParameters);
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        txInput.setScriptSig(inputScript);

        tx.addInput(txInput);
        int inputIndex = 0;

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1Key.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1Key);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        txInput.setScriptSig(inputScript);

        // Act
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(tx, inputIndex, federator1Key, sighash);

        // Assert
        Assertions.assertTrue(isSigned);
    }

    @Test
    void isInputSignedByThisFederator_whenSigningWithCorrectFederator_forSegwitFed_shouldBeTrue() {
        // Arrange
        Federation federation = P2shP2wshErpFederationBuilder.builder().build();
        List<BtcECKey> federationSignersKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{
            "member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"
        }, true); // we need the priv keys

        BtcTransaction prevTx = new BtcTransaction(bridgeConstantsMainnet.getBtcParams());
        prevTx.setVersion(ReleaseTransactionBuilder.BTC_TX_VERSION_2);
        Coin prevValue = Coin.COIN;
        prevTx.addOutput(prevValue, federation.getAddress());

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.setVersion(ReleaseTransactionBuilder.BTC_TX_VERSION_2);
        tx.addInput(prevTx.getOutput(0));

        int inputIndex = 0;
        addSpendingFederationBaseScript(tx, inputIndex, federation.getRedeemScript(), federation.getFormatVersion());

        BtcECKey federatorSigningKey = federationSignersKeys.get(0);

        // generate signatures
        List<Sha256Hash> sigHashes = generateTransactionInputsSigHashes(tx);
        Sha256Hash sigHash = sigHashes.get(inputIndex);
        int sigInsertionIndex = getSigInsertionIndex(tx, inputIndex, sigHash, federatorSigningKey);
        List<byte[]> federatorSigs = generateSignerEncodedSignatures(federatorSigningKey, sigHashes);
        byte[] federatorSig = federatorSigs.get(inputIndex);
        TransactionSignature txSig = new TransactionSignature(BtcECKey.ECDSASignature.decodeFromDER(federatorSig), BtcTransaction.SigHash.ALL, false);

        // sign
        TransactionWitness inputWitness = tx.getWitness(inputIndex);
        TransactionWitness inputWitnessWithSignature = inputWitness.updateWitnessWithSignature(federation.getP2SHScript(), txSig.encodeToBitcoin(), sigInsertionIndex);
        tx.setWitness(inputIndex, inputWitnessWithSignature);

        // Act
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(tx, inputIndex, federatorSigningKey, sigHash);

        // assert
        assertTrue(isSigned);
    }

    @Test
    void isInputSignedByThisFederator_isSignedByAnotherFederator() {
        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
            Instant.now(),
            0,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(networkParameters);
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        txInput.setScriptSig(inputScript);

        tx.addInput(txInput);
        int inputIndex = 0;

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1Key.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1Key);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        txInput.setScriptSig(inputScript);

        // Act
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(tx, inputIndex, federator2Key, sighash);

        // Assert
        Assertions.assertFalse(isSigned);
    }

    @Test
    void isInputSignedByThisFederator_notSigned() {
        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
            Instant.now(),
            0,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(networkParameters);
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        txInput.setScriptSig(inputScript);

        tx.addInput(txInput);
        int inputIndex = 0;

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);

        // Act
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(tx, inputIndex, federator1Key, sighash);

        // Assert
        Assertions.assertFalse(isSigned);
    }

    @Test
    void serializeBtcAddressWithVersion_p2pkh_testnet_before_rskip284() {
        Address address = Address.fromBase58(networkParameters, "mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs");
        byte[] serializedVersion = Hex.decode("6f"); // Testnet pubkey hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(false, address, serializedVersion, serializedAddress);
    }

    @Test
    void serializeBtcAddressWithVersion_p2sh_testnet_before_rskip284() {
        Address address = Address.fromBase58(networkParameters, "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP");
        byte[] serializedVersion = Hex.decode("00c4"); // Testnet script hash, with leading zeroes
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(false, address, serializedVersion, serializedAddress);
    }

    @Test
    void serializeBtcAddressWithVersion_p2pkh_mainnet_before_rskip284() {
        Address address = Address.fromBase58(bridgeConstantsMainnet.getBtcParams(), "16zJJhTZWB1txoisGjEmhtHQam4sikpTd2");
        byte[] serializedVersion = Hex.decode("00"); // Mainnet pubkey hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(false, address, serializedVersion, serializedAddress);
    }

    @Test
    void serializeBtcAddressWithVersion_p2sh_mainnet_before_rskip284() {
        Address address = Address.fromBase58(bridgeConstantsMainnet.getBtcParams(), "37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn");
        byte[] serializedVersion = Hex.decode("05"); // Mainnet script hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(false, address, serializedVersion, serializedAddress);
    }

    @Test
    void serializeBtcAddressWithVersion_p2pkh_testnet_after_rskip284() {
        Address address = Address.fromBase58(networkParameters, "mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs");
        byte[] serializedVersion = Hex.decode("6f"); // Testnet pubkey hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(true, address, serializedVersion, serializedAddress);
    }

    @Test
    void serializeBtcAddressWithVersion_p2sh_testnet_after_rskip284() {
        Address address = Address.fromBase58(networkParameters, "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP");
        byte[] serializedVersion = Hex.decode("c4"); // Testnet script hash, no leading zeroes after HF activation
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(true, address, serializedVersion, serializedAddress);
    }

    @Test
    void serializeBtcAddressWithVersion_p2pkh_mainnet_after_rskip284() {
        Address address = Address.fromBase58(bridgeConstantsMainnet.getBtcParams(), "16zJJhTZWB1txoisGjEmhtHQam4sikpTd2");
        byte[] serializedVersion = Hex.decode("00"); // Mainnet pubkey hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(true, address, serializedVersion, serializedAddress);
    }

    @Test
    void serializeBtcAddressWithVersion_p2sh_mainnet_after_rskip284() {
        Address address = Address.fromBase58(bridgeConstantsMainnet.getBtcParams(), "37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn");
        byte[] serializedVersion = Hex.decode("05"); // Mainnet script hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(true, address, serializedVersion, serializedAddress);
    }

    @Test
    void deserializeBtcAddressWithVersion_before_rskip284_p2sh_testnet() {
        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // Should use the legacy method and fail for using testnet script hash
            test_deserializeBtcAddressWithVersion(
                false,
                NetworkParameters.ID_TESTNET,
                addressBytes,
                0,
                new byte[]{},
                ""
            );
        });
    }

    @Test
    void deserializeBtcAddressWithVersion_before_rskip284_p2pkh_testnet() throws BridgeIllegalArgumentException {
        int addressVersion = 111;
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs";

        // Should use the legacy method and deserialize correctly if using testnet p2pkh
        test_deserializeBtcAddressWithVersion(
            false,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test
    void deserializeBtcAddressWithVersion_null_bytes() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        Assertions.assertThrows(BridgeIllegalArgumentException.class, () -> {
            BridgeUtils.deserializeBtcAddressWithVersion(
                bridgeConstantsRegtest.getBtcParams(),
                activations,
                null
            );
        });
    }

    @Test
    void deserializeBtcAddressWithVersion_empty_bytes() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        Assertions.assertThrows(BridgeIllegalArgumentException.class, () -> {
            BridgeUtils.deserializeBtcAddressWithVersion(
                bridgeConstantsRegtest.getBtcParams(),
                activations,
                new byte[]{}
            );
        });
    }

    @Test
    void deserializeBtcAddressWithVersion_p2pkh_testnet() throws BridgeIllegalArgumentException {
        int addressVersion = 111;
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs";

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test
    void deserializeBtcAddressWithVersion_p2pkh_testnet_wrong_network() {
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Assertions.assertThrows(IllegalArgumentException.class, () -> test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_MAINNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        ));
    }

    @Test
    void deserializeBtcAddressWithVersion_p2sh_testnet() throws BridgeIllegalArgumentException {
        int addressVersion = 196;
        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP";

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test
    void deserializeBtcAddressWithVersion_p2sh_testnet_wrong_network() {
        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Assertions.assertThrows(IllegalArgumentException.class, () -> test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_MAINNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        ));
    }

    @Test
    void deserializeBtcAddressWithVersion_p2pkh_mainnet() throws BridgeIllegalArgumentException {
        int addressVersion = 0;
        String addressVersionHex = "00"; // Mainnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "16zJJhTZWB1txoisGjEmhtHQam4sikpTd2";

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_MAINNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test
    void deserializeBtcAddressWithVersion_p2pkh_mainnet_wrong_network() {
        String addressVersionHex = "00"; // Mainnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Assertions.assertThrows(IllegalArgumentException.class, () -> test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        ));
    }

    @Test
    void deserializeBtcAddressWithVersion_p2sh_mainnet() throws BridgeIllegalArgumentException {
        int addressVersion = 5;
        String addressVersionHex = "05"; // Mainnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn";

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_MAINNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test
    void deserializeBtcAddressWithVersion_p2sh_mainnet_wrong_network() throws BridgeIllegalArgumentException {
        String addressVersionHex = "05"; // Mainnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Assertions.assertThrows(IllegalArgumentException.class, () -> test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        ));
    }

    @Test
    void deserializeBtcAddressWithVersion_with_extra_bytes() {
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        String extraData = "0000aaaaeeee1111ffff";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex).concat(extraData));

        Assertions.assertThrows(BridgeIllegalArgumentException.class, () -> test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        ));
    }

    @Test
    void deserializeBtcAddressWithVersion_invalid_address_hash() {
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        // Should fail for having less than 21 bytes
        Assertions.assertThrows(BridgeIllegalArgumentException.class, () -> test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        ));
    }

    @Test
    void testCalculatePegoutTxSize_ZeroInput_ZeroOutput() {

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> BridgeUtils.calculatePegoutTxSize(activations, federation, 0, 0));
    }

    @Test
    void testCalculatePegoutTxSize_2Inputs_2Outputs() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP305)).thenReturn(true);
        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        int inputSize = 2;
        int outputSize = 2;
        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        assertEquals(2058, pegoutTxSize);

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 2076; // Data for 2 inputs, 2 outputs From https://www.blockchain.com/btc/tx/e92cab54ecf738a00083fd8990515247aa3404df4f76ec358d9fe87d95102ae4
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        assertTrue(difference < tolerance && difference > -tolerance);

        ErpFederation p2shP2wshFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(keys)
            .build();

        int pegoutTxSizeSegwit = BridgeUtils.calculatePegoutTxSize(activations, p2shP2wshFederation, inputSize, outputSize);

        assertEquals(694, pegoutTxSizeSegwit);

        double segWitSavingPercentage = (100 - ((double) pegoutTxSizeSegwit / pegoutTxSize * 100));

        assertTrue(segWitSavingPercentage > 59);

    }

    @Test
    void testCalculatePegoutTxSize_9Inputs_2Outputs() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP305)).thenReturn(true);
        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        int inputSize = 9;
        int outputSize = 2;
        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        assertEquals(9002, pegoutTxSize);

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 9069; // Data for 9 inputs, 2 outputs From https://www.blockchain.com/btc/tx/15adf52f7b4b7a7e563fca92aec7bbe8149b87fac6941285a181e6fcd799a1cd
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        assertTrue(difference < tolerance && difference > -tolerance);

        ErpFederation p2shP2wshFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(keys)
            .build();

        int pegoutTxSizeSegwit = BridgeUtils.calculatePegoutTxSize(activations, p2shP2wshFederation, inputSize, outputSize);

        assertEquals(2866, pegoutTxSizeSegwit);

        double segWitSavingPercentage = (100 - ((double) pegoutTxSizeSegwit / pegoutTxSize * 100));

        assertTrue(segWitSavingPercentage > 59);

    }

    @Test
    void testCalculatePegoutTxSize_10Inputs_20Outputs() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP305)).thenReturn(true);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        // Create a pegout tx with 10 inputs and 20 outputs
        int inputSize = 10;
        int outputSize = 20;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, federation, keys);

        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        assertEquals(10570, pegoutTxSize);

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        assertTrue(difference < tolerance && difference > -tolerance);

        ErpFederation p2shP2wshFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(keys)
            .build();

        int pegoutTxSizeSegwit = BridgeUtils.calculatePegoutTxSize(activations, p2shP2wshFederation, inputSize, outputSize);

        assertEquals(3752, pegoutTxSizeSegwit);

        double segWitSavingPercentage = (100 - ((double) pegoutTxSizeSegwit / pegoutTxSize * 100));

        assertTrue(segWitSavingPercentage >= 59);

    }

    @Test
    void testCalculatePegoutTxSize_50Inputs_200Outputs() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP305)).thenReturn(true);
        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        // Create a pegout tx with 50 inputs and 200 outputs
        int inputSize = 50;
        int outputSize = 200;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, federation, keys);

        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        assertEquals(56010, pegoutTxSize);

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        assertTrue(difference < tolerance && difference > -tolerance);

        ErpFederation p2shP2wshFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(keys)
            .build();

        int pegoutTxSizeSegwit = BridgeUtils.calculatePegoutTxSize(activations, p2shP2wshFederation, inputSize, outputSize);

        assertEquals(21922, pegoutTxSizeSegwit);

        double segWitSavingPercentage = (100 - ((double) pegoutTxSizeSegwit / pegoutTxSize * 100));

        assertTrue(segWitSavingPercentage >= 59);

    }

    @Test
    void testCalculatePegoutTxSize_50Inputs_200Outputs_nonStandardErpFederation() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP305)).thenReturn(true);
        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        defaultFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04")),
            BtcECKey.fromPrivate(Hex.decode("fa05"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        FederationArgs federationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters
        );
        ErpFederation nonStandardErpFederation =
            FederationFactory.buildNonStandardErpFederation(federationArgs, erpFederationPublicKeys, 500L, activations);

        // Create a pegout tx with 50 inputs and 200 outputs
        int inputSize = 50;
        int outputSize = 200;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, nonStandardErpFederation, defaultFederationKeys);

        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(activations, nonStandardErpFederation, inputSize, outputSize);

        assertEquals(26510, pegoutTxSize);

        // The difference between the calculated size and a real tx size should be smaller than 3% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .03;

        assertTrue(difference < tolerance && difference > -tolerance);

        ErpFederation p2shP2wshFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(defaultFederationKeys)
            .build();

        int pegoutTxSizeSegwit = BridgeUtils.calculatePegoutTxSize(activations, p2shP2wshFederation, inputSize, outputSize);

        assertEquals(13172, pegoutTxSizeSegwit);

        double segWitSavingPercentage = (100 - ((double) pegoutTxSizeSegwit / pegoutTxSize * 100));

        assertTrue(segWitSavingPercentage >= 49);
    }

    @Test
    void testCalculatePegoutTxSize_100Inputs_50Outputs_nonStandardErpFederation() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP305)).thenReturn(true);
        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        defaultFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04")),
            BtcECKey.fromPrivate(Hex.decode("fa05"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        FederationArgs federationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters
        );
        ErpFederation nonStandardErpFederation =
            FederationFactory.buildNonStandardErpFederation(federationArgs, erpFederationPublicKeys, 500L, activations);

        // Create a pegout tx with 100 inputs and 50 outputs
        int inputSize = 100;
        int outputSize = 50;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, nonStandardErpFederation, defaultFederationKeys);

        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(activations, nonStandardErpFederation, inputSize, outputSize);

        assertEquals(41810, pegoutTxSize);

        // The difference between the calculated size and a real tx size should be smaller than 3% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .03;

        assertTrue(difference < tolerance && difference > -tolerance);

        ErpFederation p2shP2wshFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(defaultFederationKeys)
            .build();

        int pegoutTxSizeSegwit = BridgeUtils.calculatePegoutTxSize(activations, p2shP2wshFederation, inputSize, outputSize);

        assertEquals(15135, pegoutTxSizeSegwit);

        double segWitSavingPercentage = (100 - ((double) pegoutTxSizeSegwit / pegoutTxSize * 100));

        assertTrue(segWitSavingPercentage >= 49);

    }

    @Test
    void getRegularPegoutTxSize_has_proper_calculations() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        BtcECKey key1 = new BtcECKey();
        BtcECKey key2 = new BtcECKey();
        BtcECKey key3 = new BtcECKey();
        List<BtcECKey> keys = Arrays.asList(key1, key2, key3);
        FederationArgs federationArgs = new FederationArgs(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters
        );
        Federation fed = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        // Create a pegout tx with two inputs and two outputs
        int inputs = 2;
        BtcTransaction pegoutTx = createPegOutTx(Collections.emptyList(), inputs, fed, false);

        for (int inputIndex = 0; inputIndex < inputs; inputIndex++) {
            Script inputScript = pegoutTx.getInput(inputIndex).getScriptSig();

            Sha256Hash sighash = pegoutTx.hashForSignature(inputIndex, fed.getRedeemScript(), BtcTransaction.SigHash.ALL, false);

            for (int keyIndex = 0; keyIndex < keys.size() - 1; keyIndex++) {
                BtcECKey key = keys.get(keyIndex);
                BtcECKey.ECDSASignature sig = key.sign(sighash);
                TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
                byte[] txSigEncoded = txSig.encodeToBitcoin();

                int sigIndex = inputScript.getSigInsertionIndex(sighash, key);
                inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
                pegoutTx.getInput(inputIndex).setScriptSig(inputScript);
            }
        }

        int pegoutTxSize = BridgeUtils.getRegularPegoutTxSize(activations, fed);

        // The difference between the calculated size and a real tx size should be smaller than 10% in any direction
        int difference = pegoutTx.bitcoinSerialize().length - pegoutTxSize;
        double tolerance = pegoutTxSize * .1;
        assertTrue(difference < tolerance && difference > -tolerance);
    }

    private void test_getSpendWallet(boolean isFlyoverCompatible) throws UTXOProviderException {
        List<FederationMember> federationMembers =
            FederationTestUtils.getFederationMembersWithBtcKeys(
                Arrays.asList(
                    BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                    BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
                )
            );
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            Instant.ofEpochMilli(5005L),
            0L,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(networkParameters);

        List<UTXO> mockedUtxos = new ArrayList<>();
        mockedUtxos.add(mock(UTXO.class));
        mockedUtxos.add(mock(UTXO.class));
        mockedUtxos.add(mock(UTXO.class));

        Wallet wallet = BridgeUtils.getFederationSpendWallet(mockedBtcContext, federation, mockedUtxos, isFlyoverCompatible, null);

        if (isFlyoverCompatible) {
            Assertions.assertEquals(FlyoverCompatibleBtcWalletWithStorage.class, wallet.getClass());
        } else {
            Assertions.assertEquals(BridgeBtcWallet.class, wallet.getClass());
        }

        assertIsWatching(federation.getAddress(), wallet, networkParameters);
        CoinSelector selector = wallet.getCoinSelector();
        Assertions.assertEquals(RskAllowUnconfirmedCoinSelector.class, selector.getClass());
        UTXOProvider utxoProvider = wallet.getUTXOProvider();
        Assertions.assertEquals(RskUTXOProvider.class, utxoProvider.getClass());
        Assertions.assertEquals(mockedUtxos, utxoProvider.getOpenTransactionOutputs(Collections.emptyList()));
    }

    private void test_getNoSpendWallet(boolean isFlyoverCompatible) {
        List<FederationMember> federationMembers =
            FederationTestUtils.getFederationMembersWithBtcKeys(
                Arrays.asList(
                    BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                    BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
                )
            );
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            Instant.ofEpochMilli(5005L),
            0L,
            networkParameters
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs);
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(networkParameters);

        Wallet wallet = BridgeUtils.getFederationNoSpendWallet(mockedBtcContext, federation, isFlyoverCompatible, null);

        if (isFlyoverCompatible) {
            Assertions.assertEquals(FlyoverCompatibleBtcWalletWithStorage.class, wallet.getClass());
        } else {
            Assertions.assertEquals(BridgeBtcWallet.class, wallet.getClass());
        }

        assertIsWatching(federation.getAddress(), wallet, networkParameters);
    }

    private void getAmountSentToAddresses_ok_by_network(BridgeConstants bridgeConstants) {
        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Address activeFederationAddress = activeFederation.getAddress();

        Federation retiringFederation = PegTestUtils.createFederation(bridgeConstants, "fa01", "fa02");
        Address retiringFederationAddress = retiringFederation.getAddress();

        Coin valueToTransfer = Coin.COIN;
        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToTransfer, activeFederationAddress);
        btcTx.addOutput(valueToTransfer, retiringFederationAddress);

        Coin totalAmountExpected = valueToTransfer.multiply(2);

        Assertions.assertEquals(
            totalAmountExpected,
            BridgeUtils.getAmountSentToAddresses(
                activations,
                bridgeConstants.getBtcParams(),
                new Context(bridgeConstants.getBtcParams()),
                btcTx,
                Arrays.asList(
                    activeFederationAddress,
                    retiringFederationAddress
                )
            )
        );

        btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToTransfer, activeFederationAddress);
        totalAmountExpected = Coin.COIN;
        Assertions.assertEquals(
            totalAmountExpected,
            BridgeUtils.getAmountSentToAddresses(
                activations,
                bridgeConstants.getBtcParams(),
                new Context(bridgeConstants.getBtcParams()),
                btcTx,
                Arrays.asList(
                    activeFederationAddress,
                    retiringFederationAddress
                )
            )
        );

        btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToTransfer, activeFederationAddress);
        totalAmountExpected = Coin.COIN;
        Assertions.assertEquals(
            totalAmountExpected,
            BridgeUtils.getAmountSentToAddresses(
                activations,
                bridgeConstants.getBtcParams(),
                new Context(bridgeConstants.getBtcParams()),
                btcTx,
                Arrays.asList(activeFederationAddress)
            )
        );

        btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToTransfer, retiringFederationAddress);
        totalAmountExpected = Coin.COIN;
        Assertions.assertEquals(
            totalAmountExpected,
            BridgeUtils.getAmountSentToAddresses(
                activations,
                bridgeConstants.getBtcParams(),
                new Context(bridgeConstants.getBtcParams()),
                btcTx,
                Arrays.asList(retiringFederationAddress)
            )
        );
    }

    @Test
    void getAmountSentToAddresses_ok() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        getAmountSentToAddresses_ok_by_network(bridgeConstantsMainnet);
        getAmountSentToAddresses_ok_by_network(bridgeConstantsRegtest);
    }

    private void getAmountSentToAddresses_no_output_for_address_by_network(BridgeConstants bridgeConstants) {
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstantsMainnet.getFederationConstants());
        Address receiver = genesisFederation.getAddress();
        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());

        Assertions.assertEquals(
            Coin.ZERO,
            BridgeUtils.getAmountSentToAddresses(
                activations,
                bridgeConstants.getBtcParams(),
                new Context(bridgeConstants.getBtcParams()),
                btcTx,
                Arrays.asList(receiver)
            )
        );
    }

    @Test
    void getAmountSentToAddresses_no_output_for_address() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        getAmountSentToAddresses_no_output_for_address_by_network(bridgeConstantsMainnet);
        getAmountSentToAddresses_no_output_for_address_by_network(bridgeConstantsRegtest);
    }

    private void getAmountSentToAddresses_output_value_is_0_by_network(BridgeConstants bridgeConstants) {
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstantsMainnet.getFederationConstants());
        Address receiver = genesisFederation.getAddress();

        Coin valueToTransfer = Coin.ZERO;

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToTransfer, receiver);

        Assertions.assertEquals(
            Coin.ZERO,
            BridgeUtils.getAmountSentToAddresses(
                activations,
                bridgeConstants.getBtcParams(),
                new Context(bridgeConstants.getBtcParams()),
                btcTx,
                Arrays.asList(receiver)
            )
        );
    }

    @Test
    void getAmountSentToAddresses_output_value_is_0() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        getAmountSentToAddresses_output_value_is_0_by_network(bridgeConstantsMainnet);
        getAmountSentToAddresses_output_value_is_0_by_network(bridgeConstantsRegtest);
    }

    private void test_serializeBtcAddressWithVersion(boolean isRskip284Active, Address address, byte[] serializedVersion, byte[] serializedAddress) {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);

        byte[] addressWithVersionBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, address);
        int expectedLength = serializedVersion.length + serializedAddress.length;
        Assertions.assertEquals(expectedLength, addressWithVersionBytes.length);

        byte[] versionBytes = new byte[serializedVersion.length];
        System.arraycopy(addressWithVersionBytes, 0, versionBytes, 0, serializedVersion.length);

        byte[] addressBytes = new byte[serializedAddress.length];
        System.arraycopy(addressWithVersionBytes, serializedVersion.length, addressBytes, 0, serializedAddress.length);

        Assertions.assertArrayEquals(serializedVersion, versionBytes);
        Assertions.assertArrayEquals(serializedAddress, addressBytes);
    }

    private void test_deserializeBtcAddressWithVersion(boolean isRskip284Active, String networkId, byte[] serializedAddress,
        int expectedVersion, byte[] expectedHash, String expectedAddress) throws BridgeIllegalArgumentException {

        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);

        BridgeConstants bridgeConstants = networkId.equals(NetworkParameters.ID_MAINNET) ?
            BridgeMainNetConstants.getInstance() :
            new BridgeRegTestConstants();

        Address address = BridgeUtils.deserializeBtcAddressWithVersion(
            bridgeConstants.getBtcParams(),
            activations,
            serializedAddress
        );

        Assertions.assertEquals(expectedVersion, address.getVersion());
        Assertions.assertArrayEquals(expectedHash, address.getHash160());
        Assertions.assertEquals(expectedAddress, address.toBase58());
    }

    private void assertIsWatching(Address address, Wallet wallet, NetworkParameters parameters) {
        List<Script> watchedScripts = wallet.getWatchedScripts();
        Assertions.assertEquals(1, watchedScripts.size());
        Script watchedScript = watchedScripts.get(0);
        Assertions.assertTrue(watchedScript.isPayToScriptHash());
        Assertions.assertEquals(address.toString(), watchedScript.getToAddress(parameters).toString());
    }

    private void isFreeBridgeTx(boolean expected, RskAddress destinationAddress, byte[] privKeyBytes) {
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
            new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
            constants.getBridgeConstants(),
            activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactory, signatureCache);
        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
            0,
            1,
            1,
            destinationAddress,
            0,
            Bridge.UPDATE_COLLECTIONS, constants.getChainId());
        rskTx.sign(privKeyBytes);

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(trieStore, new Trie())));
        Block rskExecutionBlock = new BlockGenerator().createChildBlock(getGenesisInstance(trieStore));
        bridge.init(rskTx, rskExecutionBlock, repository.startTracking(), null, null, null);
        Assertions.assertEquals(expected, BridgeUtils.isFreeBridgeTx(rskTx, constants, activationConfig.forBlock(rskExecutionBlock.getNumber()), signatureCache));
    }

    private Genesis getGenesisInstance(TrieStore trieStore) {
        return new TestGenesisLoader(trieStore, "frontier.json", constants.getInitialNonce(), false, true, true).load();
    }

    private ErpFederation createNonStandardErpFederation() {
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstantsRegtest.getFederationConstants());
        FederationArgs genesisFederationArgs = genesisFederation.getArgs();
        List<BtcECKey> erpPubKeys = bridgeConstantsRegtest.getFederationConstants().getErpFedPubKeysList();
        long activationDelay = bridgeConstantsRegtest.getFederationConstants().getErpFedActivationDelay();

        return FederationFactory.buildNonStandardErpFederation(genesisFederationArgs, erpPubKeys, activationDelay, activations);
    }

    private BtcTransaction createPegOutTx(
        List<byte[]> signatures,
        int inputsToAdd,
        Federation federation,
        boolean isFlyover
    ) {
        // Setup
        Address address;
        byte[] program;

        if (federation == null) {
            federation = FederationTestUtils.getGenesisFederation(bridgeConstantsRegtest.getFederationConstants());
        }

        if (isFlyover) {
            Keccak256 flyoverDerivationHash = RskTestUtils.createHash(1);

            Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                flyoverDerivationHash,
                federation.getRedeemScript()
            );

            Script flyoverP2SH = getFlyoverFederationOutputScript(flyoverRedeemScript, federation.getFormatVersion());
            address = Address.fromP2SHHash(networkParameters, flyoverP2SH.getPubKeyHash());
            program = flyoverRedeemScript.getProgram();

        } else {
            address = federation.getAddress();
            program = federation.getRedeemScript().getProgram();
        }

        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(networkParameters);
        TransactionOutput prevOut = new TransactionOutput(networkParameters, prevTx, Coin.FIFTY_COINS, address);
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        // Add inputs
        for (int i = 0; i < inputsToAdd; i++) {
            btcTx.addInput(prevOut);
        }

        Script scriptSig;

        if (signatures.isEmpty()) {
            scriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        } else {
            scriptSig = ScriptBuilder.createMultiSigInputScriptBytes(signatures, program);
        }

        // Sign inputs
        for (int i = 0; i < inputsToAdd; i++) {
            btcTx.getInput(i).setScriptSig(scriptSig);
        }

        TransactionOutput output = new TransactionOutput(
            networkParameters,
            btcTx,
            Coin.COIN,
            new BtcECKey().toAddress(networkParameters)
        );
        btcTx.addOutput(output);

        TransactionOutput changeOutput = new TransactionOutput(
            networkParameters,
            btcTx,
            Coin.COIN,
            federation.getAddress()
        );
        btcTx.addOutput(changeOutput);

        return btcTx;
    }

    private BtcTransaction createPegOutTx(int inputSize, int outputSize, Federation federation, List<BtcECKey> keys) {
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        TransactionInput transInput = new TransactionInput(
            networkParameters,
            btcTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );

        // Add inputs
        for (int i = 0; i < inputSize; i++) {
            btcTx.addInput(transInput);
            // sign input
            signWithNecessaryKeys(federation, keys, transInput, btcTx);
        }

        // Add outputs
        for (int i = 0; i < outputSize; i++) {
            btcTx.addOutput(Coin.COIN, randomAddress);
        }

        return btcTx;
    }

    private BtcTransaction createPegOutTx(List<byte[]> signatures, int inputsToAdd) {
        return createPegOutTx(signatures, inputsToAdd, null, false);
    }

    private BtcTransaction createPegOutTxForFlyover(List<byte[]> signatures, int inputsToAdd, Federation federation) {
        return createPegOutTx(signatures, inputsToAdd, federation, true);
    }

    private byte[] generatePrivKey() {
        SecureRandom random = new SecureRandom();
        byte[] privKey = new byte[32];
        random.nextBytes(privKey);
        return privKey;
    }

    private void signWithNecessaryKeys(
        Federation federation,
        List<BtcECKey> privateKeys,
        TransactionInput txIn,
        BtcTransaction tx) {

        signWithNecessaryKeys(
            federation,
            federation.getRedeemScript(),
            privateKeys,
            txIn,
            tx
        );
    }

    private void signWithNecessaryKeys(
        Federation federation,
        Script federationRedeemScript,
        List<BtcECKey> privateKeys,
        TransactionInput txIn,
        BtcTransaction tx) {

        signWithNKeys(
            federation,
            federationRedeemScript,
            privateKeys,
            txIn,
            tx,
            federation.getNumberOfSignaturesRequired()
        );
    }

    private void signWithNKeys(
        Federation federation,
        Script federationRedeemScript,
        List<BtcECKey> privateKeys,
        TransactionInput txIn,
        BtcTransaction tx,
        int numberOfSignatures) {

        Script scriptPubKey = federation.getP2SHScript();
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), federationRedeemScript);
        Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);

        txIn.setScriptSig(inputScript);

        Sha256Hash sighash = tx.hashForSignature(
            0,
            federationRedeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        for (int i = 0; i < numberOfSignatures; i++) {
            inputScript = signWithOneKey(federation, privateKeys, inputScript, sighash, i);
        }
        txIn.setScriptSig(inputScript);
    }

    private Script signWithOneKey(
        Federation federation,
        List<BtcECKey> privateKeys,
        Script inputScript,
        Sha256Hash sighash,
        int federatorIndex) {

        BtcECKey federatorPrivKey = privateKeys.get(federatorIndex);
        BtcECKey federatorPublicKey = federation.getBtcPublicKeys().get(federatorIndex);

        BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighash);
        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);

        return inputScript;
    }

    @Test
    void testValidateFlyoverPeginValue_sent_zero_amount_before_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        Address btcAddressReceivingFunds = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Context btcContext = new Context(networkParameters);

        BtcTransaction btcTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        btcTx.addOutput(Coin.ZERO, btcAddressReceivingFunds);
        /* Send funds also to random addresses in order to assure the method distinguishes that those funds
        were not sent to the given address(normally the federation address */
        btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(networkParameters));
        btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(networkParameters));

        Assertions.assertEquals(
            FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR,
            BridgeUtils.validateFlyoverPeginValue(
                activations,
                bridgeConstantsRegtest,
                btcContext,
                btcTx,
                Collections.singletonList(btcAddressReceivingFunds)
            )
        );
    }

    @Test
    void testValidateFlyoverPeginValue_sent_one_utxo_with_amount_below_minimum_before_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        Address addressReceivingFundsBelowMinimum = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address addressReceivingFundsAboveMinimum = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        Context btcContext = new Context(bridgeConstantsRegtest.getBtcParams());

        Coin valueBelowMinimum = bridgeConstantsRegtest.getMinimumPeginTxValue(activations).minus(Coin.SATOSHI);

        BtcTransaction btcTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        btcTx.addOutput(valueBelowMinimum, addressReceivingFundsBelowMinimum);
        btcTx.addOutput(Coin.COIN, addressReceivingFundsAboveMinimum);

        Assertions.assertEquals(
            FlyoverTxResponseCodes.VALID_TX,
            BridgeUtils.validateFlyoverPeginValue(
                activations,
                bridgeConstantsRegtest,
                btcContext,
                btcTx,
                Arrays.asList(addressReceivingFundsBelowMinimum, addressReceivingFundsAboveMinimum)
            )
        );
    }

    @Test
    void testValidateFlyoverPeginValue_sent_one_utxo_with_amount_below_minimum_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        Context btcContext = new Context(networkParameters);
        Address btcAddressReceivingFunds = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        Coin valueBelowMinimum = bridgeConstantsRegtest.getMinimumPeginTxValue(activations).minus(Coin.SATOSHI);
        btcTx.addOutput(valueBelowMinimum, btcAddressReceivingFunds);
        btcTx.addOutput(Coin.COIN, btcAddressReceivingFunds);

        Assertions.assertEquals(
            FlyoverTxResponseCodes.UNPROCESSABLE_TX_UTXO_AMOUNT_SENT_BELOW_MINIMUM_ERROR,
            BridgeUtils.validateFlyoverPeginValue(
                activations,
                bridgeConstantsRegtest,
                btcContext,
                btcTx,
                Arrays.asList(btcAddressReceivingFunds)
            )
        );
    }

    @Test
    void testValidateFlyoverPeginValue_funds_sent_equal_to_minimum_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        Context btcContext = new Context(bridgeConstantsRegtest.getBtcParams());
        Address btcAddressReceivingFundsEqualToMin = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address secondBtcAddressReceivingFundsEqualToMin = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        Coin minimumPegInTxValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, btcAddressReceivingFundsEqualToMin);
        btcTx.addOutput(minimumPegInTxValue, secondBtcAddressReceivingFundsEqualToMin);

        Assertions.assertEquals(
            FlyoverTxResponseCodes.VALID_TX,
            BridgeUtils.validateFlyoverPeginValue(
                activations,
                bridgeConstantsRegtest,
                btcContext,
                btcTx,
                Arrays.asList(btcAddressReceivingFundsEqualToMin, secondBtcAddressReceivingFundsEqualToMin)
            )
        );
    }

    @Test
    void testValidateFlyoverPeginValue_funds_sent_above_minimum_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        Address btcAddressReceivingFundsEqualToMin = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddressReceivingFundsAboveMin = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Context btcContext = new Context(bridgeConstantsRegtest.getBtcParams());

        Coin minimumPegInTxValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);
        Coin aboveMinimumPegInTxValue = minimumPegInTxValue.plus(Coin.SATOSHI);

        BtcTransaction btcTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, btcAddressReceivingFundsEqualToMin);
        btcTx.addOutput(aboveMinimumPegInTxValue, btcAddressReceivingFundsAboveMin);

        Assertions.assertEquals(
            FlyoverTxResponseCodes.VALID_TX,
            BridgeUtils.validateFlyoverPeginValue(
                activations,
                bridgeConstantsRegtest,
                btcContext,
                btcTx,
                Arrays.asList(btcAddressReceivingFundsEqualToMin, btcAddressReceivingFundsAboveMin)
            )
        );
    }

    @Test
    void testGetUTXOsSentToAddresses_multiple_utxos_sent_to_random_address_and_one_utxo_sent_to_bech32_address_before_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        Context btcContext = new Context(bridgeConstantsRegtest.getBtcParams());
        Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        TransactionOutput bech32Output = PegTestUtils.createBech32Output(networkParameters, Coin.COIN);
        BtcTransaction btcTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        btcTx.addOutput(bech32Output);
        btcTx.addOutput(Coin.COIN, btcAddress);
        btcTx.addOutput(Coin.ZERO, btcAddress);
        btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(networkParameters));

        List<Address> addresses = Collections.singletonList(btcAddress);
        Assertions.assertThrows(ScriptException.class, () -> BridgeUtils.getUTXOsSentToAddresses(
            activations,
            networkParameters,
            btcContext,
            btcTx,
            addresses
        ));
    }

    @Test
    void testGetUTXOsSentToAddresses_multiple_utxo_sent_to_multiple_addresses_before_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        Context btcContext = new Context(bridgeConstantsRegtest.getBtcParams());
        Address btcAddress1 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress2 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress3 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress4 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction btcTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        btcTx.addOutput(Coin.COIN, btcAddress1);
        btcTx.addOutput(Coin.ZERO, btcAddress1);
        btcTx.addOutput(Coin.COIN, btcAddress2);
        btcTx.addOutput(Coin.COIN, btcAddress2);
        btcTx.addOutput(Coin.COIN, btcAddress3);
        btcTx.addOutput(Coin.COIN, btcAddress4);

        List<UTXO> expectedResult = new ArrayList<>();
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 0, Coin.COIN));
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 1, Coin.ZERO));

        List<UTXO> foundUTXOs = BridgeUtils.getUTXOsSentToAddresses(
            activations,
            networkParameters,
            btcContext,
            btcTx,
            /* Only the first address in the list is used to pick the utxos sent.
            This is due the legacy logic before RSKIP293 */
            Arrays.asList(btcAddress1, btcAddress2, btcAddress3)
        );

        Assertions.assertArrayEquals(expectedResult.toArray(), foundUTXOs.toArray());

        Coin amount = foundUTXOs.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Coin expectedAmount = expectedResult.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Assertions.assertEquals(amount, expectedAmount);
    }

    @Test
    void testGetUTXOsSentToAddresses_no_utxo_sent_to_given_address_before_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        Context btcContext = new Context(bridgeConstantsRegtest.getBtcParams());
        Address btcAddress1 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress3 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress4 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction btcTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        btcTx.addOutput(Coin.COIN, btcAddress1);
        btcTx.addOutput(Coin.ZERO, btcAddress1);
        btcTx.addOutput(Coin.COIN, btcAddress3);
        btcTx.addOutput(Coin.COIN, btcAddress4);

        List<UTXO> foundUTXOs = BridgeUtils.getUTXOsSentToAddresses(
            activations,
            networkParameters,
            btcContext,
            btcTx,
            /* Even we are passing three address, only the first one in the list will be use to pick the utxos sent to
            it. This is due the legacy logic before RSKIP293 */
            Arrays.asList(PegTestUtils.createRandomP2PKHBtcAddress(networkParameters), btcAddress1, btcAddress3)
        );

        Assertions.assertTrue(foundUTXOs.isEmpty());
    }

    @Test
    void testGetUTXOsSentToAddresses_multiple_utxos_sent_to_random_address_and_one_utxo_sent_to_bech32_address_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        Context btcContext = new Context(networkParameters);
        Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        TransactionOutput bech32Output = PegTestUtils.createBech32Output(networkParameters, Coin.COIN);
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addOutput(bech32Output);
        btcTx.addOutput(Coin.COIN, btcAddress);
        btcTx.addOutput(Coin.ZERO, btcAddress);
        btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(networkParameters));

        List<UTXO> expectedResult = new ArrayList<>();
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 1, Coin.COIN));
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 2, Coin.ZERO));

        List<UTXO> foundUTXOs = BridgeUtils.getUTXOsSentToAddresses(
            activations,
            networkParameters,
            btcContext,
            btcTx,
            Collections.singletonList(btcAddress)
        );

        Assertions.assertArrayEquals(expectedResult.toArray(), foundUTXOs.toArray());

        Coin amount = foundUTXOs.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Coin expectedAmount = expectedResult.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Assertions.assertEquals(amount, expectedAmount);
    }

    @Test
    void testGetUTXOsSentToAddresses_multiple_utxo_sent_to_multiple_addresses_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        Context btcContext = new Context(networkParameters);
        Address btcAddress1 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress2 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress3 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress4 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addOutput(Coin.COIN, btcAddress1);
        btcTx.addOutput(Coin.ZERO, btcAddress1);
        btcTx.addOutput(Coin.COIN, btcAddress2);
        btcTx.addOutput(Coin.COIN, btcAddress2);
        btcTx.addOutput(Coin.COIN, btcAddress3);
        btcTx.addOutput(Coin.COIN, btcAddress4);

        List<UTXO> expectedResult = new ArrayList<>();
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 0, Coin.COIN));
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 1, Coin.ZERO));
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 2, Coin.COIN));
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 3, Coin.COIN));
        expectedResult.add(PegTestUtils.createUTXO(btcTx.getHash(), 4, Coin.COIN));

        List<UTXO> foundUTXOs = BridgeUtils.getUTXOsSentToAddresses(
            activations,
            networkParameters,
            btcContext,
            btcTx,
            Arrays.asList(
                btcAddress1,
                btcAddress2,
                btcAddress3,
                PegTestUtils.createRandomP2PKHBtcAddress(networkParameters),
                PegTestUtils.createRandomP2PKHBtcAddress(networkParameters)
            )
        );

        Assertions.assertArrayEquals(expectedResult.toArray(), foundUTXOs.toArray());

        Coin amount = foundUTXOs.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Coin expectedAmount = expectedResult.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Assertions.assertEquals(amount, expectedAmount);
    }

    @Test
    void testGetUTXOsSentToAddresses_no_utxo_sent_to_given_address_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        Context btcContext = new Context(networkParameters);
        Address btcAddress1 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress2 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress3 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Address btcAddress4 = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addOutput(Coin.COIN, btcAddress1);
        btcTx.addOutput(Coin.ZERO, btcAddress1);
        btcTx.addOutput(Coin.COIN, btcAddress2);
        btcTx.addOutput(Coin.COIN, btcAddress2);
        btcTx.addOutput(Coin.COIN, btcAddress3);
        btcTx.addOutput(Coin.COIN, btcAddress4);

        List<UTXO> foundUTXOs = BridgeUtils.getUTXOsSentToAddresses(
            activations,
            networkParameters,
            btcContext,
            btcTx,
            Arrays.asList(PegTestUtils.createRandomP2PKHBtcAddress(networkParameters))
        );

        Assertions.assertTrue(foundUTXOs.isEmpty());
    }

    @Test
    void calculateSignedSegwitBtcTxVirtualSize_withASegwitTx_shouldReturnCorrectVirtualSize() {
        // arrange
        // https://mempool.space/tx/6f8af5e623c82b9a8e1806d677c57f81118a1da3e44032ec1a2f3603c90a7749
        byte[] btcRawTx = Hex.decode("02000000000103762640beb71fcf0d15a61a825c7787c3f60dabf010df4686d3bec4f3c6d2364f02000000232200202bd90b1cbb2ef3b7cb4e3909dfea9b91de17ebc9c04db0ad243fc0358708b928ffffffff1db227830f151ef617fb0aaa39adb6bf30a3b53debeabffcc54a9cb2a836035100000000232200202bd90b1cbb2ef3b7cb4e3909dfea9b91de17ebc9c04db0ad243fc0358708b928ffffffff5af576578a2d88dad6e732a3be1ba82d29cf69211d4cbf342f8fcec31b1ad47501000000232200202bd90b1cbb2ef3b7cb4e3909dfea9b91de17ebc9c04db0ad243fc0358708b928ffffffff018ec62a000000000017a91484708b5f82a7c6b1f1a2274d2d801a9b86fa330d870800483045022100fbd02d4aa6d457732c1b0aaf62a2d9464510efb8d84d548bbf2020c208739fd6022077e6096df604f8beeed4e1eadf56c40cd917337f922054ab2e56ed1ad49ab87e014730440220636fedd886447bfc7181c1c6d6a207bf78cc126a14133d057c3b0927d084c6880220101a21167888382ca3680ffe69bd8a4e3da844a61bbdb0a7246eb294289740c601483045022100b5151837b89cf5a23475870a72d1b5957a7808796c373d99bb6b0e2436fb4e5a02201022c48104723a9a50c323efbaf6764f9adb49292121a7e05294807a9fa4df58014730440220673cd56a052eebfea76661488aa9f05beea12860442040e54fa0d1f0ada94362022025c547e82fcbb4f4eec15f55c17357caf33b5e1d4e72763a671f0634e4f3b6e601483045022100cbf313d9e9d1b75f73fe626fd6450c1910cbe3f0cb2a6d7a571f653c3d588aa30220705879beda53624e7ad16e396dc15f0b6c1363984795f105a9f758756c43aa0c0100fdc901645521026df77fe41e8ac503ba47cb3a27e12661c5ee9d7f9f185d11c5680c0923356c3e21027279e6b34e38bca7c2b05524eb942beb8e5ef4a1e88cadb366eec66128e3036021027ec0acfd4ae4af03dc9559e0508d98b1790607165d8f95a1d4dcd4da47c05743210285409152cf9031c098a968cde3bcc1e85e4135f0dc8bc022d903a0395a84385221028c561a89624fa81819167ecf6175703c5760fce9a7df6703fa0589050c8f02522102b0db2c66fbad3a46f2b0a617660a66ad72f5391aec659dd4b4de5e45d642e40421031c749a4e732bf871ec985496431b71d85c533690c12a4228143cc290c928078f21031d881aabb972128028fc1b830921f1cc9009a51629c42dd59c7582bfef19a90121036613ff4a2959177ab44913d0a900f9917dab43161b126113d6a098994330330d59ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae6808004730440220592d8757774639dc57e422186ec96185a895ba02ac67db9f393a5387492d6e6202206ed71c353d423e8e2ace536d51697b49bb957907a8a1d5a2ace091022e8221e601483045022100c1ea2f7de1884617f59b399c43695073bb0368e1299224e2b75a6cf67205765a022021f97603c075f7835fdae892a5fe3e5d8db00edb6d62c30ac82debc61103852b0147304402204db4235f92b4279dd839b092f863c7a93396997f00f2e297ff5f2dba7e93180c02205c147fab59087e7e23f46c154c08610e62e6d1ba1bcebf1136eba5084804a03001473044022030d8aab9bee2ceba4983d7def269080470ed283bdd9d8f7508b166195e02576b02201e09d3080c6306c1274bfcc5687581df737efbb20b91256be908bdb49c4c44b601483045022100d517a452532c13d04c2f8f2843d523f818ed73d5b23a43becf295ca4ddbbe69802202d7595ad110459b2078ebf94c68cbbba0060b0f2df3b7cbe40ad20e26dbd06c70100fdc901645521026df77fe41e8ac503ba47cb3a27e12661c5ee9d7f9f185d11c5680c0923356c3e21027279e6b34e38bca7c2b05524eb942beb8e5ef4a1e88cadb366eec66128e3036021027ec0acfd4ae4af03dc9559e0508d98b1790607165d8f95a1d4dcd4da47c05743210285409152cf9031c098a968cde3bcc1e85e4135f0dc8bc022d903a0395a84385221028c561a89624fa81819167ecf6175703c5760fce9a7df6703fa0589050c8f02522102b0db2c66fbad3a46f2b0a617660a66ad72f5391aec659dd4b4de5e45d642e40421031c749a4e732bf871ec985496431b71d85c533690c12a4228143cc290c928078f21031d881aabb972128028fc1b830921f1cc9009a51629c42dd59c7582bfef19a90121036613ff4a2959177ab44913d0a900f9917dab43161b126113d6a098994330330d59ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae680800483045022100c99a13fec8eca738ed941d5683342938cf2bb1e339a9938db64589cdd7dd1095022048e1d2b305d919424387456250adf9b4dcb688a8e2d5a3154ad977c8f3525a3901483045022100e2f3a2000c789a46288bd9f51894d8e67cc12227d0909868a52d3bd782b6124e022064c9106a6945cc1f9369f20383f76b83e193b902786f4d9cdd7bfc0299232f5501473044022034753f3fa57d8f8b9fb98324c5e250805cc0662d37912818431d5838e8cbc8a8022016523e5c9a067936a60d0e8e16245576541900f0dd5ae9fada44d33a8677ac9001483045022100d550ad9d5c33e92b0f12e714cbdd18b2522e73886d3e162b57a99523e564142b02201de44c2e4625adf578fb8167030f2e6105a121dec487762c4d5e8531d68032ba01483045022100d03a25a6332ecd3e8486b49afa0cca0c61c895ad6c0556dee0a4a277de3f61a3022051497aa8ce2ad5063ae26d80692d56af44d2a2a87d61e8d46b2a83e2bfda19840100fdc901645521026df77fe41e8ac503ba47cb3a27e12661c5ee9d7f9f185d11c5680c0923356c3e21027279e6b34e38bca7c2b05524eb942beb8e5ef4a1e88cadb366eec66128e3036021027ec0acfd4ae4af03dc9559e0508d98b1790607165d8f95a1d4dcd4da47c05743210285409152cf9031c098a968cde3bcc1e85e4135f0dc8bc022d903a0395a84385221028c561a89624fa81819167ecf6175703c5760fce9a7df6703fa0589050c8f02522102b0db2c66fbad3a46f2b0a617660a66ad72f5391aec659dd4b4de5e45d642e40421031c749a4e732bf871ec985496431b71d85c533690c12a4228143cc290c928078f21031d881aabb972128028fc1b830921f1cc9009a51629c42dd59c7582bfef19a90121036613ff4a2959177ab44913d0a900f9917dab43161b126113d6a098994330330d59ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae6800000000");
        BtcTransaction btcTx = new BtcTransaction(networkParameters, btcRawTx);
        int expectedVirtualBytes = 890;

        // act
        int actualVirtualBytes = calculateSignedSegwitBtcTxVirtualSize(btcTx);

        //assert
        Assertions.assertEquals(expectedVirtualBytes, actualVirtualBytes);
    }
}
