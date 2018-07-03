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

package co.rsk.rpc;

import co.rsk.rpc.modules.notifications.NotificationsModule;

import java.util.List;

public interface Web3NotificationsModule {
    default List<NotificationsModule.PanicFlag> notifications_getPanicStatus() { return getNotificationsModule().getPanicStatus(); }

    default List<NotificationsModule.FederationAlert> notifications_getAlerts() { return getNotificationsModule().getAlerts(); }

    NotificationsModule getNotificationsModule();
}

