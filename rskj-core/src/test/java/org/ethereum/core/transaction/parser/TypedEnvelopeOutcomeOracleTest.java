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
package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.exception.InvalidRskAddressException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression oracle for the raw typed-envelope parsers (Type-1, Type-2, Type-4), seen only through
 * {@link RawTransactionEnvelopeParser#parse(byte[])}.
 *
 * <p>It pins today's outcome, exception type and exact message, for every violation the parsers can
 * report and for every pair of violations whose winner depends on the check order. Expected values
 * are hand-written literals, never computed by production code, so the suite can prove that a later
 * rewrite of the parsers keeps behaviour.
 *
 * <p>The contract it pins, first throwing step wins:
 * <ol start="0">
 *   <li>RLP shape of the payload (outside the type parsers)</li>
 *   <li>field count</li>
 *   <li>{@code to} address length, then the Type-4 non-null destination</li>
 *   <li>byte-string framing of every non-list field, in index order</li>
 *   <li>access list codec, then (Type-4) the authorization list codec</li>
 *   <li>fee cap relationship (Type-2, Type-4)</li>
 *   <li>data-word size of the scalars, in the scalar check order</li>
 *   <li>canonical encoding of the same scalars, in the same order</li>
 *   <li>signature state: yParity, incomplete pair, r, s, chainId</li>
 *   <li>Type-4 outer signature format</li>
 *   <li>canonical re-encoding of the whole envelope</li>
 * </ol>
 * A pair case combines one representative violation of two different steps and expects the message
 * of the earlier step, which is the contract above. Pairs inside one step pin the order within it.
 */
class TypedEnvelopeOutcomeOracleTest {

    private static final String ADDRESS_HEX = "1234567890123456789012345678901234567890";
    private static final String CHAIN_ID_HEX = "21";
    private static final String NONCE_HEX = "01";
    private static final String GAS_PRICE_HEX = "3b9aca00";
    private static final String MAX_PRIORITY_HEX = "3b9aca00";
    private static final String MAX_FEE_HEX = "77359400";
    private static final String GAS_LIMIT_HEX = "5208";
    private static final String VALUE_HEX = "0de0b6b3a7640000";
    private static final String DATA_HEX = "010203";
    private static final String ACCESS_ADDRESS_HEX = "aa".repeat(20);
    private static final String ACCESS_KEY_HEX = "bb".repeat(32);
    private static final String Y_PARITY_HEX = "01";
    private static final String R_HEX = "11".repeat(32);
    private static final String S_HEX = "22".repeat(32);
    private static final String ORDER_MINUS_ONE_HEX =
            "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140";
    private static final String ORDER_HEX =
            "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141";
    private static final String AUTH_ADDRESS_HEX = "00".repeat(19) + "03";

    /** rlp([[0xaa*20, [0xbb*32]]]), written out by hand: f8 38 | f7 | 94 addr | e1 a0 key. */
    private static final String ACCESS_LIST_HEX = "f838f794" + ACCESS_ADDRESS_HEX + "e1a0" + ACCESS_KEY_HEX;

    /** rlp([[33, 0x00..03, "", "", 0x11*32, 0x22*32]]), written out by hand: f8 5c | f8 5a | ... */
    private static final String AUTH_LIST_HEX = "f85cf85a" + "21" + "94" + AUTH_ADDRESS_HEX + "80" + "80"
            + "a0" + R_HEX + "a0" + S_HEX;

    private static final String LEADING_ZERO_SUFFIX = " must not have leading zero bytes; zero is encoded as the empty string";

    // ---------------------------------------------------------------------------------------------
    // Layouts
    // ---------------------------------------------------------------------------------------------

    private static final Layout TYPE_1 = new Layout("type1", TransactionType.TYPE_1.getByteCode(),
            "RSKIP546 (EIP-2930)", 11, 6, base(
            "chainId", item(CHAIN_ID_HEX),
            "nonce", item(NONCE_HEX),
            "gasPrice", item(GAS_PRICE_HEX),
            "gasLimit", item(GAS_LIMIT_HEX),
            "to", item(ADDRESS_HEX),
            "value", item(VALUE_HEX),
            "data", item(DATA_HEX),
            "accessList", raw(ACCESS_LIST_HEX),
            "yParity", item(Y_PARITY_HEX),
            "r", item(R_HEX),
            "s", item(S_HEX)));

    private static final Layout TYPE_2 = new Layout("type2", TransactionType.TYPE_2.getByteCode(),
            "RSKIP546 (EIP-1559)", 12, 7, base(
            "chainId", item(CHAIN_ID_HEX),
            "nonce", item(NONCE_HEX),
            "maxPriorityFee", item(MAX_PRIORITY_HEX),
            "maxFee", item(MAX_FEE_HEX),
            "gasLimit", item(GAS_LIMIT_HEX),
            "to", item(ADDRESS_HEX),
            "value", item(VALUE_HEX),
            "data", item(DATA_HEX),
            "accessList", raw(ACCESS_LIST_HEX),
            "yParity", item(Y_PARITY_HEX),
            "r", item(R_HEX),
            "s", item(S_HEX)));

    private static final Layout TYPE_4 = new Layout("type4", TransactionType.TYPE_4.getByteCode(),
            "RSKIP545 (EIP-7702)", 13, 7, base(
            "chainId", item(CHAIN_ID_HEX),
            "nonce", item(NONCE_HEX),
            "maxPriorityFee", item(MAX_PRIORITY_HEX),
            "maxFee", item(MAX_FEE_HEX),
            "gasLimit", item(GAS_LIMIT_HEX),
            "to", item(ADDRESS_HEX),
            "value", item(VALUE_HEX),
            "data", item(DATA_HEX),
            "accessList", raw(ACCESS_LIST_HEX),
            "authList", raw(AUTH_LIST_HEX),
            "yParity", item(Y_PARITY_HEX),
            "r", item(R_HEX),
            "s", item(S_HEX)));

    // ---------------------------------------------------------------------------------------------
    // Rejected inputs
    // ---------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedCases")
    void rejectsWithExactOutcome(Case testCase) {
        byte[] rawData = testCase.rawData();

        Throwable thrown = assertThrows(Throwable.class, () -> RawTransactionEnvelopeParser.parse(rawData));

        assertEquals(testCase.exceptionType(), thrown.getClass());
        assertEquals(testCase.message(), thrown.getMessage());
    }

    static Stream<Case> rejectedCases() {
        List<Case> cases = new ArrayList<>();
        cases.addAll(payloadShapeCases());
        for (Layout layout : List.of(TYPE_1, TYPE_2, TYPE_4)) {
            cases.addAll(singleViolationCases(layout));
            cases.addAll(stepPairCases(layout));
            cases.addAll(scalarCases(layout));
            cases.addAll(framingOrderCases(layout));
            cases.addAll(signatureOrderCases(layout));
        }
        cases.addAll(type4ListOrderCases());
        return cases.stream();
    }

    /** Step 0: the payload is not a single RLP list. */
    private static List<Case> payloadShapeCases() {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("step0 emptyInput", new byte[0], IllegalArgumentException.class,
                "Transaction raw data cannot be null or empty"));
        cases.add(new Case("step0 nullInput", null, IllegalArgumentException.class,
                "Transaction raw data cannot be null or empty"));
        cases.add(new Case("step0 payloadIsNotAList", Hex.decode("0180"), IllegalArgumentException.class,
                "The decoded element wasn't a list"));
        cases.add(new Case("step0 payloadHoldsTwoItems", Hex.decode("01c0c0"), IllegalArgumentException.class,
                "Expected one RLP item but got 2"));
        return cases;
    }

    /** Every single violation of every step, one case each. Field names are those of the layout. */
    private static List<Case> singleViolationCases(Layout layout) {
        List<Case> cases = new ArrayList<>();
        for (Violation violation : violations(layout)) {
            cases.add(violation.toCase(layout));
        }
        return cases;
    }

    /**
     * One case per pair of representative violations from different steps. Both are built into the
     * same envelope and the earlier step must win. A pair is skipped when both violations edit the
     * same field, since they could not coexist.
     */
    private static List<Case> stepPairCases(Layout layout) {
        List<Violation> representatives = new ArrayList<>();
        for (Violation violation : violations(layout)) {
            if (violation.representative()) {
                representatives.add(violation);
            }
        }
        List<Case> cases = new ArrayList<>();
        for (int i = 0; i < representatives.size(); i++) {
            for (int j = i + 1; j < representatives.size(); j++) {
                Violation earlier = representatives.get(i);
                Violation later = representatives.get(j);
                if (earlier.touchesSameFieldAs(later)) {
                    throw new IllegalStateException("representatives must be independent: " + earlier.label()
                            + " / " + later.label());
                }
                List<Edit> edits = new ArrayList<>(earlier.edits());
                edits.addAll(later.edits());
                cases.add(new Case(layout.label() + " pair step" + earlier.step() + "+step" + later.step()
                        + " " + earlier.label() + " + " + later.label(),
                        layout.envelope(edits), earlier.exceptionType(), earlier.message()));
            }
        }
        return cases;
    }

    /** Violations of steps 1-10, each with its first-error outcome. */
    private static List<Violation> violations(Layout layout) {
        String type = layout.typeName();
        int dataIndex = layout.dataIndex();
        String framingMessage = "Transaction field at index " + dataIndex + " must be encoded as an RLP byte string";
        boolean typed2or4 = layout != TYPE_1;
        boolean type4 = layout == TYPE_4;

        List<Violation> v = new ArrayList<>();

        // Step 1: field count.
        String countMessage = type + " transaction must have exactly " + layout.count() + " elements";
        v.add(new Violation(1, "oneFieldTooMany", true,
                List.of(Edit.append(raw("80"))), IllegalArgumentException.class, countMessage));
        v.add(new Violation(1, "oneFieldTooFew", false,
                List.of(Edit.dropLast()), IllegalArgumentException.class, countMessage));

        // Step 2: to address.
        v.add(new Violation(2, "toNineteenBytes", true,
                List.of(Edit.set("to", item("12".repeat(19)))),
                InvalidRskAddressException.class, "An RSK address must be 20 bytes long"));
        v.add(new Violation(2, "toTwentyOneBytes", false,
                List.of(Edit.set("to", item("12".repeat(21)))),
                InvalidRskAddressException.class, "An RSK address must be 20 bytes long"));
        if (type4) {
            v.add(new Violation(2, "toEmpty", false,
                    List.of(Edit.set("to", item(""))),
                    IllegalArgumentException.class, "Set-code transaction must have a non-null destination"));
        }

        // Step 3: byte-string framing.
        v.add(new Violation(3, "dataAsList", true,
                List.of(Edit.set("data", raw("c0"))), IllegalArgumentException.class, framingMessage));
        v.add(new Violation(3, "nonceAsList", false,
                List.of(Edit.set("nonce", raw("c0"))), IllegalArgumentException.class,
                "Transaction field at index 1 must be encoded as an RLP byte string"));
        v.add(new Violation(3, "valueAsList", false,
                List.of(Edit.set("value", raw("c0"))), IllegalArgumentException.class,
                "Transaction field at index " + layout.valueIndex() + " must be encoded as an RLP byte string"));

        // Step 4: list codecs.
        v.add(new Violation(4, "accessListEntryWithOneElement", true,
                List.of(Edit.set("accessList", raw("c2c101"))), IllegalArgumentException.class,
                "Access list entry at index 0 must have exactly 2 elements"));
        v.add(new Violation(4, "accessListAsByteString", false,
                List.of(Edit.set("accessList", raw("80"))), IllegalArgumentException.class,
                "Access list must be encoded as an RLP list"));
        v.add(new Violation(4, "accessListAddressNineteenBytes", false,
                List.of(Edit.set("accessList", raw("d6d593" + "12".repeat(19) + "c0"))),
                IllegalArgumentException.class,
                "Access list entry address at index 0 must be exactly 20 bytes"));
        if (type4) {
            v.add(new Violation(4, "authListAsByteString", false,
                    List.of(Edit.set("authList", raw("80"))), IllegalArgumentException.class,
                    "Authorization list must be encoded as an RLP list"));
            v.add(new Violation(4, "authListEmpty", false,
                    List.of(Edit.set("authList", raw("c0"))), IllegalArgumentException.class,
                    "Set-code transaction authorization_list must not be empty"));
            v.add(new Violation(4, "authTupleWithOneField", false,
                    List.of(Edit.set("authList", raw("c2c101"))), IllegalArgumentException.class,
                    "Authorization list tuple must have 6 fields"));
        }

        // Step 5: fee cap relationship.
        if (typed2or4) {
            v.add(new Violation(5, "priorityFeeAboveMaxFee", true,
                    List.of(Edit.set("maxPriorityFee", item(MAX_FEE_HEX)), Edit.set("maxFee", item(MAX_PRIORITY_HEX))),
                    IllegalArgumentException.class,
                    "maxPriorityFeePerGas (2000000000) must not exceed maxFeePerGas (1000000000)"));
        }

        // Step 6: data-word size. Representative: nonce, 33 raw bytes.
        v.add(new Violation(6, "nonce33Bytes", true, dataWordEdits("nonce"),
                IllegalArgumentException.class, dataWordMessage("nonce")));
        // Step 7: canonical encoding. Representative: gas limit.
        v.add(new Violation(7, "gasLimitLeadingZero", true, canonicalEdits("gasLimit"),
                IllegalArgumentException.class, canonicalMessage("gasLimit")));

        // Step 8: signature state.
        v.add(new Violation(8, "yParityTwo", true,
                List.of(Edit.set("yParity", item("02"))), IllegalArgumentException.class,
                "Typed transaction yParity must be 0 or 1, got: 2"));
        v.add(new Violation(8, "yParityLeadingZero", false,
                List.of(Edit.set("yParity", item("0001"))), IllegalArgumentException.class,
                "Typed transaction yParity" + LEADING_ZERO_SUFFIX));
        v.add(new Violation(8, "yParityTwoBytes", false,
                List.of(Edit.set("yParity", item("0100"))), IllegalArgumentException.class,
                "Typed transaction yParity must fit in a single byte"));
        v.add(new Violation(8, "yParityOneWithoutSignature", false,
                List.of(Edit.set("r", item("")), Edit.set("s", item(""))), IllegalArgumentException.class,
                "Typed transaction yParity must be 0 when the signature is absent"));
        v.add(new Violation(8, "sMissing", false,
                List.of(Edit.set("s", item(""))), IllegalArgumentException.class,
                "Typed transaction signature is incomplete"));
        v.add(new Violation(8, "rMissing", false,
                List.of(Edit.set("r", item(""))), IllegalArgumentException.class,
                "Typed transaction signature is incomplete"));
        v.add(new Violation(8, "rThirtyThreeBytes", false,
                List.of(Edit.set("r", item("11".repeat(33)))), IllegalArgumentException.class,
                "Signature R is not valid"));
        v.add(new Violation(8, "rLeadingZero", false,
                List.of(Edit.set("r", item("00" + "11".repeat(31)))), IllegalArgumentException.class,
                "Signature R" + LEADING_ZERO_SUFFIX));
        v.add(new Violation(8, "sThirtyThreeBytes", false,
                List.of(Edit.set("s", item("22".repeat(33)))), IllegalArgumentException.class,
                "Signature S is not valid"));
        v.add(new Violation(8, "sLeadingZero", false,
                List.of(Edit.set("s", item("00" + "22".repeat(31)))), IllegalArgumentException.class,
                "Signature S" + LEADING_ZERO_SUFFIX));
        v.add(new Violation(8, "chainIdZero", false,
                List.of(Edit.set("chainId", item(""))), IllegalArgumentException.class,
                "Typed transaction chainId must not be zero or absent"));
        v.add(new Violation(8, "chainIdLeadingZero", false,
                List.of(Edit.set("chainId", item("0021"))), IllegalArgumentException.class,
                "Typed transaction chainId" + LEADING_ZERO_SUFFIX));
        v.add(new Violation(8, "chainIdAboveMax", false,
                List.of(Edit.set("chainId", item("0100"))), IllegalArgumentException.class,
                "Typed transaction chainId must be between 1 and 255, got: 256"));
        v.add(new Violation(8, "chainIdZeroWithoutSignature", false,
                List.of(Edit.set("chainId", item("")), Edit.set("yParity", item("")),
                        Edit.set("r", item("")), Edit.set("s", item(""))),
                IllegalArgumentException.class, "Typed transaction chainId must not be zero or absent"));

        // Step 9: Type-4 outer signature format.
        if (type4) {
            v.add(new Violation(9, "sAboveHalfOrder", true,
                    List.of(Edit.set("s", item(ORDER_MINUS_ONE_HEX))), IllegalArgumentException.class,
                    "Type 4 transaction signature s must be less than secp256k1n/2"));
            v.add(new Violation(9, "rEqualToOrder", false,
                    List.of(Edit.set("r", item(ORDER_HEX))), IllegalArgumentException.class,
                    "Type 4 transaction signature components are invalid"));
        }

        // Step 10: canonical re-encoding of the whole envelope. chainId 33 spelled with a length prefix.
        v.add(new Violation(10, "chainIdWithRedundantLengthPrefix", true,
                List.of(Edit.set("chainId", raw("8121"))), IllegalArgumentException.class,
                "Typed transaction envelope is not canonically encoded"));
        return v;
    }

    // ---------------------------------------------------------------------------------------------
    // Scalars: steps 6 and 7 and their check order
    // ---------------------------------------------------------------------------------------------

    /** Scalars in check order. The order differs from the envelope order for Type-2 and Type-4. */
    private static List<String> scalarCheckOrder(Layout layout) {
        return layout == TYPE_1
                ? List.of("nonce", "gasPrice", "gasLimit", "value")
                : List.of("nonce", "gasLimit", "value", "maxPriorityFee", "maxFee");
    }

    private static String dataWordMessage(String scalar) {
        return switch (scalar) {
            case "nonce" -> "Nonce is not valid";
            case "gasLimit" -> "Gas Limit is not valid";
            case "value" -> "Value is not valid";
            case "gasPrice", "maxPriorityFee", "maxFee" -> "Gas Price is not valid";
            default -> throw new IllegalArgumentException(scalar);
        };
    }

    private static String canonicalMessage(String scalar) {
        return switch (scalar) {
            case "nonce" -> "Nonce" + LEADING_ZERO_SUFFIX;
            case "gasLimit" -> "Gas Limit" + LEADING_ZERO_SUFFIX;
            case "value" -> "Value" + LEADING_ZERO_SUFFIX;
            case "gasPrice" -> "Gas Price" + LEADING_ZERO_SUFFIX;
            case "maxPriorityFee" -> "Max priority fee per gas" + LEADING_ZERO_SUFFIX;
            case "maxFee" -> "Max fee per gas" + LEADING_ZERO_SUFFIX;
            default -> throw new IllegalArgumentException(scalar);
        };
    }

    /**
     * 2^256 spelled in 33 bytes. A priority fee that large would first trip the fee cap, so the
     * priority fee edit also raises the cap above it, which makes that edit touch both fields.
     */
    private static List<Edit> dataWordEdits(String scalar) {
        String thirtyThreeBytes = "01" + "00".repeat(32);
        return switch (scalar) {
            case "maxPriorityFee" -> List.of(
                    Edit.set("maxPriorityFee", item(thirtyThreeBytes)),
                    Edit.set("maxFee", item("02" + "00".repeat(32))));
            default -> List.of(Edit.set(scalar, item(thirtyThreeBytes)));
        };
    }

    /** A leading zero byte over the base value, so the numeric value and the fee cap are unchanged. */
    private static List<Edit> canonicalEdits(String scalar) {
        String paddedBase = switch (scalar) {
            case "nonce" -> "00" + NONCE_HEX;
            case "gasPrice" -> "00" + GAS_PRICE_HEX;
            case "gasLimit" -> "00" + GAS_LIMIT_HEX;
            case "value" -> "00" + VALUE_HEX;
            case "maxPriorityFee" -> "00" + MAX_PRIORITY_HEX;
            case "maxFee" -> "00" + MAX_FEE_HEX;
            default -> throw new IllegalArgumentException(scalar);
        };
        return List.of(Edit.set(scalar, item(paddedBase)));
    }

    private static List<Case> scalarCases(Layout layout) {
        List<Case> cases = new ArrayList<>();
        List<String> order = scalarCheckOrder(layout);
        String prefix = layout.label() + " ";

        for (String scalar : order) {
            cases.add(new Case(prefix + "step6 " + scalar + "33Bytes",
                    layout.envelope(dataWordEdits(scalar)), IllegalArgumentException.class,
                    dataWordMessage(scalar)));
            cases.add(new Case(prefix + "step7 " + scalar + "LeadingZero",
                    layout.envelope(canonicalEdits(scalar)), IllegalArgumentException.class,
                    canonicalMessage(scalar)));
        }

        // Within step 6 and within step 7: the earlier scalar in check order wins.
        for (int i = 0; i < order.size(); i++) {
            for (int j = i + 1; j < order.size(); j++) {
                String first = order.get(i);
                String second = order.get(j);
                // The two fee data-word messages are identical, so that pair cannot show an order.
                if (!(first.equals("maxPriorityFee") && second.equals("maxFee"))) {
                    List<Edit> edits = new ArrayList<>(dataWordEdits(first));
                    edits.addAll(dataWordEdits(second));
                    cases.add(new Case(prefix + "step6 pair " + first + "+" + second,
                            layout.envelope(edits), IllegalArgumentException.class, dataWordMessage(first)));
                }
                List<Edit> edits = new ArrayList<>(canonicalEdits(first));
                edits.addAll(canonicalEdits(second));
                cases.add(new Case(prefix + "step7 pair " + first + "+" + second,
                        layout.envelope(edits), IllegalArgumentException.class, canonicalMessage(first)));
            }
        }

        // Across the steps: a data-word failure on any scalar beats a canonical failure on any other.
        for (String canonicalScalar : order) {
            for (String dataWordScalar : order) {
                List<Edit> canonicalEdits = canonicalEdits(canonicalScalar);
                List<Edit> dataWordEdits = dataWordEdits(dataWordScalar);
                if (sharesField(canonicalEdits, dataWordEdits)) {
                    continue;
                }
                List<Edit> edits = new ArrayList<>(canonicalEdits);
                edits.addAll(dataWordEdits);
                cases.add(new Case(prefix + "step6 beats step7 " + dataWordScalar + "33Bytes+"
                        + canonicalScalar + "LeadingZero",
                        layout.envelope(edits), IllegalArgumentException.class, dataWordMessage(dataWordScalar)));
            }
        }

        // Coin scalars are measured on Coin.getBytes(), the signed two's complement form, so a canonical
        // 32-byte value with the top bit set takes 33 bytes and fails the data-word check. Nonce and
        // gas limit are raw, and accept the same 32 bytes (see the accepted cases).
        for (String scalar : order) {
            if (scalar.equals("nonce") || scalar.equals("gasLimit")) {
                continue;
            }
            List<Edit> edits = switch (scalar) {
                case "maxPriorityFee" -> List.of(
                        Edit.set("maxPriorityFee", item("80" + "00".repeat(31))),
                        Edit.set("maxFee", item("81" + "00".repeat(31))));
                default -> List.of(Edit.set(scalar, item("80" + "00".repeat(31))));
            };
            cases.add(new Case(prefix + "coinSignBitSet " + scalar + "32Bytes",
                    layout.envelope(edits), IllegalArgumentException.class, dataWordMessage(scalar)));
        }

        // 33 bytes with a leading zero. Nonce and gas limit are measured on raw length and fail the
        // data-word check; the other scalars are measured as numbers and fail the canonical check.
        String zeroThen32 = "00" + "01".repeat(32);
        for (String scalar : order) {
            boolean rawMeasured = scalar.equals("nonce") || scalar.equals("gasLimit");
            List<Edit> edits = switch (scalar) {
                case "maxPriorityFee" -> List.of(
                        Edit.set("maxPriorityFee", item(zeroThen32)),
                        Edit.set("maxFee", item("02".repeat(32))));
                default -> List.of(Edit.set(scalar, item(zeroThen32)));
            };
            cases.add(new Case(prefix + "33BytesLeadingZero " + scalar + (rawMeasured ? " failsDataWord" : " failsCanonical"),
                    layout.envelope(edits), IllegalArgumentException.class,
                    rawMeasured ? dataWordMessage(scalar) : canonicalMessage(scalar)));
        }
        return cases;
    }

    private static boolean sharesField(List<Edit> a, List<Edit> b) {
        for (Edit left : a) {
            for (Edit right : b) {
                if (left.field().equals(right.field())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Order inside step 3 and step 8
    // ---------------------------------------------------------------------------------------------

    private static List<Case> framingOrderCases(Layout layout) {
        String prefix = layout.label() + " step3 pair ";
        List<Case> cases = new ArrayList<>();
        // Index order: the lowest offending index is reported.
        cases.add(new Case(prefix + "nonceAsList+dataAsList",
                layout.envelope(List.of(Edit.set("nonce", raw("c0")), Edit.set("data", raw("c0")))),
                IllegalArgumentException.class, "Transaction field at index 1 must be encoded as an RLP byte string"));
        cases.add(new Case(prefix + "dataAsList+sAsList",
                layout.envelope(List.of(Edit.set("data", raw("c0")), Edit.set("s", raw("c0")))),
                IllegalArgumentException.class,
                "Transaction field at index " + layout.dataIndex() + " must be encoded as an RLP byte string"));
        cases.add(new Case(prefix + "nonceAsList+sAsList",
                layout.envelope(List.of(Edit.set("nonce", raw("c0")), Edit.set("s", raw("c0")))),
                IllegalArgumentException.class, "Transaction field at index 1 must be encoded as an RLP byte string"));
        cases.add(new Case(prefix + "valueAsList+yParityAsList",
                layout.envelope(List.of(Edit.set("value", raw("c0")), Edit.set("yParity", raw("c0")))),
                IllegalArgumentException.class,
                "Transaction field at index " + layout.valueIndex() + " must be encoded as an RLP byte string"));
        cases.add(new Case(prefix + "chainIdAsList+nonceAsList",
                layout.envelope(List.of(Edit.set("chainId", raw("c0")), Edit.set("nonce", raw("c0")))),
                IllegalArgumentException.class, "Transaction field at index 0 must be encoded as an RLP byte string"));
        // Framing runs before any list codec, so a framing failure beats a malformed access list.
        cases.add(new Case(prefix + "sAsList+accessListAsByteString",
                layout.envelope(List.of(Edit.set("s", raw("c0")), Edit.set("accessList", raw("80")))),
                IllegalArgumentException.class,
                "Transaction field at index " + (layout.count() - 1) + " must be encoded as an RLP byte string"));
        // The to address is parsed before framing, so a wrong length beats a list elsewhere.
        cases.add(new Case(layout.label() + " step2 beats step3 toNineteenBytes+chainIdAsList",
                layout.envelope(List.of(Edit.set("to", item("12".repeat(19))), Edit.set("chainId", raw("c0")))),
                InvalidRskAddressException.class, "An RSK address must be 20 bytes long"));
        cases.add(new Case(prefix + "yParityAsList+accessListAsByteString",
                layout.envelope(List.of(Edit.set("yParity", raw("c0")), Edit.set("accessList", raw("80")))),
                IllegalArgumentException.class,
                "Transaction field at index " + (layout.count() - 3) + " must be encoded as an RLP byte string"));
        cases.add(new Case(prefix + "rAsList+accessListAsByteString",
                layout.envelope(List.of(Edit.set("r", raw("c0")), Edit.set("accessList", raw("80")))),
                IllegalArgumentException.class,
                "Transaction field at index " + (layout.count() - 2) + " must be encoded as an RLP byte string"));
        // A list-framed to is read through getRLPData() before framing, which includes the list header:
        // a 19 byte payload reads as exactly 20 bytes and reaches the framing check, any other length
        // fails the address check first.
        String toFramingMessage = "Transaction field at index " + layout.toIndex() + " must be encoded as an RLP byte string";
        cases.add(new Case(layout.label() + " step2 toAsListEmptyPayload",
                layout.envelope(List.of(Edit.set("to", raw("c0")))),
                InvalidRskAddressException.class, "An RSK address must be 20 bytes long"));
        cases.add(new Case(layout.label() + " step2 toAsListPayload18Bytes",
                layout.envelope(List.of(Edit.set("to", raw("d2" + "01".repeat(18))))),
                InvalidRskAddressException.class, "An RSK address must be 20 bytes long"));
        cases.add(new Case(layout.label() + " step3 toAsListPayload19Bytes",
                layout.envelope(List.of(Edit.set("to", raw("d3" + "01".repeat(19))))),
                IllegalArgumentException.class, toFramingMessage));
        cases.add(new Case(layout.label() + " step2 toAsListPayload20Bytes",
                layout.envelope(List.of(Edit.set("to", raw("d4" + "01".repeat(20))))),
                InvalidRskAddressException.class, "An RSK address must be 20 bytes long"));
        cases.add(new Case(layout.label() + " step3 pair toAsListPayload19Bytes+chainIdAsList",
                layout.envelope(List.of(Edit.set("to", raw("d3" + "01".repeat(19))), Edit.set("chainId", raw("c0")))),
                IllegalArgumentException.class, "Transaction field at index 0 must be encoded as an RLP byte string"));
        return cases;
    }

    private static List<Case> signatureOrderCases(Layout layout) {
        String prefix = layout.label() + " step8 pair ";
        List<Case> cases = new ArrayList<>();
        cases.add(new Case(prefix + "yParityTwo+sMissing",
                layout.envelope(List.of(Edit.set("yParity", item("02")), Edit.set("s", item("")))),
                IllegalArgumentException.class, "Typed transaction yParity must be 0 or 1, got: 2"));
        cases.add(new Case(prefix + "yParityTwo+chainIdZero",
                layout.envelope(List.of(Edit.set("yParity", item("02")), Edit.set("chainId", item("")))),
                IllegalArgumentException.class, "Typed transaction yParity must be 0 or 1, got: 2"));
        cases.add(new Case(prefix + "sMissing+rLeadingZero",
                layout.envelope(List.of(Edit.set("s", item("")), Edit.set("r", item("00" + "11".repeat(31))))),
                IllegalArgumentException.class, "Typed transaction signature is incomplete"));
        cases.add(new Case(prefix + "rLeadingZero+sLeadingZero",
                layout.envelope(List.of(
                        Edit.set("r", item("00" + "11".repeat(31))), Edit.set("s", item("00" + "22".repeat(31))))),
                IllegalArgumentException.class, "Signature R" + LEADING_ZERO_SUFFIX));
        cases.add(new Case(prefix + "rThirtyThreeBytes+sLeadingZero",
                layout.envelope(List.of(
                        Edit.set("r", item("11".repeat(33))), Edit.set("s", item("00" + "22".repeat(31))))),
                IllegalArgumentException.class, "Signature R is not valid"));
        cases.add(new Case(prefix + "sLeadingZero+chainIdZero",
                layout.envelope(List.of(Edit.set("s", item("00" + "22".repeat(31))), Edit.set("chainId", item("")))),
                IllegalArgumentException.class, "Signature S" + LEADING_ZERO_SUFFIX));
        cases.add(new Case(prefix + "sMissing+chainIdZero",
                layout.envelope(List.of(Edit.set("s", item("")), Edit.set("chainId", item("")))),
                IllegalArgumentException.class, "Typed transaction signature is incomplete"));
        return cases;
    }

    /** Access list before authorization list, and the three authorization list checks in order. */
    private static List<Case> type4ListOrderCases() {
        String prefix = "type4 step4 pair ";
        List<Case> cases = new ArrayList<>();
        cases.add(new Case(prefix + "accessListEntryWithOneElement+authListAsByteString",
                TYPE_4.envelope(List.of(
                        Edit.set("accessList", raw("c2c101")), Edit.set("authList", raw("80")))),
                IllegalArgumentException.class, "Access list entry at index 0 must have exactly 2 elements"));
        cases.add(new Case(prefix + "accessListEntryWithOneElement+authListEmpty",
                TYPE_4.envelope(List.of(
                        Edit.set("accessList", raw("c2c101")), Edit.set("authList", raw("c0")))),
                IllegalArgumentException.class, "Access list entry at index 0 must have exactly 2 elements"));
        cases.add(new Case(prefix + "accessListAsByteString+authListEmpty",
                TYPE_4.envelope(List.of(
                        Edit.set("accessList", raw("80")), Edit.set("authList", raw("c0")))),
                IllegalArgumentException.class, "Access list must be encoded as an RLP list"));
        cases.add(new Case(prefix + "authListAsByteString+nonce33Bytes",
                TYPE_4.envelope(List.of(
                        Edit.set("authList", raw("80")), Edit.set("nonce", item("01" + "00".repeat(32))))),
                IllegalArgumentException.class, "Authorization list must be encoded as an RLP list"));
        cases.add(new Case(prefix + "authTupleWithOneField+priorityFeeAboveMaxFee",
                TYPE_4.envelope(List.of(
                        Edit.set("authList", raw("c2c101")),
                        Edit.set("maxPriorityFee", item(MAX_FEE_HEX)),
                        Edit.set("maxFee", item(MAX_PRIORITY_HEX)))),
                IllegalArgumentException.class, "Authorization list tuple must have 6 fields"));
        cases.add(new Case(prefix + "toEmpty+authListEmpty",
                TYPE_4.envelope(List.of(Edit.set("to", item("")), Edit.set("authList", raw("c0")))),
                IllegalArgumentException.class, "Set-code transaction must have a non-null destination"));
        cases.add(new Case(prefix + "toEmpty+dataAsList",
                TYPE_4.envelope(List.of(Edit.set("to", item("")), Edit.set("data", raw("c0")))),
                IllegalArgumentException.class, "Set-code transaction must have a non-null destination"));
        cases.add(new Case(prefix + "rEqualToOrder+sAboveHalfOrder",
                TYPE_4.envelope(List.of(
                        Edit.set("r", item(ORDER_HEX)), Edit.set("s", item(ORDER_MINUS_ONE_HEX)))),
                IllegalArgumentException.class, "Type 4 transaction signature components are invalid"));
        return cases;
    }

    // ---------------------------------------------------------------------------------------------
    // Accepted inputs
    // ---------------------------------------------------------------------------------------------

    @Test
    void type1ValidSignedEnvelopeParsesEveryField() {
        ParsedType1Transaction parsed = parseType1(TYPE_1.envelope(List.of()));

        assertEquals(TransactionType.TYPE_1, parsed.typePrefix().type());
        assertArrayEquals(new byte[]{0x01}, parsed.nonce());
        assertEquals(Coin.valueOf(1_000_000_000L), parsed.gasPrice());
        assertArrayEquals(new byte[]{0x52, 0x08}, parsed.gasLimit());
        assertEquals(new RskAddress(ADDRESS_HEX), parsed.receiveAddress());
        assertEquals(Coin.valueOf(1_000_000_000_000_000_000L), parsed.value());
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, parsed.data());
        assertArrayEquals(Hex.decode(ACCESS_LIST_HEX), parsed.accessListBytes());
        assertSignedWithChainId33AndYParity1(parsed.signatureState());
    }

    @Test
    void type2ValidSignedEnvelopeParsesEveryField() {
        ParsedType2Transaction parsed = parseType2(TYPE_2.envelope(List.of()));

        assertEquals(TransactionType.TYPE_2, parsed.typePrefix().type());
        assertArrayEquals(new byte[]{0x01}, parsed.nonce());
        assertEquals(Coin.valueOf(1_000_000_000L), parsed.maxPriorityFeePerGas());
        assertEquals(Coin.valueOf(2_000_000_000L), parsed.maxFeePerGas());
        assertArrayEquals(new byte[]{0x52, 0x08}, parsed.gasLimit());
        assertEquals(new RskAddress(ADDRESS_HEX), parsed.receiveAddress());
        assertEquals(Coin.valueOf(1_000_000_000_000_000_000L), parsed.value());
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, parsed.data());
        assertArrayEquals(Hex.decode(ACCESS_LIST_HEX), parsed.accessListBytes());
        assertSignedWithChainId33AndYParity1(parsed.signatureState());
    }

    @Test
    void type4ValidSignedEnvelopeParsesEveryField() {
        ParsedType4Transaction parsed = parseType4(TYPE_4.envelope(List.of()));

        assertEquals(TransactionType.TYPE_4, parsed.typePrefix().type());
        assertArrayEquals(new byte[]{0x01}, parsed.nonce());
        assertEquals(Coin.valueOf(1_000_000_000L), parsed.maxPriorityFeePerGas());
        assertEquals(Coin.valueOf(2_000_000_000L), parsed.maxFeePerGas());
        assertArrayEquals(new byte[]{0x52, 0x08}, parsed.gasLimit());
        assertEquals(new RskAddress(ADDRESS_HEX), parsed.receiveAddress());
        assertEquals(Coin.valueOf(1_000_000_000_000_000_000L), parsed.value());
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, parsed.data());
        assertArrayEquals(Hex.decode(ACCESS_LIST_HEX), parsed.accessListBytes());
        assertSignedWithChainId33AndYParity1(parsed.signatureState());
        assertEquals(1, parsed.authorizationList().size());
        SetCodeAuthorization authorization = parsed.authorizationList().get(0);
        assertEquals(BigInteger.valueOf(33), authorization.getChainId());
        assertEquals(new RskAddress(AUTH_ADDRESS_HEX), authorization.getAddress());
        assertArrayEquals(new byte[0], authorization.getNonceBytes());
        assertEquals(27, authorization.getSignature().getV());
        assertEquals(new BigInteger(R_HEX, 16), authorization.getSignature().getR());
        assertEquals(new BigInteger(S_HEX, 16), authorization.getSignature().getS());
    }

    @Test
    void type1UnsignedZeroScalarsAndContractCreationParse() {
        ParsedType1Transaction parsed = parseType1(TYPE_1.envelope(List.of(
                Edit.set("nonce", item("")), Edit.set("gasPrice", item("")), Edit.set("gasLimit", item("")),
                Edit.set("to", item("")), Edit.set("value", item("")), Edit.set("data", item("")),
                Edit.set("accessList", raw("c0")), Edit.set("yParity", item("")),
                Edit.set("r", item("")), Edit.set("s", item("")))));

        assertArrayEquals(new byte[0], parsed.nonce());
        assertEquals(Coin.ZERO, parsed.gasPrice());
        assertArrayEquals(new byte[0], parsed.gasLimit());
        assertEquals(RskAddress.nullAddress(), parsed.receiveAddress());
        assertEquals(Coin.ZERO, parsed.value());
        assertArrayEquals(new byte[0], parsed.data());
        assertArrayEquals(new byte[]{(byte) 0xc0}, parsed.accessListBytes());
        assertEquals(new UnsignedSignature((byte) 33), parsed.signatureState());
    }

    @Test
    void type2UnsignedZeroScalarsAndContractCreationParse() {
        ParsedType2Transaction parsed = parseType2(TYPE_2.envelope(List.of(
                Edit.set("nonce", item("")), Edit.set("maxPriorityFee", item("")), Edit.set("maxFee", item("")),
                Edit.set("gasLimit", item("")), Edit.set("to", item("")), Edit.set("value", item("")),
                Edit.set("data", item("")), Edit.set("accessList", raw("c0")), Edit.set("yParity", item("")),
                Edit.set("r", item("")), Edit.set("s", item("")))));

        assertArrayEquals(new byte[0], parsed.nonce());
        assertEquals(Coin.ZERO, parsed.maxPriorityFeePerGas());
        assertEquals(Coin.ZERO, parsed.maxFeePerGas());
        assertArrayEquals(new byte[0], parsed.gasLimit());
        assertEquals(RskAddress.nullAddress(), parsed.receiveAddress());
        assertEquals(Coin.ZERO, parsed.value());
        assertArrayEquals(new byte[0], parsed.data());
        assertArrayEquals(new byte[]{(byte) 0xc0}, parsed.accessListBytes());
        assertEquals(new UnsignedSignature((byte) 33), parsed.signatureState());
    }

    @Test
    void type4UnsignedZeroScalarsParse() {
        ParsedType4Transaction parsed = parseType4(TYPE_4.envelope(List.of(
                Edit.set("nonce", item("")), Edit.set("maxPriorityFee", item("")), Edit.set("maxFee", item("")),
                Edit.set("gasLimit", item("")), Edit.set("value", item("")), Edit.set("data", item("")),
                Edit.set("accessList", raw("c0")), Edit.set("yParity", item("")),
                Edit.set("r", item("")), Edit.set("s", item("")))));

        assertArrayEquals(new byte[0], parsed.nonce());
        assertEquals(Coin.ZERO, parsed.maxPriorityFeePerGas());
        assertEquals(Coin.ZERO, parsed.maxFeePerGas());
        assertArrayEquals(new byte[0], parsed.gasLimit());
        assertEquals(new RskAddress(ADDRESS_HEX), parsed.receiveAddress());
        assertEquals(Coin.ZERO, parsed.value());
        assertArrayEquals(new byte[0], parsed.data());
        assertArrayEquals(new byte[]{(byte) 0xc0}, parsed.accessListBytes());
        assertEquals(new UnsignedSignature((byte) 33), parsed.signatureState());
        assertEquals(1, parsed.authorizationList().size());
    }

    @Test
    void type1ScalarsAtTheirDataWordLimitParse() {
        String rawMax = "ff".repeat(32);
        String coinMax = "7f" + "ff".repeat(31);
        ParsedType1Transaction parsed = parseType1(TYPE_1.envelope(List.of(
                Edit.set("nonce", item(rawMax)), Edit.set("gasPrice", item(coinMax)),
                Edit.set("gasLimit", item(rawMax)), Edit.set("value", item(coinMax)),
                Edit.set("chainId", item("ff")))));

        assertArrayEquals(Hex.decode(rawMax), parsed.nonce());
        assertEquals(new Coin(new BigInteger(coinMax, 16)), parsed.gasPrice());
        assertArrayEquals(Hex.decode(rawMax), parsed.gasLimit());
        assertEquals(new Coin(new BigInteger(coinMax, 16)), parsed.value());
        assertEquals((byte) 0xff, assertInstanceOf(SignedSignature.class, parsed.signatureState()).chainId());
    }

    @Test
    void type2ScalarsAtTheirDataWordLimitAndPriorityEqualToMaxFeeParse() {
        String rawMax = "ff".repeat(32);
        String max = "7f" + "ff".repeat(31);
        ParsedType2Transaction parsed = parseType2(TYPE_2.envelope(List.of(
                Edit.set("nonce", item(rawMax)), Edit.set("maxPriorityFee", item(max)),
                Edit.set("maxFee", item(max)), Edit.set("gasLimit", item(rawMax)), Edit.set("value", item(max)))));

        assertArrayEquals(Hex.decode(rawMax), parsed.nonce());
        assertEquals(new Coin(new BigInteger(max, 16)), parsed.maxPriorityFeePerGas());
        assertEquals(new Coin(new BigInteger(max, 16)), parsed.maxFeePerGas());
        assertArrayEquals(Hex.decode(rawMax), parsed.gasLimit());
        assertEquals(new Coin(new BigInteger(max, 16)), parsed.value());
    }

    @Test
    void type4ScalarsAtTheirDataWordLimitAndPriorityEqualToMaxFeeParse() {
        String rawMax = "ff".repeat(32);
        String max = "7f" + "ff".repeat(31);
        ParsedType4Transaction parsed = parseType4(TYPE_4.envelope(List.of(
                Edit.set("nonce", item(rawMax)), Edit.set("maxPriorityFee", item(max)),
                Edit.set("maxFee", item(max)), Edit.set("gasLimit", item(rawMax)), Edit.set("value", item(max)))));

        assertArrayEquals(Hex.decode(rawMax), parsed.nonce());
        assertEquals(new Coin(new BigInteger(max, 16)), parsed.maxPriorityFeePerGas());
        assertEquals(new Coin(new BigInteger(max, 16)), parsed.maxFeePerGas());
        assertArrayEquals(Hex.decode(rawMax), parsed.gasLimit());
        assertEquals(new Coin(new BigInteger(max, 16)), parsed.value());
    }

    /** Data is a byte string, not a scalar: wider than a data word and led by a zero byte, it parses as sent. */
    private static final String WIDE_DATA_HEX = "00" + "ab".repeat(40);

    @Test
    void type1DataWiderThanADataWordWithALeadingZeroParses() {
        ParsedType1Transaction parsed = parseType1(TYPE_1.envelope(List.of(Edit.set("data", item(WIDE_DATA_HEX)))));

        assertArrayEquals(Hex.decode(WIDE_DATA_HEX), parsed.data());
    }

    @Test
    void type2DataWiderThanADataWordWithALeadingZeroParses() {
        ParsedType2Transaction parsed = parseType2(TYPE_2.envelope(List.of(Edit.set("data", item(WIDE_DATA_HEX)))));

        assertArrayEquals(Hex.decode(WIDE_DATA_HEX), parsed.data());
    }

    @Test
    void type4DataWiderThanADataWordWithALeadingZeroParses() {
        ParsedType4Transaction parsed = parseType4(TYPE_4.envelope(List.of(Edit.set("data", item(WIDE_DATA_HEX)))));

        assertArrayEquals(Hex.decode(WIDE_DATA_HEX), parsed.data());
    }

    private static void assertSignedWithChainId33AndYParity1(SignatureState state) {
        SignedSignature signed = assertInstanceOf(SignedSignature.class, state);
        assertEquals(33, signed.chainId());
        assertEquals(28, signed.signature().getV());
        assertEquals(new BigInteger(R_HEX, 16), signed.signature().getR());
        assertEquals(new BigInteger(S_HEX, 16), signed.signature().getS());
    }

    private static ParsedType1Transaction parseType1(byte[] raw) {
        return assertInstanceOf(ParsedType1Transaction.class, RawTransactionEnvelopeParser.parse(raw));
    }

    private static ParsedType2Transaction parseType2(byte[] raw) {
        return assertInstanceOf(ParsedType2Transaction.class, RawTransactionEnvelopeParser.parse(raw));
    }

    private static ParsedType4Transaction parseType4(byte[] raw) {
        return assertInstanceOf(ParsedType4Transaction.class, RawTransactionEnvelopeParser.parse(raw));
    }

    // ---------------------------------------------------------------------------------------------
    // Building blocks
    // ---------------------------------------------------------------------------------------------

    /** An RLP byte string holding the given hex bytes; the empty string is the single byte 0x80. */
    private static byte[] item(String hex) {
        return RLP.encodeElement(Hex.decode(hex));
    }

    /** Bytes inserted into the envelope exactly as written, for lists and hand-framed items. */
    private static byte[] raw(String hex) {
        return Hex.decode(hex);
    }

    private static Map<String, byte[]> base(Object... namesAndFramedFields) {
        Map<String, byte[]> fields = new LinkedHashMap<>();
        for (int i = 0; i < namesAndFramedFields.length; i += 2) {
            fields.put((String) namesAndFramedFields[i], (byte[]) namesAndFramedFields[i + 1]);
        }
        return fields;
    }

    /** One envelope layout: the type byte, the first-error type name, the field count, and a valid base. */
    private record Layout(String label, byte typeByte, String typeName, int count, int dataIndex,
                          Map<String, byte[]> baseFields) {

        int toIndex() {
            return dataIndex - 2;
        }

        int valueIndex() {
            return dataIndex - 1;
        }

        byte[] envelope(List<Edit> edits) {
            List<String> names = new ArrayList<>(baseFields.keySet());
            List<byte[]> items = new ArrayList<>(baseFields.values());
            for (Edit edit : edits) {
                if (edit.field().equals(Edit.APPEND) || edit.field().equals(Edit.DROP_LAST)) {
                    continue;
                }
                int index = names.indexOf(edit.field());
                if (index < 0) {
                    throw new IllegalArgumentException("unknown field " + edit.field() + " for " + label);
                }
                items.set(index, edit.value());
            }
            for (Edit edit : edits) {
                if (edit.field().equals(Edit.APPEND)) {
                    items.add(edit.value());
                } else if (edit.field().equals(Edit.DROP_LAST)) {
                    items.remove(items.size() - 1);
                }
            }
            byte[] list = RLP.encodeList(items.toArray(new byte[0][]));
            byte[] out = new byte[list.length + 1];
            out[0] = typeByte;
            System.arraycopy(list, 0, out, 1, list.length);
            return out;
        }
    }

    /** Replaces a named field with already-framed bytes, appends a framed field, or drops the last one. */
    private record Edit(String field, byte[] value) {
        static final String APPEND = "+append";
        static final String DROP_LAST = "-dropLast";

        static Edit set(String field, byte[] framed) {
            return new Edit(field, framed);
        }

        static Edit append(byte[] framed) {
            return new Edit(APPEND, framed);
        }

        static Edit dropLast() {
            return new Edit(DROP_LAST, null);
        }
    }

    /** One way to break a valid envelope at a given step, with the outcome the parsers give it alone. */
    private record Violation(int step, String label, boolean representative, List<Edit> edits,
                             Class<? extends Throwable> exceptionType, String message) {

        boolean touchesSameFieldAs(Violation other) {
            return sharesField(edits, other.edits);
        }

        Case toCase(Layout layout) {
            return new Case(layout.label() + " step" + step + " " + label,
                    layout.envelope(edits), exceptionType, message);
        }
    }

    /** A raw input and the exact outcome expected for it. Prints as its name. */
    private record Case(String name, byte[] rawData, Class<? extends Throwable> exceptionType, String message) {
        @Override
        public String toString() {
            return name;
        }
    }
}
