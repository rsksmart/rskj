package co.rsk.metrics.block.builder;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.metrics.block.ValueGenerator;
import co.rsk.metrics.block.builder.metadata.MetadataWriter;
import co.rsk.metrics.block.tests.ContractData;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

import java.math.BigInteger;
import java.util.*;

public class MockTransactionsBuilder {

    private long maxTrxsPerType, tokenTrx, normalTrx;
    private BigInteger contractNonce;
    private ValueGenerator valueGenerator;
    private Vector<AccountStatus> accounts;
    private Vector<RskAddress> tokens;
    private Vector<RskAddress> dummyContracts;
    private Map<String, Account> accountCache;
    private TestSystemProperties config;
    private Account contractsCreator;
    private GasLimits gasLimits;
    private boolean includeTokenTransfers;

    public MockTransactionsBuilder(double blockFillPercentage,GasLimits gasLimits, ValueGenerator datasource, Vector<AccountStatus> accounts, TestSystemProperties config, AccountStatus tokensOwner, boolean includeTokenTransfers) {

        this.maxTrxsPerType = Math.round(blockFillPercentage*gasLimits.getBlockLimit().divide(gasLimits.getCoinTransferLimit().add(gasLimits.getTokenTransferLimit())).longValue());
        this.valueGenerator = datasource;
        this.accounts = accounts;
        this.config = config;
        this.accountCache = new HashMap<>();
        this.contractNonce = BigInteger.ZERO;
        this.contractsCreator = getAccount(tokensOwner.getAccountName());
        this.gasLimits = gasLimits;
        this.includeTokenTransfers = includeTokenTransfers;

    }

    public List<Transaction> generateTransactions() {

        normalTrx = includeTokenTransfers?maxTrxsPerType:maxTrxsPerType*2;
        tokenTrx = includeTokenTransfers?maxTrxsPerType:0;

        long maxTrxsPerBlock = tokenTrx + normalTrx;

        //logger.info("Generating {} block transactions", maxTrxsPerBlock);

        List<Transaction> trxs = new ArrayList<>();
        long eightyPercTrxs = Math.round(Math.floor(maxTrxsPerBlock * 0.8));
        long twentyPercTrxs = Math.round(Math.ceil(maxTrxsPerBlock * 0.2));

        // logger.info("Total transactions to create {}", maxTrxsPerBlock);
        if (eightyPercTrxs + twentyPercTrxs < maxTrxsPerBlock) {
            twentyPercTrxs++;
        }
        //logger.info("80% is {} and 20% is {}", eightyPercTrxs, twentyPercTrxs);

        //logger.info("normalTrxs {} and tokenTrxs{}", normalTrx, tokenTrx);
        for (int i = 0; i < eightyPercTrxs; i++) {
            TransactionData tData = selectParticipants(true);
            Transaction tx = generateTransaction(tData);
            trxs.add(tx);
        }

        //20% of the transactions is performed by 80 % of the accounts
        for (int i = 0; i < twentyPercTrxs; i++) {
            TransactionData tData = selectParticipants(false);
            Transaction tx = generateTransaction(tData);
            trxs.add(tx);
        }

        return trxs;
    }

    public List<Transaction> generateSpecialCasesCall(Transaction dynCreate, Transaction ecs2){

        List<Transaction> trxs = new ArrayList<>();

        if(dynCreate != null){
            contractNonce = contractNonce.add(BigInteger.ONE);
            Transaction tx = CallTransaction.createCallTransaction(config, contractNonce.longValue(), 1, gasLimits.getSpecialCasesLimit().longValue(),
                    dynCreate.getContractAddress(), 0, CallTransaction.Function.fromSignature("create",  "uint"), "16");

            tx.sign(contractsCreator.getEcKey().getPrivKeyBytes());
            trxs.add(tx);
        }

        if(ecs2 != null){

            byte[] addresses = new byte[20*dummyContracts.size()];

            int start = 0;
            for(RskAddress contract: dummyContracts){
                byte[] contractBytes = contract.getBytes();
                System.arraycopy(contractBytes, 0, addresses, start, contractBytes.length);
                start += contractBytes.length;
            }

            contractNonce = contractNonce.add(BigInteger.ONE);
            Transaction tx = CallTransaction.createCallTransaction(config, contractNonce.longValue(), 1, gasLimits.getSpecialCasesLimit().longValue(),
                    ecs2.getContractAddress(), 0, CallTransaction.Function.fromSignature("run",  "bytes"), addresses);

            tx.sign(contractsCreator.getEcKey().getPrivKeyBytes());
            trxs.add(tx);
        }

        return trxs;
    }

