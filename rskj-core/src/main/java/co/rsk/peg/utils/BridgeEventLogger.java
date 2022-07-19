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
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.Federation;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;

import java.util.List;

/**
 * Responsible for logging events triggered by BridgeContract.
 *
 * @author martin.medina
 */
public interface BridgeEventLogger {

    void logUpdateCollections(Transaction rskTx);

    void logAddSignature(BtcECKey federatorPublicKey, BtcTransaction btcTx, byte[] rskTxHash);

    void logReleaseBtc(BtcTransaction btcTx, byte[] rskTxHash);

    void logCommitFederation(Block executionBlock, Federation oldFederation, Federation newFederation);

    void logLockBtc(RskAddress rskReceiver, BtcTransaction btcTx, Address senderBtcAddress, Coin amount);

    void logPeginBtc(RskAddress rskReceiver, BtcTransaction btcTx, Coin amount, int protocolVersion);

    void logReleaseBtcRequested(byte[] rskTxHash, BtcTransaction btcTx, Coin amount);

    void logRejectedPegin(BtcTransaction btcTx, RejectedPeginReason reason);

    void logUnrefundablePegin(BtcTransaction btcTx, UnrefundablePeginReason reason);

    void logReleaseBtcRequestReceived(String sender, Address btcDestinationAddress, Coin amount);

    void logReleaseBtcRequestRejected(String sender, Coin amount, RejectedPegoutReason reason);

    void logBatchPegoutCreated(BtcTransaction btcTx, List<Keccak256> rskTxHashes);

}
