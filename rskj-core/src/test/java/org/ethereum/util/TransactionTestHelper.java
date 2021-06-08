package org.ethereum.util;

import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;

import co.rsk.core.RskAddress;

public class TransactionTestHelper {

	
	public static Web3.CallArguments createArguments(RskAddress sender, RskAddress receiver) {
		
    	// Simulation of the args handled in the sendTransaction call
    	Web3.CallArguments args = new Web3.CallArguments();
    	args.from = sender.toJsonString();
    	args.to = receiver.toJsonString();
    	args.gasLimit = "0x76c0";
    	args.gasPrice = "0x9184e72a000";
    	args.value = "0x186A0";
    	args.nonce = "0x01";
		
    	return args;
	}
	
	public static Transaction createTransaction(Web3.CallArguments args, byte chainId, Account senderAccount) {
		

    	// Transaction that is expected to be constructed WITH the gasLimit
        Transaction tx = Transaction
                .builder()
                .nonce(TypeConverter.stringNumberAsBigInt(args.nonce))
                .gasPrice(TypeConverter.stringNumberAsBigInt(args.gasPrice))
                .gasLimit(TypeConverter.stringNumberAsBigInt(args.gasLimit))
                .destination(TypeConverter.stringHexToByteArray(args.to))
                .chainId(chainId)
                .value(TypeConverter.stringNumberAsBigInt(args.value))
                .build();
        tx.sign(senderAccount.getEcKey().getPrivKeyBytes());
		
        return tx;
	}
	
}
