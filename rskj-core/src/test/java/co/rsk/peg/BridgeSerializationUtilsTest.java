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

import static co.rsk.peg.BridgeSerializationUtils.deserializeRskTxHash;
import static co.rsk.peg.PegTestUtils.createHash3;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.federation.*;
import co.rsk.peg.resources.TestConstants;
import co.rsk.peg.utils.MerkleTreeUtils;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedBytes;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class BridgeSerializationUtilsTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters MAINNET_PARAMETERS = bridgeMainnetConstants.getBtcParams();

    private static final BridgeConstants bridgeTestnetConstants = BridgeTestNetConstants.getInstance();
    private static final NetworkParameters TESTNET_PARAMETERS = bridgeTestnetConstants.getBtcParams();

    private static final Address ADDRESS = BitcoinTestUtils.createP2PKHAddress(MAINNET_PARAMETERS, "first");
    private static final Address OTHER_ADDRESS = BitcoinTestUtils.createP2PKHAddress(MAINNET_PARAMETERS, "second");

    @Test
    void serializeAndDeserializeBtcTransaction_withValidDataAndInputs_shouldReturnEqualResults() {
        // Arrange
        BtcTransaction prevTx = new BtcTransaction(MAINNET_PARAMETERS);
        prevTx.addOutput(Coin.FIFTY_COINS, ADDRESS);
        prevTx.addOutput(Coin.FIFTY_COINS, OTHER_ADDRESS);

        BtcTransaction btcTx = new BtcTransaction(MAINNET_PARAMETERS);
        btcTx.addInput(prevTx.getOutput(0));
        btcTx.addInput(prevTx.getOutput(1));
        btcTx.addOutput(Coin.COIN, OTHER_ADDRESS);

        // Act
        byte[] serializedBtcTransaction = BridgeSerializationUtils.serializeBtcTransaction(btcTx);
        BtcTransaction deserializedBtcTransaction = BridgeSerializationUtils.deserializeBtcTransactionWithInputs(serializedBtcTransaction, MAINNET_PARAMETERS);

        // Assert
        assertNotNull(serializedBtcTransaction);
        assertNotNull(deserializedBtcTransaction);
        assertEquals(btcTx, deserializedBtcTransaction);
    }

    @Test
    void serializeAndDeserializeBtcTransaction_withValidDataAndWithoutInputs_shouldReturnEqualResults() {
        // Arrange
        BtcTransaction prevTx = new BtcTransaction(MAINNET_PARAMETERS);
        prevTx.addOutput(Coin.FIFTY_COINS, ADDRESS);
        prevTx.addOutput(Coin.FIFTY_COINS, OTHER_ADDRESS);

        BtcTransaction btcTx = new BtcTransaction(MAINNET_PARAMETERS);
        btcTx.addOutput(Coin.COIN, OTHER_ADDRESS);

        // Act
        byte[] serializedBtcTransaction = BridgeSerializationUtils.serializeBtcTransaction(btcTx);
        BtcTransaction deserializedBtcTransaction = BridgeSerializationUtils.deserializeBtcTransactionWithoutInputs(serializedBtcTransaction, MAINNET_PARAMETERS);

        // Assert
        assertNotNull(serializedBtcTransaction);
        assertNotNull(deserializedBtcTransaction);
        assertEquals(btcTx, deserializedBtcTransaction);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    void deserializeBtcTransaction_withInvalidData_shouldReturnNull(byte[] data) {
        // Act
        BtcTransaction deserializedTxWithInputs = BridgeSerializationUtils.deserializeBtcTransactionWithInputs(data, MAINNET_PARAMETERS);
        BtcTransaction deserializedTxWithoutInputs = BridgeSerializationUtils.deserializeBtcTransactionWithoutInputs(data, MAINNET_PARAMETERS);

        // Assert
        assertNull(deserializedTxWithInputs);
        assertNull(deserializedTxWithoutInputs);
    }

    @Test
    void serializeAndDeserializeSvpFundTransaction_withValidData_shouldReturnEqualResults() {
        // Arrange
        BtcTransaction prevTx = new BtcTransaction(MAINNET_PARAMETERS);
        prevTx.addOutput(Coin.FIFTY_COINS, ADDRESS);
        prevTx.addOutput(Coin.FIFTY_COINS, OTHER_ADDRESS);

        BtcTransaction svpFundTx = new BtcTransaction(MAINNET_PARAMETERS);
        svpFundTx.addInput(prevTx.getOutput(0));
        svpFundTx.addInput(prevTx.getOutput(1));
        svpFundTx.addOutput(Coin.COIN, OTHER_ADDRESS);

        // Act
        byte[] serializedSvpFundTransaction = BridgeSerializationUtils.serializeBtcTransaction(svpFundTx);
        BtcTransaction deserializedSvpFundTransaction = BridgeSerializationUtils.deserializeBtcTransactionWithInputs(serializedSvpFundTransaction, MAINNET_PARAMETERS);

        // Assert
        assertNotNull(serializedSvpFundTransaction);
        assertNotNull(deserializedSvpFundTransaction);
        assertEquals(svpFundTx, deserializedSvpFundTransaction);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    void deserializeSvpFundTransaction_withInvalidData_shouldReturnNull(byte[] data) {
        // Act
        BtcTransaction result = BridgeSerializationUtils.deserializeBtcTransactionWithInputs(data, MAINNET_PARAMETERS);

        // Assert
        assertNull(result);
    }

    @Test
    void serializeAndDeserializeRskTxWaitingForSignatures_whenValidData_shouldReturnEqualResults() {
        // Arrange
        BtcTransaction prevTx = new BtcTransaction(MAINNET_PARAMETERS);
        prevTx.addOutput(Coin.FIFTY_COINS, ADDRESS);

        Keccak256 pegoutCreationRskTxHash = createHash3(1);
        BtcTransaction pegoutTx = new BtcTransaction(MAINNET_PARAMETERS);
        pegoutTx.addInput(prevTx.getOutput(0));
        pegoutTx.addOutput(Coin.COIN, OTHER_ADDRESS);

        Map.Entry<Keccak256, BtcTransaction> pegoutTxWaitingForSignaturesEntry =
            new AbstractMap.SimpleEntry<>(pegoutCreationRskTxHash, pegoutTx);

        // Act
        byte[] serializedEntry =
            BridgeSerializationUtils.serializeRskTxWaitingForSignatures(pegoutTxWaitingForSignaturesEntry);
        Map.Entry<Keccak256, BtcTransaction> deserializedEntry =
            BridgeSerializationUtils.deserializeRskTxWaitingForSignatures(serializedEntry, MAINNET_PARAMETERS);

        // Assert
        assertNotNull(serializedEntry);
        assertTrue(serializedEntry.length > 0);

        assertNotNull(deserializedEntry);
        assertEquals(pegoutCreationRskTxHash, deserializedEntry.getKey());
        assertArrayEquals(pegoutTx.bitcoinSerialize(), deserializedEntry.getValue().bitcoinSerialize());
    }

    @Test
    void serializeRskTxWaitingForSignatures_whenNullValuePassed_shouldReturnEmptyResult() {
        // Act
        byte[] result =
            BridgeSerializationUtils.serializeRskTxWaitingForSignatures(null);

        // Assert
        assertArrayEquals(RLP.encodedEmptyList(), result);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidData")
    void deserializeRskTxWaitingForSignatures_whenInvalidData_shouldReturnEmptyResult(byte[] data) {
        // Act
        Map.Entry<Keccak256, BtcTransaction> result =
            BridgeSerializationUtils.deserializeRskTxWaitingForSignatures(data, MAINNET_PARAMETERS);

        // Assert
        assertNull(result);
    }
    
    private static Stream<byte[]> provideInvalidData() {
        return Stream.of(
            null,
            new byte[]{},
            RLP.encodedEmptyList());
    }

    @Test
    void serializeAndDeserializeRskTxsWaitingForSignatures_whenValidEntries_shouldReturnEqualsResults() {
        // Arrange
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignaturesMap = new TreeMap<>();

        BtcTransaction prevTx = new BtcTransaction(MAINNET_PARAMETERS);
        prevTx.addOutput(Coin.COIN.multiply(5), ADDRESS);

        BtcTransaction pegoutTx1 = new BtcTransaction(MAINNET_PARAMETERS);
        pegoutTx1.addInput(prevTx.getOutput(0));
        pegoutTx1.addOutput(Coin.COIN, OTHER_ADDRESS);

        BtcTransaction pegoutTx2 = new BtcTransaction(MAINNET_PARAMETERS);
        pegoutTx2.addInput(prevTx.getOutput(0));
        pegoutTx2.addOutput(Coin.COIN.multiply(10), OTHER_ADDRESS);

        Keccak256 pegoutCreationRskTxHash1 = createHash3(1);
        rskTxsWaitingForSignaturesMap.put(pegoutCreationRskTxHash1, pegoutTx1);

        Keccak256 pegoutCreationRskTxHash2 = createHash3(2);
        rskTxsWaitingForSignaturesMap.put(pegoutCreationRskTxHash2, pegoutTx2);

        // Act
        byte[] serializedRskTxsWaitingForSignaturesMap = 
            BridgeSerializationUtils.serializeRskTxsWaitingForSignatures(rskTxsWaitingForSignaturesMap);
        SortedMap<Keccak256, BtcTransaction> deserializedRskTxsWaitingForSignaturesMap = 
            BridgeSerializationUtils.deserializeRskTxsWaitingForSignatures(serializedRskTxsWaitingForSignaturesMap, MAINNET_PARAMETERS);

        // Assert
        assertNotNull(serializedRskTxsWaitingForSignaturesMap);
        assertTrue(serializedRskTxsWaitingForSignaturesMap.length > 0);

        assertEquals(rskTxsWaitingForSignaturesMap, deserializedRskTxsWaitingForSignaturesMap);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    void deserializeRskTxsWaitingForSignatures_whenInvalidData_shouldReturnEmptyResult(byte[] data) {
        // Act
        SortedMap<Keccak256, BtcTransaction> result =
            BridgeSerializationUtils.deserializeRskTxsWaitingForSignatures(data, MAINNET_PARAMETERS);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void deserializeRskTxHash_returnsExpectedHash() {
        // arrange
        Keccak256 expectedRskTxHash = RskTestUtils.createHash(1);
        byte[] rskTxHashSerialized = expectedRskTxHash.getBytes();

        // act
        Keccak256 rskTxHash = deserializeRskTxHash(rskTxHashSerialized);

        // assert
        assertEquals(expectedRskTxHash, rskTxHash);
    }

    @Test
    void deserializeRskTxHash_withInvalidLengthSerializedHash_throwsIllegalArgumentException() {
        // arrange
        byte[] rskTxHashSerialized = new byte[31];

        // act & assert
        assertThrows(IllegalArgumentException.class,
            () -> deserializeRskTxHash(rskTxHashSerialized));
    }

    @Test
    void deserializeRskTxHash_withNullValue_throwsIllegalArgumentException() {
        // act & assert
        assertThrows(IllegalArgumentException.class, () -> deserializeRskTxHash(null));
    }

    @Test
    void serializeMapOfHashesToLong() {
        Map<Sha256Hash, Long> sample = new HashMap<>();
        sample.put(Sha256Hash.wrap(charNTimes('b', 64)), 1L);
        sample.put(Sha256Hash.wrap(charNTimes('d', 64)), 2L);
        sample.put(Sha256Hash.wrap(charNTimes('a', 64)), 3L);
        sample.put(Sha256Hash.wrap(charNTimes('c', 64)), 4L);

        byte[] result = BridgeSerializationUtils.serializeMapOfHashesToLong(sample);

        String hexResult = ByteUtil.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();

        expectedBuilder.append("f888");

        char[] sorted = new char[]{'a','b','c','d'};

        for (char c : sorted) {
            String key = charNTimes(c, 64);
            expectedBuilder.append("a0");
            expectedBuilder.append(key);
            expectedBuilder.append("0").append(sample.get(Sha256Hash.wrap(key)));
        }

        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    void deserializeMapOfHashesToLong_emptyOrNull() {
        assertEquals(BridgeSerializationUtils.deserializeMapOfHashesToLong(null), new HashMap<>());
        assertEquals(BridgeSerializationUtils.deserializeMapOfHashesToLong(new byte[]{}), new HashMap<>());
    }

    @Test
    void deserializeMapOfHashesToLong_nonEmpty() {
        byte[] rlpFirstKey = RLP.encodeElement(Hex.decode(charNTimes('b', 64)));
        byte[] rlpSecondKey = RLP.encodeElement(Hex.decode(charNTimes('d', 64)));
        byte[] rlpThirdKey = RLP.encodeElement(Hex.decode(charNTimes('a', 64)));

        byte[] rlpFirstValue = RLP.encodeBigInteger(BigInteger.valueOf(7));
        byte[] rlpSecondValue = RLP.encodeBigInteger(BigInteger.valueOf(76));
        byte[] rlpThirdValue = RLP.encodeBigInteger(BigInteger.valueOf(123));

        byte[] data = RLP.encodeList(rlpFirstKey, rlpFirstValue, rlpSecondKey, rlpSecondValue, rlpThirdKey, rlpThirdValue);

        Map<Sha256Hash, Long> result = BridgeSerializationUtils.deserializeMapOfHashesToLong(data);
        assertEquals(3, result.size());
        assertEquals(7L, result.get(Sha256Hash.wrap(charNTimes('b', 64))).longValue());
        assertEquals(76L, result.get(Sha256Hash.wrap(charNTimes('d', 64))).longValue());
        assertEquals(123L, result.get(Sha256Hash.wrap(charNTimes('a', 64))).longValue());
    }

    @Test
    void deserializeMapOfHashesToLong_nonEmptyOddSize() {
        byte[] rlpFirstKey = RLP.encodeElement(Hex.decode(charNTimes('b', 64)));
        byte[] rlpSecondKey = RLP.encodeElement(Hex.decode(charNTimes('d', 64)));
        byte[] rlpThirdKey = RLP.encodeElement(Hex.decode(charNTimes('a', 64)));
        byte[] rlpFourthKey = RLP.encodeElement(Hex.decode(charNTimes('e', 64)));

        byte[] rlpFirstValue = RLP.encodeBigInteger(BigInteger.valueOf(7));
        byte[] rlpSecondValue = RLP.encodeBigInteger(BigInteger.valueOf(76));
        byte[] rlpThirdValue = RLP.encodeBigInteger(BigInteger.valueOf(123));

        byte[] data = RLP.encodeList(rlpFirstKey, rlpFirstValue, rlpSecondKey, rlpSecondValue, rlpThirdKey, rlpThirdValue, rlpFourthKey);

        boolean thrown = false;

        try {
            BridgeSerializationUtils.deserializeMapOfHashesToLong(data);
        } catch (RuntimeException e) {
            thrown = true;
        }
        Assertions.assertTrue(thrown);
    }

    @Test
    void serializeFederationOnlyBtcKeys() {
        byte[][] publicKeyBytes = new byte[][]{
            BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        // Only actual keys serialized are BTC keys, so we don't really care about RSK or MST keys
        List<FederationMember> members = FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(publicKeyBytes[0]),
            BtcECKey.fromPublicOnly(publicKeyBytes[1]),
            BtcECKey.fromPublicOnly(publicKeyBytes[2]),
            BtcECKey.fromPublicOnly(publicKeyBytes[3]),
            BtcECKey.fromPublicOnly(publicKeyBytes[4]),
            BtcECKey.fromPublicOnly(publicKeyBytes[5])
        ));
        Instant creationTime = Instant.ofEpochMilli(0xabcdef);
        long creationBlockNumber = 42L;

        FederationArgs federationArgs = new FederationArgs(
            members,
            creationTime,
            creationBlockNumber,
            TESTNET_PARAMETERS
        );
        Federation standardMultisigFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

        byte[] result = BridgeSerializationUtils.serializeFederationOnlyBtcKeys(standardMultisigFederation);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("f8d3"); // Outer list
        expectedBuilder.append("83abcdef"); // Creation time
        expectedBuilder.append("2a"); // Creation block number
        expectedBuilder.append("f8cc"); // Inner list

        standardMultisigFederation.getBtcPublicKeys().stream().sorted(BtcECKey.PUBKEY_COMPARATOR).forEach(key -> {
            expectedBuilder.append("a1");
            expectedBuilder.append(ByteUtil.toHexString(key.getPubKey()));
        });

        String expected = expectedBuilder.toString();

        Assertions.assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    void deserializeFederationOnlyBtcKeys_ok() {
        byte[][] publicKeyBytes = Stream.of(100, 200, 300, 400, 500, 600)
            .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .map(BtcECKey::getPubKey)
            .toArray(byte[][]::new);

        byte[][] rlpKeys = new byte[publicKeyBytes.length][];

        for (int k = 0; k < publicKeyBytes.length; k++) {
            rlpKeys[k] = RLP.encodeElement(publicKeyBytes[k]);
        }

        byte[] rlpFirstElement = RLP.encodeElement(Hex.decode("1388")); // First element (creation date -> 5000 milliseconds from epoch)
        byte[] rlpSecondElement = RLP.encodeElement(Hex.decode("002a")); // Second element block number 42
        byte[] rlpKeyList = RLP.encodeList(rlpKeys);

        byte[] data = RLP.encodeList(rlpFirstElement, rlpSecondElement, rlpKeyList);

        Federation deserializedFederation = BridgeSerializationUtils.deserializeStandardMultisigFederationOnlyBtcKeys(data, TESTNET_PARAMETERS);

        Assertions.assertEquals(5000, deserializedFederation.getCreationTime().toEpochMilli());
        Assertions.assertEquals(4, deserializedFederation.getNumberOfSignaturesRequired());
        Assertions.assertEquals(6, deserializedFederation.getBtcPublicKeys().size());
        MatcherAssert.assertThat(deserializedFederation.getCreationBlockNumber(), is(42L));

        for (int i = 0; i < 6; i++) {
            assertArrayEquals(
                publicKeyBytes[i],
                deserializedFederation.getBtcPublicKeys().get(i).getPubKey()
            );
        }

        assertEquals(TESTNET_PARAMETERS, deserializedFederation.getBtcParams());
    }

    @Test
    void deserializeFederationOnlyBtcKeys_wrongListSize() {
        byte[] rlpFirstElement = RLP.encodeElement(Hex.decode("1388")); // First element (creation date -> 5000 milliseconds from epoch)
        byte[] rlpSecondElement = RLP.encodeElement(Hex.decode("03")); // Second element (# of signatures required - 3)
        byte[] rlpThirdElement = RLP.encodeElement(Hex.decode("03"));
        byte[] rlpFourthElement = RLP.encodeElement(Hex.decode("aabbccdd"));

        byte[] data = RLP.encodeList(rlpFirstElement, rlpSecondElement, rlpThirdElement, rlpFourthElement);

        boolean thrown = false;

        try {
            BridgeSerializationUtils.deserializeStandardMultisigFederationOnlyBtcKeys(data, TESTNET_PARAMETERS);
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains("Expected 3 elements"));
            thrown = true;
        }

        Assertions.assertTrue(thrown);
    }

    @Test
    void serializeAndDeserializeFederation_beforeRskip284_testnet() {
        testSerializeAndDeserializeFederation(
            false,
            false,
            NetworkParameters.ID_TESTNET
        );
    }

    @Test
    void serializeAndDeserializeFederation_beforeRskip284_mainnet() {
        testSerializeAndDeserializeFederation(
            false,
            false,
            NetworkParameters.ID_MAINNET
        );
    }

    @Test
    void serializeAndDeserializeFederation_afterRskip284_testnet() {
        testSerializeAndDeserializeFederation(
            true,
            false,
            NetworkParameters.ID_TESTNET
        );
    }

    @Test
    void serializeAndDeserializeFederation_afterRskip284_mainnet() {
        testSerializeAndDeserializeFederation(
            true,
            false,
            NetworkParameters.ID_MAINNET
        );
    }

    @Test
    void serializeAndDeserializeFederation_afterRskip353_testnet() {
        testSerializeAndDeserializeFederation(
            true,
            true,
            NetworkParameters.ID_TESTNET
        );
    }

    @Test
    void serializeAndDeserializeFederation_afterRskip353_mainnet() {
        testSerializeAndDeserializeFederation(
            true,
            true,
            NetworkParameters.ID_MAINNET
        );
    }

    @Test
    void serializeFederation_serializedKeysAreCompressedAndThree() {
        final int NUM_MEMBERS = 10;
        final int EXPECTED_NUM_KEYS = 3;
        final int EXPECTED_PUBLICKEY_SIZE = 33;

        List<FederationMember> members = new ArrayList<>();
        for (int j = 0; j < NUM_MEMBERS; j++) {
            members.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
        }

        Instant creationTime = Instant.now();
        long creationBlockNumber = 123;

        FederationArgs federationArgs =
            new FederationArgs(members, creationTime, creationBlockNumber, TESTNET_PARAMETERS);
        Federation testStandardMultisigFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);
        byte[] serializedFederation = BridgeSerializationUtils.serializeFederation(testStandardMultisigFederation);

        RLPList serializedList = (RLPList) RLP.decode2(serializedFederation).get(0);
        Assertions.assertEquals(3, serializedList.size());

        RLPList memberList = (RLPList) serializedList.get(2);
        Assertions.assertEquals(NUM_MEMBERS, memberList.size());

        for (int i = 0; i < NUM_MEMBERS; i++) {
            RLPList memberKeys = (RLPList) RLP.decode2(memberList.get(i).getRLPData()).get(0);
            Assertions.assertEquals(EXPECTED_NUM_KEYS, memberKeys.size());
            for (int j = 0; j < EXPECTED_NUM_KEYS; j++) {
                Assertions.assertEquals(EXPECTED_PUBLICKEY_SIZE, memberKeys.get(j).getRLPData().length);
            }

        }
    }

    @Test
    void deserializeFederation_wrongListSize() {
        byte[] serialized = RLP.encodeList(RLP.encodeElement(new byte[0]), RLP.encodeElement(new byte[0]));
        Exception ex = assertThrows(RuntimeException.class, () -> BridgeSerializationUtils.deserializeStandardMultisigFederation(serialized, TESTNET_PARAMETERS));
        Assertions.assertTrue(ex.getMessage().contains("Invalid serialized Federation"));
    }

    @Test
    void deserializeFederation_invalidFederationMember() {
        byte[] serialized = RLP.encodeList(
            RLP.encodeElement(BigInteger.valueOf(1).toByteArray()),
            RLP.encodeElement(BigInteger.valueOf(1).toByteArray()),
            RLP.encodeList(RLP.encodeList(RLP.encodeElement(new byte[0]), RLP.encodeElement(new byte[0])))
        );

        Exception ex = assertThrows(RuntimeException.class, () -> BridgeSerializationUtils.deserializeStandardMultisigFederation(serialized, TESTNET_PARAMETERS));
        Assertions.assertTrue(ex.getMessage().contains("Invalid serialized FederationMember"));
    }

    @Test
    void serializeElection() {
        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        Map<ABICallSpec, List<RskAddress>> sampleVotes = new HashMap<>();
        sampleVotes.put(
            new ABICallSpec("one-function", new byte[][]{}),
            Arrays.asList(createAddress("8899"), createAddress("aabb"))
        );
        sampleVotes.put(
            new ABICallSpec("another-function", new byte[][]{ Hex.decode("01"), Hex.decode("0203") }),
            Arrays.asList(createAddress("ccdd"), createAddress("eeff"), createAddress("0011"))
        );
        sampleVotes.put(
            new ABICallSpec("yet-another-function", new byte[][]{ Hex.decode("0405") }),
            Arrays.asList(createAddress("fa"), createAddress("ca"))
        );

        ABICallElection sample = new ABICallElection(authorizer, sampleVotes);

        byte[] result = BridgeSerializationUtils.serializeElection(sample);
        String hexResult = ByteUtil.toHexString(result);

        StringBuilder expectedBuilder = new StringBuilder();

        expectedBuilder.append("f8d7d6");

        expectedBuilder.append("90");
        expectedBuilder.append(ByteUtil.toHexString("another-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("c4");
        expectedBuilder.append("01");
        expectedBuilder.append("820203");
        expectedBuilder.append("f83f");
        expectedBuilder.append("94").append(createAddress("0011"));
        expectedBuilder.append("94").append(createAddress("ccdd"));
        expectedBuilder.append("94").append(createAddress("eeff"));

        expectedBuilder.append("ce");
        expectedBuilder.append("8c");
        expectedBuilder.append(ByteUtil.toHexString("one-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("c0");

        expectedBuilder.append("ea");
        expectedBuilder.append("94").append(createAddress("8899"));
        expectedBuilder.append("94").append(createAddress("aabb"));

        expectedBuilder.append("d9");
        expectedBuilder.append("94");
        expectedBuilder.append(ByteUtil.toHexString("yet-another-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("c3");
        expectedBuilder.append("820405");
        expectedBuilder.append("ea");
        expectedBuilder.append("94").append(createAddress("ca")).append("94").append(createAddress("fa"));

        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    void deserializeElection_emptyOrNull() {
        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();
        ABICallElection election;
        election = BridgeSerializationUtils.deserializeElection(null, authorizer);
        Assertions.assertEquals(0, election.getVotes().size());
        election = BridgeSerializationUtils.deserializeElection(new byte[]{}, authorizer);
        Assertions.assertEquals(0, election.getVotes().size());
    }

    @Test
    void deserializeElection_nonEmpty() {
        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        ABICallSpec firstSpec = new ABICallSpec("funct", new byte[][]{});
        List<RskAddress> firstVoters = Arrays.asList(
            createAddress("aa"),
            createAddress("bbccdd")
        );

        ABICallSpec secondSpec = new ABICallSpec("other-funct", new byte[][]{
            Hex.decode("1122"),
            Hex.decode("334455")
        });
        List<RskAddress> secondVoters = Arrays.asList(
            createAddress("55"),
            createAddress("66"),
            createAddress("77")
        );

        ABICallSpec thirdSpec = new ABICallSpec("random-funct", new byte[][]{
            Hex.decode("aabb")
        });

        List<RskAddress> thirdVoters = Arrays.asList(
            createAddress("1111"),
            createAddress("3333"),
            createAddress("5555"),
            createAddress("77")
        );

        Map<ABICallSpec, List<RskAddress>> specsVotersToProcess = new HashMap<>();

        specsVotersToProcess.put(firstSpec, firstVoters);
        specsVotersToProcess.put(secondSpec, secondVoters);
        specsVotersToProcess.put(thirdSpec, thirdVoters);

        assertNotEquals(0, thirdVoters.get(0).getBytes().length);

        ABICallElection electionToProcess = new ABICallElection(authorizer, specsVotersToProcess);

        byte[] data = BridgeSerializationUtils.serializeElection(electionToProcess);

        ABICallElection election = BridgeSerializationUtils.deserializeElection(data, authorizer);

        Assertions.assertEquals(3, election.getVotes().size());
        List<RskAddress> voters;
        ABICallSpec spec;

        spec = new ABICallSpec("funct", new byte[][]{});
        Assertions.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
            createAddress("aa"),
            createAddress("bbccdd")
        );

        assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());

        spec = new ABICallSpec("other-funct", new byte[][]{
            Hex.decode("1122"),
            Hex.decode("334455")
        });
        Assertions.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
            createAddress("55"),
            createAddress("66"),
            createAddress("77")
        );

        assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());
        assertArrayEquals(voters.get(2).getBytes(), election.getVotes().get(spec).get(2).getBytes());

        spec = new ABICallSpec("random-funct", new byte[][]{
            Hex.decode("aabb")
        });
        Assertions.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
            createAddress("1111"),
            createAddress("3333"),
            createAddress("5555"),
            createAddress("77")
        );

        Assertions.assertEquals(4, election.getVotes().get(spec).size());
        assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());
        assertArrayEquals(voters.get(2).getBytes(), election.getVotes().get(spec).get(2).getBytes());
        assertArrayEquals(voters.get(3).getBytes(), election.getVotes().get(spec).get(3).getBytes());
    }

    @Test
    void deserializeElection_unevenOuterList() {
        AddressBasedAuthorizer mockedAuthorizer = mock(AddressBasedAuthorizer.class);

        byte[] rlpFirstElement = RLP.encodeElement(Hex.decode("010203"));
        byte[] data = RLP.encodeList(rlpFirstElement);

        try {
            BridgeSerializationUtils.deserializeElection(data, mockedAuthorizer);
        } catch (RuntimeException e) {
            Assertions.assertTrue(e.getMessage().contains("expected an even number of entries, but odd given"));
            return;
        }

        fail();
    }

    @Test
    void deserializeElection_invalidCallSpec() {
        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        byte[] rlpFirstSpec = RLP.encodeList(RLP.encode(Hex.decode("010203"))); // invalid spec
        byte[] rlpFirstVoters = RLP.encodeList(RLP.encodeElement(Hex.decode("03"))); // doesn't matter

        byte[] data = RLP.encodeList(rlpFirstSpec, rlpFirstVoters);

        try {
            BridgeSerializationUtils.deserializeElection(data, authorizer);
        } catch (RuntimeException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid serialized ABICallSpec"));
            return;
        }

        fail();
    }

    @Test
    void serializeLockWhitelist() {
        byte[][] addressesBytes = new byte[][]{
            BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKeyHash(),
            BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKeyHash(),
            BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKeyHash(),
            BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKeyHash(),
            BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKeyHash(),
            BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKeyHash(),
        };
        Coin maxToTransfer = Coin.CENT;

        LockWhitelist lockWhitelist = new LockWhitelist(
            Arrays.stream(addressesBytes)
                .map(bytes -> new Address(TESTNET_PARAMETERS, bytes))
                .collect(Collectors.toMap(Function.identity(), k -> new OneOffWhiteListEntry(k, maxToTransfer))),
            0);

        byte[] result = BridgeSerializationUtils.serializeOneOffLockWhitelist(Pair.of(
            lockWhitelist.getAll(OneOffWhiteListEntry.class),
            lockWhitelist.getDisableBlockHeight()
        ));
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("f897");
        Arrays.stream(addressesBytes).sorted(UnsignedBytes.lexicographicalComparator()).forEach(bytes -> {
            expectedBuilder.append("94");
            expectedBuilder.append(ByteUtil.toHexString(bytes));
            expectedBuilder.append("83");
            expectedBuilder.append(ByteUtil.toHexString(BigInteger.valueOf(maxToTransfer.value).toByteArray()));
        });
        expectedBuilder.append("80");
        String expected = expectedBuilder.toString();
        Assertions.assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    void deserializeOneOffLockWhitelistAndDisableBlockHeight() {
        byte[][] addressesBytes = Stream.of(100, 200, 300, 400)
            .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .map(BtcECKey::getPubKeyHash)
            .toArray(byte[][]::new);

        byte[][] rlpBytes = new byte[9][0];

        for (int k = 0; k < addressesBytes.length; k++) {
            rlpBytes[k * 2] = RLP.encodeElement(addressesBytes[k]);
            rlpBytes[k * 2 + 1] = RLP.encodeElement(Hex.decode("0186a0")); // Coin.MILLICOIN
        }

        rlpBytes[8] = RLP.encodeElement(Hex.decode("002a"));

        byte[] data = RLP.encodeList(rlpBytes);

        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(
            data,
            TESTNET_PARAMETERS
        );

        MatcherAssert.assertThat(deserializedLockWhitelist.getLeft().size(), is(addressesBytes.length));
        MatcherAssert.assertThat(deserializedLockWhitelist.getLeft().keySet().stream().map(Address::getHash160).collect(Collectors.toList()), containsInAnyOrder(addressesBytes));
        Set<Coin> deserializedCoins = deserializedLockWhitelist.getLeft().values()
            .stream()
            .map(OneOffWhiteListEntry::maxTransferValue)
            .collect(Collectors.toSet());
        MatcherAssert.assertThat(deserializedCoins, hasSize(1));
        MatcherAssert.assertThat(deserializedCoins, hasItem(Coin.MILLICOIN));
        MatcherAssert.assertThat(deserializedLockWhitelist.getRight(), is(42));
    }

    @Test
    void deserializeOneOffLockWhitelistAndDisableBlockHeight_null() {
        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(
            null,
            TESTNET_PARAMETERS
        );

        assertNull(deserializedLockWhitelist);

        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist2 = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(
            new byte[]{},
            TESTNET_PARAMETERS
        );

        assertNull(deserializedLockWhitelist2);
    }

    @Test
    void serializeDeserializeOneOffLockWhitelistAndDisableBlockHeight() {
        NetworkParameters btcParams = TESTNET_PARAMETERS;
        Map<Address, LockWhitelistEntry> whitelist = new HashMap<>();
        Address address = BtcECKey.fromPrivate(BigInteger.valueOf(100L)).toAddress(btcParams);
        whitelist.put(address, new OneOffWhiteListEntry(address, Coin.COIN));

        LockWhitelist originalLockWhitelist = new LockWhitelist(whitelist, 0);
        byte[] serializedLockWhitelist = BridgeSerializationUtils.serializeOneOffLockWhitelist(Pair.of(
            originalLockWhitelist.getAll(OneOffWhiteListEntry.class),
            originalLockWhitelist.getDisableBlockHeight()
        ));
        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(serializedLockWhitelist, btcParams);

        List<Address> originalAddresses = originalLockWhitelist.getAddresses();
        List<Address> deserializedAddresses = new ArrayList<>(deserializedLockWhitelist.getLeft().keySet());
        MatcherAssert.assertThat(originalAddresses, hasSize(1));
        MatcherAssert.assertThat(deserializedAddresses, hasSize(1));
        MatcherAssert.assertThat(originalAddresses, is(deserializedAddresses));
        MatcherAssert.assertThat(
            ((OneOffWhiteListEntry)originalLockWhitelist.get(originalAddresses.get(0))).maxTransferValue(),
            is((deserializedLockWhitelist.getLeft().get(deserializedAddresses.get(0))).maxTransferValue()));
    }

    @Test
    void serializeAndDeserializeFederationOnlyBtcKeysWithRealRLP() {
        NetworkParameters networkParams = TESTNET_PARAMETERS;

        byte[][] publicKeyBytes = new byte[][]{
            BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        // Only actual keys serialized are BTC keys, so deserialization will fill RSK and MST keys with those
        List<FederationMember> members = FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(publicKeyBytes[0]),
            BtcECKey.fromPublicOnly(publicKeyBytes[1]),
            BtcECKey.fromPublicOnly(publicKeyBytes[2]),
            BtcECKey.fromPublicOnly(publicKeyBytes[3]),
            BtcECKey.fromPublicOnly(publicKeyBytes[4]),
            BtcECKey.fromPublicOnly(publicKeyBytes[5])
        ));
        FederationArgs federationArgs = new FederationArgs(members, Instant.ofEpochMilli(0xabcdef), 42L,
            networkParams);
        Federation standardMultisigFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

        byte[] result = BridgeSerializationUtils.serializeFederationOnlyBtcKeys(standardMultisigFederation);
        Federation deserializedFederation = BridgeSerializationUtils.deserializeStandardMultisigFederationOnlyBtcKeys(result, networkParams);
        MatcherAssert.assertThat(standardMultisigFederation, is(deserializedFederation));
    }

    @Test
    void serializeRequestQueue() {
        List<ReleaseRequestQueue.Entry> sampleEntries = Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddressHash160("ccdd"), Coin.valueOf(10)),
            new ReleaseRequestQueue.Entry(mockAddressHash160("bb"), Coin.valueOf(50)),
            new ReleaseRequestQueue.Entry(mockAddressHash160("bb"), Coin.valueOf(20)),
            new ReleaseRequestQueue.Entry(mockAddressHash160("aa"), Coin.valueOf(30))
        );
        ReleaseRequestQueue sample = new ReleaseRequestQueue(sampleEntries);

        byte[] result = BridgeSerializationUtils.serializeReleaseRequestQueue(sample);
        String hexResult = ByteUtil.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("cd");
        expectedBuilder.append("82ccdd");
        expectedBuilder.append("0a");
        expectedBuilder.append("81bb");
        expectedBuilder.append("32");
        expectedBuilder.append("81bb");
        expectedBuilder.append("14");
        expectedBuilder.append("81aa");
        expectedBuilder.append("1e");
        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    void deserializeRequestQueue_emptyOrNull() {
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseRequestQueue(null, TESTNET_PARAMETERS).size());
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseRequestQueue(new byte[]{}, TESTNET_PARAMETERS).size());
    }

    @Test
    void deserializeRequestQueue_nonEmpty() {
        NetworkParameters params = TESTNET_PARAMETERS;

        Address a1 = Address.fromBase58(params, "mynmcQfJnVjheAqh9XL6htnxPZnaDFbqkB");
        Address a2 = Address.fromBase58(params, "mfrfxeo5L2f5NDURS6YTtCNfVw2t5HAfty");
        Address a3 = Address.fromBase58(params, "myw7AMh5mpKHao6MArhn7EvkeASGsGJzrZ");

        List<ReleaseRequestQueue.Entry> expectedEntries = Arrays.asList(
            new ReleaseRequestQueue.Entry(a1, Coin.valueOf(10)),
            new ReleaseRequestQueue.Entry(a2, Coin.valueOf(7)),
            new ReleaseRequestQueue.Entry(a3, Coin.valueOf(8))
        );

        byte[][] rlpItems = new byte[6][];

        rlpItems[0] = RLP.encodeElement(a1.getHash160());
        rlpItems[1] = RLP.encodeBigInteger(BigInteger.valueOf(10));
        rlpItems[2] = RLP.encodeElement(a2.getHash160());
        rlpItems[3] = RLP.encodeBigInteger(BigInteger.valueOf(7));
        rlpItems[4] = RLP.encodeElement(a3.getHash160());
        rlpItems[5] = RLP.encodeBigInteger(BigInteger.valueOf(8));

        byte[] data = RLP.encodeList(rlpItems);

        ReleaseRequestQueue result = new ReleaseRequestQueue(BridgeSerializationUtils.deserializeReleaseRequestQueue(data, params));

        List<ReleaseRequestQueue.Entry> entries = result.getEntries();
        assertEquals(expectedEntries, entries);
    }

    @Test
    void deserializeRequestQueue_nonEmptyOddSize() {
        NetworkParameters params = TESTNET_PARAMETERS;

        Address a1 = Address.fromBase58(params, "mynmcQfJnVjheAqh9XL6htnxPZnaDFbqkB");
        Address a2 = Address.fromBase58(params, "mfrfxeo5L2f5NDURS6YTtCNfVw2t5HAfty");
        Address a3 = Address.fromBase58(params, "myw7AMh5mpKHao6MArhn7EvkeASGsGJzrZ");

        byte[][] rlpItems = new byte[7][];

        rlpItems[0] = RLP.encodeElement(a1.getHash160());
        rlpItems[1] = RLP.encodeBigInteger(BigInteger.valueOf(10));
        rlpItems[2] = RLP.encodeElement(a2.getHash160());
        rlpItems[3] = RLP.encodeBigInteger(BigInteger.valueOf(7));
        rlpItems[4] = RLP.encodeElement(a3.getHash160());
        rlpItems[5] = RLP.encodeBigInteger(BigInteger.valueOf(8));
        rlpItems[6] = RLP.encodeBigInteger(BigInteger.valueOf(8));

        byte[] data = RLP.encodeList(rlpItems);

        try {
            BridgeSerializationUtils.deserializeReleaseRequestQueue(data, params);
        } catch (RuntimeException e) {
            return;
        }
        fail();
    }

    @Test
    void serializeTransactionSet() {
        Set<PegoutsWaitingForConfirmations.Entry> sampleEntries = new HashSet<>(Arrays.asList(
            new PegoutsWaitingForConfirmations.Entry(mockBtcTransactionSerialize("ccdd"), 10L),
            new PegoutsWaitingForConfirmations.Entry(mockBtcTransactionSerialize("bb"), 20L),
            new PegoutsWaitingForConfirmations.Entry(mockBtcTransactionSerialize("ba"), 30L),
            new PegoutsWaitingForConfirmations.Entry(mockBtcTransactionSerialize("aa"), 40L)
        ));
        PegoutsWaitingForConfirmations sample = new PegoutsWaitingForConfirmations(sampleEntries);

        byte[] result = BridgeSerializationUtils.serializePegoutsWaitingForConfirmations(sample);
        String hexResult = ByteUtil.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("cd");
        expectedBuilder.append("81aa");
        expectedBuilder.append("28");
        expectedBuilder.append("81ba");
        expectedBuilder.append("1e");
        expectedBuilder.append("81bb");
        expectedBuilder.append("14");
        expectedBuilder.append("82ccdd");
        expectedBuilder.append("0a");
        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    void deserializeTransactionSet_emptyOrNull() {
        assertEquals(0, BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(null, TESTNET_PARAMETERS).getEntries().size());
        assertEquals(0, BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(new byte[]{}, TESTNET_PARAMETERS).getEntries().size());
    }

    @Test
    void deserializeTransactionSet_nonEmpty() {
        NetworkParameters params = TESTNET_PARAMETERS;

        BtcTransaction input = new BtcTransaction(params);
        input.addOutput(Coin.FIFTY_COINS, Address.fromBase58(params, "mvc8mwDcdLEq2jGqrL43Ub3sxTR13tB8LL"));

        BtcTransaction t1 = new BtcTransaction(params);
        t1.addInput(input.getOutput(0));
        t1.addOutput(Coin.COIN, Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));
        BtcTransaction t2 = new BtcTransaction(params);
        t2.addInput(input.getOutput(0));
        t2.addOutput(Coin.COIN.multiply(10), Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));
        BtcTransaction t3 = new BtcTransaction(params);
        t3.addInput(input.getOutput(0));
        t3.addOutput(Coin.valueOf(15), Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));
        BtcTransaction t4 = new BtcTransaction(params);
        t4.addInput(input.getOutput(0));
        t4.addOutput(Coin.MILLICOIN, Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));

        Set<PegoutsWaitingForConfirmations.Entry> expectedEntries = new HashSet<>(Arrays.asList(
            new PegoutsWaitingForConfirmations.Entry(t1, 32L),
            new PegoutsWaitingForConfirmations.Entry(t2, 14L),
            new PegoutsWaitingForConfirmations.Entry(t3, 102L),
            new PegoutsWaitingForConfirmations.Entry(t4, 20L)
        ));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(expectedEntries);

        byte[] data = BridgeSerializationUtils.serializePegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations);

        PegoutsWaitingForConfirmations result = BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(data, params);

        Set<PegoutsWaitingForConfirmations.Entry> entries = result.getEntries();

        assertEquals(expectedEntries, entries);
    }

    @Test
    void deserializeTransactionSet_nonEmpty_withTxHash_fails() {
        NetworkParameters params = TESTNET_PARAMETERS;

        BtcTransaction input = new BtcTransaction(params);
        input.addOutput(Coin.FIFTY_COINS, Address.fromBase58(params, "mvc8mwDcdLEq2jGqrL43Ub3sxTR13tB8LL"));

        BtcTransaction t1 = new BtcTransaction(params);
        t1.addInput(input.getOutput(0));
        t1.addOutput(Coin.COIN, Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));

        Set<PegoutsWaitingForConfirmations.Entry> expectedEntries = new HashSet<>(Collections.singletonList(
            new Entry(t1, 32L, PegTestUtils.createHash3(0))
        ));

        PegoutsWaitingForConfirmations rtc = new PegoutsWaitingForConfirmations(expectedEntries);
        byte[] serializedEntries = BridgeSerializationUtils.serializePegoutsWaitingForConfirmationsWithTxHash(rtc);

        assertThrows(RuntimeException.class, () -> BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(serializedEntries, params));
    }

    @Test
    void deserializeTransactionSet_nonEmpty_withoutTxHash_fails() {
        NetworkParameters params = TESTNET_PARAMETERS;

        BtcTransaction input = new BtcTransaction(params);
        input.addOutput(Coin.FIFTY_COINS, Address.fromBase58(params, "mvc8mwDcdLEq2jGqrL43Ub3sxTR13tB8LL"));

        BtcTransaction t1 = new BtcTransaction(params);
        t1.addInput(input.getOutput(0));
        t1.addOutput(Coin.COIN, Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));

        Set<PegoutsWaitingForConfirmations.Entry> expectedEntries = new HashSet<>(Collections.singletonList(
            new Entry(t1, 32L)
        ));

        PegoutsWaitingForConfirmations rtc = new PegoutsWaitingForConfirmations(expectedEntries);
        byte[] serializedEntries = BridgeSerializationUtils.serializePegoutsWaitingForConfirmations(rtc);

        assertThrows(RuntimeException.class, () -> BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(serializedEntries, params, true));
    }

    @Test
    void deserializeTransactionSet_nonEmptyOddSize() {
        byte[] firstItem = RLP.encodeElement(Hex.decode("010203"));
        byte[] data = RLP.encodeList(firstItem);

        try {
            BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(data, TESTNET_PARAMETERS);
        } catch (RuntimeException e) {
            return;
        }
        fail();
    }

    @Test
    void serializeDeserializeCoin() {
        byte[] serialized1 = BridgeSerializationUtils.serializeCoin(Coin.COIN);
        MatcherAssert.assertThat(BridgeSerializationUtils.deserializeCoin(serialized1),
            is(Coin.COIN));
        byte[] serialized2 = BridgeSerializationUtils.serializeCoin(Coin.valueOf(Long.MAX_VALUE));
        MatcherAssert.assertThat(BridgeSerializationUtils.deserializeCoin(serialized2),
            is(Coin.valueOf(Long.MAX_VALUE)));
        byte[] serialized3 = BridgeSerializationUtils.serializeCoin(Coin.ZERO);
        MatcherAssert.assertThat(BridgeSerializationUtils.deserializeCoin(serialized3),
            is(Coin.ZERO));
        MatcherAssert.assertThat(BridgeSerializationUtils.deserializeCoin(null),
            nullValue());
        MatcherAssert.assertThat(BridgeSerializationUtils.deserializeCoin(new byte[0]),
            nullValue());
    }

    @Test
    void serializeInteger() {
        Assertions.assertEquals(BigInteger.valueOf(123), RLP.decodeBigInteger(BridgeSerializationUtils.serializeInteger(123), 0));
        Assertions.assertEquals(BigInteger.valueOf(1200), RLP.decodeBigInteger(BridgeSerializationUtils.serializeInteger(1200), 0));
    }

    @Test
    void deserializeInteger() {
        Assertions.assertEquals(123, BridgeSerializationUtils.deserializeInteger(RLP.encodeBigInteger(BigInteger.valueOf(123))).intValue());
        Assertions.assertEquals(1200, BridgeSerializationUtils.deserializeInteger(RLP.encodeBigInteger(BigInteger.valueOf(1200))).intValue());
    }

    @Test
    void serializeSha256Hash() {
        Sha256Hash originalHash = BitcoinTestUtils.createHash(2);
        byte[] encodedHash = RLP.encodeElement(originalHash.getBytes());

        byte[] result = BridgeSerializationUtils.serializeSha256Hash(originalHash);

        assertArrayEquals(encodedHash, result);
    }

    @Test
    void deserializeSha256Hash() {
        Sha256Hash originalHash = BitcoinTestUtils.createHash(2);
        byte[] encodedHash = RLP.encodeElement(originalHash.getBytes());

        Sha256Hash result = BridgeSerializationUtils.deserializeSha256Hash(encodedHash);
        Assertions.assertEquals(originalHash, result);
    }

    @Test
    void deserializeSha256Hash_nullValue() {
        Sha256Hash result = BridgeSerializationUtils.deserializeSha256Hash(null);
        assertNull(result);
    }

    @Test
    void deserializeSha256Hash_hashWithLeadingZero() {
        Sha256Hash originalHash = BitcoinTestUtils.createHash(0);
        byte[] encodedHash = RLP.encodeElement(originalHash.getBytes());

        Sha256Hash result = BridgeSerializationUtils.deserializeSha256Hash(encodedHash);
        Assertions.assertEquals(originalHash, result);
    }

    @Test
    void serializeScript() {
        Script expectedScript = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey()));

        byte[] actualData = BridgeSerializationUtils.serializeScript(expectedScript);

        Assertions.assertEquals(expectedScript, new Script(((RLPList) RLP.decode2(actualData).get(0)).get(0).getRLPData()));
    }

    @Test
    void deserializeScript() {
        Script expectedScript = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey()));
        byte[] data = RLP.encodeList(RLP.encodeElement(expectedScript.getProgram()));

        Script actualScript = BridgeSerializationUtils.deserializeScript(data);

        Assertions.assertEquals(expectedScript, actualScript);
    }

    @Test
    void deserializeFlyoverFederationInformation_no_data() {
        FlyoverFederationInformation result = BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            new byte[]{},
            new byte[]{}
        );

        assertNull(result);
    }

    @Test
    void deserializeFlyoverFederationInformation_null_data() {
        FlyoverFederationInformation result = BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            null,
            null
        );

        assertNull(result);
    }

    @Test
    void deserializeFlyoverFederationInformation_one_data() {
        byte[][] rlpElements = new byte[1][];
        rlpElements[0] = RLP.encodeElement(new byte[]{(byte)0x11});

        byte[] data = RLP.encodeList(rlpElements);
        assertThrows(RuntimeException.class, () -> BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            data,
            new byte[]{(byte)0x23}
        ));
    }

    @Test
    void deserializeFlyoverFederationInformation_ok() {
        byte[][] rlpElements = new byte[2][];
        rlpElements[0] = RLP.encodeElement(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000002").getBytes());
        rlpElements[1] = RLP.encodeElement(new byte[]{(byte)0x22});

        FlyoverFederationInformation result = BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            RLP.encodeList(rlpElements),
            new byte[]{(byte)0x23}
        );

        Assertions.assertNotNull(result);
        assertArrayEquals(
            Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000002").getBytes(),
            result.getDerivationHash().getBytes()
        );
        assertArrayEquals(new byte[]{(byte)0x22}, result.getFederationRedeemScriptHash());
        assertArrayEquals(new byte[]{(byte)0x23}, result.getFlyoverFederationRedeemScriptHash());
    }

    @Test
    void serializeFlyoverFederationInformation_no_data() {
        byte[] result = BridgeSerializationUtils.serializeFlyoverFederationInformation(null);

        Assertions.assertEquals(0, result.length);
    }

    @Test
    void serializeFlyoverFederationInformation_Ok() {
        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte)0x23};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            PegTestUtils.createHash3(2),
            new byte[]{(byte)0x22},
            flyoverFederationRedeemScriptHash
        );

        FlyoverFederationInformation result = BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation),
            flyoverFederationRedeemScriptHash
        );

        assertArrayEquals(
            flyoverFederationInformation.getDerivationHash().getBytes(),
            result.getDerivationHash().getBytes()
        );
        assertArrayEquals(
            flyoverFederationInformation.getFederationRedeemScriptHash(),
            result.getFederationRedeemScriptHash()
        );
        assertArrayEquals(
            flyoverFederationInformation.getFlyoverFederationRedeemScriptHash(),
            result.getFlyoverFederationRedeemScriptHash()
        );
    }

    @Test
    void deserializeCoinbaseInformation_dataIsNull_returnsNull() {
        assertNull(BridgeSerializationUtils.deserializeCoinbaseInformation(null));
    }

    @Test
    void deserializeCoinbaseInformation_dataContainsInvalidList_throwsRuntimeException() {
        byte[] firstItem = RLP.encodeElement(Hex.decode("010101"));
        byte[] secondItem = RLP.encodeElement(Hex.decode("010102"));
        byte[] thirdItem = RLP.encodeElement(Hex.decode("010103"));
        byte[] data = RLP.encodeList(firstItem, secondItem, thirdItem);

        try {
            BridgeSerializationUtils.deserializeCoinbaseInformation(data);
            fail("Runtime exception should be thrown!");
        } catch (RuntimeException e) {
            Assertions.assertEquals("Invalid serialized coinbase information, expected 1 value but got 3", e.getMessage());
        }
    }

    @Test
    void deserializeCoinbaseInformation_dataIsValid_returnsValidCoinbaseInformation() {
        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        Sha256Hash witnessRoot = MerkleTreeUtils.combineLeftRight(Sha256Hash.ZERO_HASH, secondHashTx);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessRoot);
        byte[] serializedCoinbaseInformation = BridgeSerializationUtils.serializeCoinbaseInformation(coinbaseInformation);

        Assertions.assertEquals(witnessRoot, BridgeSerializationUtils.deserializeCoinbaseInformation(serializedCoinbaseInformation).getWitnessMerkleRoot());
    }

    private void testSerializeAndDeserializeFederation(
        boolean isRskip284Active,
        boolean isRskip353Active,
        String networkId) {

        final int NUM_CASES = 20;

        BridgeConstants bridgeConstants;
        if (networkId.equals(NetworkParameters.ID_MAINNET)) {
            bridgeConstants = bridgeMainnetConstants;
        } else {
            bridgeConstants = bridgeTestnetConstants;
        }

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);
        when(activations.isActive(ConsensusRule.RSKIP353)).thenReturn(isRskip353Active);

        for (int i = 0; i < NUM_CASES; i++) {
            int numMembers = randomInRange(2, 14);
            List<FederationMember> members = new ArrayList<>();

            for (int j = 0; j < numMembers; j++) {
                members.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
            }

            Instant creationTime = Instant.now();
            long creationBlockNumber = 123;
            NetworkParameters btcParams = bridgeConstants.getBtcParams();

            FederationArgs federationArgs =
                new FederationArgs(members, creationTime, creationBlockNumber, btcParams);
            Federation testStandardMultisigFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);
            byte[] serializedTestStandardMultisigFederation = BridgeSerializationUtils.serializeFederation(testStandardMultisigFederation);

            Federation deserializedTestStandardMultisigFederation =
                BridgeSerializationUtils.deserializeStandardMultisigFederation(serializedTestStandardMultisigFederation, bridgeConstants.getBtcParams());
            FederationArgs deserializedTestStandardMultisigFederationArgs = deserializedTestStandardMultisigFederation.getArgs();

            FederationConstants federationConstants = bridgeConstants.getFederationConstants();

            List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
            long activationDelay = federationConstants.getErpFedActivationDelay();
            Federation testNonStandardErpFederation = FederationFactory.buildNonStandardErpFederation(deserializedTestStandardMultisigFederationArgs,
                erpPubKeys, activationDelay, activations);
            byte[] serializedTestNonStandardErpFederation = BridgeSerializationUtils.serializeFederation(testNonStandardErpFederation);

            ErpFederation deserializedTestNonStandardErpFederation = BridgeSerializationUtils.deserializeNonStandardErpFederation(
                serializedTestNonStandardErpFederation,
                federationConstants,
                activations
            );

            Assertions.assertEquals(testStandardMultisigFederation, deserializedTestStandardMultisigFederation);
            Assertions.assertEquals(testNonStandardErpFederation, deserializedTestNonStandardErpFederation);
            assertNotEquals(testStandardMultisigFederation, deserializedTestNonStandardErpFederation);
            assertNotEquals(testNonStandardErpFederation, deserializedTestStandardMultisigFederation);

            if (!isRskip284Active && networkId.equals(NetworkParameters.ID_TESTNET)) {
                Assertions.assertEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, testNonStandardErpFederation.getRedeemScript());
            }

            if (isRskip353Active) {
                Federation testP2shErpFederation = FederationFactory.buildP2shErpFederation(deserializedTestStandardMultisigFederationArgs,
                    erpPubKeys, activationDelay);
                byte[] serializedTestP2shErpFederation = BridgeSerializationUtils.serializeFederation(testP2shErpFederation);

                Federation deserializedTestP2shErpFederation = BridgeSerializationUtils.deserializeP2shErpFederation(
                    serializedTestP2shErpFederation,
                    federationConstants
                );

                assertEquals(testP2shErpFederation, deserializedTestP2shErpFederation);
                assertNotEquals(testStandardMultisigFederation, deserializedTestP2shErpFederation);
                assertNotEquals(testNonStandardErpFederation, deserializedTestP2shErpFederation);
            }
        }
    }

    private Address mockAddressHash160(String hash160) {
        Address result = mock(Address.class);
        when(result.getHash160()).thenReturn(Hex.decode(hash160));
        return result;
    }

    private BtcTransaction mockBtcTransactionSerialize(String serialized) {
        BtcTransaction result = mock(BtcTransaction.class);
        when(result.bitcoinSerialize()).thenReturn(Hex.decode(serialized));
        return result;
    }

    private String charNTimes(char c, int n) {
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {
            sb.append(c);
        }

        return sb.toString();
    }

    private RskAddress createAddress(String addr) {
        String address;

        if (addr.length() < RskAddress.LENGTH_IN_BYTES * 2) {
            address = addr + charNTimes('0', RskAddress.LENGTH_IN_BYTES * 2 - addr.length());
        }
        else {
            address = addr;
        }

        return new RskAddress(Hex.decode(address));
    }

    private int randomInRange(int min, int max) {
        return TestUtils.generateInt(BridgeSerializationUtilsTest.class.toString(),max - min + 1) + min;
    }

    private static AddressBasedAuthorizer getTestingAddressBasedAuthorizer() {
        return new AddressBasedAuthorizer(Collections.EMPTY_LIST, null) {
            @Override
            public boolean isAuthorized(RskAddress address) {
                return true;
            }
        };
    }
}
