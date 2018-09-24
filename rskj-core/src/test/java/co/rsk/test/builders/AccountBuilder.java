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

package co.rsk.test.builders;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.test.World;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class AccountBuilder {
    private String name;
    private Coin balance;
    private byte[] code;
    private BlockChainImpl blockChain;

    public AccountBuilder() {
    }

    public AccountBuilder(World world) {
        this(world.getBlockChain());
    }

    public AccountBuilder(BlockChainImpl blockChain) {
        this.blockChain = blockChain;
    }

    public AccountBuilder name(String name) {
        this.name = name;
        return this;
    }

    public AccountBuilder balance(Coin balance) {
        this.balance = balance;
        return this;
    }

    public AccountBuilder code(byte[] code) {
        this.code = code;
        return this;
    }

    public Account build() {
        byte[] privateKeyBytes = HashUtil.keccak256(name.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        Account account = new Account(key);

        if (blockChain != null) {
            Block best = blockChain.getStatus().getBestBlock();
            BlockDifficulty td = blockChain.getStatus().getTotalDifficulty();
            Repository repository = blockChain.getRepository();

            Repository track = repository.startTracking();

            track.createAccount(account.getAddress());

            if (this.balance != null)
                track.addBalance(account.getAddress(), this.balance);

            if (this.code != null) {
                track.saveCode(account.getAddress(), this.code);
                track.getCode(account.getAddress());
            }
            track.commit();
            track.save();

            // Check that code is there...
            repository.getCode(account.getAddress());

            best.setStateRoot(repository.getRoot());
            best.flushRLP();

            blockChain.getBlockStore().saveBlock(best, td, true);
        }

        return account;
    }
}
