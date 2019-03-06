package co.rsk.metrics.block;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.metrics.block.builder.*;
import co.rsk.metrics.block.builder.metadata.MetadataWriter;
import co.rsk.metrics.block.tests.TestContext;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.config.CommonConfig;
import org.ethereum.config.DefaultConfig;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generates a new blockchain ,executed using mock block data
 */
public class BlockchainGenerator {

    private static Logger logger = LoggerFactory.getLogger("TestBlockchain");
    private final TestSystemProperties config;
    private int numOfBlocks, numOfEmptyBlocks;
    private double blockFillPercentage;
    private long transactionsStartBlock;
    private boolean includeRemasc, generateSpecialScenarios;
    private BigInteger blockDifficulty;
    private String databaseDir;
    private ValueGenerator datasource;
    private Vector<AccountStatus> regularAccounts,remascCoinbases;
    private AccountStatus tokensOwner;
    private Transaction dynamicContractCreator,ecs2;
    private GasLimits gasLimits;


    public BlockchainGenerator(String generationDir, GasLimits gasLimits, double blockFillPercentage, String genesisRoot, int numOfBlocks, int emptyBlocks, ValueGenerator datasource, BigInteger blockDifficulty, TestSystemProperties config, MetadataWriter metadataWriter, boolean includeRemasc, boolean specialScenarios, boolean includeTokenTransfers) throws IOException, InvalidGenesisFileException {
        DefaultConfig defaultConfig = new DefaultConfig();

        this.config = config;
        this.databaseDir = generationDir;
        this.numOfBlocks = numOfBlocks;
        this.datasource = datasource;
        this.blockDifficulty = blockDifficulty;
        this.includeRemasc = includeRemasc;
        this.numOfEmptyBlocks = emptyBlocks;
        this.generateSpecialScenarios = specialScenarios;
        this.blockFillPercentage = blockFillPercentage;
        this.gasLimits = gasLimits;

        Path path = Paths.get(databaseDir);
        if(Files.exists(path)){
            //Delete any existing files in the destination blockchain path
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        else{
            Files.createDirectories(path);
        }

        GenesisLoader genesisLoader = GenesisLoader.newGenesisLoader(config, genesisRoot); //Loads an already generated genesis file
        regularAccounts = genesisLoader.getRegularAccounts();
        remascCoinbases = genesisLoader.getRemascCoinbases();
        this.tokensOwner = genesisLoader.getTokensOwner();

        BlockStore blockStore = defaultConfig.buildBlockStore(databaseDir);
        ReceiptStore receiptStore = defaultConfig.buildReceiptStore(databaseDir);
        Repository repository = new CommonConfig().buildRepository(databaseDir, 1024);

        Genesis genesis = genesisLoader.loadGenesis();


        metadataWriter.write("{ \"genesis\": {");
        metadataWriter.write("\"hash\": \"" + genesis.getHashJsonString() + "\"},");

        List<BlockInfo> blocks = generateBlockList(genesis, includeTokenTransfers);

        BlockChainBuilder builder = new BlockChainBuilder(includeRemasc);
        builder.setBlockStore(blockStore);
        builder.setReceiptStore(receiptStore);
        builder.setRepository(repository);
        builder.setGenesis(genesis);
        builder.setBlocks(blocks);
        builder.setConfig(this.config);
        builder.build();
        metadataWriter.write("}");
        metadataWriter.close();
    }


    public String getDatabaseDir(){
        return this.databaseDir;
    }

    public TestSystemProperties getConfig(){
        return this.config;
    }

    private List<BlockInfo> tokensAssignmentBlocks(long parent, String coinbase, MockTransactionsBuilder trxBuilder){

        logger.info("Generating token-assignment blocks starting from block {}", parent+1);

        List<List<Transaction>> tokenAssignments = trxBuilder.generateTokenPreAssignmentTransactions(regularAccounts);
        List<BlockInfo> blocks = new ArrayList<>();

        long parentNum = parent;

        for(List<Transaction> trxList : tokenAssignments){

            if(includeRemasc){
                Transaction remascTx = new RemascTransaction(parentNum+1);
                trxList.add(remascTx);
            }

            Coin paidFees = Coin.ZERO;

            for(Transaction tx : trxList){
                BigInteger gasLimit = new BigInteger(1, tx.getGasLimit());
                Coin gasPrice = tx.getGasPrice();
                paidFees = paidFees.add(gasPrice.multiply(gasLimit));
            }

            blocks.add(new BlockInfo(trxList, paidFees, blockDifficulty, parentNum+1, gasLimits.getBlockLimit(), null, coinbase));
            parentNum++;
        }

        return blocks;
    }


    private List<BlockInfo> specialCasesBlocks(long parentNum, MockTransactionsBuilder trxBuilder, String coinbase){

        logger.info("Generating block {} with special-cases transactions", parentNum+1);

        List<BlockInfo> blocks = new ArrayList<>();
        //writer.write(",{ \"special-case-transactions\":[");
        List<Transaction> transactions =  trxBuilder.generateSpecialCasesCall(this.dynamicContractCreator, this.ecs2);

        List<Transaction> scTrx = new ArrayList<>(2);
        scTrx.add(transactions.get(0));

        if(includeRemasc){
            //Include REMASC Transaction
            Transaction remascTx = new RemascTransaction(parentNum+1);
            scTrx.add(remascTx);
            coinbase = selectRandomCoinbase(remascCoinbases);
        }

        Coin paidFees = Coin.ZERO;

        for(Transaction trx : scTrx){
            BigInteger gasLimit = new BigInteger(1, trx.getGasLimit());
            Coin gasPrice = trx.getGasPrice();
            paidFees = paidFees.add(gasPrice.multiply(gasLimit));
        }

        //writer.write("{\"trx\":{\"end\": \"true\"}}");

        BlockInfo block = new BlockInfo(scTrx ,paidFees, blockDifficulty, parentNum+1, gasLimits.getBlockLimit(), null, coinbase);
        blocks.add(block);
        parentNum++;


        scTrx = new ArrayList<>(2);
        scTrx.add(transactions.get(1));

        if(includeRemasc){
            //Include REMASC Transaction
            Transaction remascTx = new RemascTransaction(parentNum+1);
            scTrx.add(remascTx);
            coinbase = selectRandomCoinbase(remascCoinbases);
        }

        paidFees = Coin.ZERO;

        for(Transaction trx : scTrx){
            BigInteger gasLimit = new BigInteger(1, trx.getGasLimit());
            Coin gasPrice = trx.getGasPrice();
            paidFees = paidFees.add(gasPrice.multiply(gasLimit));
        }

        //writer.write("{\"trx\":{\"end\": \"true\"}}");

        block = new BlockInfo(scTrx ,paidFees, blockDifficulty, parentNum+1, gasLimits.getBlockLimit(), null, coinbase);
        blocks.add(block);


        return blocks;
        //writer.write(", \"block-number\": \"" + block.getBlockNumber() + "\"}");
    }

    private List<BlockInfo> transferBlocks(long parentNum, MockTransactionsBuilder trxBuilder, String coinbase){
        logger.info("Generating {} blocks with coin-transfer transactions", numOfBlocks);

        List<BlockInfo> blocks = new ArrayList<>(numOfBlocks);

        for(int i = 0; i<numOfBlocks; i++){

            //writer.write(",{ \"transactions\":[");
            List<Transaction> trxs =  trxBuilder.generateTransactions();

            if(includeRemasc){
                Transaction remascTx = new RemascTransaction(parentNum+1);
                trxs.add(remascTx);
                coinbase = selectRandomCoinbase(remascCoinbases);
            }

            Coin paidFees = Coin.ZERO;

            for(Transaction trx : trxs){
                BigInteger gasLimit = new BigInteger(1, trx.getGasLimit());
                Coin gasPrice = trx.getGasPrice();
                paidFees = paidFees.add(gasPrice.multiply(gasLimit));
            }

            //writer.write("{\"trx\":{\"end\": \"true\"}}");

            blocks.add(new BlockInfo(trxs ,paidFees, blockDifficulty, parentNum+1, gasLimits.getBlockLimit(),null, coinbase));
            parentNum++;
            //writer.write(", \"block-number\": \"" + block.getBlockNumber() + "\"}");
        }

        return blocks;
    }

    private List<BlockInfo> emptyBlocks(long parentNum, String coinbase){

        logger.info("Generating {} empty blocks", numOfEmptyBlocks);

        List<BlockInfo> blocks = new ArrayList<>(numOfEmptyBlocks);

        for(int i = 0; i<numOfEmptyBlocks; i++){
            //writer.write(",{ \"transactions\":[");
            List<Transaction> trxs = new ArrayList<>();

            if(includeRemasc){
                Transaction remascTx = new RemascTransaction(parentNum+1);
                trxs.add(remascTx);
                coinbase = selectRandomCoinbase(remascCoinbases);
            }

            //writer.write("{\"trx\":{\"end\": \"true\"}}");

            BlockInfo block = new BlockInfo(trxs , Coin.ZERO, blockDifficulty, parentNum+1, gasLimits.getBlockLimit(), null, coinbase);

            parentNum++;
            blocks.add(block);
            //writer.write(", \"block-number\": \"" + block.getBlockNumber() + "\"}");
        }

        return blocks;
    }

    private List<BlockInfo> tokenContractsInstantiationBlocks(long parentNum, MockTransactionsBuilder trxBuilder, String coinbase){

        logger.info("Generating block {} with token contracts' instantiation transactions", parentNum+1);

        List<BlockInfo> blocks = new ArrayList<>();

        //writer.write("\"blocks\":[{ \"token-create-transactions\":[");

        List<Transaction> tokenContractCreation = trxBuilder.generateTokenCreationTransactions();
        System.out.println(" TOKEN CONTRACTS " + tokenContractCreation.size());

        Vector<RskAddress> contractAddresses = new Vector<>(5);
        for(Transaction contract : tokenContractCreation){
            contractAddresses.add(contract.getContractAddress());
        }
        trxBuilder.setTokenContracts(contractAddresses);

        if(generateSpecialScenarios){

            List<Transaction> dynContractGen = trxBuilder.generateDynamicContractGenerationContractTransaction();
            System.out.println("ANGEL: " + dynContractGen.size());
            List<Transaction> dummyContracts = trxBuilder.generateDummyContracts(TestContext.DUMMY_CONTRACTS_QTY);
            System.out.println("DUMMY: " + dummyContracts.size());

            Vector<RskAddress> dummyContractAddresses = new Vector<>(dummyContracts.size());
            for(Transaction dummyContract : dummyContracts){
                dummyContractAddresses.add(dummyContract.getContractAddress());
            }
            trxBuilder.setDummyContracts(dummyContractAddresses);
            this.dynamicContractCreator = dynContractGen.get(1);

            tokenContractCreation.addAll(dynContractGen);
            tokenContractCreation.addAll(dummyContracts);



            this.ecs2 = trxBuilder.generateExtcodesizeContractTransaction();

            tokenContractCreation.add(this.ecs2);

            System.out.println("TOKEN + ANGEL + DUMMY + EXCODESIZE: " + tokenContractCreation.size());

        }

        List<Transaction> blockTrxs = new ArrayList<>();
        long limit = this.gasLimits.getBlockLimit().longValue()-10;
        long currentGas = 0;

        for(Transaction contractTrx : tokenContractCreation){

            long contractGasLimit = new BigInteger(1, contractTrx.getGasLimit()).longValue();

            if(((currentGas + contractGasLimit) >= limit) && blockTrxs.size() > 0 ){

                if(includeRemasc){
                    Transaction remascTx = new RemascTransaction(parentNum+1);
                    blockTrxs.add(remascTx);
                }

                Coin paidFees = Coin.ZERO;

                for(Transaction tx : blockTrxs){
                    BigInteger gasLimit = new BigInteger(1, tx.getGasLimit());
                    Coin gasPrice = tx.getGasPrice();
                    paidFees = paidFees.add(gasPrice.multiply(gasLimit));
                }

                BlockInfo block = new BlockInfo(blockTrxs, paidFees, blockDifficulty, parentNum+1, gasLimits.getBlockLimit(), null, coinbase);
                blocks.add(block);

                System.out.println("CONTRACT INSTANTIATION BLOCK "+ block.getBlockNumber() + " with " + block.getTransactions().size() + " transactions using a total gas of " + currentGas);
                parentNum++;
                blockTrxs = new ArrayList<>();
                currentGas = 0;
            }

            blockTrxs.add(contractTrx);
            currentGas += contractGasLimit;
        }


        if(blockTrxs.size() > 0){
            if(includeRemasc){
                Transaction remascTx = new RemascTransaction(parentNum+1);
                blockTrxs.add(remascTx);
            }

            Coin paidFees = Coin.ZERO;

            for(Transaction tx : blockTrxs){
                BigInteger gasLimit = new BigInteger(1, tx.getGasLimit());
                Coin gasPrice = tx.getGasPrice();
                paidFees = paidFees.add(gasPrice.multiply(gasLimit));
            }

            BlockInfo block = new BlockInfo(blockTrxs, paidFees, blockDifficulty, parentNum+1, gasLimits.getBlockLimit(), null, coinbase);
            blocks.add(block);
            System.out.println("CONTRACT INSTANTIATION BLOCK "+ block.getBlockNumber() + " with " + block.getTransactions().size() + " transactions using a total gas of " + currentGas);

        }

        return blocks;
    }

    private String selectRandomCoinbase(Vector<AccountStatus> remascCoinbases){
        AccountStatus coinbase = remascCoinbases.get(this.datasource.nextCoinbase());
        return coinbase.getAddress();
    }

    public long getTransactionsStartBlock() {
        return transactionsStartBlock;
    }

    private List<BlockInfo> generateBlockList(Genesis genesis, boolean includeTokenTransfers){
        logger.info("Genesis block info: Block number {}, Block hash {}",genesis.getHeader().getNumber(), genesis.getHash().toString());

        MockTransactionsBuilder trxBuilder = new MockTransactionsBuilder(blockFillPercentage, gasLimits,  datasource, regularAccounts, config, tokensOwner, includeTokenTransfers);
        String coinbase = genesis.getCoinbase().toString();


        List<BlockInfo> contractCreationBlocks = tokenContractsInstantiationBlocks(genesis.getNumber(), trxBuilder, genesis.getCoinbase().toString());
        //writer.write("\"blocks\":[{ \"token-assignment-transactions\":[");

        List<BlockInfo> tokenAssignmentBlocks = tokensAssignmentBlocks(contractCreationBlocks.get(contractCreationBlocks.size()-1).getBlockNumber(), coinbase, trxBuilder);
        //writer.write("{\"trx\":{\"end\": \"true\"}}]");

        List<BlockInfo> emptyBlocks = emptyBlocks(tokenAssignmentBlocks.get(tokenAssignmentBlocks.size()-1).getBlockNumber(), coinbase);

        transactionsStartBlock = emptyBlocks.get(emptyBlocks.size()-1).getBlockNumber()+1;


        List<BlockInfo> transferBlocks = transferBlocks(transactionsStartBlock-1 ,trxBuilder, coinbase);
        System.out.println("First Block with transfers: [" + transferBlocks.get(0).getBlockNumber()+"]");

        List<BlockInfo> blocks = new ArrayList<>();
        blocks.addAll(contractCreationBlocks);
        blocks.addAll(tokenAssignmentBlocks);
        blocks.addAll(emptyBlocks);
        blocks.addAll(transferBlocks);

        //Extra block for special case scenarios
        //Currently: 1. Dynamic contract generation and 2. extcodesize calls
        if(generateSpecialScenarios){
            List<BlockInfo> specialCasesBlocks = specialCasesBlocks(transferBlocks.get(transferBlocks.size()-1).getBlockNumber(), trxBuilder, coinbase);
            blocks.addAll(specialCasesBlocks);
        }

        //writer.write("]");
        return blocks;
    }
}
