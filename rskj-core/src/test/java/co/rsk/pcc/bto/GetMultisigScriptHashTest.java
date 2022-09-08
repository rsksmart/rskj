/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc.bto;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;

public class GetMultisigScriptHashTest {
    private GetMultisigScriptHash method;

    @BeforeEach
    public void createMethod() {
        ExecutionEnvironment executionEnvironment = mock(ExecutionEnvironment.class);
        method = new GetMultisigScriptHash(executionEnvironment);
    }

    @Test
    public void functionSignatureOk() {
        CallTransaction.Function fn = method.getFunction();
        Assertions.assertEquals("getMultisigScriptHash", fn.name);

        Assertions.assertEquals(2, fn.inputs.length);
        Assertions.assertEquals(SolidityType.getType("int256").getName(), fn.inputs[0].type.getName());
        Assertions.assertEquals(SolidityType.getType("bytes[]").getName(), fn.inputs[1].type.getName());

        Assertions.assertEquals(1, fn.outputs.length);
        Assertions.assertEquals(SolidityType.getType("bytes").getName(), fn.outputs[0].type.getName());
    }

    @Test
    public void shouldBeEnabled() {
        Assertions.assertTrue(method.isEnabled());
    }

    @Test
    public void shouldAllowAnyTypeOfCall() {
        Assertions.assertFalse(method.onlyAllowsLocalCalls());
    }

    @Test
    public void executesWithAllCompressed() throws NativeContractIllegalArgumentException {
        Assertions.assertEquals(
                "51f103320b435b5fe417b3f3e0f18972ccc710a0",
                ByteUtil.toHexString((byte[]) method.execute(new Object[]{
                        BigInteger.valueOf(8L),
                        new byte[][] {
                                Hex.decode("03b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2"),
                                Hex.decode("027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344"),
                                Hex.decode("0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70"),
                                Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"),
                                Hex.decode("0294c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adc"),
                                Hex.decode("0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6"),
                                Hex.decode("0340df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44"),
                                Hex.decode("02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb"),
                                Hex.decode("031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26"),
                                Hex.decode("0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea"),
                                Hex.decode("02550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe8"),
                                Hex.decode("02481f02b7140acbf3fcdd9f72cf9a7d9484d8125e6df7c9451cfa55ba3b077265"),
                                Hex.decode("03f909ae15558c70cc751aff9b1f495199c325b13a9e5b934fd6299cd30ec50be8"),
                                Hex.decode("02c6018fcbd3e89f3cf9c7f48b3232ea3638eb8bf217e59ee290f5f0cfb2fb9259"),
                                Hex.decode("03b65694ccccda83cbb1e56b31308acd08e993114c33f66a456b627c2c1c68bed6")
                        }
                })));
    }

    @Test
    public void executesWithMixed() throws NativeContractIllegalArgumentException {
        Assertions.assertEquals(
                "51f103320b435b5fe417b3f3e0f18972ccc710a0",
                ByteUtil.toHexString((byte[]) method.execute(new Object[]{
                        BigInteger.valueOf(8L),
                        new byte[][] {
                                Hex.decode("04b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2aafaaa2611606699ec4f82777a268b708dab346de4880cd223969f7bbe5422bf"),
                                Hex.decode("027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344"),
                                Hex.decode("0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70"),
                                Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"),
                                Hex.decode("0494c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adcb17171aa9ec8d8587098e0771f686ee61ac35279f9e5aadf9b06b738aa6d3720"),
                                Hex.decode("0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6"),
                                Hex.decode("0440df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44e1abebaea4c3c57c6e9e39e205b4df046f7110a8d3477c0d8e26a28be9692c29"),
                                Hex.decode("02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb"),
                                Hex.decode("031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26"),
                                Hex.decode("0445ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeeae024d50312de76a7950f8c6268fbf454335cf252f961a67c47e67dc06fa590ba"),
                                Hex.decode("02550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe8"),
                                Hex.decode("02481f02b7140acbf3fcdd9f72cf9a7d9484d8125e6df7c9451cfa55ba3b077265"),
                                Hex.decode("03f909ae15558c70cc751aff9b1f495199c325b13a9e5b934fd6299cd30ec50be8"),
                                Hex.decode("02c6018fcbd3e89f3cf9c7f48b3232ea3638eb8bf217e59ee290f5f0cfb2fb9259"),
                                Hex.decode("03b65694ccccda83cbb1e56b31308acd08e993114c33f66a456b627c2c1c68bed6")
                        }
                })));
    }

