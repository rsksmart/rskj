/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package org.ethereum.core.genesis;

import javax.annotation.Nullable;

public enum BlockTag {
    PENDING("pending"), LATEST("latest"), EARLIEST("earliest"), FINALIZED("finalized"), SAFE("safe");

    private final String tag;

    BlockTag(String tag) {
        this.tag = tag;
    }

    @Nullable
    public static BlockTag fromString(String text) {
        for (BlockTag b : BlockTag.values()) {
            if (b.tag.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }

    public String getTag() {
        return tag;
    }

    public boolean tagEquals(String tagName) {
        return tag.contentEquals(tagName);
    }
}