    public List<Transaction> generateDynamicContractGenerationContractTransaction(){
        String created = ContractData.DYN_GEN_CREATED_CONTRACT;
        String creator = ContractData.DYN_GEN_CREATOR_CONTRACT;
        contractNonce = contractNonce.add(BigInteger.ONE);
        List<Transaction> result = new ArrayList<>(2);

        Transaction trx =  new Transaction(config, null, BigInteger.ZERO, contractNonce, BigInteger.ONE, gasLimits.getSpecialCasesContractGenLimit(), Hex.decode(created));
        trx.sign(contractsCreator.getEcKey().getPrivKeyBytes());
        result.add(trx);

        contractNonce = contractNonce.add(BigInteger.ONE);
        trx = new Transaction(config, null, BigInteger.ZERO, contractNonce, BigInteger.ONE, gasLimits.getSpecialCasesContractGenLimit(), Hex.decode(creator));
        trx.sign(contractsCreator.getEcKey().getPrivKeyBytes());
        result.add(trx);

        return result;

    }


    public List<Transaction> generateDummyContracts(int quantity){
        List<Transaction> trxs = new ArrayList<>(quantity);
        String contractCode = ContractData.DUMMY_CONTRACT;
        for(int i = 0; i < quantity; i++){
            contractNonce = contractNonce.add(BigInteger.ONE);
            Transaction trx = new Transaction(config, null, BigInteger.ZERO, contractNonce, BigInteger.ONE, gasLimits.getErc20ContractGenLimit(), Hex.decode(contractCode));
            trx.sign(contractsCreator.getEcKey().getPrivKeyBytes());
            trxs.add(trx);
        }
        return  trxs;
    }

    public Transaction generateExtcodesizeContractTransaction(){
        String ECS2 = ContractData.EC2_CONTRACT;
        contractNonce = contractNonce.add(BigInteger.ONE);
        Transaction trx =  new Transaction(config, null, BigInteger.ZERO, contractNonce, BigInteger.ONE, gasLimits.getSpecialCasesContractGenLimit(), Hex.decode(ECS2));
        trx.sign(contractsCreator.getEcKey().getPrivKeyBytes());
        return trx;
    }


    public List<Transaction> generateTokenCreationTransactions(){

        List<Transaction> trxs = new ArrayList<>(5);
        BigInteger balance = BigInteger.ZERO;


        //TokenA
        String code = ContractData.TOKEN_A_CONTRACT;
        Transaction tx  =  generateTokenCreationTransaction(contractsCreator, contractNonce, balance, code);
        trxs.add(tx);

        //TokenB
        code = ContractData.TOKEN_B_CONTRACT;
        contractNonce = contractNonce.add(BigInteger.ONE);
        tx  =  generateTokenCreationTransaction(contractsCreator, contractNonce, balance, code);
        trxs.add(tx);

        //TokenC
        code = ContractData.TOKEN_C_CONTRACT;
        contractNonce = contractNonce.add(BigInteger.ONE);
        tx  =  generateTokenCreationTransaction(contractsCreator, contractNonce, balance, code);
        trxs.add(tx);

        //TokenD
        code = ContractData.TOKEN_D_CONTRACT;
        contractNonce = contractNonce.add(BigInteger.ONE);
        tx  =  generateTokenCreationTransaction(contractsCreator, contractNonce, balance, code);
        trxs.add(tx);

        //TokenE
        code = ContractData.TOKEN_E_CONTRACT;
        contractNonce = contractNonce.add(BigInteger.ONE);
        tx  =  generateTokenCreationTransaction(contractsCreator, contractNonce, balance, code);
        trxs.add(tx);

        return trxs;

    }


    public List<List<Transaction>> generateTokenPreAssignmentTransactions(Vector<AccountStatus> accounts){

        List<List<Transaction>> result = new ArrayList<>();
        List<Transaction> trxs = new ArrayList<>();

        long maxTokenCallPerBlock = gasLimits.getBlockLimit().divide(gasLimits.getTokenTransferLimit()).longValue()-2;

        System.out.println("Generating token preassignment transactions, with " + maxTokenCallPerBlock + " transactions per block");
        for (AccountStatus tokenRecipient: accounts) {

            for(RskAddress tokenAddr: tokens){

                if(trxs.size() >= maxTokenCallPerBlock){
                    result.add(trxs);
                    trxs = new ArrayList<>();
                }
                trxs.add(generateTokenPreAssignmentTransaction(tokenRecipient, tokenAddr));
            }

        }

        if(trxs.size() > 0){
            result.add(trxs);
        }

        return result;
    }


    private Transaction generateTokenCreationTransaction(Account creator, BigInteger nonce, BigInteger balance, String code){
        Transaction trx =  new Transaction(config, null, balance, nonce, BigInteger.ONE, gasLimits.getErc20ContractGenLimit(), Hex.decode(code));
        trx.sign(creator.getEcKey().getPrivKeyBytes());
        return trx;
    }

