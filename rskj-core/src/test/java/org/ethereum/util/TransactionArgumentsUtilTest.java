package org.ethereum.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Arrays;

import org.bouncycastle.util.encoders.Hex;
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
