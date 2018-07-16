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

import co.rsk.crypto.Keccak256;
import co.rsk.net.notifications.panics.PanicFlag;
import co.rsk.rpc.modules.notifications.NotificationsModule;
import org.ethereum.core.Block;

import java.time.Instant;
import java.util.function.Function;

public class ForkAttackAlert extends FederationAlert {
    private Keccak256 bestBlockHash;
    private long bestBlockNumber;
    private boolean isFederatedNode;

    public ForkAttackAlert(Instant created, Block bestBlock, boolean isFederatedNode) {
        super(created);
        this.bestBlockHash = bestBlock.getHash();
        this.bestBlockNumber = bestBlock.getNumber();
        this.isFederatedNode = isFederatedNode;
    }

    public Keccak256 getBestBlockHash() {
        return bestBlockHash;
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

    @Override
    public Function<FederationAlert, NotificationsModule.FederationAlert> getConverterForNotificationsModule() {
        return NotificationsModule.ForkAttackAlert.convert;
    }
}