    public void setTokenContracts(Vector<RskAddress> contracts){
        this.tokens = contracts;
    }

    public void setDummyContracts(Vector<RskAddress> contracts){
        this.dummyContracts = contracts;
    }


    private Transaction generateTokenPreAssignmentTransaction(AccountStatus tokenRecipient, RskAddress tokenAddr) {

        int trxValue = 1000000;

        //long nonce = tokensOwner.nextNonce().longValue();
        contractNonce = contractNonce.add(BigInteger.ONE);


        Transaction tx = CallTransaction.createCallTransaction(config, contractNonce.longValue(), gasLimits.getGasPrice().longValue(), gasLimits.getTokenTransferLimit().longValue(),
                        tokenAddr, 0, CallTransaction.Function.fromSignature("transfer", "address", "uint"), tokenRecipient.getAddress(), Integer.toString(trxValue));
        //tx.sign(tokensOwnerAccount.getEcKey().getPrivKeyBytes());
        tx.sign(contractsCreator.getEcKey().getPrivKeyBytes());
        return tx;
    }


    private Transaction generateCurrencyTransferTransaction(TransactionData data){

        BigInteger nonce = data.getFrom().nextNonce();
        int trxValue = valueGenerator.nextTrxAmount();

        Transaction tx = new Transaction(
                config, data.getTo().getAddress(),
                BigInteger.valueOf(trxValue), nonce, gasLimits.getGasPrice(), gasLimits.getCoinTransferLimit());

        Account account = getAccount(data.getFrom().getAccountName());
        tx.sign(account.getEcKey().getPrivKeyBytes());

        normalTrx--;


        return tx;
    }

    private Transaction generateTokenTransferTransaction(TransactionData data){

        //Randomly select the token contract
        int trxValue = valueGenerator.nextTrxAmount();

        RskAddress tokenAddr = tokens.get(valueGenerator.nextTokenContract());
        long nonce = data.getFrom().nextNonce().longValue();

        Transaction tx = CallTransaction.createCallTransaction(config, nonce, gasLimits.getGasPrice().longValue(), gasLimits.getTokenTransferLimit().longValue(),
                tokenAddr, 0, CallTransaction.Function.fromSignature("transfer", "address", "uint"), data.getTo().getAddress(), Integer.toString(trxValue));

        Account account = getAccount(data.getFrom().getAccountName());
        tx.sign(account.getEcKey().getPrivKeyBytes());

        tokenTrx--;
        return tx;
    }


    private Transaction generateTransaction(TransactionData data) {

        Transaction tx = null;

        boolean isNormalTrx = includeTokenTransfers?valueGenerator.nextTransferType():true; //[true] = currency transfer, [false] = token transfer

        if (isNormalTrx) {
            if (normalTrx > 0) {
                tx = generateCurrencyTransferTransaction(data);
            } else if (tokenTrx > 0 && includeTokenTransfers) {
               tx = generateTokenTransferTransaction(data);
            }

        } else {
            if (tokenTrx > 0) {
                tx = generateTokenTransferTransaction(data);
            } else if (normalTrx > 0) {
               tx = generateCurrencyTransferTransaction(data);
            }
        }

        return tx;
    }

    private Account getAccount(String accountName){

        if(accountCache.containsKey(accountName)){
            return accountCache.get(accountName);
        }

        byte[] privateKeyBytes = HashUtil.keccak256(accountName.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        Account account =  new Account(key);
        accountCache.put(accountName, account);
        return account;
    }



    private TransactionData selectParticipants(boolean minoritySubset) {
        int count = 0;
        int from, to;
        int rndMaxTries = 2000;

        if (minoritySubset) {
            from = valueGenerator.nextMinorityAccount();
            to = valueGenerator.nextMinorityAccount();
            while (from == to && count < rndMaxTries) {
                to = valueGenerator.nextMinorityAccount();
                count++;
            }
        } else {
            from = valueGenerator.nextMayorityAccount();
            to = valueGenerator.nextMayorityAccount();
            while (from == to && count < rndMaxTries) {
                to = valueGenerator.nextMayorityAccount();
                count++;
            }
        }

        if (count == rndMaxTries) return null;
        return new TransactionData(accounts.get(from), accounts.get(to));

    }


    final class TransactionData {
        private AccountStatus from;
        private AccountStatus to;

        TransactionData(AccountStatus from, AccountStatus to) {
            this.from = from;
            this.to = to;
        }

        public AccountStatus getFrom() {
            return this.from;
        }

        public AccountStatus getTo() {
            return this.to;
        }
    }

}
