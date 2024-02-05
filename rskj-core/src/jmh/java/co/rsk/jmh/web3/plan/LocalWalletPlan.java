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

@State(Scope.Benchmark)
public class LocalWalletPlan extends BasePlan{

    public static final String ETH_SIGN_ADDRESS = "ethSign.address";
    public static final String ETH_SIGN_MESSAGE = "ethSign.message";

    private String ethSignMessage;
    private String ethSignAddress;


    @Setup(Level.Trial)
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);
        ethSignAddress = configuration.getString(ETH_SIGN_ADDRESS);
        ethSignMessage = configuration.getString(ETH_SIGN_MESSAGE);
    }

    public String getEthSignAddress() {
        return ethSignAddress;
    }

    public String getEthSignMessage() {
        return ethSignMessage;
    }
}
