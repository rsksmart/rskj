package co.rsk.rpc.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.jsonrpc4j.InvocationListener;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class JsonRpcWeb3ServerHandlerInvocationListener implements InvocationListener {
    private final Map<String, Boolean> methodsToCheckMap = new HashMap<>();

    public JsonRpcWeb3ServerHandlerInvocationListener() {
        methodsToCheckMap.put("eth_getBlockTransactionCountByHash", true);
        methodsToCheckMap.put("eth_getBlockByHash", true);
        methodsToCheckMap.put("eth_getTransactionByHash", true);
        methodsToCheckMap.put("eth_getTransactionByBlockHashAndIndex", true);
        methodsToCheckMap.put("eth_getUncleByBlockHashAndIndex", true);
    }

    @Override
    public void willInvoke(Method method, List<JsonNode> arguments) {
        if(!methodsToCheckMap.getOrDefault(method.getName(), false)){
            return;
        }

        Class<?>[] paramClasses = method.getParameterTypes();
        int paramCount = method.getParameterCount();

        for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
            Class<?> methodParamClass = paramClasses[paramIndex];
            JsonNode node = arguments.get(paramIndex);

            if (!methodParamClass.getSimpleName().equalsIgnoreCase(node.getNodeType().name())) {
                throw invalidParamError(
                        String.format(
                                "invalid argument %s json: cannot unmarshal %s into of type %s",
                                paramIndex,
                                node.getNodeType().name().toLowerCase(),
                                methodParamClass.getSimpleName().toLowerCase()
                        )
                );
            }
        }
    }

    @Override
    public void didInvoke(Method method, List<JsonNode> arguments, Object result, Throwable t, long duration) {

    }
}
