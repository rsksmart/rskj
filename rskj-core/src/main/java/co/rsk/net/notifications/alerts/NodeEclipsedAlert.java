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

import co.rsk.net.notifications.panics.PanicFlag;

public class NodeEclipsedAlert extends FederationAlert {
    private long timeWithoutFederationNotifications;

    public NodeEclipsedAlert(long timeWithoutFederationNotifications) {
        this.timeWithoutFederationNotifications = timeWithoutFederationNotifications;
    }

    public FederationAlert copy() {
        return new NodeEclipsedAlert(timeWithoutFederationNotifications);
    }

    public long getTimeWithoutFederationNotifications() {
        return timeWithoutFederationNotifications;
    }

    @Override
    public PanicFlag getAssociatedPanicFlag(long forBlockNumber) {
        return PanicFlag.FederationEclipsed(forBlockNumber);
    }
}
