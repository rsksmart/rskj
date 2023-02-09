/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by ajlopez on 19/04/2017.
 */
public class ModuleDescription {
    private String name;
    private String version;
    private boolean enabled;
    private long timeout;
    private Map<String, Long> methodTimeoutMap;

    private List<String> enabledMethods;
    private List<String> disabledMethods;

    public ModuleDescription(String name, String version, boolean enabled, List<String> enabledMethods, List<String> disabledMethods, long timeout, Map<String, Long> methodTimeoutMap) {
        this.name = name;
        this.version = version;
        this.enabled = enabled;
        this.timeout = timeout;
        this.methodTimeoutMap = methodTimeoutMap;
        this.enabledMethods = enabledMethods == null ? new ArrayList<>() : enabledMethods;
        this.disabledMethods = disabledMethods == null ? new ArrayList<>() : disabledMethods;
    }

    public String getName() {
        return this.name;
    }

    public String getVersion() {
        return this.version;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public List<String> getEnabledMethods() {
        return this.enabledMethods;
    }

    public List<String> getDisabledMethods() {
        return this.disabledMethods;
    }

    public boolean methodIsInModule(String methodName) {
        if (methodName == null) {
            return false;
        }

        if (!methodName.startsWith(this.name)) {
            return false;
        }

        if (methodName.length() == this.name.length()) {
            return false;
        }

        if (methodName.charAt(this.name.length()) != '_') {
            return false;
        }

        return true;
    }

    public long getTimeout() {
        return timeout;
    }

    public long getTimeout(String methodName, long defaultTimeout) {
        if (methodName == null) {
            throw new IllegalArgumentException("methodName cannot be null.");
        }

        if (methodName.isEmpty()) {
            return defaultTimeout;
        }

        Optional<Long> optMethodTimeout = Optional.ofNullable(getMethodTimeout(methodName));

        return optMethodTimeout.orElseGet(this::getTimeout);
    }

    public Long getMethodTimeout(String methodName) {
        return methodTimeoutMap.get(methodName);
    }

    public boolean methodIsEnable(String methodName) {
        if (!this.isEnabled()) {
            return false;
        }

        if (!this.methodIsInModule(methodName)) {
            return false;
        }

        if (this.disabledMethods.contains(methodName)) {
            return false;
        }

        if (this.enabledMethods.isEmpty() && this.disabledMethods.isEmpty()) {
            return true;
        }

        if (this.enabledMethods.contains(methodName)) {
            return true;
        }

        if (!this.enabledMethods.isEmpty()) {
            return false;
        }

        return true;
    }
}