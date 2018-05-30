package co.rsk.net.notifications.utils;

import co.rsk.config.RskSystemProperties;
import co.rsk.net.notifications.FederationNotification;
import co.rsk.net.notifications.FederationNotificationException;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;

public class NodeFederationNotificationSigner implements FederationNotificationSigner {

    private final RskSystemProperties config;

    public NodeFederationNotificationSigner(RskSystemProperties config) {
        this.config = config;
    }

    @Override
    public void sign(FederationNotification notification) {
        Account account = config.localCoinbaseAccount();
        if (account == null) {
            throw new FederationNotificationException("Failed to find localCoinbaseAccount");
        }

        ECKey ecKey = account.getEcKey();
        byte[] hash = notification.getHash();
        notification.setSignature(ecKey.sign(hash));
    }
}
