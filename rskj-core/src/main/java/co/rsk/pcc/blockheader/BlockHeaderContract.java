/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.pcc.blockheader;

import co.rsk.core.RskAddress;
import co.rsk.pcc.NativeContract;
import co.rsk.pcc.NativeMethod;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Precompiled contract that provides access to Block Header fields (coinbase, minimum gas price, block hash, merged
 * mining tags, bitcoin header, gas limit, gas used, RSK difficulty and coinbase for uncles).
 *
 * @author Diego Masini
 */
public class BlockHeaderContract extends NativeContract {
    // See: REMASC Maturity
    private static final short MAX_DEPTH = 4000;

    private final BlockAccessor blockAccessor;

    public BlockHeaderContract(ActivationConfig activationConfig, RskAddress contractAddress) {
        super(activationConfig, contractAddress);
        this.blockAccessor = new BlockAccessor(MAX_DEPTH);
    }

    @Override
    public List<NativeMethod> getMethods() {
        return Arrays.asList(
                new GetCoinbaseAddress(getExecutionEnvironment(), this.blockAccessor),
                new GetBlockHash(getExecutionEnvironment(), this.blockAccessor),
                new GetMergedMiningTags(getExecutionEnvironment(), this.blockAccessor),
                new GetMinimumGasPrice(getExecutionEnvironment(), this.blockAccessor),
                new GetGasLimit(getExecutionEnvironment(), this.blockAccessor),
                new GetGasUsed(getExecutionEnvironment(), this.blockAccessor),
                new GetDifficulty(getExecutionEnvironment(), this.blockAccessor),
                new GetBitcoinHeader(getExecutionEnvironment(), this.blockAccessor),
                new GetUncleCoinbaseAddress(getExecutionEnvironment(), this.blockAccessor)
        );
    }

    @Override
    public Optional<NativeMethod> getDefaultMethod() {
        return Optional.empty();
    }
}


