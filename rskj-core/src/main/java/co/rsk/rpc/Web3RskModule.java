package co.rsk.rpc;

import co.rsk.rpc.modules.rsk.RskModule;

public interface Web3RskModule {

    default String rsk_getRawTransactionReceiptByHash(String transactionHash)  {
        return getRskModule().getRawTransactionReceiptByHash(transactionHash);
    }

    default String rsk_flush()  {
        getRskModule().flush();
        return "";
    }
    default String rsk_shutdown()  {
        getRskModule().shutdown();
        return "";
    }

    default String[] rsk_getTransactionReceiptNodesByHash(String blockHash, String transactionHash) {
        return getRskModule().getTransactionReceiptNodesByHash(blockHash, transactionHash);
    }

    default String rsk_getRawBlockHeaderByHash(String blockHash) {
        return getRskModule().getRawBlockHeaderByHash(blockHash);
    }

    default String rsk_getRawBlockHeaderByNumber(String bnOrId) {
        return getRskModule().getRawBlockHeaderByNumber(bnOrId);
    }

    RskModule getRskModule();
}
