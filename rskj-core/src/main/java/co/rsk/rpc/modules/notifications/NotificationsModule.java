/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.rpc.modules.notifications;

import org.spongycastle.util.encoders.Hex;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface NotificationsModule {
    class PanicFlag {
        public String code;
        public String description;
        public long sinceBlockNumber;

        public PanicFlag(String code, String description, long sinceBlockNumber) {
            this.code = code;
            this.description = description;
            this.sinceBlockNumber = sinceBlockNumber;
        }

        public static PanicFlag fromPanicStatusFlag(co.rsk.net.notifications.panics.PanicFlag flag) {
            return new PanicFlag(flag.getReason().getCode(), flag.getReason().getDescription(), flag.getSinceBlockNumber());
        }
    }

    abstract class FederationAlert {
        public final String code;
        public final long created;

        public static FederationAlert fromFederationAlert(co.rsk.net.notifications.alerts.FederationAlert alert) {
            return alert.getConverterForNotificationsModule().apply(alert);
        }

        public FederationAlert(String code, long created) {
            this.code = code;
            this.created = created;
        }
    }

    class FederationFrozenAlert extends FederationAlert {
        public final List<String> frozenMembers;

        public static final Function<co.rsk.net.notifications.alerts.FederationAlert, FederationAlert> convert = a -> {
            co.rsk.net.notifications.alerts.FederationFrozenAlert ffa = (co.rsk.net.notifications.alerts.FederationFrozenAlert) a;
            return new FederationFrozenAlert(
                    ffa.getCreated().toEpochMilli(),
                    ffa.getFrozenMembers().stream().map(m -> Hex.toHexString(m.getBytes())).collect(Collectors.toList()));
        };

        public FederationFrozenAlert(long created, List<String> frozenMembers) {
            super("federation_frozen", created);
            this.frozenMembers = frozenMembers;
        }
    }

    class ForkAttackAlert extends FederationAlert {
        public final String bestBlockHash;
        public final long bestBlockNumber;
        public final boolean isFederatedNode;

        public static final Function<co.rsk.net.notifications.alerts.FederationAlert, FederationAlert> convert = a -> {
            co.rsk.net.notifications.alerts.ForkAttackAlert faa = (co.rsk.net.notifications.alerts.ForkAttackAlert) a;
            return new ForkAttackAlert(
                    faa.getCreated().toEpochMilli(),
                    Hex.toHexString(faa.getBestBlockHash().getBytes()),
                    faa.getBestBlockNumber(),
                    faa.isFederatedNode());
        };

        public ForkAttackAlert(long created, String bestBlockHash, long bestBlockNumber, boolean isFederatedNode) {
            super("fork_attack", created);
            this.bestBlockHash = bestBlockHash;
            this.bestBlockNumber = bestBlockNumber;
            this.isFederatedNode = isFederatedNode;
        }
    }

    class NodeEclipsedAlert extends FederationAlert {
        public final long timeWithoutFederationNotifications;

        public static final Function<co.rsk.net.notifications.alerts.FederationAlert, FederationAlert> convert = a -> {
            co.rsk.net.notifications.alerts.NodeEclipsedAlert nea = (co.rsk.net.notifications.alerts.NodeEclipsedAlert) a;
            return new NodeEclipsedAlert(nea.getCreated().toEpochMilli(), nea.getTimeWithoutFederationNotifications());
        };

        public NodeEclipsedAlert(long created, long timeWithoutFederationNotifications) {
            super("node_eclipsed", created);
            this.timeWithoutFederationNotifications = timeWithoutFederationNotifications;
        }
    }

    List<PanicFlag> getPanicStatus();

    List<FederationAlert> getAlerts();

    long getLastNotificationReceivedTime();
}
