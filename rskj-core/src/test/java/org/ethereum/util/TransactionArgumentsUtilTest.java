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

package org.ethereum.util;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.ethereum.config.Constants;
import org.ethereum.core.TransactionArguments;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.rpc.Web3;
import org.junit.Test;

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;

public class TransactionArgumentsUtilTest {

	@Test
	public void processArguments() {

		Constants constants = Constants.regtest();
		
		Wallet wallet = new Wallet(new HashMapDB());
		RskAddress sender = wallet.addAccount();
		RskAddress receiver = wallet.addAccount();
		
		Web3.CallArguments args = TransactionTestHelper.createArguments(sender, receiver);
		
		TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, null, wallet.getAccount(sender), constants.getChainId());
		
		assertEquals(txArgs.value, BigInteger.valueOf(100000L));
		assertEquals(txArgs.gasPrice, BigInteger.valueOf(10000000000000L));
		assertEquals(txArgs.gasLimit, BigInteger.valueOf(30400L));
		assertEquals(txArgs.chainId, 33);
		assertEquals(txArgs.nonce, BigInteger.ONE);
		assertEquals(txArgs.data, null);
		assertArrayEquals(txArgs.to, receiver.getBytes());

	}

}