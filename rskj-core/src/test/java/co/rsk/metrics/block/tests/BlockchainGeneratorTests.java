package co.rsk.metrics.block.tests;

import co.rsk.config.TestSystemProperties;
import co.rsk.metrics.block.BlockchainGenerator;
import co.rsk.metrics.block.ValueGenerator;
import co.rsk.metrics.block.builder.GasLimits;
import co.rsk.metrics.block.builder.InvalidGenesisFileException;
import co.rsk.metrics.block.builder.metadata.FileMetadataWriter;
import co.rsk.metrics.block.builder.metadata.MetadataWriter;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.junit.Test;

import java.io.IOException;

public class BlockchainGeneratorTests {

    @Test
    public void testBlockchainCreateFullBlocks() throws IOException, InvalidGenesisFileException {
        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR);
        MetadataWriter metadataWriter = new FileMetadataWriter(TestContext.METADATA_PATH);
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        GasLimits gasLimits = new GasLimits(TestContext.SPECIAL_CASES_CALL_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.MIN_GAS_PRICE, TestContext.ERC20_CONTRACT_GENERATION_GAS_LIMIT, TestContext.EXCODESIZE_CONTRACT_GENERATION_GAS_LIMIT);
        BlockchainGenerator generator = new BlockchainGenerator(TestContext.BLOCK_DB_DIR, gasLimits, 1, TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, valueGenerator, TestContext.BLOCK_DIFFICULTY, config, metadataWriter, false, true, true);
        System.out.println("Starting block with transfer transactions: " + generator.getTransactionsStartBlock());
    }

    @Test
    public void testBlockchainCreateFullBlocksWithoutTokenTransfer() throws IOException, InvalidGenesisFileException {
        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR);
        MetadataWriter metadataWriter = new FileMetadataWriter(TestContext.METADATA_PATH);
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        GasLimits gasLimits = new GasLimits(TestContext.SPECIAL_CASES_CALL_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.MIN_GAS_PRICE, TestContext.ERC20_CONTRACT_GENERATION_GAS_LIMIT, TestContext.EXCODESIZE_CONTRACT_GENERATION_GAS_LIMIT);
        String dbDir = TestContext.BLOCK_DB_DIR+"-no-token-transfers";
        BlockchainGenerator generator = new BlockchainGenerator(dbDir, gasLimits, 1, TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, valueGenerator, TestContext.BLOCK_DIFFICULTY, config, metadataWriter, false, true, false);
        System.out.println("Starting block with transfer transactions: " + generator.getTransactionsStartBlock());
    }


    @Test
    public void testBlockchainCreateFullBlocksWithRemasc() throws IOException, InvalidGenesisFileException {
        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR);
        MetadataWriter metadataWriter = new FileMetadataWriter(TestContext.METADATA_PATH+".remasc");
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        String dbDir = TestContext.BLOCK_DB_DIR+"-resmac";
        GasLimits gasLimits = new GasLimits(TestContext.SPECIAL_CASES_CALL_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.MIN_GAS_PRICE, TestContext.ERC20_CONTRACT_GENERATION_GAS_LIMIT, TestContext.EXCODESIZE_CONTRACT_GENERATION_GAS_LIMIT);

        BlockchainGenerator generator = new BlockchainGenerator(dbDir, gasLimits, 1, TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, valueGenerator, TestContext.BLOCK_DIFFICULTY, config, metadataWriter, true, true, true);
        System.out.println("Starting block with transfer transactions: " + generator.getTransactionsStartBlock());

    }

    @Test
    public void testAllFullBlockBlockchainsCreation() throws IOException, InvalidGenesisFileException {
        testBlockchainCreateFullBlocks();
        testBlockchainCreateFullBlocksWithRemasc();
    }
    @Test
    public void testBlockchainCreateFiftyPercentBlocks() throws IOException, InvalidGenesisFileException {
        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR);
        MetadataWriter metadataWriter = new FileMetadataWriter(TestContext.METADATA_PATH);
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        GasLimits gasLimits = new GasLimits(TestContext.SPECIAL_CASES_CALL_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.MIN_GAS_PRICE, TestContext.ERC20_CONTRACT_GENERATION_GAS_LIMIT, TestContext.EXCODESIZE_CONTRACT_GENERATION_GAS_LIMIT);

        new BlockchainGenerator(TestContext.BLOCK_DB_DIR, gasLimits, 0.5, TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, valueGenerator, TestContext.BLOCK_DIFFICULTY,config, metadataWriter, false, true, true);
    }

    @Test
    public void testBlockchainCreateTenPercentBlocks() throws IOException, InvalidGenesisFileException {
        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR);
        MetadataWriter metadataWriter = new FileMetadataWriter(TestContext.METADATA_PATH);
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        GasLimits gasLimits = new GasLimits(TestContext.SPECIAL_CASES_CALL_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.MIN_GAS_PRICE, TestContext.ERC20_CONTRACT_GENERATION_GAS_LIMIT, TestContext.EXCODESIZE_CONTRACT_GENERATION_GAS_LIMIT);

        new BlockchainGenerator(TestContext.BLOCK_DB_DIR, gasLimits, 0.1,  TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, valueGenerator, TestContext.BLOCK_DIFFICULTY,config, metadataWriter, false, true, true);
    }
}
