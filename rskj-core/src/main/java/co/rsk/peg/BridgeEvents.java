package co.rsk.peg;

import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;

public enum BridgeEvents {

    LOG_UPDATE_COLLECTIONS("logUpdateCollections",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(false, "sender", SolidityType.getType("address"))
            }
    ),
    LOG_BTC("lock_btc",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, "receiver", SolidityType.getType("address")),
                    new CallTransaction.Param(false, "btcTxHash", SolidityType.getType("bytes32")),
                    new CallTransaction.Param(false, "senderBtcAddress", SolidityType.getType("string")),
                    new CallTransaction.Param(false, "amount", SolidityType.getType("int"))
            })
    ;

    private String eventName;
    private CallTransaction.Param[] params;

    BridgeEvents(String eventName, CallTransaction.Param[] params) {
        this.eventName = eventName;
        this.params = params.clone();
    }

    public CallTransaction.Function getEvent() {
        return CallTransaction.Function.fromEventSignature(eventName, params);
    }
}

