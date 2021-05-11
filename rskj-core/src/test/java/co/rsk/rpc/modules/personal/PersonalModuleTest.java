package co.rsk.rpc.modules.personal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.junit.Test;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;

public class PersonalModuleTest {

	private static final String PASS_FRASE = "passfrase";
	
    @Test
    public void sendTransactionWithGasLimitTest() throws Exception {
    	
    	TestSystemProperties props = new TestSystemProperties();
    	
    	Wallet wallet = new Wallet(new HashMapDB());
    	RskAddress sender = wallet.addAccount(PASS_FRASE);
    	RskAddress receiver = wallet.addAccount();
    	
    	// Simulation of the args handled in the sendTransaction call
    	Web3.CallArguments args = new Web3.CallArguments();
    	args.from = sender.toJsonString();
    	args.to = receiver.toJsonString();
    	args.gasLimit = "0x76c0";
    	args.gasPrice = "0x9184e72a000";
    	args.value = "0x186A0";
    	args.nonce = "0x01";

    	// Transaction that is expected to be constructed WITH the gasLimit
        Transaction tx = Transaction
                .builder()
                .nonce(TypeConverter.stringNumberAsBigInt(args.nonce))
                .gasPrice(TypeConverter.stringNumberAsBigInt(args.gasPrice))
                .gasLimit(TypeConverter.stringNumberAsBigInt(args.gasLimit))
                .destination(TypeConverter.stringHexToByteArray(args.to))
                .chainId(props.getNetworkConstants().getChainId())
                .value(TypeConverter.stringNumberAsBigInt(args.value))
                .build();
        tx.sign(wallet.getAccount(sender, PASS_FRASE).getEcKey().getPrivKeyBytes());

        // Hash of the expected transaction
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
