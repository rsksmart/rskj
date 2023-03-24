package co.rsk.rpc.json;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class CustomJsonNodeFactory extends JsonNodeFactory {

    private final int limit;

    public CustomJsonNodeFactory(int limit) {
        this.limit = limit;
    }

    public CustomJsonNodeFactory() {
        limit = -1;
    }

    @Override
    public ArrayNode arrayNode() {
        return limit > 0 ? new LimitedArrayNode(this, limit) : super.arrayNode();
    }


}
