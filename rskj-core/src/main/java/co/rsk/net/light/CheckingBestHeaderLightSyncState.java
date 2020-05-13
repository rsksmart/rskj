/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light;

import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.BlockHeader;

import java.util.List;

public class CheckingBestHeaderLightSyncState implements LightSyncState {

    private LightSyncProcessor lightSyncProcessor;
    private byte[] bestBlockHash;
    private LightPeer lightPeer;
    private ProofOfWorkRule blockHeaderValidationRule;

    public CheckingBestHeaderLightSyncState(LightSyncProcessor lightSyncProcessor, byte[] bestBlockHash, LightPeer lightPeer, ProofOfWorkRule blockHeaderValidationRule) {
        this.lightSyncProcessor = lightSyncProcessor;
        this.bestBlockHash = bestBlockHash;
        this.lightPeer = lightPeer;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
    }

    @Override
    public void onEnter() {
        trySendHeaderRequest();
    }

    @Override
    public void newBlockHeaderMessage(List<BlockHeader> blockHeaders) {

        if (blockHeaders.isEmpty()) {
            return;
        }

        //TODO: Mechanism of disconnecting when peer gives bad information
        for (BlockHeader h : blockHeaders) {
            if (!blockHeaderValidationRule.isValid(h)) {
                return;
            }
        }

        this.lightPeer.receivedBlock(blockHeaders);
    }

    private void trySendHeaderRequest() {
        lightSyncProcessor.sendGetBlockHeadersMessage(lightPeer, bestBlockHash);
    }
}
