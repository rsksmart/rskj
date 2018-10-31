package co.rsk.metrics.block.builder;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.metrics.block.ValueGenerator;
import co.rsk.metrics.block.builder.metadata.MetadataWriter;
import org.ethereum.core.Account;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

import java.math.BigInteger;
import java.util.*;

public class MockTransactionsBuilder {


    private long maxTrxsPerBlock;
    private BigInteger minGasPrice, txGasLimit, tokenGasLimit;
    private ValueGenerator valueGenerator;
    private Vector<AccountStatus> accounts;
    private Vector<AccountStatus> tokens;
    private int tokenTrx, normalTrx;
    private TestSystemProperties config;
    private MetadataWriter writer;
    private Map<String, Account> accountCache;
    private AccountStatus tokensOwner;

    public MockTransactionsBuilder(long maxTrxPerBlock, BigInteger minGasPrice, BigInteger txGasLimit, BigInteger tokenGasLimit, ValueGenerator datasource, Vector<AccountStatus> accounts, Vector<AccountStatus> tokens, TestSystemProperties config, AccountStatus tokensOwner, MetadataWriter writer) {
        this.maxTrxsPerBlock = maxTrxPerBlock;
        this.minGasPrice = minGasPrice;
        this.txGasLimit = txGasLimit;
        this.tokenGasLimit = tokenGasLimit;
        this.valueGenerator = datasource;
        this.accounts = accounts;
        this.tokens = tokens;
        this.config = config;
        this.writer = writer;
        this.accountCache = new HashMap<>();
        this.tokensOwner = tokensOwner;
    }
    //Gas per regular trx 21000
    //Block Gas limit 3141592
    //100% usage = 149 trx
    //50% usage = 74 trx
    //10% usage = 14 trx

    //Force 80% of transactions be done between the first 20% of the generated accounts

    //Regular transactions 50% of total transactions
    //If 100% block is used, that represents a total of 148 trxs
    //50% is 74 trxs, 80% of those transactions (59) must be performed by a group composed of 20% of the total accounts (200).
    //The rest 20% of the transactions (15) must be performed by the rest of the accounts (800)

    //80% of the transactions, performed between accounts belonging to a 20% of the total accounts

    public List<Transaction> generateTransactions() {

        tokenTrx = new Double(Math.floor(maxTrxsPerBlock / 2.0)).intValue();
        normalTrx = tokenTrx;


        if (normalTrx + tokenTrx < maxTrxsPerBlock) {
            normalTrx++;
        }

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
            //tx.verify();
            trxs.add(tx);
        }

        //20% of the transactions is performed by 80 % of the accounts
        for (int i = 0; i < twentyPercTrxs; i++) {
            TransactionData tData = selectParticipants(false);
            Transaction tx = generateTransaction(tData);
            //tx.verify();
            trxs.add(tx);
        }


