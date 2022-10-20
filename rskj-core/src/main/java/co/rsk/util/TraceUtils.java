package co.rsk.util;

import org.apache.commons.lang3.RandomStringUtils;

public class TraceUtils {
    public static final String RSK_WIRE_TRACE_ID = "connection.id";
    public static final String ETHEREUM_CLIENT_ID = "eth-client.id";
    public static final String JSON_RPC_REQ_ID = "jrpc-req.id";
    private TraceUtils(){}

    public static String getRandomId(){
        return RandomStringUtils.randomAlphanumeric(15);
    }
}
