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

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import co.rsk.peg.simples.SimpleRskTransaction;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.Keccak256Helper;

/**
 * Created by oscar on 05/08/2016.
 */
public class PegTestUtils {

    private static int nhash = 0;

    /**
     * @deprecated Use createHash3(int) instead. Avoid using persisted state in static class in test environments
     */
    @Deprecated
    public static Keccak256 createHash3() {
        return createHash3(nhash++);
    }

    public static Keccak256 createHash3(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (nHash & 0xFF);
        bytes[1] = (byte) (nHash >>8 & 0xFF);
        return new Keccak256(bytes);
    }

    /**
     * @deprecated Use createHash(int) instead. Avoid using persisted state in static class in test environments
     */
    @Deprecated
    public static Sha256Hash createHash() {
        return createHash(nhash++);
    }

    public static Sha256Hash createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (0xFF & nHash);
        bytes[1] = (byte) (0xFF & nHash >> 8);
        bytes[2] = (byte) (0xFF & nHash >> 16);
        bytes[3] = (byte) (0xFF & nHash >> 24);

        return Sha256Hash.wrap(bytes);
    }

    public static Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);
        Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        return inputScript;
    }

    public static Script createBaseRedeemScriptThatSpendsFromTheFederation(Federation federation) {
        Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());
        return redeemScript;
    }

    public static Script createOpReturnScriptForRsk(
        int protocolVersion,
        RskAddress rskDestinationAddress,
        Optional<Address> btcRefundAddressOptional
    ) {
        int index = 0;
        int payloadLength;
        if (btcRefundAddressOptional.isPresent()) {
            payloadLength = 46;
        } else {
            payloadLength = 25;
        }
        byte[] payloadBytes = new byte[payloadLength];

        byte[] prefix = Hex.decode("52534b54"); // 'RSKT' in hexa
        System.arraycopy(prefix, 0, payloadBytes, index, prefix.length);
        index += prefix.length;

        payloadBytes[index] = (byte) protocolVersion;
        index++;

        System.arraycopy(
            rskDestinationAddress.getBytes(),
            0,
            payloadBytes,
            index,
            rskDestinationAddress.getBytes().length
        );
        index += rskDestinationAddress.getBytes().length;

        if (btcRefundAddressOptional.isPresent()) {
            Address btcRefundAddress = btcRefundAddressOptional.get();
            if (btcRefundAddress.isP2SHAddress()) {
                payloadBytes[index] = 2; // P2SH address type
            } else {
                payloadBytes[index] = 1; // P2PKH address type
            }
            index++;

            System.arraycopy(
                btcRefundAddress.getHash160(),
                0,
                payloadBytes,
                index,
                btcRefundAddress.getHash160().length
            );
        }

        return ScriptBuilder.createOpReturnScript(payloadBytes);
    }

    public static Script createOpReturnScriptForRskWithCustomPayload(int protocolVersion, byte[] customPayload) {
        int index = 0;
        int payloadLength = customPayload.length;

        byte[] payloadBytes = new byte[payloadLength + 5]; // Add 4 bytes for the prefix, and another for the protocol version

        byte[] prefix = Hex.decode("52534b54"); // 'RSKT' in hexa
        System.arraycopy(prefix, 0, payloadBytes, index, prefix.length);
        index += prefix.length;

        payloadBytes[index] = (byte) protocolVersion;
        index++;

        System.arraycopy(customPayload, 0, payloadBytes, index, customPayload.length);

        return ScriptBuilder.createOpReturnScript(payloadBytes);
    }

    public static Address createRandomP2PKHBtcAddress() {
        BtcECKey key = new BtcECKey();
        return key.toAddress(RegTestParams.get());
    }

    public static Address createRandomP2SHMultisigAddress(NetworkParameters networkParameters, int keysCount) {
        List<BtcECKey> keys = createRandomBtcECKeys(keysCount);
        Script redeemScript = ScriptBuilder.createRedeemScript((keys.size() / 2) + 1, keys);
        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);

        return Address.fromP2SHScript(networkParameters, outputScript);
    }

    public static List<BtcECKey> createRandomBtcECKeys(int keysCount) {
        List<BtcECKey> keys = new ArrayList<>();
        for (int i = 0; i < keysCount; i++) {
            keys.add(new BtcECKey());
        }
        return keys;
    }

    public static UTXO createUTXO(int nHash, long index, Coin value) {
        return createUTXO(nHash, index, value, createRandomP2PKHBtcAddress());
    }

    public static UTXO createUTXO(int nHash, long index, Coin value, Address address) {
        return new UTXO(
            createHash(nHash),
            index,
            value,
            10,
            false,
            ScriptBuilder.createOutputScript(address));
    }

    public static List<UTXO> createUTXOs(int amount, Address address) {
        List<UTXO> utxos = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            utxos.add(createUTXO(i + 1, 0, Coin.COIN, address));
        }

        return utxos;
    }

    public static List<ReleaseRequestQueue.Entry> createReleaseRequestQueueEntries(int amount) {
        List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            ReleaseRequestQueue.Entry entry = new ReleaseRequestQueue.Entry(
                createRandomP2PKHBtcAddress(),
                Coin.COIN.add(Coin.valueOf(i))
            );
            entries.add(entry);
        }

        return entries;
    }

    public static Address getFastBridgeAddressFromRedeemScript(BridgeConstants bridgeConstants, Script redeemScript, Sha256Hash derivationArgumentHash) {
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            redeemScript,
            derivationArgumentHash
        );
        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
        return Address.fromP2SHScript(bridgeConstants.getBtcParams(), fastBridgeP2SH);
    }

    public static Federation createSimpleActiveFederation(BridgeConstants bridgeConstants) {
        return createFederation(bridgeConstants, "fa01", "fa02");
    }

    public static Federation createSimpleRetiringFederation(BridgeConstants bridgeConstants) {
        return createFederation(bridgeConstants, "fa03", "fa04");
    }

    public static Federation createFederation(BridgeConstants bridgeConstants, String... fedKeys) {
        List<BtcECKey> federationKeys = Arrays.stream(fedKeys).map(s -> BtcECKey.fromPrivate(Hex.decode(s))).collect(Collectors.toList());
        return createFederation(bridgeConstants, federationKeys);
    }

    public static Federation createFederation(BridgeConstants bridgeConstants, List<BtcECKey> federationKeys) {
        federationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        return new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            bridgeConstants.getBtcParams()
        );
    }

    public static BtcTransaction createBtcTransactionWithOutputToAddress(NetworkParameters networkParameters, Coin amount, Address btcAddress) {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(amount, btcAddress);
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1),
            0, ScriptBuilder.createInputScript(null, srcKey));
        return tx;
    }

    public static Transaction getMockedRskTxWithHash(String s) {
        byte[] hash = Keccak256Helper.keccak256(s);
        return new SimpleRskTransaction(hash);
    }

    public static TransactionOutput createBech32Output(NetworkParameters networkParameters, Coin valuesToSend) {
        byte[] scriptBytes = networkParameters.getId().equals(NetworkParameters.ID_MAINNET)?
                                 Hex.decode("001437c383ea78269585c73289daa36d7b7014b65294") :
                                 Hex.decode("0014ef57424d0d625cf82fabe4fd7657d24a5f13dfb2");
        return new TransactionOutput(networkParameters, null,
            valuesToSend,
            scriptBytes
        );
    }

    public static TransactionOutput createP2pkhOutput(NetworkParameters networkParameters, Coin valuesToSend) {
        Address address = networkParameters.getId().equals(NetworkParameters.ID_MAINNET)?
                              Address.fromBase58(networkParameters, "1JMaBRALrJQArLrqe5zRSn3bVk1z9RGzML") :
                              Address.fromBase58(networkParameters, "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn");
        return new TransactionOutput(networkParameters, null,
            valuesToSend,
            address
        );
    }

    public static TransactionOutput createP2shOutput(NetworkParameters networkParameters, Coin valuesToSend) {
        Address address = networkParameters.getId().equals(NetworkParameters.ID_MAINNET)?
                              Address.fromBase58(networkParameters, "3FiMaJinRy86WgJZDfAfRUt2ZLEBGZSfLL") :
                              Address.fromBase58(networkParameters, "2N7TdMZSYfwFMUwyr8YADSKUhLUyH6eqohz");
        return new TransactionOutput(networkParameters, null,
            valuesToSend,
            address
        );
    }
}
