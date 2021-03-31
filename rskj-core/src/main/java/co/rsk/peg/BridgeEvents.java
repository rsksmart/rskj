package co.rsk.peg;

import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;

public enum BridgeEvents {

    LOCK_BTC("lock_btc",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, Fields.RECEIVER, SolidityType.getType(SolidityType.ADDRESS)),
                    new CallTransaction.Param(false, "btcTxHash", SolidityType.getType(SolidityType.BYTES32)),
                    new CallTransaction.Param(false, "senderBtcAddress", SolidityType.getType(SolidityType.STRING)),
                    new CallTransaction.Param(false, Fields.AMOUNT, SolidityType.getType(SolidityType.INT))
            }
    ),
    PEGIN_BTC("pegin_btc",
        new CallTransaction.Param[]{
            new CallTransaction.Param(true, Fields.RECEIVER, SolidityType.getType(SolidityType.ADDRESS)),
            new CallTransaction.Param(true, "btcTxHash", SolidityType.getType(SolidityType.BYTES32)),
            new CallTransaction.Param(false, Fields.AMOUNT, SolidityType.getType(SolidityType.INT)),
            new CallTransaction.Param(false, "protocolVersion", SolidityType.getType(SolidityType.INT))
        }
    ),
    REJECTED_PEGIN("rejected_pegin",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, "btcTxHash", SolidityType.getType("bytes32")),
                    new CallTransaction.Param(false, "reason", SolidityType.getType("int"))
            }
    ),
    UNREFUNDABLE_PEGIN("unrefundable_pegin",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, "btcTxHash", SolidityType.getType("bytes32")),
                    new CallTransaction.Param(false, "reason", SolidityType.getType("int"))
            }
    ),
    UPDATE_COLLECTIONS("update_collections",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(false, Fields.SENDER, SolidityType.getType(SolidityType.ADDRESS))
            }
    ),
    ADD_SIGNATURE("add_signature",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, "releaseRskTxHash", SolidityType.getType(SolidityType.BYTES32)),
                    new CallTransaction.Param(true, "federatorRskAddress", SolidityType.getType(SolidityType.ADDRESS)),
                    new CallTransaction.Param(false, "federatorBtcPublicKey", SolidityType.getType(SolidityType.BYTES))
            }
    ),
    RELEASE_BTC("release_btc",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, "releaseRskTxHash", SolidityType.getType(SolidityType.BYTES32)),
                    new CallTransaction.Param(false, "btcRawTransaction", SolidityType.getType(SolidityType.BYTES))
            }
    ),
    COMMIT_FEDERATION("commit_federation",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(false, "oldFederationBtcPublicKeys", SolidityType.getType(SolidityType.BYTES)),
                    new CallTransaction.Param(false, "oldFederationBtcAddress", SolidityType.getType(SolidityType.STRING)),
                    new CallTransaction.Param(false, "newFederationBtcPublicKeys", SolidityType.getType(SolidityType.BYTES)),
                    new CallTransaction.Param(false, "newFederationBtcAddress", SolidityType.getType(SolidityType.STRING)),
                    new CallTransaction.Param(false, "activationHeight", SolidityType.getType(SolidityType.INT256))
            }
    ),
    RELEASE_REQUESTED("release_requested",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, "rskTxHash", SolidityType.getType(SolidityType.BYTES32)),
                    new CallTransaction.Param(true, "btcTxHash", SolidityType.getType(SolidityType.BYTES32)),
                    new CallTransaction.Param(false, Fields.AMOUNT, SolidityType.getType(SolidityType.UINT))
            }
    ),
    RELEASE_REQUEST_RECEIVED("release_request_received",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, Fields.SENDER, SolidityType.getType(SolidityType.ADDRESS)),
                    new CallTransaction.Param(false, "btcDestinationAddress", SolidityType.getType(SolidityType.BYTES)),
                    new CallTransaction.Param(false, Fields.AMOUNT, SolidityType.getType(SolidityType.UINT))
            }
    ),
    RELEASE_REQUEST_REJECTED("release_request_rejected",
            new CallTransaction.Param[]{
                    new CallTransaction.Param(true, Fields.SENDER, SolidityType.getType(SolidityType.ADDRESS)),
                    new CallTransaction.Param(false, Fields.AMOUNT, SolidityType.getType(SolidityType.UINT)),
                    new CallTransaction.Param(false, Fields.REASON, SolidityType.getType(SolidityType.INT))
            }
    );

    private String eventName;
    private CallTransaction.Param[] params;

    BridgeEvents(String eventName, CallTransaction.Param[] params) {
        this.eventName = eventName;
        this.params = params.clone();
    }

    public CallTransaction.Function getEvent() {
        return CallTransaction.Function.fromEventSignature(eventName, params);
    }

    private static class Fields {
        private static final String RECEIVER = "receiver";
        private static final String SENDER = "sender";
        private static final String AMOUNT = "amount";
        private static final String REASON = "reason";
    }
}
