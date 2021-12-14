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

package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.Federation;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

import java.util.Collections;
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
    private final ActivationConfig.ForBlock activations;

    private List<LogInfo> logs;

    public BridgeEventLoggerImpl(BridgeConstants bridgeConstants, ActivationConfig.ForBlock activations, List<LogInfo> logs) {
        this.bridgeConstants = bridgeConstants;
        this.activations = activations;
        this.logs = logs;
    }

    public void logUpdateCollections(Transaction rskTx) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            logUpdateCollectionsInSolidityFormat(rskTx);
        } else {
            logUpdateCollectionsInRLPFormat(rskTx);
        }
    }

    private void logUpdateCollectionsInSolidityFormat(Transaction rskTx) {
        CallTransaction.Function event = BridgeEvents.UPDATE_COLLECTIONS.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics();
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(rskTx.getSender().toString());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    private void logUpdateCollectionsInRLPFormat(Transaction rskTx) {
        this.logs.add(
                new LogInfo(BRIDGE_CONTRACT_ADDRESS,
                        Collections.singletonList(Bridge.UPDATE_COLLECTIONS_TOPIC),
                        RLP.encodeElement(rskTx.getSender().getBytes())
                )
        );
    }

    public void logAddSignature(BtcECKey federatorPublicKey, BtcTransaction btcTx, byte[] rskTxHash) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            ECKey key = ECKey.fromPublicOnly(federatorPublicKey.getPubKey());
            String federatorRskAddress = ByteUtil.toHexString(key.getAddress());
            logAddSignatureInSolidityFormat(rskTxHash, federatorRskAddress, federatorPublicKey);
        } else {
            logAddSignatureInRLPFormat(federatorPublicKey, btcTx, rskTxHash);
        }
    }

    private void logAddSignatureInSolidityFormat(byte[] rskTxHash, String federatorRskAddress, BtcECKey federatorPublicKey) {
        CallTransaction.Function event = BridgeEvents.ADD_SIGNATURE.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(rskTxHash, federatorRskAddress);
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(federatorPublicKey.getPubKey());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    private void logAddSignatureInRLPFormat(BtcECKey federatorPublicKey, BtcTransaction btcTx, byte[] rskTxHash) {
        List<DataWord> topics = Collections.singletonList(Bridge.ADD_SIGNATURE_TOPIC);
        byte[] data = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()),
                RLP.encodeElement(federatorPublicKey.getPubKeyHash()),
                RLP.encodeElement(rskTxHash));

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, topics, data));
    }

    public void logReleaseBtc(BtcTransaction btcTx, byte[] rskTxHash) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            logReleaseBtcInSolidityFormat(btcTx, rskTxHash);
        } else {
            logReleaseBtcInRLPFormat(btcTx);
        }
    }

    private void logReleaseBtcInSolidityFormat(BtcTransaction btcTx, byte[] rskTxHash) {
        CallTransaction.Function event = BridgeEvents.RELEASE_BTC.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(rskTxHash);
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(btcTx.bitcoinSerialize());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    private void logReleaseBtcInRLPFormat(BtcTransaction btcTx) {
        List<DataWord> topics = Collections.singletonList(Bridge.RELEASE_BTC_TOPIC);
        byte[] data = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()), RLP.encodeElement(btcTx.bitcoinSerialize()));

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, topics, data));
    }

    public void logCommitFederation(Block executionBlock, Federation oldFederation, Federation newFederation) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            logCommitFederationInSolidityFormat(executionBlock, oldFederation, newFederation);
        } else {
            logCommitFederationInRLPFormat(executionBlock, oldFederation, newFederation);
        }
    }

    private void logCommitFederationInRLPFormat(Block executionBlock, Federation oldFederation, Federation newFederation) {
        List<DataWord> topics = Collections.singletonList(Bridge.COMMIT_FEDERATION_TOPIC);

        byte[] oldFedFlatPubKeys = flatKeysAsRlpCollection(oldFederation.getBtcPublicKeys());
        byte[] oldFedData = RLP.encodeList(RLP.encodeElement(oldFederation.getAddress().getHash160()), RLP.encodeList(oldFedFlatPubKeys));

        byte[] newFedFlatPubKeys = flatKeysAsRlpCollection(newFederation.getBtcPublicKeys());
        byte[] newFedData = RLP.encodeList(RLP.encodeElement(newFederation.getAddress().getHash160()), RLP.encodeList(newFedFlatPubKeys));

        long newFedActivationBlockNumber = executionBlock.getNumber() + this.bridgeConstants.getFederationActivationAge();

        byte[] data = RLP.encodeList(oldFedData, newFedData, RLP.encodeString(Long.toString(newFedActivationBlockNumber)));

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, topics, data));
    }

    private void logCommitFederationInSolidityFormat(Block executionBlock, Federation oldFederation, Federation newFederation) {
        // Convert old federation public keys in bytes array
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();
        long newFedActivationBlockNumber = executionBlock.getNumber() + this.bridgeConstants.getFederationActivationAge();

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

    public void logLockBtc(RskAddress receiver, BtcTransaction btcTx, Address senderBtcAddress, Coin amount) {
        CallTransaction.Function event = BridgeEvents.LOCK_BTC.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(receiver.toString());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(btcTx.getHash().getBytes(), senderBtcAddress.toString(), amount.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    public void logPeginBtc(RskAddress receiver, BtcTransaction btcTx, Coin amount, int protocolVersion) {
        CallTransaction.Function event = BridgeEvents.PEGIN_BTC.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(receiver.toString(), btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(amount.getValue(), protocolVersion);

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    public void logReleaseBtcRequested(byte[] rskTransactionHash, BtcTransaction btcTx, Coin amount) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUESTED.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(rskTransactionHash, btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(amount.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    public void logRejectedPegin(BtcTransaction btcTx, RejectedPeginReason reason) {
        CallTransaction.Function event = BridgeEvents.REJECTED_PEGIN.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(reason.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    public void logUnrefundablePegin(BtcTransaction btcTx, UnrefundablePeginReason reason) {
        CallTransaction.Function event = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);

        byte[] encodedData = event.encodeEventData(reason.getValue());

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, encodedTopics, encodedData));
    }

    @Override
    public void logReleaseBtcRequestReceived(String sender, byte[] btcDestinationAddress, Coin amount) {
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
    public void logBatchPegoutCreated(BtcTransaction btcTx, byte[] rskTxHash) {
        CallTransaction.Function event = BridgeEvents.BATCH_PEGOUT_CREATED.getEvent();
        byte[][] encodedTopicsInBytes = event.encodeEventTopics(btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsInBytes);
        byte[] encodedData = event.encodeEventData(rskTxHash);

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

    private byte[] flatKeysAsRlpCollection(List<BtcECKey> keys) {
        return flatKeys(keys, (k -> RLP.encodeElement(k.getPubKey())));
    }

    private byte[] flatKeysAsByteArray(List<BtcECKey> keys) {
        return flatKeys(keys, BtcECKey::getPubKey);
    }
}
