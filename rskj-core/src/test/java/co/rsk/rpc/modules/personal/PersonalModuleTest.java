package co.rsk.rpc.modules.personal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.Web3;
import org.ethereum.util.TransactionTestHelper;
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

        // Hash of the expected transaction
    	Web3.CallArguments args = TransactionTestHelper.createArguments(sender, receiver);
        Transaction tx = TransactionTestHelper.createTransaction(args, props.getNetworkConstants().getChainId(), wallet.getAccount(sender, PASS_FRASE));
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
