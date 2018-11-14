package co.rsk.metrics.block;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainImpl;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

/**
 * Generates a new blockchain ,executed using mock block data
 */
public class BlockchainGenerator {
    private static Logger logger = LoggerFactory.getLogger("TestBlockchain");
    private final TestSystemProperties config;

    private BigInteger minGasPrice, txGasLimit,tokenGasLimit, blockGasLimit;
    private String databaseDir;
    private BigInteger blockDifficulty;
    private int numOfBlocks;
    private long trxPerBlock;
    private ValueGenerator datasource;
    private Vector<AccountStatus> regularAccounts;
    private AccountStatus tokensOwner;
    private boolean includeRemasc;
    private Transaction angelCreator;


    public BlockchainGenerator(String generationDir, BigInteger minGasPrice, BigInteger txGasLimit, BigInteger tokenGasLimit, BigInteger blockGasLimit, String genesisRoot, int numOfBlocks, long trxPerBlock, ValueGenerator datasource, BigInteger blockDifficulty, TestSystemProperties config, MetadataWriter metadataWriter, boolean includeRemasc) throws IOException, InvalidGenesisFileException {
        DefaultConfig defaultConfig = new DefaultConfig();

        this.config = config;
        this.minGasPrice = minGasPrice;
        this.txGasLimit = txGasLimit;
        this.tokenGasLimit = tokenGasLimit;
        this.blockGasLimit = blockGasLimit;
        this.databaseDir = generationDir;
        this.numOfBlocks = numOfBlocks;
        this.trxPerBlock = trxPerBlock;
        this.datasource = datasource;
        this.blockDifficulty = blockDifficulty;
        this.includeRemasc = includeRemasc;

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
        //tokenContracts = genesisLoader.getTokenContracts();
        this.tokensOwner = genesisLoader.getTokensOwner();



        BlockStore blockStore = defaultConfig.buildBlockStore(databaseDir);
        ReceiptStore receiptStore = defaultConfig.buildReceiptStore(databaseDir);
        Repository repository = new CommonConfig().buildRepository(databaseDir, 1024);


        Genesis genesis = genesisLoader.loadGenesis();


        metadataWriter.write("{ \"genesis\": {");
        metadataWriter.write("\"hash\": \"" + genesis.getHashJsonString() + "\"},");


        List<BlockInfo> blocks = generateBlockList(genesis, metadataWriter);

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

    private BlockInfo tokenContractsInstantiationBlock(Genesis genesis, MetadataWriter writer, MockTransactionsBuilder trxBuilder){

        logger.info("Generating token instantiation transactions");

        BlockInfo block = null;
        Block parent = genesis;
        List<BlockHeader> uncles = null;


        logger.info("block info: Block number {}, Block hash {}",parent.getHeader().getNumber(), parent.getHash().toString());

        //First block
        writer.write("\"blocks\":[{ \"token-create-transactions\":[");

        List<Transaction> tokenContractCreation = trxBuilder.generateTokenCreationTransactions("TokenCreatorAccount");

        Vector<RskAddress> contractAddresses = new Vector<>(5);

        for(Transaction contract : tokenContractCreation){
            contractAddresses.add(contract.getContractAddress());
        }
        trxBuilder.setTokenContracts(contractAddresses);

        for(Transaction contractTrx : tokenContractCreation){
            System.out.println("Contract"+ contractTrx.getNonceAsInteger());
            System.out.println("Address:["+contractTrx.getContractAddress()+"]");
        }


        //TODO REMOVE ANGEL CONTRACT BEFORE COMITTING
        List<Transaction> angelTrx = trxBuilder.generateAngelContractTransaction("TokenCreatorAccount");
        tokenContractCreation.addAll(angelTrx);
        this.angelCreator = angelTrx.get(1);

        writer.write("{\"trx\":{\"end\": \"true\"}}]");

        long parentNum = parent.getNumber();

        logger.info("Building Token assignment blocks");

        if(includeRemasc){
            //Include REMASC transaction
            Transaction remascTx = new RemascTransaction(parentNum+1);
            tokenContractCreation.add(remascTx);
        }


        Coin paidFees = Coin.ZERO;

        for(Transaction trx : tokenContractCreation){
            BigInteger gasLimit = new BigInteger(1, trx.getGasLimit());
            Coin gasPrice = trx.getGasPrice();
            paidFees = paidFees.add(gasPrice.multiply(gasLimit));
        }



        block = new BlockInfo(tokenContractCreation, paidFees, blockDifficulty, parentNum+1,blockGasLimit, uncles);

        writer.write(", \"block-number\": \"" + block.getBlockNumber() + "\"}");
        logger.info("Block info: Block number {}",block.getBlockNumber());

        writer.write("]");
        return block;
    }

    private List<BlockInfo> generateBlockList(Genesis genesis, MetadataWriter writer){
        logger.info("Genesis block info: Block number {}, Block hash {}",genesis.getHeader().getNumber(), genesis.getHash().toString());

        logger.info("Generating {} blocks", numOfBlocks);


        MockTransactionsBuilder trxBuilder = new MockTransactionsBuilder(trxPerBlock, minGasPrice, txGasLimit, tokenGasLimit,  datasource, regularAccounts, config, tokensOwner, writer);

        List<BlockInfo> blocks = new ArrayList<>();
        BlockInfo firstBlock = tokenContractsInstantiationBlock(genesis, writer, trxBuilder);
        blocks.add(firstBlock);

        List<BlockHeader> uncles = null;


        //First block
        writer.write("\"blocks\":[{ \"token-assignment-transactions\":[");

        List<List<Transaction>> tokenAssignments = trxBuilder.generateTokenPreAssignmentTransactions(regularAccounts);

        writer.write("{\"trx\":{\"end\": \"true\"}}]");

        long parentNum = firstBlock.getBlockNumber();

        logger.info("Building Token assignment blocks");


        for(List<Transaction> assignTrx : tokenAssignments){

            if(includeRemasc){
                //Include REMASC transaction
                Transaction remascTx = new RemascTransaction(parentNum+1);
                assignTrx.add(remascTx);
            }


            Coin paidFees = Coin.ZERO;

            for(Transaction trx : assignTrx){
                BigInteger gasLimit = new BigInteger(1, trx.getGasLimit());
                Coin gasPrice = trx.getGasPrice();
                paidFees = paidFees.add(gasPrice.multiply(gasLimit));
            }


            BlockInfo block = new BlockInfo(assignTrx, paidFees, blockDifficulty, parentNum+1,blockGasLimit, uncles);
            parentNum++;

            writer.write(", \"block-number\": \"" + block.getBlockNumber() + "\"}");
            blocks.add(block);
            logger.info("PREASSIGNMENT BLOCK info: Block number {}",block.getBlockNumber());
        }

        logger.info("Building transfer transactions blocks");

        for(int i = 0; i<numOfBlocks; i++){
            writer.write(",{ \"transactions\":[");
            List<Transaction> trxs =  trxBuilder.generateTransactions(i+1>=numOfBlocks?this.angelCreator:null);

            if(includeRemasc){
                //Include REMASC Transaction
                Transaction remascTx = new RemascTransaction(parentNum+1);
                trxs.add(remascTx);
            }


            Coin paidFees = Coin.ZERO;

            for(Transaction trx : trxs){
                BigInteger gasLimit = new BigInteger(1, trx.getGasLimit());
                Coin gasPrice = trx.getGasPrice();
                paidFees = paidFees.add(gasPrice.multiply(gasLimit));
            }

            writer.write("{\"trx\":{\"end\": \"true\"}}");

            BlockInfo block = new BlockInfo(trxs ,paidFees, blockDifficulty, parentNum+1,TestContext.BLOCK_GAS_LIMIT,uncles);

            if(i+1>=numOfBlocks){
                logger.info("ANGEL TRX ADDED IN BLOCK {}", block.getBlockNumber());
            }

            parentNum++;
            blocks.add(block);
            writer.write(", \"block-number\": \"" + block.getBlockNumber() + "\"}");
            logger.info("Transfer TRX Block info: Block number {}",block.getBlockNumber());
        }

        writer.write("]");
        return blocks;
    }
}
