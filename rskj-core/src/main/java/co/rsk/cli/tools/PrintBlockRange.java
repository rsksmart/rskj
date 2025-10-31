package co.rsk.cli.tools;

        import co.rsk.cli.PicoCliToolRskContextAware;
        import co.rsk.core.BlockDifficulty;
        import co.rsk.util.HexUtils;
        import org.ethereum.core.Block;
        import org.ethereum.db.BlockStore;
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;
        import picocli.CommandLine;

        import java.lang.invoke.MethodHandles;
        import java.util.stream.Collectors;

/**
         * CLI tool to print block details for a specific range
         */
        @CommandLine.Command(name = "print-block-range", mixinStandardHelpOptions = true, version = "print-block-range 1.0",
                description = "Prints block details for a specific block range")
        public class PrintBlockRange extends PicoCliToolRskContextAware {
            private static final Logger logger = LoggerFactory.getLogger(PrintBlockRange.class);
            @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number", required = true)
            private Long fromBlockNumber;

            @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number", required = true)
            private Long toBlockNumber;

            public static void main(String[] args) {
                create(MethodHandles.lookup().lookupClass()).execute(args);
            }

            @Override
            public Integer call() {
                BlockStore blockStore = ctx.getBlockStore();

                for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
                    Block block = blockStore.getChainBlockByNumber(n);
                    if (block == null) {
                        logger.info("PRINT Block {} not found.", n);
                        continue;
                    }

                    BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(block.getHash().getBytes());

                    logger.info(
                            "DIFFICULTY2 {} [blockHeight={}, blockHash={}] blockNum: [{}], parentHash:[{}], coinbase:[{}], uncles:[{}], difficulty:[{}], totalDifficulty:[{}], txs:[{}], txsHashes:[{}], timestamp:{}, fees:{}",
                            block.getTimestamp(),
                            block.getNumber(),
                            block.getHash(),
                            block.getNumber(),
                            block.getParentHash(),
                            block.getCoinbase(),
                            String.join(", ", HexUtils.toJsonHex(block.getUnclesHash())),
                            block.getDifficulty(),
                            totalDifficulty,
                            block.getTransactionsList().size(),
                            String.join(", ", block.getTransactionsList().stream().map((t)->t.getHash().toString()).collect(Collectors.joining())),
                            block.getTimestamp(),
                            block.getFeesPaidToMiner()
                    );
                }

                return 0;
            }
        }