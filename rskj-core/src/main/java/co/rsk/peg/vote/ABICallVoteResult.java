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

package co.rsk.peg.vote;

/**
 * Immutable representation of the result of a vote
 * on a given ABI function call.
 * Can either be successful or failed.
 * Both successful and failed vote results
 * can carry an associated result.
 * @author Ariel Mendelzon
 */
public final class ABICallVoteResult {
    private final boolean successful;
    private final Object result;

    public ABICallVoteResult(boolean successful, Object result) {
        this.successful = successful;
        this.result = result;
    }

    public boolean wasSuccessful() {
        return successful;
    }

    public Object getResult() {
        return result;
    }
}
