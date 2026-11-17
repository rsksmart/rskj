package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.utils.NonRefundablePeginReason;
import co.rsk.peg.utils.RejectedPegoutReason;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.ethereum.core.CallTransaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;

public class BridgeEventsTestUtils {

    public static List<DataWord> getEncodedTopics(CallTransaction.Function bridgeEvent, Object... args) {
        byte[][] encodedTopicsInBytes = bridgeEvent.encodeEventTopics(args);
        return LogInfo.byteArrayToList(encodedTopicsInBytes);
    }

    public static byte[] getEncodedData(CallTransaction.Function bridgeEvent, Object... args) {
        return bridgeEvent.encodeEventData(args);
    }

    public static Optional<LogInfo> getLogsTopics(List<LogInfo> logs, List<DataWord> expectedTopics) {
        return logs.stream()
            .filter(log -> log.getTopics().equals(expectedTopics))
            .findFirst();
    }

    public static Optional<LogInfo> getLogsData(List<LogInfo> logs, byte[] expectedData) {
        return logs.stream()
            .filter(log -> Arrays.equals(log.getData(), expectedData))
            .findFirst();
    }

    public static List<LogInfo> getLogsBySignature(List<LogInfo> logs, CallTransaction.Function bridgeEvent) {
        DataWord signatureTopic = DataWord.valueOf(bridgeEvent.encodeSignatureLong());
        return logs.stream()
            .filter(log -> !log.getTopics().isEmpty() && log.getTopics().get(0).equals(signatureTopic))
            .toList();
    }

