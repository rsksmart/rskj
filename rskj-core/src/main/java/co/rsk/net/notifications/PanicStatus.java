package co.rsk.net.notifications;

public class PanicStatus {
    private PanicStatusReason reason;
    private long sinceBlockNumber;

    private PanicStatus(PanicStatusReason reason, long sinceBlockNumber) {
        this.reason = reason;
        this.sinceBlockNumber = sinceBlockNumber;
    }

    public static PanicStatus NoPanic(long sinceBlockNumber) {
        return new PanicStatus(PanicStatusReason.NONE, sinceBlockNumber);
    }

    public static PanicStatus FederationEclipsedPanic(long sinceBlockNumber) {
        return new PanicStatus(PanicStatusReason.FEDERATION_ECLIPSED, sinceBlockNumber);
    }

    public static PanicStatus FederationBlockchainForkedPanic(long sinceBlockNumber) {
        return new PanicStatus(PanicStatusReason.FEDERATION_FORKED, sinceBlockNumber);
    }

    public static PanicStatus NodeBlockchainForkedPanic(long sinceBlockNumber) {
        return new PanicStatus(PanicStatusReason.NODE_FORKED, sinceBlockNumber);
    }

    public static PanicStatus FederationFrozenPanic(long sinceBlockNumber) {
        return new PanicStatus(PanicStatusReason.FEDERATION_FROZEN, sinceBlockNumber);
    }

    public PanicStatusReason getReason() {
        return reason;
    }

    public long getSinceBlockNumber() {
        return sinceBlockNumber;
    }

    public boolean isNoPanic() {
        return reason.equals(PanicStatusReason.NONE);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (!PanicStatus.class.isAssignableFrom(other.getClass())) {
            return false;
        }

        final PanicStatus ps = (PanicStatus) other;

        return this.reason.equals(ps.getReason()) && this.sinceBlockNumber == ps.getSinceBlockNumber();
    }
}
