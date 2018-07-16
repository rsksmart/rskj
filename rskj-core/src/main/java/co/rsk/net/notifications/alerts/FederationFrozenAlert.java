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

package co.rsk.net.notifications.alerts;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.notifications.FederationNotificationSender;
import co.rsk.net.notifications.panics.PanicFlag;
import co.rsk.rpc.modules.notifications.NotificationsModule;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public class FederationFrozenAlert extends FederationAlert {
    private List<FederationNotificationSender> frozenMembers;

    public FederationFrozenAlert(Instant created, List<FederationNotificationSender> frozenMembers) {
        super(created);
        this.frozenMembers = frozenMembers;
    }

    public List<FederationNotificationSender> getFrozenMembers() {
        return frozenMembers;
    }

    @Override
    public PanicFlag getAssociatedPanicFlag(long forBlockNumber) {
        return PanicFlag.FederationFrozen(forBlockNumber);
    }

    @Override
    public Function<FederationAlert, NotificationsModule.FederationAlert> getConverterForNotificationsModule() {
        return NotificationsModule.FederationFrozenAlert.convert;
    }
}
