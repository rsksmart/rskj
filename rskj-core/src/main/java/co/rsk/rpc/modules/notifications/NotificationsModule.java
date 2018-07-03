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

import java.util.List;
import java.util.function.Function;

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

        public static FederationAlert fromFederationAlert(co.rsk.net.notifications.alerts.FederationAlert alert) {
            return alert.getConverterForNotificationsModule().apply(alert);
        }

        public FederationAlert(String code) {
            this.code = code;
        }
    }

    class FederationFrozenAlert extends FederationAlert {
        public String source;
        public String confirmationBlockHash;
        public long confirmationBlockNumber;

        public static final Function<co.rsk.net.notifications.alerts.FederationAlert, FederationAlert> convert = a -> {
            co.rsk.net.notifications.alerts.FederationFrozenAlert ffa = (co.rsk.net.notifications.alerts.FederationFrozenAlert) a;
            return new FederationFrozenAlert(
                    Hex.toHexString(ffa.getSource().getBytes()),
                    Hex.toHexString(ffa.getConfirmationBlockHash().getBytes()),
                    ffa.getConfirmationBlockNumber());
        };

        public FederationFrozenAlert(String source, String confirmationBlockHash, long confirmationBlockNumber) {
            super("federation_frozen");
            this.source = source;
            this.confirmationBlockHash = confirmationBlockHash;
            this.confirmationBlockNumber = confirmationBlockNumber;
        }
    }

    class ForkAttackAlert extends FederationAlert {
        public String source;
        public String confirmationBlockHash;
        public String inBestChainBlockHash;
        public long confirmationBlockNumber;
        public long bestBlockNumber;
        public boolean isFederatedNode;

        public static final Function<co.rsk.net.notifications.alerts.FederationAlert, FederationAlert> convert = a -> {
            co.rsk.net.notifications.alerts.ForkAttackAlert faa = (co.rsk.net.notifications.alerts.ForkAttackAlert) a;
            return new ForkAttackAlert(
                    Hex.toHexString(faa.getSource().getBytes()),
                    Hex.toHexString(faa.getConfirmationBlockHash().getBytes()),
                    faa.getConfirmationBlockNumber(),
                    Hex.toHexString(faa.getInBestChainBlockHash().getBytes()),
                    faa.getBestBlockNumber(),
                    faa.isFederatedNode());
        };

        public ForkAttackAlert(String source, String confirmationBlockHash, long confirmationBlockNumber, String inBestChainBlockHash, long bestBlockNumber, boolean isFederatedNode) {
            super("fork_attack");
            this.source = source;
            this.confirmationBlockHash = confirmationBlockHash;
            this.confirmationBlockNumber = confirmationBlockNumber;
            this.inBestChainBlockHash = inBestChainBlockHash;
            this.bestBlockNumber = bestBlockNumber;
            this.isFederatedNode = isFederatedNode;
        }
    }

    class NodeEclipsedAlert extends FederationAlert {
        public long timeWithoutFederationNotifications;

        public static final Function<co.rsk.net.notifications.alerts.FederationAlert, FederationAlert> convert = a -> {
            co.rsk.net.notifications.alerts.NodeEclipsedAlert nea = (co.rsk.net.notifications.alerts.NodeEclipsedAlert) a;
            return new NodeEclipsedAlert(nea.getTimeWithoutFederationNotifications());
        };

        public NodeEclipsedAlert(long timeWithoutFederationNotifications) {
            super("node_eclipsed");
            this.timeWithoutFederationNotifications = timeWithoutFederationNotifications;
        }
    }

    List<PanicFlag> getPanicStatus();

    List<FederationAlert> getAlerts();
}