    public static void assertLogReleaseRequestReceived(
        List<LogInfo> logs,
        RskAddress sender,
        Address btcDestinationAddress,
        co.rsk.core.Coin amount
    ) {
        CallTransaction.Function releaseRequestReceivedEvent = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(releaseRequestReceivedEvent, sender.toHexString());

        byte[] encodedData = getEncodedData(releaseRequestReceivedEvent, btcDestinationAddress.toString(), amount.asBigInteger());

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogReleaseRequestRejected(
        List<LogInfo> logs,
        RskAddress sender,
        co.rsk.core.Coin amount,
        RejectedPegoutReason reason
    ) {
        CallTransaction.Function releaseRequestRejectedEvent = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(releaseRequestRejectedEvent, sender.toHexString());

        byte[] encodedData = getEncodedData(releaseRequestRejectedEvent, amount.asBigInteger(), reason.getValue());

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogReleaseRequested(List<LogInfo> logs, Keccak256 releaseCreationTxHash, Sha256Hash pegoutTransactionHash, Coin requestedAmount) {
        CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();

        byte[] releaseCreationTxHashSerialized = releaseCreationTxHash.getBytes();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(releaseRequestedEvent, releaseCreationTxHashSerialized, pegoutTransactionHashSerialized);

        byte[] encodedData = getEncodedData(releaseRequestedEvent, requestedAmount.getValue());

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogPegoutTransactionCreated(List<LogInfo> logs, BtcTransaction releaseTransaction, List<Coin> expectedOutpointsValues) {
        CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
        Sha256Hash pegoutTransactionHash = releaseTransaction.getHash();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(pegoutTransactionCreatedEvent, pegoutTransactionHashSerialized);

        byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(expectedOutpointsValues);
        byte[] encodedData = getEncodedData(pegoutTransactionCreatedEvent, serializedOutpointValues);

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogUtxosRegistered(
        List<LogInfo> logs,
        Sha256Hash btcTxHash,
        List<Coin> expectedOutputValuesInSatoshis,
        List<Long> expectedOutputIndexes,
        Address expectedFederationAddress
    ) {
        CallTransaction.Function utxosRegisteredEvent = BridgeEvents.UTXOS_REGISTERED.getEvent();
        byte[] btcTxHashSerialized = btcTxHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(utxosRegisteredEvent, btcTxHashSerialized);

        byte[] serializedOutputValuesInSatoshis = UtxoUtils.encodeOutpointValues(expectedOutputValuesInSatoshis);
        byte[] serializedOutputIndexes = UtxoUtils.encodeOutputIndexes(expectedOutputIndexes);
        byte[] encodedData = getEncodedData(
            utxosRegisteredEvent,
            serializedOutputValuesInSatoshis,
            serializedOutputIndexes,
            expectedFederationAddress.toString()
        );

        // topics and data must match on the same log; checked separately, they could
        // come from different utxos_registered logs, since equal registrations share data
        List<LogInfo> matchingLogs = logs.stream()
            .filter(log -> log.getTopics().equals(encodedTopics))
            .filter(log -> Arrays.equals(log.getData(), encodedData))
            .toList();
        assertEquals(1, matchingLogs.size());
    }

    public static void assertLogFlyoverUtxosRegistered(
        List<LogInfo> logs,
        Sha256Hash btcTxHash,
        List<Coin> expectedOutputValuesInSatoshis,
        List<Long> expectedOutputIndexes,
        Address expectedFederationAddress,
        Keccak256 expectedFlyoverDerivationHash
    ) {
        CallTransaction.Function flyoverUtxosRegisteredEvent = BridgeEvents.FLYOVER_UTXOS_REGISTERED.getEvent();
        byte[] btcTxHashSerialized = btcTxHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(flyoverUtxosRegisteredEvent, btcTxHashSerialized);

        byte[] serializedOutputValuesInSatoshis = UtxoUtils.encodeOutpointValues(expectedOutputValuesInSatoshis);
        byte[] serializedOutputIndexes = UtxoUtils.encodeOutputIndexes(expectedOutputIndexes);
        byte[] encodedData = getEncodedData(
            flyoverUtxosRegisteredEvent,
            serializedOutputValuesInSatoshis,
            serializedOutputIndexes,
            expectedFederationAddress.toString(),
            expectedFlyoverDerivationHash.getBytes()
        );

        // topics and data must match on the same log; checked separately, they could
        // come from different flyover_utxos_registered logs, since equal registrations share data
        List<LogInfo> matchingLogs = logs.stream()
            .filter(log -> log.getTopics().equals(encodedTopics))
            .filter(log -> Arrays.equals(log.getData(), encodedData))
            .toList();
        assertEquals(1, matchingLogs.size());
    }

    public static void assertLogAddSignature(List<LogInfo> logs, byte[] rskTxHash, String federatorRskAddress, BtcECKey federatorBtcPublicKey) {
        CallTransaction.Function addSignatureEvent = BridgeEvents.ADD_SIGNATURE.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(addSignatureEvent, rskTxHash, federatorRskAddress);

        byte[] encodedData = getEncodedData(addSignatureEvent, federatorBtcPublicKey.getPubKey());

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogReleaseBtc(List<LogInfo> logs, BtcTransaction btcTx, Keccak256 rskTxHash) {
        CallTransaction.Function releaseBtcEvent = BridgeEvents.RELEASE_BTC.getEvent();
        byte[] rskTxHashSerialized = rskTxHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(releaseBtcEvent, rskTxHashSerialized);

        byte[] btcTxSerialized = btcTx.bitcoinSerialize();
        byte[] encodedData = getEncodedData(releaseBtcEvent, btcTxSerialized);

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogRejectedPegin(List<LogInfo> logs, BtcTransaction btcTx, RejectedPeginReason reason) {
        CallTransaction.Function rejectedPeginEvent = BridgeEvents.REJECTED_PEGIN.getEvent();

        byte[] btcTxHashSerialized = btcTx.getHash().getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(rejectedPeginEvent, btcTxHashSerialized);

        int reasonValue = reason.getValue();
        byte[] encodedData = getEncodedData(rejectedPeginEvent, reasonValue);

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogUnrefundablePegin(List<LogInfo> logs, BtcTransaction pegin, NonRefundablePeginReason reason) {
        CallTransaction.Function unrefundablePeginEvent = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();
        byte[] btcTxHashSerialized = pegin.getHash().getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(unrefundablePeginEvent, btcTxHashSerialized);

        int reasonValue = reason.getValue();
        byte[] encodedData = getEncodedData(unrefundablePeginEvent, reasonValue);

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogPegoutConfirmed(List<LogInfo> logs, Sha256Hash btcTxHash, long pegoutCreationRskBlockNumber) {
        CallTransaction.Function pegoutConfirmedEvent = BridgeEvents.PEGOUT_CONFIRMED.getEvent();

        byte[] btcTxHashSerialized = btcTxHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(pegoutConfirmedEvent, btcTxHashSerialized);

        byte[] encodedData = getEncodedData(pegoutConfirmedEvent, pegoutCreationRskBlockNumber);

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogCommitFederation(
        List<LogInfo> logs,
        Federation oldFederation,
        Federation newFederation,
        long expectedActivationBlockNumber
    ) {
        CallTransaction.Function commitFederationEvent = BridgeEvents.COMMIT_FEDERATION.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(commitFederationEvent);

        byte[] oldFederationFlatPubKeys = BitcoinTestUtils.flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = BitcoinTestUtils.flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();

        byte[] encodedData = getEncodedData(
            commitFederationEvent,
            oldFederationFlatPubKeys,
            oldFederationBtcAddress,
            newFederationFlatPubKeys,
            newFederationBtcAddress,
            expectedActivationBlockNumber
        );

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogCommitFederationFailed(List<LogInfo> logs, Federation proposedFederation, long expectedBlockNumber) {
        CallTransaction.Function commitFederationFailedEvent = BridgeEvents.COMMIT_FEDERATION_FAILED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(commitFederationFailedEvent);

        byte[] proposedFederationRedeemScriptSerialized = proposedFederation.getRedeemScript().getProgram();
        byte[] encodedData = getEncodedData(commitFederationFailedEvent, proposedFederationRedeemScriptSerialized, expectedBlockNumber);

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogPeginBtc(
        List<LogInfo> logs,
        BtcTransaction peginTransaction,
        RskAddress expectedReceiver,
        Coin expectedAmount,
        int expectedProtocolVersion
    ) {
        CallTransaction.Function peginBtcEvent = BridgeEvents.PEGIN_BTC.getEvent();

        byte[] btcTxHashSerialized = peginTransaction.getHash().getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(peginBtcEvent, expectedReceiver.toString(), btcTxHashSerialized);

        byte[] encodedData = getEncodedData(peginBtcEvent, expectedAmount.getValue(), expectedProtocolVersion);

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogUpdateCollections(List<LogInfo> logs, RskAddress sender) {
        CallTransaction.Function updateCollectionsEvent = BridgeEvents.UPDATE_COLLECTIONS.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(updateCollectionsEvent);

        byte[] encodedData = getEncodedData(updateCollectionsEvent, sender.toHexString());

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogUnionBridgeTransferPermissionsUpdated(List<LogInfo> logs, RskAddress caller, boolean requestEnabled, boolean releaseEnabled) {
        CallTransaction.Function transferPermissionsEvent = BridgeEvents.UNION_BRIDGE_TRANSFER_PERMISSIONS_UPDATED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(transferPermissionsEvent, caller.toHexString());

        byte[] encodedData = getEncodedData(transferPermissionsEvent, requestEnabled, releaseEnabled);

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogUnionLockingCapIncreased(
        List<LogInfo> logs,
        RskAddress caller,
        co.rsk.core.Coin previousLockingCap,
        co.rsk.core.Coin newLockingCap
    ) {
        CallTransaction.Function unionLockingCapIncreasedEvent = BridgeEvents.UNION_LOCKING_CAP_INCREASED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(unionLockingCapIncreasedEvent, caller.toHexString());

        byte[] encodedData = getEncodedData(unionLockingCapIncreasedEvent, previousLockingCap.asBigInteger(), newLockingCap.asBigInteger());

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogUnionRbtcRequested(List<LogInfo> logs, RskAddress unionBridgeAddress, co.rsk.core.Coin amountRequested) {
        CallTransaction.Function unionRbtcRequestedEvent = BridgeEvents.UNION_RBTC_REQUESTED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(unionRbtcRequestedEvent, unionBridgeAddress.toHexString());

        byte[] encodedData = getEncodedData(unionRbtcRequestedEvent, amountRequested.asBigInteger());

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    public static void assertLogUnionRbtcReleased(List<LogInfo> logs, RskAddress unionBridgeAddress, co.rsk.core.Coin amountReleased) {
        CallTransaction.Function unionRbtcReleasedEvent = BridgeEvents.UNION_RBTC_RELEASED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(unionRbtcReleasedEvent, unionBridgeAddress.toHexString());

        byte[] encodedData = getEncodedData(unionRbtcReleasedEvent, amountReleased.asBigInteger());

        assertEventWasEmitted(logs, encodedTopics, encodedData);
    }

    private static void assertEventWasEmitted(List<LogInfo> logs, List<DataWord> expectedTopics, byte[] expectedData) {
        // topics and data have to match on the same log entry
        assertTrue(logs.stream().anyMatch(log ->
            log.getTopics().equals(expectedTopics) && Arrays.equals(log.getData(), expectedData)
        ));
    }

    public static void assertEventWasNotEmitted(List<LogInfo> logs, CallTransaction.Function bridgeEvent) {
        List<LogInfo> event = getLogsBySignature(logs, bridgeEvent);
        assertEquals(0, event.size());
    }
}
