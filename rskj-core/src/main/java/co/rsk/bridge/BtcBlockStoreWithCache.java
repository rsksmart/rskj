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

package co.rsk.bridge;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.core.Repository;

/**
 * Implementation of a bitcoinj blockstore that persists to RSK's Repository
 * @author Oscar Guindzberg
 */
public interface BtcBlockStoreWithCache extends BtcBlockStore {

    StoredBlock getFromCache(Sha256Hash branchBlockHash);

    StoredBlock getStoredBlockAtMainChainHeight(int height) throws BlockStoreException;

    StoredBlock getStoredBlockAtMainChainDepth(int depth) throws BlockStoreException;

    interface Factory {
        BtcBlockStoreWithCache newInstance(
            Repository track,
            BridgeConstants bridgeConstants,
            BridgeStorageProvider bridgeStorageProvider,
            ForBlock activations
        );
    }
}
