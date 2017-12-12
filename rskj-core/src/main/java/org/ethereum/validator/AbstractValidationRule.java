/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.validator;

import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;

/**
 * Holds errors list to share between all rules
 *
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public abstract class AbstractValidationRule implements ValidationRule {

    protected List<String> errors = new LinkedList<>();

    @Override
    public List<String> getErrors() {
        return errors;
    }

    public void logErrors(Logger logger) {
        if (logger.isErrorEnabled()) {
            for (String msg : errors) {
                logger.warn("{} invalid: {}", getEntityClass().getSimpleName(), msg);
            }
        }
    }

    public abstract Class getEntityClass();
}
