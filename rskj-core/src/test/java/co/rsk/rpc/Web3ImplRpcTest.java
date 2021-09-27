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

package co.rsk.rpc;

import co.rsk.config.TestSystemProperties;
import co.rsk.db.RepositoryLocator;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.Web3Impl;
import org.ethereum.rpc.Web3Mocks;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 20/04/2017.
 */
public class Web3ImplRpcTest {
    @Test
    public void getRpcModules() {
        Ethereum eth = Web3Mocks.getMockEthereum();
        Blockchain blockchain = Web3Mocks.getMockBlockchain();
        PersonalModule pm = new PersonalModuleWalletDisabled();
        Web3Impl web3 = new Web3RskImpl(eth, blockchain,
                new TestSystemProperties(), null, null, pm,
                            null, null, null, null,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, null, null, null);

        Map<String, String> result = web3.rpc_modules();

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue(result.containsKey("eth"));
        Assert.assertEquals("1.0", result.get("eth"));
    }
}