    @Test
    public void minimumSignaturesMustBePresent() {
        assertFails(
                () -> method.execute(new Object[]{
                        null,
                        new Object[]{}
                }),
                "Minimum required signatures"
        );
    }

    @Test
    public void minimumSignaturesMustBeGreaterThanZero() {
        assertFails(
                () -> method.execute(new Object[]{
                        BigInteger.ZERO,
                        new Object[]{}
                }),
                "Minimum required signatures"
        );
    }

    @Test
    public void mustProvideAtLeastTwoPublicKey() {
        assertFails(
                () -> method.execute(new Object[]{
                        BigInteger.ONE,
                        new Object[]{}
                }),
                "At least 2 public keys"
        );
        assertFails(
                () -> method.execute(new Object[]{
                        BigInteger.ONE,
                        null
                }),
                "At least 2 public keys"
        );
        assertFails(
                () -> method.execute(new Object[]{
                        BigInteger.ONE,
                        new Object[]{Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7")}
                }),
                "At least 2 public keys"
        );
    }

    @Test
    public void atLeastAsManyPublicKeyAsMinimumSignatures() {
        assertFails(
                () -> method.execute(new Object[]{
                        BigInteger.valueOf(3L),
                        new Object[]{
                                Hex.decode("03b65694ccccda83cbb1e56b31308acd08e993114c33f66a456b627c2c1c68bed6"),
                                Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"),
                        }
                }),
                "are less than the minimum required signatures"
        );
    }

    @Test
    public void atMostFifteenPublicKeys() {
        byte[][] keys = new byte[16][];
        for (int i = 0; i < 16; i++) {
            keys[i] = new BtcECKey().getPubKeyPoint().getEncoded(true);
        }

        assertFails(
                () -> method.execute(new Object[]{
                        BigInteger.valueOf(3L),
                        keys
                }),
                "are more than the maximum allowed signatures"
        );
    }

    @Test
    public void keyLengthIsValidated() {
        assertFails(
                () -> method.execute(new Object[]{
                        BigInteger.valueOf(1L),
                        new Object[]{
                                Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"),
                                Hex.decode("aabbcc"),
                        }
                }),
                "Invalid public key length"
        );
    }

    @Test
    public void keyFormatIsValidated() {
        assertFails(
                () -> method.execute(new Object[]{
                        BigInteger.valueOf(1L),
                        new Object[]{
                                Hex.decode("03566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"),
                                Hex.decode("08566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"),
                        }
                }),
                "Invalid public key format"
        );
    }

    @Test
    public void gasIsBaseIfLessThanOrEqualstoTwoKeysPassed() {
        Assertions.assertEquals(20_000L, method.getGas(new Object[]{
                BigInteger.valueOf(1L),
                null
        }, new byte[]{}));
        Assertions.assertEquals(20_000L, method.getGas(new Object[]{
                BigInteger.valueOf(1L),
                new Object[]{
                }
        }, new byte[]{}));
        Assertions.assertEquals(20_000L, method.getGas(new Object[]{
                BigInteger.valueOf(1L),
                new Object[]{
                        Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7")
                }
        }, new byte[]{}));
        Assertions.assertEquals(20_000L, method.getGas(new Object[]{
                BigInteger.valueOf(1L),
                new Object[]{
                        Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"),
                        Hex.decode("aabbcc")
                }
        }, new byte[]{}));
    }

    private void assertFails(Runnable runnable, Consumer<Exception> exceptionHandler) {
        boolean failed = false;
        try {
            runnable.run();
        } catch (Exception ex) {
            failed = true;
            exceptionHandler.accept(ex);
        }
        Assertions.assertTrue(failed);
    }

    private void assertFails(Runnable runnable, String expectedMessage) {
        assertFails(runnable, (ex) -> {
            Assertions.assertEquals(NativeContractIllegalArgumentException.class, ex.getClass());
            Assertions.assertTrue(ex.getMessage().contains(expectedMessage));
        });
    }

    public interface Runnable {
        void run() throws Exception;
    }
}
