package co.rsk.metrics.block.tests;

import co.rsk.config.TestSystemProperties;
import co.rsk.metrics.block.builder.InvalidGenesisFileException;
import co.rsk.metrics.block.builder.metadata.MetadataWriter;
import co.rsk.metrics.block.BlockchainGenerator;
import co.rsk.metrics.block.builder.metadata.FileMetadataWriter;
import co.rsk.metrics.block.ValueGenerator;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.junit.Test;
import java.io.IOException;

public class BlockchainGeneratorTests {

    @Test
    public void runGC(){
        //Put as a separate run
        System.gc();
    }

    @Test
    public void testBlockchainCreateFullBlocks() throws IOException, InvalidGenesisFileException {
        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR);
        MetadataWriter metadataWriter = new FileMetadataWriter(TestContext.METADATA_PATH);
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        new BlockchainGenerator(TestContext.BLOCK_DB_DIR, TestContext.MIN_GAS_PRICE, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, TestContext.MAX_TRX_PER_BLOCK, valueGenerator, TestContext.BLOCK_DIFFICULTY, config, metadataWriter, false);
    }


    @Test
    public void testBlockchainCreateFullBlocksWithRemasc() throws IOException, InvalidGenesisFileException {
        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR);
        MetadataWriter metadataWriter = new FileMetadataWriter(TestContext.METADATA_PATH+".remasc");
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        String dbDir = TestContext.BLOCK_DB_DIR+"-resmac";
        new BlockchainGenerator(dbDir,TestContext.MIN_GAS_PRICE, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, TestContext.MAX_TRX_PER_BLOCK, valueGenerator, TestContext.BLOCK_DIFFICULTY, config, metadataWriter, true);
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
        long filledFiftyPercent = Math.round(Math.ceil(TestContext.MAX_TRX_PER_BLOCK*0.5));
        new BlockchainGenerator(TestContext.BLOCK_DB_DIR, TestContext.MIN_GAS_PRICE, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, filledFiftyPercent, valueGenerator, TestContext.BLOCK_DIFFICULTY,config, metadataWriter, false);
    }

    @Test
    public void testBlockchainCreateTenPercentBlocks() throws IOException, InvalidGenesisFileException {
        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR);
        MetadataWriter metadataWriter = new FileMetadataWriter(TestContext.METADATA_PATH);
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        long filledTenPercent = Math.round(Math.ceil(TestContext.MAX_TRX_PER_BLOCK*0.1));
        new BlockchainGenerator(TestContext.BLOCK_DB_DIR, TestContext.MIN_GAS_PRICE, TestContext.TRANSF_TX_GAS_LIMIT, TestContext.TOKEN_TRANSF_TX_GAS_LIMIT, TestContext.BLOCK_GAS_LIMIT, TestContext.GENESIS_FILE_ROOT, TestContext.BLOCKS_TO_GENERATE, TestContext.EMPTY_BLOCKS_TO_GENERATE, filledTenPercent, valueGenerator, TestContext.BLOCK_DIFFICULTY,config, metadataWriter, false);
    }
}
