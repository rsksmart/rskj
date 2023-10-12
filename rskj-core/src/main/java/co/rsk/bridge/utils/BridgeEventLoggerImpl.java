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
package co.rsk.bridge.utils;

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.bridge.BridgeEvents;
import co.rsk.bridge.Federation;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Responsible for logging events triggered by BridgeContract.
 *
 * @author martin.medina
 */
public class BridgeEventLoggerImpl implements BridgeEventLogger {

    private static final byte[] BRIDGE_CONTRACT_ADDRESS = PrecompiledContracts.BRIDGE_ADDR.getBytes();

    private final BridgeConstants bridgeConstants;

    private final SignatureCache signatureCache;

    private List<LogInfo> logs;

    private ActivationConfig.ForBlock activations;

    public BridgeEventLoggerImpl(BridgeConstants bridgeConstants, ActivationConfig.ForBlock activations, List<LogInfo> logs, SignatureCache signatureCache) {
        this.activations = activations;
        this.bridgeConstants = bridgeConstants;
        this.signatureCache = signatureCache;
        this.logs = logs;
    }

    @Override
    public void logUpdateCollections(Transaction rskTx) {
        CallTransaction.Function event = BridgeEvents.UPDATE_COLLECTIONS.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics();
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(rskTx.getSender(signatureCache).toString());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logAddSignature(BtcECKey federatorPublicKey, BtcTransaction btcTx, byte[] rskTxHash) {
        ECKey key = ECKey.fromPublicOnly(federatorPublicKey.getPubKey());
        String federatorRskAddress = ByteUtil.toHexString(key.getAddress());
        logAddSignatureInSolidityFormat(rskTxHash, federatorRskAddress, federatorPublicKey);
    }

    private void logAddSignatureInSolidityFormat(byte[] rskTxHash, String federatorRskAddress, BtcECKey federatorPublicKey) {
        CallTransaction.Function event = BridgeEvents.ADD_SIGNATURE.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(rskTxHash, federatorRskAddress);
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(federatorPublicKey.getPubKey());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logReleaseBtc(BtcTransaction btcTx, byte[] rskTxHash) {
        CallTransaction.Function event = BridgeEvents.RELEASE_BTC.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(rskTxHash);
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(btcTx.bitcoinSerialize());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logCommitFederation(Block executionBlock, Federation oldFederation, Federation newFederation) {
        // Convert old federation public keys in bytes array
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();
        long newFedActivationBlockNumber = executionBlock.getNumber() + this.bridgeConstants.getFederationActivationAge(activations);

        CallTransaction.Function event = BridgeEvents.COMMIT_FEDERATION.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics();
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(
            oldFederationFlatPubKeys,
            oldFederationBtcAddress,
            newFederationFlatPubKeys,
            newFederationBtcAddress,
            newFedActivationBlockNumber
        );

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logLockBtc(RskAddress receiver, BtcTransaction btcTx, Address senderBtcAddress, Coin amount) {
        CallTransaction.Function event = BridgeEvents.LOCK_BTC.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(receiver.toString());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(btcTx.getHash().getBytes(), senderBtcAddress.toString(), amount.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logPeginBtc(RskAddress receiver, BtcTransaction btcTx, Coin amount, int protocolVersion) {
        CallTransaction.Function event = BridgeEvents.PEGIN_BTC.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(receiver.toString(), btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(amount.getValue(), protocolVersion);

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logReleaseBtcRequested(byte[] rskTransactionHash, BtcTransaction btcTx, Coin amount) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUESTED.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(rskTransactionHash, btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(amount.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logRejectedPegin(BtcTransaction btcTx, RejectedPeginReason reason) {
        CallTransaction.Function event = BridgeEvents.REJECTED_PEGIN.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(reason.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logUnrefundablePegin(BtcTransaction btcTx, UnrefundablePeginReason reason) {
        CallTransaction.Function event = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(reason.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logReleaseBtcRequestReceived(String sender, Address btcDestinationAddress, Coin amount) {
        if (activations.isActive(ConsensusRule.RSKIP326)) {
            logReleaseBtcRequestReceived(sender, btcDestinationAddress.toString(), amount);
        } else {
            logReleaseBtcRequestReceived(sender, btcDestinationAddress.getHash160(), amount);
        }
    }

    private void logReleaseBtcRequestReceived(String sender, byte[] btcDestinationAddress, Coin amount) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(sender);
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(btcDestinationAddress, amount.getValue());
        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    private void logReleaseBtcRequestReceived(String sender, String btcDestinationAddress, Coin amount) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(sender);
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(btcDestinationAddress, amount.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logReleaseBtcRequestRejected(String sender, Coin amount, RejectedPegoutReason reason) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(sender);
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(amount.getValue(), reason.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logBatchPegoutCreated(Sha256Hash btcTxHash, List<Keccak256> rskTxHashes) {
        CallTransaction.Function event = BridgeEvents.BATCH_PEGOUT_CREATED.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(btcTxHash.getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] serializedRskTxHashes = serializeRskTxHashes(rskTxHashes);
        byte[] encodedData = event.encodeEventData(serializedRskTxHashes);

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logPegoutConfirmed(Sha256Hash btcTxHash, long pegoutCreationRskBlockNumber) {
        CallTransaction.Function event = BridgeEvents.PEGOUT_CONFIRMED.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(btcTxHash.getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(pegoutCreationRskBlockNumber);
        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    private byte[] flatKeys(List<BtcECKey> keys, Function<BtcECKey, byte[]> parser) {
        List<byte[]> pubKeys = keys.stream()
                .map(parser)
                .collect(Collectors.toList());
        int pubKeysLength = pubKeys.stream().mapToInt(key -> key.length).sum();

        byte[] flatPubKeys = new byte[pubKeysLength];
        int copyPos = 0;
        for (byte[] key : pubKeys) {
            System.arraycopy(key, 0, flatPubKeys, copyPos, key.length);
            copyPos += key.length;
        }

        return flatPubKeys;
    }

    private byte[] flatKeysAsByteArray(List<BtcECKey> keys) {
        return flatKeys(keys, BtcECKey::getPubKey);
    }

    private byte[] serializeRskTxHashes(List<Keccak256> rskTxHashes) {
        List<byte[]> rskTxHashesList = rskTxHashes.stream()
            .map(Keccak256::getBytes)
            .collect(Collectors.toList());
        int rskTxHashesLength = rskTxHashesList.stream().mapToInt(key -> key.length).sum();

        byte[] serializedRskTxHashes = new byte[rskTxHashesLength];
        int copyPos = 0;
        for (byte[] rskTxHash : rskTxHashesList) {
            System.arraycopy(rskTxHash, 0, serializedRskTxHashes, copyPos, rskTxHash.length);
            copyPos += rskTxHash.length;
        }

        return serializedRskTxHashes;
    }
}
