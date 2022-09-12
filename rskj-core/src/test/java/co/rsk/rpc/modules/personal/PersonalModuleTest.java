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

package co.rsk.rpc.modules.personal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.TransactionFactoryHelper;
import org.junit.jupiter.api.Test;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;

class PersonalModuleTest {

	private static final String PASS_FRASE = "passfrase";

	@Test
	void sendTransactionWithGasLimitTest() throws Exception {

		TestSystemProperties props = new TestSystemProperties();

		Wallet wallet = new Wallet(new HashMapDB());
		RskAddress sender = wallet.addAccount(PASS_FRASE);
		RskAddress receiver = wallet.addAccount();

		// Hash of the expected transaction
		CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
		Transaction tx = TransactionFactoryHelper.createTransaction(args, props.getNetworkConstants().getChainId(), wallet.getAccount(sender, PASS_FRASE));
		String txExpectedResult = tx.getHash().toJsonString();

		TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
		when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

		Ethereum ethereum = mock(Ethereum.class);

		PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(props, ethereum, wallet, null);

		// Hash of the actual transaction builded inside the sendTransaction
		String txResult = personalModuleWalletEnabled.sendTransaction(args, PASS_FRASE);

		assertEquals(txExpectedResult, txResult);
	}

}
