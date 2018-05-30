package co.rsk.net.notifications.utils;

import co.rsk.net.notifications.FederationNotification;

public interface FederationNotificationSigner {
    void sign(FederationNotification notification);
}
