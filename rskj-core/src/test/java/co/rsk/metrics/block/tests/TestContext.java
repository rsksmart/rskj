package co.rsk.metrics.block.tests;

import co.rsk.core.BlockDifficulty;

import java.math.BigInteger;

public abstract class TestContext {

    public static final BigInteger BLOCK_DIFFICULTY = BigInteger.ONE;
    public static final int MAX_TRX_PER_BLOCK = 149;//10;//149;
    public static final int BLOCKS_TO_GENERATE = 100;
    public static final int ACCOUNTS_TO_GENERATE = 1000;
    public static final int TRX_MAX_RND_AMOUNT = 4;
    public static final BigInteger ACCOUNT_BALANCE = new BigInteger("210000000000000000000000000000");
    public static final BigInteger MIN_GAS_PRICE = BigInteger.TEN;
    public static final BigInteger BLOCK_GAS_LIMIT = new BigInteger("100000000000000");
    public static final BigInteger TOKEN_TRANSF_TX_GAS_LIMIT = new BigInteger("1000000");
    public static final BigInteger TRANSF_TX_GAS_LIMIT = new BigInteger("2141592");
    //2976000


    public static final String ROOT = "/s/RSK/Repos/rskj/rskj-core";
    public static final String BLOCK_DB_NAME = "database-test";
    public static final String BLOCK_DB_DIR = ROOT + "/"+BLOCK_DB_NAME;
    public static final String BLOCK_REPLAY_DIR = ROOT + "/src/test/resources/performance/player-runs";
    public static final String PLAY_DB_FILE = BLOCK_REPLAY_DIR + "/database-test-replay";
    public static final String METADATA_PATH = ROOT +  "/src/test/resources/performance/blockchainMetadata.json";
    public static final String DATASOURCE_DIR = ROOT + "/src/test/resources/performance";
    public static final String GENESIS_FILE_ROOT = "./src/main/resources/genesis";
    public static final String GENESIS_FILE = "rsk-block-performance-test.json";
    public static final BigInteger INITIAL_TRX_NONCE = BigInteger.ZERO;//"deadbeefdeadbef0";
    public static final int DATASOURCE_VALUES_TO_GENERATE = 4000000;
    public static final String[] contracts = new String[]{"TokenA", "TokenB", "TokenC", "TokenD", "TokenE"};


}
