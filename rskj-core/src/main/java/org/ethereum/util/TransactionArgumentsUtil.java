package org.ethereum.util;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import org.ethereum.core.Account;
import org.ethereum.core.TransactionArguments;
import org.ethereum.core.TransactionPool;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.vm.GasCost;

public class TransactionArgumentsUtil {

	private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);
	
		
	public static TransactionArguments processArguments(Web3.CallArguments argsParam, TransactionPool transactionPool, Account senderAccount) {
		
		TransactionArguments argsRet = new TransactionArguments();
		
		argsRet.to = stringHexToByteArray(argsParam.to);

        argsRet.nonce = stringNumberAsBigInt(argsParam.nonce, () -> transactionPool.getPendingState().getNonce(senderAccount.getAddress()));
        
        argsRet.value = stringNumberAsBigInt(argsParam.value, () -> BigInteger.ZERO);
        
        argsRet.gasPrice = stringNumberAsBigInt(argsParam.gasPrice, () -> BigInteger.ZERO);
        
        argsRet.gasLimit = stringNumberAsBigInt(argsParam.gas, () -> null);
        
        if(argsRet.gasLimit == null) {
        	argsRet.gasLimit = stringNumberAsBigInt(argsParam.gasLimit, () -> DEFAULT_GAS_LIMIT);
        }

        if (argsParam.data != null && argsParam.data.startsWith("0x")) {
        	argsParam.data = argsParam.data.substring(2);
        }
		
		return argsRet;
	} 
		
	private static BigInteger stringNumberAsBigInt(String number, Supplier<BigInteger> getDefaultValue) {
		
		BigInteger ret = Optional.ofNullable(number).map(TypeConverter::stringNumberAsBigInt).orElseGet(getDefaultValue);
		
		return ret;
	}
	
	private static byte[] stringHexToByteArray(String value) {
		
		byte[] ret = Optional.ofNullable(value).map(TypeConverter::stringHexToByteArray).orElse(null);
		
		return ret;
	}
	
}
