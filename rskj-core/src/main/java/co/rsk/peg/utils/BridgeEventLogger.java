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

import co.rsk.bitcoinj.core.*;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.pegin.RejectedPeginReason;
import org.ethereum.core.Block;
import java.util.List;

/**
 * Responsible for logging events triggered by BridgeContract.
 *
 * @author martin.medina
 */
public interface BridgeEventLogger {

    void logUpdateCollections(RskAddress sender);

    void logAddSignature(FederationMember federationMember, BtcTransaction btcTx, byte[] rskTxHash);

    void logReleaseBtc(BtcTransaction btcTx, byte[] rskTxHash);

    void logCommitFederation(Block executionBlock, Federation oldFederation, Federation newFederation);

    void logCommitFederationFailure(Block executionBlock, Federation proposedFederation);

    default void logLockBtc(RskAddress rskReceiver, BtcTransaction btcTx, Address senderBtcAddress, Coin amount) {
        throw new UnsupportedOperationException();
    }

    default void logPeginBtc(RskAddress rskReceiver, BtcTransaction btcTx, Coin amount, int protocolVersion) {
        throw new UnsupportedOperationException();
    }

    default void logReleaseBtcRequested(byte[] rskTxHash, BtcTransaction btcTx, Coin amount) {
        throw new UnsupportedOperationException();
    }

    default void logRejectedPegin(BtcTransaction btcTx, RejectedPeginReason reason) {
        throw new UnsupportedOperationException();
    }

    default void logNonRefundablePegin(BtcTransaction btcTx, NonRefundablePeginReason reason) {
        throw new UnsupportedOperationException();
    }

    default void logReleaseBtcRequestReceived(RskAddress sender, Address btcDestinationAddress, co.rsk.core.Coin amountInWeis) {
        throw new UnsupportedOperationException();
    }

    default void logReleaseBtcRequestRejected(RskAddress sender, co.rsk.core.Coin amountInWeis, RejectedPegoutReason reason) {
        throw new UnsupportedOperationException();
    }

    default void logBatchPegoutCreated(Sha256Hash btcTxHash, List<Keccak256> rskTxHashes) {
        throw new UnsupportedOperationException();
    }

    default void logPegoutConfirmed(Sha256Hash btcTxHash, long pegoutCreationRskBlockNumber) {
        throw new UnsupportedOperationException();
    }

    default void logPegoutTransactionCreated(Sha256Hash btcTxHash, List<Coin> outpointValues) {
        throw new UnsupportedOperationException();
    }

    default void logUnionLockingCapIncreased(RskAddress caller, co.rsk.core.Coin previousLockingCap, co.rsk.core.Coin newLockingCap) {
        throw new UnsupportedOperationException();
    }

    default void logUnionRbtcRequested(RskAddress requester, co.rsk.core.Coin amount) {
        throw new UnsupportedOperationException();
    }

    default void logUnionRbtcReleased(RskAddress receiver, co.rsk.core.Coin amount) {
        throw new UnsupportedOperationException();
    }
}
