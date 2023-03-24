package co.rsk.rpc.json;

import co.rsk.rpc.exception.JsonRpcResponseLimitException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class LimitedArrayNode extends ArrayNode {
    private final int maxLimit;
    private int totalAdded = 0;


    public LimitedArrayNode(JsonNodeFactory nf) {
        super(nf);
        maxLimit = -1;
    }

    public LimitedArrayNode(JsonNodeFactory nf, int maxLimit) {
        super(nf);
        this.maxLimit = maxLimit;
    }

    @Override
    public ArrayNode add(JsonNode node) {
        if (isLimited()) {
            totalAdded += getLength(node);
            if(totalAdded > maxLimit){
                throw new JsonRpcResponseLimitException(maxLimit);
            }
        }
        return super.add(node);
    }

    private boolean isLimited() {
        return maxLimit > 0;
    }

    private int getLength(JsonNode node) {
        return JsonResponseSizeLimiter.getSizeInBytesWithLimit(node,maxLimit);
    }
}
