/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.net.notifications.alerts;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.notifications.panics.PanicFlag;
import org.ethereum.util.ByteUtil;

public class ForkAttackAlert extends FederationAlert {
    private RskAddress source;
    private Keccak256 confirmationBlockHash;
    private Keccak256 inBestChainBlockHash;
    private long confirmationBlockNumber;
    private long bestBlockNumber;
    private boolean isFederatedNode;

    public ForkAttackAlert(RskAddress source, Keccak256 confirmationBlockHash, long confirmationBlockNumber, Keccak256 inBestChainBlockHash, long bestBlockNumber, boolean isFederatedNode) {
        this.source = source;
        this.confirmationBlockHash = confirmationBlockHash;
        this.confirmationBlockNumber = confirmationBlockNumber;
        this.inBestChainBlockHash = inBestChainBlockHash;
        this.bestBlockNumber = bestBlockNumber;
        this.isFederatedNode = isFederatedNode;
    }

    public FederationAlert copy() {
        RskAddress sourceCopy = new RskAddress(ByteUtil.cloneBytes(source.getBytes()));
        return new ForkAttackAlert(sourceCopy, confirmationBlockHash.copy(), confirmationBlockNumber, inBestChainBlockHash.copy(), bestBlockNumber, isFederatedNode);
    }

    public RskAddress getSource() {
        return source;
    }

    public Keccak256 getConfirmationBlockHash() {
        return confirmationBlockHash;
    }

    public Keccak256 getInBestChainBlockHash() {
        return inBestChainBlockHash;
    }

    public long getConfirmationBlockNumber() {
        return confirmationBlockNumber;
    }

    public long getBestBlockNumber() {
        return bestBlockNumber;
    }

    public boolean isFederatedNode() {
        return isFederatedNode;
    }

    @Override
    public PanicFlag getAssociatedPanicFlag(long forBlockNumber) {
        return isFederatedNode ? PanicFlag.FederationBlockchainForked(forBlockNumber)
                : PanicFlag.NodeBlockchainForked(forBlockNumber);
    }
}