        return trxs;
    }


    public List<List<Transaction>> generateTokenPreAssignmentTransactions(Vector<AccountStatus> accounts){


        List<List<Transaction>> result = new ArrayList<>();
        List<Transaction> trxs = new ArrayList<>();


        int includedTrx = 0;

        for (AccountStatus tokenRecipient: accounts) {
            for(AccountStatus tokenAcc: tokens){
                Transaction tx = generateTokenPreAssignmentTransaction(tokenRecipient, tokenAcc);
                trxs.add(tx);
                includedTrx++;
                if(includedTrx>=maxTrxsPerBlock){
                    result.add(trxs);
                    trxs = new ArrayList<>();
                    includedTrx = 0;
                }
            }

        }

        return  result;
    }


    private Transaction generateTokenPreAssignmentTransaction(AccountStatus tokenRecipient, AccountStatus tokenAccount) {

        int trxValue = 1000000;

        long nonce = tokensOwner.nextNonce().longValue();
        RskAddress tokenAddr = new RskAddress(tokenAccount.getAddress());

        Transaction tx = CallTransaction.createCallTransaction(config, nonce, minGasPrice.longValue(), tokenGasLimit.longValue(),
                        tokenAddr, 0, CallTransaction.Function.fromSignature("transfer", "address", "uint"), tokenRecipient.getAddress(), Integer.toString(trxValue));
        tx.sign(getAccount(tokensOwner.getAccountName()).getEcKey().getPrivKeyBytes());

        writer.write("{\"trx\": { \"sender\": \"" + tokensOwner.getAddress() + "\",");
        writer.write("\"nonce\": \""+ nonce + "\",");
        writer.write("\"receiver\": \"" + tokenRecipient.getAddress() + "\",");
        writer.write("\"token-amount\": \"" + trxValue + "\", ");
        writer.write("\"token-address\": \"" + tokenAddr+"\",");
        writer.write("\"tx-hash\": \"" + tx.getHash().toJsonString() + "\" ");
        writer.write("}},");

        return tx;
    }

    private Transaction generateTransaction(TransactionData data) {
        Transaction tx = null;

        int trxValue = valueGenerator.nextTrxAmount();

        boolean isNormalTrx = valueGenerator.nextTransferType(); //True currency transfer, false token transfer

        if (isNormalTrx) {
            if (normalTrx > 0) {

                BigInteger nonce = data.getFrom().nextNonce();

                tx = Transaction.create(
                        config, data.getTo().getAddress(),
                        BigInteger.valueOf(trxValue), nonce, minGasPrice, txGasLimit);

                Account account = getAccount(data.getFrom().getAccountName());
                tx.sign(account.getEcKey().getPrivKeyBytes());

                normalTrx--;

                writer.write("{\"trx\" : { \"sender\": \"" + data.getFrom().getAddress() + "\",");
                writer.write("\"nonce\": \""+ nonce+ "\",");
                writer.write("\"receiver\": \"" + data.getTo().getAddress() + "\",");
                writer.write("\"coin-amount\": \"" + trxValue + "\", ");
                writer.write("\"tx-hash\": \"" + tx.getHash().toJsonString() + "\" ");

                writer.write("}},");

            } else if (tokenTrx > 0) {

                //Randomly select the token contract

                RskAddress tokenAddr = new RskAddress(tokens.get(valueGenerator.nextTokenContract()).getAddress());
                long nonce = data.getFrom().nextNonce().longValue();

                tx = CallTransaction.createCallTransaction(config, nonce, minGasPrice.longValue(), tokenGasLimit.longValue(),
                        tokenAddr, 0, CallTransaction.Function.fromSignature("transfer", "address", "uint"), data.getTo().getAddress(), Integer.toString(trxValue));

                Account account = getAccount(data.getFrom().getAccountName());
                tx.sign(account.getEcKey().getPrivKeyBytes());

                tokenTrx--;

                writer.write("{\"trx\": { \"sender\": \"" + data.getFrom().getAddress() + "\",");
                writer.write("\"nonce\": \""+ nonce + "\",");
                writer.write("\"receiver\": \"" + data.getTo().getAddress() + "\",");
                writer.write("\"token-amount\": \"" + trxValue + "\", ");
                writer.write("\"token-address\": \"" + tokenAddr+"\",");
                writer.write("\"tx-hash\": \"" + tx.getHash().toJsonString() + "\" ");
                writer.write("}},");


            }

        } else {
            if (tokenTrx > 0) {

                //Randomly select the token contract
                RskAddress tokenAddr = new RskAddress(tokens.get(valueGenerator.nextTokenContract()).getAddress());
                long nonce = data.getFrom().nextNonce().longValue();

                tx = CallTransaction.createCallTransaction(config, nonce, minGasPrice.longValue(), tokenGasLimit.longValue(),
                        tokenAddr, 0, CallTransaction.Function.fromSignature("transfer", "address", "uint"), data.getTo().getAddress(), Integer.toString(trxValue));

                Account account = getAccount(data.getFrom().getAccountName());
                tx.sign(account.getEcKey().getPrivKeyBytes());

                tokenTrx--;

                writer.write("{\"trx\": { \"sender\": \"" + data.getFrom().getAddress() + "\",");
                writer.write("\"nonce\": \""+ nonce + "\",");
                writer.write("\"receiver\": \"" + data.getTo().getAddress() + "\",");
                writer.write("\"token-amount\": \"" + trxValue + "\", ");
                writer.write("\"token-address\": \"" + tokenAddr+"\",");
                writer.write("\"tx-hash\": \"" + tx.getHash().toJsonString() + "\" ");
                writer.write("}},");


            } else if (normalTrx > 0) {

                BigInteger nonce = data.getFrom().nextNonce();

                tx = Transaction.create(
                        config, data.getTo().getAddress(),
                        BigInteger.valueOf(trxValue), nonce, minGasPrice, txGasLimit);

                Account account = getAccount(data.getFrom().getAccountName());
                tx.sign(account.getEcKey().getPrivKeyBytes());
                normalTrx--;

                writer.write("{\"trx\" : { \"sender\": \"" + data.getFrom().getAddress() + "\",");
                writer.write("\"nonce\": \""+ nonce+ "\",");
                writer.write("\"receiver\": \"" + data.getTo().getAddress() + "\",");
                writer.write("\"coin-amount\": \"" + trxValue + "\", ");
                writer.write("\"tx-hash\": \"" + tx.getHash().toJsonString() + "\" ");

                writer.write("}},");


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
