package co.rsk.peg.federation;

public enum FederationFormatVersion {
    STANDARD_MULTISIG_FEDERATION(1000),
    NON_STANDARD_ERP_FEDERATION(2000),
    P2SH_ERP_FEDERATION(3000),
    P2SH_P2WSH_ERP_FEDERATION(4000);

    private int version;

    FederationFormatVersion(int version) {
        this.version = version;
    }

    public int getFormatVersion() {
        return version;
    }
}
