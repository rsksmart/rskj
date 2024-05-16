package co.rsk.peg.federation;

public enum FederationChangeFunction {
    CREATE("create"),
    ADD("add"),
    ADD_MULTI("add-multi"),
    COMMIT("commit"),
    ROLLBACK("rollback")
    ;

    private final String key;

    FederationChangeFunction(String key) { this.key = key; }

    public String getKey() { return key; }
}
