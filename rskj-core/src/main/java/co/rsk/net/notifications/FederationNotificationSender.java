package co.rsk.net.notifications;

import org.ethereum.crypto.ECKey;

/**
 * Represents the sender of a {@link FederationNotification} notification.
 * This is merely a wrapper around an EC public key.
 *
 * @author Ariel Mendelzon
 */
public class FederationNotificationSender {
    private ECKey publicKey;

    public FederationNotificationSender(ECKey publicKey) {
        this.publicKey = publicKey;
    }

    public ECKey getPublicKey() {
        return publicKey;
    }

    public byte[] getBytes() {
        return publicKey.getPubKey(true );
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        FederationNotificationSender otherSender = (FederationNotificationSender) o;

        return this.getPublicKey().equals(otherSender.getPublicKey());
    }

    @Override
    public int hashCode() {
        return publicKey.hashCode();
    }
}
