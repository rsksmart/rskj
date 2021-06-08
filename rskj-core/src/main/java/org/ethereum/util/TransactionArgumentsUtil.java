package org.ethereum.util;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import org.ethereum.core.Account;
import org.ethereum.core.TransactionArguments;
import org.ethereum.core.TransactionPool;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.GasCost;

public class TransactionArgumentsUtil {

	private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);
	
	public static final String ERR_INVALID_CHAIN_ID = "Invalid chainId: ";
		
	public static TransactionArguments processArguments(Web3.CallArguments argsParam, TransactionPool transactionPool, Account senderAccount, byte defaultChainId) {
		
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
        	argsRet.data = argsParam.data.substring(2);
        }
        
        byte txChainId = hexToChainId(argsParam.chainId);
        if (txChainId == 0) {
            txChainId = defaultChainId;
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
	
    private static byte hexToChainId(String hex) {
        if (hex == null) {
            return 0;
        }
        try {
            byte[] bytes = TypeConverter.stringHexToByteArray(hex);
            if (bytes.length != 1) {
                throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex);
            }

            return bytes[0];
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex, e);
        }
    }
	
	
}
