/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core.transaction.parser.util;

import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.util.List;


public final class TypedEnvelopeSchema {

    public enum Shape {
        SCALAR,
        BYTES,
        LIST
    }

    public record FieldSpec(Shape shape, String label, String invalidMessage) {}

    private final String typeName;
    private final List<FieldSpec> fields;

    private TypedEnvelopeSchema(String typeName, List<FieldSpec> fields) {
        this.typeName = typeName;
        this.fields = fields;
    }

    public static TypedEnvelopeSchema of(String typeName, FieldSpec... fields) {
        return new TypedEnvelopeSchema(typeName, List.of(fields));
    }

    public static FieldSpec scalar(String label) {
        return scalar(label, label + " is not valid");
    }

    public static FieldSpec scalar(String label, String invalidMessage) {
        return new FieldSpec(Shape.SCALAR, label, invalidMessage);
    }

    public static FieldSpec bytes(String label) {
        return new FieldSpec(Shape.BYTES, label, null);
    }

    public static FieldSpec list(String label) {
        return new FieldSpec(Shape.LIST, label, null);
    }

    public void validate(RLPList txFields) {
        CommonParsingUtils.requireFieldCount(txFields, fields.size(), typeName);
        for (int i = 0; i < fields.size(); i++) {
            RLPElement element = txFields.get(i);
            FieldSpec field = fields.get(i);
            switch (field.shape()) {
                case LIST -> CommonParsingUtils.requireListFramed(element, field.label());
                case BYTES -> CommonParsingUtils.requireByteString(element, "Transaction field at index " + i);
                case SCALAR -> {
                    CommonParsingUtils.requireByteString(element, "Transaction field at index " + i);
                    byte[] value = element.getRLPData();
                    CommonParsingUtils.requireDataWordBytes(value, field.invalidMessage());
                    CommonParsingUtils.requireCanonicalScalar(value, field.label());
                }
            }
        }
    }
}
