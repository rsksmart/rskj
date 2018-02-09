package co.rsk.rpc.modules.txpool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.ethereum.core.PendingState;
import org.ethereum.facade.Ethereum;

import java.util.HashMap;
import java.util.Map;

public class TxPoolModuleImpl implements TxPoolModule{

    private Ethereum eth;
    private PendingState pendingState;
    private ObjectMapper mapper;
    private final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;

    public TxPoolModuleImpl(Ethereum eth, PendingState pendingState) {
        this.eth = eth;
        this.pendingState = pendingState;
        this.mapper = new ObjectMapper();
    }

    @Override
    public String content() {
//      return "{"pending": {}, queued: {}}";
        Map<String, JsonNode> txProps = new HashMap<>();
        txProps.put("pending", jsonNodeFactory.objectNode());
        txProps.put("queued", jsonNodeFactory.objectNode());
        JsonNode node = jsonNodeFactory.objectNode().setAll(txProps);
        return node.toString();
    }

    @Override
    public String inspect() {
//      return "{"pending": {}, "queued": {}}";
        Map<String, JsonNode> txProps = new HashMap<>();
        txProps.put("pending", jsonNodeFactory.objectNode());
        txProps.put("queued", jsonNodeFactory.objectNode());
        JsonNode node = jsonNodeFactory.objectNode().setAll(txProps);
        return node.toString();
    }

    @Override
    public String status() {
//        return "{pending: 0, queued: 0}";
        Map<String, JsonNode> txProps = new HashMap<>();
        txProps.put("pending", jsonNodeFactory.numberNode(0));
        txProps.put("queued", jsonNodeFactory.numberNode(0));
        JsonNode node = jsonNodeFactory.objectNode().setAll(txProps);
        return node.toString();
    }
}
