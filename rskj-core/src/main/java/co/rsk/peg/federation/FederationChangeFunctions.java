package co.rsk.peg.federation;

public enum FederationChangeFunctions {
    CREATE("create"),
    ADD("add"),
    ADD_MULTI("add-multi"),
    COMMIT("commit"),
    ROLLBACK("rollback")
    ;

    private final String key;

    FederationChangeFunctions(String key) { this.key = key; }

    public String getKey() { return key; }
}
