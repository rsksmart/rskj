package co.rsk.peg;

import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;

public enum BridgeEvents {

    LOG_UPDATE_COLLECTIONS("logUpdateCollections",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(false, "sender", SolidityType.getType("address"))
            }
    );

    private String eventName;
    private CallTransaction.Param[] params;

    BridgeEvents(String eventName, CallTransaction.Param[] params) {
        this.eventName = eventName;
        this.params = params;
    }

    public CallTransaction.Function getEvent() {
        return CallTransaction.Function.fromEventSignature(eventName, params);
    }
}

