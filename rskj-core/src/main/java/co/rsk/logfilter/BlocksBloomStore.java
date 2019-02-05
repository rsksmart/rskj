package co.rsk.logfilter;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 05/02/2019.
 */
public class BlocksBloomStore {
    public static final int NO_BLOCKS = 64;

    private final Map<Long, BlocksBloom> blocksBloom = new HashMap<>();

    public BlocksBloom getBlocksBloomByNumber(long number) {
        return this.blocksBloom.get(firstNumberInRange(number));
    }

    public void setBlocksBloom(BlocksBloom blocksBloom) {
        this.blocksBloom.put(blocksBloom.fromBlock(), blocksBloom);
    }

    public static long firstNumberInRange(long number) {
        return number - (number % NO_BLOCKS);
    }

    public static long lastNumberInRange(long number) {
        return firstNumberInRange(number) + NO_BLOCKS - 1;
    }
}
