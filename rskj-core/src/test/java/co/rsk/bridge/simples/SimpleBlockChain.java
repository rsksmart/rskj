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

package co.rsk.bridge.simples;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcBlockChain;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.store.BlockStoreException;

import java.math.BigInteger;

/**
 * Created by ajlopez on 6/9/2016.
 */
public class SimpleBlockChain extends BtcBlockChain {
    private BtcBlockStore blockStore;
    private StoredBlock block;
    private StoredBlock highBlock;
    private boolean useHighBlock = false;


    public SimpleBlockChain(Context context, BtcBlockStore blockStore) throws BlockStoreException {
        super(context, blockStore);

        this.blockStore = blockStore;
        this.highBlock = new StoredBlock(null, BigInteger.ONE, 100);
    }

    @Override
    public boolean add(BtcBlock block) {
        StoredBlock sblock = new StoredBlock(block, BigInteger.ONE, 1);
        try {
            this.blockStore.put(sblock);
            this.block = sblock;
            this.setChainHead(sblock);
        } catch (BlockStoreException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void useHighBlock() {
        this.useHighBlock = true;
    }

    public void useBlock() {
        this.useHighBlock = false;
    }

    @Override
    public StoredBlock getChainHead() {
        if(this.useHighBlock)
            return this.highBlock;
        return block;
    }

    /*@Override
    public int getBestChainHeight() {
        return 100;
    }*/
}
