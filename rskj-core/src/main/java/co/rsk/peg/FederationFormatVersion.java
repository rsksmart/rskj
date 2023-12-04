package co.rsk.peg;

public enum FederationFormatVersion {
    STANDARD_MULTISIG_FEDERATION(1000),
    NON_STANDARD_ERP_FEDERATION(2000),
    P2SH_ERP_FEDERATION(3000);

    private int version;

    FederationFormatVersion(int i) {
        this.version = i;
    }

    public int getFormatVersion() {
        return version;
    }
}
