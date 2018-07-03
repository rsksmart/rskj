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

import co.rsk.net.notifications.FederationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NotificationsModuleImpl implements NotificationsModule {
    private final FederationState federationState;

    @Autowired
    public NotificationsModuleImpl(FederationState federationState) {
        this.federationState = federationState;
    }

    @Override
    public List<PanicFlag> getPanicStatus() {
        return federationState.getPanicStatus().getFlags().stream()
                .sorted(Comparator.comparing(f -> Long.valueOf(f.getSinceBlockNumber())))
                .map(flag -> PanicFlag.fromPanicStatusFlag(flag))
                .collect(Collectors.toList());
    }
}
