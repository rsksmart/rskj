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

package co.rsk.trie;

/**
 * Created by diegogz on 4/24/17.
 */
public class TriePutResult {

    private Trie trie;

    private ResultAction action;

    private int sonToDelete;

    private int sonToDeleteNumberOfSons;

    public TriePutResult(ResultAction action, Trie trie) {
        this.trie = trie;
        this.action = action;
    }

    public TriePutResult(ResultAction action, int sonToDelete, int sonToDeleteNumberOfSons) {
        this.action = action;
        this.sonToDelete = sonToDelete;
        this.sonToDeleteNumberOfSons = sonToDeleteNumberOfSons;
    }

    public TriePutResult(ResultAction action, int sonToDeleteNumberOfSons) {
        this.action = action;
        this.sonToDeleteNumberOfSons = sonToDeleteNumberOfSons;
    }

    public Trie getTrie() {
        return trie;
    }

    public ResultAction getAction() {
        return action;
    }

    public int getSonToDelete() {
        return sonToDelete;
    }

    public int sonToDeleteNumberOfSons() {
        return sonToDeleteNumberOfSons;
    }
}
