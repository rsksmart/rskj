package co.rsk.rpc.modules.rsk;

public interface RskModule {

    String getRawTransactionReceiptByHash(String transactionHash);

    String[] getTransactionReceiptNodesByHash(String blockHash, String transactionHash);

    String getRawBlockHeaderByHash(String blockHash);

    String getRawBlockHeaderByNumber(String bnOrId);

    void shutdown();
    void flush();

}
