package co.rsk.rpc.exception;

public class JsonRpcResponseLimitError extends Error {
    public static final int ERROR_CODE = -32011;
    private static final String ERROR_MSG_WITH_LIMIT = "Response size limit exceeded. Max response size %d bytes";
    private static final long serialVersionUID = 3145337981628533511L;

    public JsonRpcResponseLimitError(int  max) {
        super(String.format(ERROR_MSG_WITH_LIMIT, max));
    }

}
