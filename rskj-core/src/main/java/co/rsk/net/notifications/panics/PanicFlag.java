package co.rsk.net.notifications.panics;

import co.rsk.net.notifications.FederationNotification;

/***
 * Instances of this class represent the possible panic flags resulting from processing
 * {@link FederationNotification} instances.
 *
 * @author Diego Masini
 * @author Jose Orlicki
 * @author Ariel Mendelzon
 *
 */
public class PanicFlag {
    public enum Reason {
        FEDERATION_FORKED("Federation forked"),
        NODE_FORKED("Node forked"),
        NODE_ECLIPSED("Node eclipsed"),
        FEDERATION_FROZEN("Federation frozen");

        String description;
        Reason(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private Reason reason;
    private long sinceBlockNumber;

    private PanicFlag(Reason reason, long sinceBlockNumber) {
        this.reason = reason;
        this.sinceBlockNumber = sinceBlockNumber;
    }

    public static PanicFlag FederationEclipsed(long sinceBlockNumber) {
        return new PanicFlag(Reason.NODE_ECLIPSED, sinceBlockNumber);
    }

    public static PanicFlag FederationBlockchainForked(long sinceBlockNumber) {
        return new PanicFlag(Reason.FEDERATION_FORKED, sinceBlockNumber);
    }

    public static PanicFlag NodeBlockchainForked(long sinceBlockNumber) {
        return new PanicFlag(Reason.NODE_FORKED, sinceBlockNumber);
    }

    public static PanicFlag FederationFrozen(long sinceBlockNumber) {
        return new PanicFlag(Reason.FEDERATION_FROZEN, sinceBlockNumber);
    }

    public static PanicFlag of(Reason reason) {
        return new PanicFlag(reason, 0);
    }

    public Reason getReason() {
        return reason;
    }

    public long getSinceBlockNumber() {
        return sinceBlockNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (!this.getClass().equals(other.getClass())) {
            return false;
        }

        final PanicFlag pf = (PanicFlag) other;

        return this.reason.equals(pf.getReason());
    }

    @Override
    public int hashCode() {
        return this.reason.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s from block #%d", this.getReason(), getSinceBlockNumber());
    }
}
