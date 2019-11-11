package co.rsk.peg;

import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;

public enum BridgeEvents {

    LOG_UPDATE_COLLECTIONS("logUpdateCollections",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(false, "sender", SolidityType.getType("address"))
            }
    ),

    LOG_RELEASE_REQUESTED("logReleaseRequested",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, "sender", SolidityType.getType("address")),
                    new CallTransaction.Param(true, "rskTxHash", SolidityType.getType("bytes32")),
                    new CallTransaction.Param(true, "btcTxHash", SolidityType.getType("bytes32")),
                    new CallTransaction.Param(false, "amount", SolidityType.getType("uint"))
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

