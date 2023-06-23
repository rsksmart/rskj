/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.jmh.web3.plan;

import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.web3j.protocol.core.DefaultBlockParameter;

import java.math.BigInteger;
import java.util.Optional;

@State(Scope.Benchmark)
public class GetLogsPlan extends BasePlan {

    private String ethFilterId;

    private DefaultBlockParameter fromBlock;
    private DefaultBlockParameter toBlock;
    private String address;
    private String blockHash;

    @Override
    @Setup(Level.Trial) // move to "Level.Iteration" in case we set a batch size at some point
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);

        this.ethFilterId = generateNewFilterId();
        this.fromBlock = DefaultBlockParameter.valueOf(new BigInteger(getConfiguration().getString("getLogs.fromBlock")));
        this.toBlock = DefaultBlockParameter.valueOf(new BigInteger(getConfiguration().getString("getLogs.toBlock")));
        this.address = getConfiguration().getString("getLogs.address");
        this.blockHash = getConfiguration().getString("getLogs.blockHash");
    }

    public BigInteger getEthFilterId() {
        return new BigInteger(this.ethFilterId.replace("0x", ""), 16);
    }

    public DefaultBlockParameter getFromBlock() {
        return fromBlock;
    }

    public DefaultBlockParameter getToBlock() {
        return toBlock;
    }

    public String getAddress() {
        return address;
    }

    public String getBlockHash() {
        return blockHash;
    }

    private String generateNewFilterId() throws BenchmarkWeb3Exception {
        String blockHash = configuration.getString("getLogs.blockHash");
        return Optional.ofNullable(web3Connector.ethNewFilter(blockHash)).orElse("");
    }
}
