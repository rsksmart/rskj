package co.rsk;

import org.ethereum.db.ReceiptStore;

public class SnappyReceiptsMetrics extends SnappyMetrics {
    private final ReceiptStore store;

    public SnappyReceiptsMetrics(String path, boolean rW, int values, int seed, boolean useSnappy) {
        super(path, rW, values, seed, useSnappy);
        this.store = objects.getReceiptStore();
    }


}
