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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.wallet.CoinSelector;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
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
        isFreeBridgeTx(true, PrecompiledContracts.BRIDGE_ADDR, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
    }

    @Test
    void isFreeBridgeTxOtherContract() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();
        isFreeBridgeTx(false, PrecompiledContracts.IDENTITY_ADDR, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
    }

    @Test
    void isFreeBridgeTxFreeTxDisabled() {
        activationConfig = ActivationConfigsForTest.only(ConsensusRule.ARE_BRIDGE_TXS_PAID);
        isFreeBridgeTx(false, PrecompiledContracts.BRIDGE_ADDR, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
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

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1Key.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1Key);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        txInput.setScriptSig(inputScript);

        // Act
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(federator1Key, sighash, txInput);

        // Assert
        Assertions.assertTrue(isSigned);
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

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1Key.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1Key);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        txInput.setScriptSig(inputScript);

        // Act
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(federator2Key, sighash, txInput);

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

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        // Act
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(federator1Key, sighash, txInput);

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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

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

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 2076; // Data for 2 inputs, 2 outputs From https://www.blockchain.com/btc/tx/e92cab54ecf738a00083fd8990515247aa3404df4f76ec358d9fe87d95102ae4
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void testCalculatePegoutTxSize_9Inputs_2Outputs() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

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

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 9069; // Data for 9 inputs, 2 outputs From https://www.blockchain.com/btc/tx/15adf52f7b4b7a7e563fca92aec7bbe8149b87fac6941285a181e6fcd799a1cd
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void testCalculatePegoutTxSize_10Inputs_20Outputs() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

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

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void testCalculatePegoutTxSize_50Inputs_200Outputs() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

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

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void testCalculatePegoutTxSize_50Inputs_200Outputs_nonStandardErpFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

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

        // The difference between the calculated size and a real tx size should be smaller than 3% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .03;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void testCalculatePegoutTxSize_100Inputs_50Outputs_nonStandardErpFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

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

        // The difference between the calculated size and a real tx size should be smaller than 3% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .03;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void getRegularPegoutTxSize_has_proper_calculations() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstantsMainnet);
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
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstantsMainnet);
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
            BridgeRegTestConstants.getInstance();

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
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstantsRegtest);
        FederationArgs genesisFederationArgs = genesisFederation.getArgs();
        List<BtcECKey> erpPubKeys = bridgeConstantsRegtest.getErpFedPubKeysList();
        long activationDelay = bridgeConstantsRegtest.getErpFedActivationDelay();

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
            federation = FederationTestUtils.getGenesisFederation(bridgeConstantsRegtest);
        }

        if (isFlyover) {
            // Create fast bridge redeem script
            Sha256Hash derivationArgumentsHash = Sha256Hash.of(new byte[]{1});
            Script flyoverRedeemScript;

            if (federation instanceof ErpFederation) {
                flyoverRedeemScript =
                    FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
                        federation.getRedeemScript(),
                        derivationArgumentsHash
                    );
            } else {
                flyoverRedeemScript =
                    FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                        federation.getRedeemScript(),
                        derivationArgumentsHash
                    );
            }

            Script flyoverP2SH = ScriptBuilder
                .createP2SHOutputScript(flyoverRedeemScript);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
}
