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

package org.ethereum.core;

import co.rsk.core.RskAddress;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test demonstrating offline transaction signing using Transaction.sign()
 * 
 * These tests show how to:
 * 1. Create a transaction using Transaction.builder()
 * 2. Sign it offline with a private key using tx.sign(privateKey)
 * 3. Verify the signature and recover the sender
 * 4. Get the raw signed transaction for broadcasting via Postman
 * 
 * Output includes raw transaction hex ready for eth_sendRawTransaction RPC calls
 */
class SimpleTransactionSigningTest {

    // Test private key (cow) - NEVER use in production!
    //private static final String TEST_PRIVATE_KEY = "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
    private static final String TEST_PRIVATE_KEY = "31c16f716f80d57764c202953a9c2de061a0ee374a82012b33319eb8dceba4e4";
    /**
     * Helper method to print Postman-ready raw transaction
     */
    private void printPostmanRequest(Transaction tx, String description) {
        String rawTxHex = "0x" + ByteUtil.toHexString(tx.getEncoded());
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📤 " + description);
        System.out.println("=".repeat(70));
        System.out.println("\n🔑 Transaction Details:");
        System.out.println("   From:    0x" + ByteUtil.toHexString(tx.getSender().getBytes()));
        if (tx.getReceiveAddress() != null) {
            System.out.println("   To:      0x" + ByteUtil.toHexString(tx.getReceiveAddress().getBytes()));
        }
        System.out.println("   Value:   " + tx.getValue().asBigInteger() + " wei");
        System.out.println("   Nonce:   " + new BigInteger(1, tx.getNonce()));
        System.out.println("   Gas:     " + new BigInteger(1, tx.getGasLimit()));
        System.out.println("   TxHash:  " + tx.getHash());
        
        System.out.println("\n📋 Raw Transaction (copy for Postman):");
        System.out.println("   " + rawTxHex);
        
        System.out.println("\n📮 Postman Request Body:");
        System.out.println("{\n" +
                "  \"jsonrpc\": \"2.0\",\n" +
                "  \"method\": \"eth_sendRawTransaction\",\n" +
                "  \"params\": [\"" + rawTxHex + "\"],\n" +
                "  \"id\": 1\n" +
                "}");
        
        System.out.println("\n💡 cURL command:");
        System.out.println("curl -X POST http://localhost:4444 \\\n" +
                "  -H \"Content-Type: application/json\" \\\n" +
                "  -d '{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\",\"params\":[\"" + rawTxHex + "\"],\"id\":1}'");
        System.out.println("=".repeat(70) + "\n");
    }
    
    @Test
    void testBasicTransactionSigning() {
        // Given: A private key and transaction parameters
        byte[] privateKey = Hex.decode(TEST_PRIVATE_KEY);
        ECKey ecKey = ECKey.fromPrivate(privateKey);
        
        byte[] recipientAddress = Hex.decode("13978aee95f38490e9769c39b2773ed763d9cd5f");
        BigInteger value = new BigInteger("1000000000000000000"); // 1 RBTC
        BigInteger gasPrice = new BigInteger("60000000000"); // 60 Gwei
        BigInteger gasLimit = BigInteger.valueOf(21000);
        BigInteger nonce = BigInteger.TWO;
        
        // When: Building and signing a transaction
        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .receiveAddress(recipientAddress)
                .value(value)
                .build();
        
        // Verify transaction is unsigned
        assertNull(tx.getSignature(), "Transaction should be unsigned initially");
        
        // Sign the transaction
        tx.sign(privateKey);
        
        // Print Postman-ready output
        printPostmanRequest(tx, "Basic Value Transfer Transaction");
        
        // Then: Transaction should have a valid signature
        assertNotNull(tx.getSignature(), "Transaction should be signed");
        assertNotNull(tx.getSignature().getR(), "Signature should have R component");
        assertNotNull(tx.getSignature().getS(), "Signature should have S component");
        assertTrue(tx.getSignature().getV() == 27 || tx.getSignature().getV() == 28, 
                   "V should be 27 or 28 for legacy transaction");
        
        // Verify sender can be recovered
        RskAddress sender = tx.getSender();
        assertNotNull(sender, "Sender should be recoverable");
        assertEquals(ByteUtil.toHexString(ecKey.getAddress()), 
                     ByteUtil.toHexString(sender.getBytes()),
                     "Recovered sender should match original address");
        
        // Verify we can get the encoded transaction
        byte[] encoded = tx.getEncoded();
        assertNotNull(encoded, "Encoded transaction should not be null");
        assertTrue(encoded.length > 0, "Encoded transaction should have data");
    }
    
    @Test
    void testTransactionSigningWithData() {
        // Given: Transaction with data field (e.g., contract call)
        byte[] privateKey = Hex.decode(TEST_PRIVATE_KEY);
        ECKey senderKey = ECKey.fromPrivate(privateKey);
        
        byte[] contractAddress = Hex.decode("7986b3df570230288501eea3d890bd66948c9b79");
        byte[] callData = Hex.decode("a9059cbb0000000000000000000000001234567890123456789012345678901234567890");
        BigInteger nonce = BigInteger.valueOf(1);
        BigInteger gasPrice = new BigInteger("60000000000");
        BigInteger gasLimit = BigInteger.valueOf(100000);
        
        // When: Building and signing transaction with data
        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .receiveAddress(contractAddress)
                .value(BigInteger.ZERO)
                .data(callData)
                .build();
        
        tx.sign(privateKey);
        
        // Print Postman-ready output
        printPostmanRequest(tx, "Contract Call Transaction (ERC20 Transfer)");
        
        // Then: Transaction should be properly signed
        assertNotNull(tx.getSignature(), "Transaction should be signed");
        assertArrayEquals(callData, tx.getData(), "Data should be preserved");
        
        // Verify sender recovery
        RskAddress recoveredSender = tx.getSender();
        assertEquals(ByteUtil.toHexString(senderKey.getAddress()),
                     ByteUtil.toHexString(recoveredSender.getBytes()),
                     "Sender should be recoverable from signed transaction");
    }
    
    @Test
    void testTransactionSigningWithChainId() {
        // Given: Transaction with chain ID (EIP-155)
        byte[] privateKey = Hex.decode(TEST_PRIVATE_KEY);
        ECKey ecKey = ECKey.fromPrivate(privateKey);
        
        byte chainId = 33; // RSK Regtest
        
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(2))
                .gasPrice(new BigInteger("60000000000"))
                .gasLimit(BigInteger.valueOf(21000))
                .receiveAddress(Hex.decode("0000000000000000000000000000000000000001"))
                .value(BigInteger.valueOf(1000000000000000L))
                .chainId(chainId)
                .build();
        
        // When: Signing with chain ID
        tx.sign(privateKey);
        
        // Print Postman-ready output
        printPostmanRequest(tx, "Transaction with Chain ID (EIP-155)");
        
        // Then: Signature should include chain ID
        assertNotNull(tx.getSignature(), "Transaction should be signed");
        assertEquals(chainId, tx.getChainId(), "Chain ID should be preserved");
        
        // Verify sender
        RskAddress sender = tx.getSender();
        assertEquals(ByteUtil.toHexString(ecKey.getAddress()),
                     ByteUtil.toHexString(sender.getBytes()),
                     "Sender should match with chain ID");
    }
    
    @Test
    void testMultipleTransactionsSigning() {
        // Given: Multiple transactions from same sender
        byte[] privateKey = Hex.decode(TEST_PRIVATE_KEY);
        ECKey ecKey = ECKey.fromPrivate(privateKey);
        String expectedSender = ByteUtil.toHexString(ecKey.getAddress());
        
        // When: Creating and signing multiple transactions
        for (int i = 0; i < 5; i++) {
            Transaction tx = Transaction.builder()
                    .nonce(BigInteger.valueOf(i))
                    .gasPrice(BigInteger.valueOf(1000000000))
                    .gasLimit(BigInteger.valueOf(21000))
                    .receiveAddress(Hex.decode("1234567890123456789012345678901234567890"))
                    .value(BigInteger.valueOf(1000 * (i + 1)))
                    .build();
            
            tx.sign(privateKey);
            
            // Then: Each transaction should be properly signed
            assertNotNull(tx.getSignature(), "Transaction " + i + " should be signed");
            assertEquals(expectedSender,
                         ByteUtil.toHexString(tx.getSender().getBytes()),
                         "Transaction " + i + " sender should match");
        }
    }
    
    @Test
    void testSignatureComponents() {
        // Given: A transaction
        byte[] privateKey = Hex.decode(TEST_PRIVATE_KEY);
        
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.valueOf(1000000000))
                .gasLimit(BigInteger.valueOf(21000))
                .receiveAddress(Hex.decode("13978aee95f38490e9769c39b2773ed763d9cd5f"))
                .value(BigInteger.valueOf(1000))
                .build();
        
        // When: Signing the transaction
        tx.sign(privateKey);
        
        // Then: Should be able to access all signature components
        assertNotNull(tx.getSignature(), "Signature should exist");
        
        BigInteger r = tx.getSignature().getR();
        BigInteger s = tx.getSignature().getS();
        byte v = tx.getSignature().getV();
        
        assertNotNull(r, "R component should exist");
        assertNotNull(s, "S component should exist");
        assertTrue(r.compareTo(BigInteger.ZERO) > 0, "R should be positive");
        assertTrue(s.compareTo(BigInteger.ZERO) > 0, "S should be positive");
        assertTrue(v == 27 || v == 28, "V should be 27 or 28");
        
        // Verify signature components can be encoded
        byte[] rBytes = BigIntegers.asUnsignedByteArray(r);
        byte[] sBytes = BigIntegers.asUnsignedByteArray(s);
        
        assertNotNull(rBytes, "R bytes should be encodable");
        assertNotNull(sBytes, "S bytes should be encodable");
        assertTrue(rBytes.length > 0, "R bytes should have data");
        assertTrue(sBytes.length > 0, "S bytes should have data");
    }
    
    /**
     * Diagnostic test: decode the exact raw transaction submitted via eth_sendRawTransaction
     * and verify the hash, sender recovery, and stable encode/decode of the wire bytes.
     */
    @Test
    void testDiagnosticDecodeSubmittedRawTransaction() {
        // The exact raw transaction submitted via Postman
        String submittedHex = "01f86c01850df84758008252089413978aee95f38490e9769c39b2773ed763d9cd5f880de0b6b3a7640000801ba053464f391362723e2ee9fa6efe2dbbc823964f2518d0bd35d9197cce4dbacdefa036039620b8eaebe315dc35123248af26e77e34d93a1d12a463fbe81c137251d5";
        byte[] rawBytes = Hex.decode(submittedHex);
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🔍 DIAGNOSTIC: Decoding submitted raw transaction");
        System.out.println("=".repeat(70));
        
        // Step 1: Parse as ImmutableTransaction (same as eth_sendRawTransaction)
        Transaction decoded = new ImmutableTransaction(rawBytes);
        
        System.out.println("\n📋 Decoded Transaction Fields:");
        System.out.println("   First byte: 0x" + String.format("%02x", rawBytes[0] & 0xFF) + " (0x01 = typed tx prefix in sample)");
        System.out.println("   Nonce:      " + new BigInteger(1, decoded.getNonce()));
        System.out.println("   GasPrice:   " + decoded.getGasPrice().asBigInteger());
        System.out.println("   GasLimit:   " + new BigInteger(1, decoded.getGasLimit()));
        System.out.println("   To:         " + decoded.getReceiveAddress());
        System.out.println("   Value:      " + decoded.getValue().asBigInteger());
        System.out.println("   Data:       " + (decoded.getData() == null ? "null" : ByteUtil.toHexString(decoded.getData())));
        System.out.println("   ChainId:    " + decoded.getChainId());
        System.out.println("   Sig V:      " + decoded.getSignature().getV());
        System.out.println("   Sig R:      " + decoded.getSignature().getR().toString(16));
        System.out.println("   Sig S:      " + decoded.getSignature().getS().toString(16));
        
        // Step 2: Compute hash (same as what eth_sendRawTransaction returns)
        System.out.println("\n📊 Hash Computation:");
        System.out.println("   getHash():    " + decoded.getHash());
        System.out.println("   getRawHash(): " + decoded.getRawHash());
        
        // Step 3: Expected hash from the receipt query
        String expectedHash = "0xb0d1196999302691c990ea6e81e31b94f576517c14ccd8275951c3dc7c8ab3be";
        String actualHash = decoded.getHash().toJsonString();
        System.out.println("   Expected:     " + expectedHash);
        System.out.println("   Actual:       " + actualHash);
        System.out.println("   Match:        " + expectedHash.equals(actualHash));
        
        // Step 4: Recover sender
        System.out.println("\n👤 Sender Recovery:");
        RskAddress sender = decoded.getSender();
        System.out.println("   Sender: 0x" + ByteUtil.toHexString(sender.getBytes()));
        
        // The cow key address
        byte[] cowPrivKey = Hex.decode(TEST_PRIVATE_KEY);
        ECKey cowKey = ECKey.fromPrivate(cowPrivKey);
        String cowAddress = ByteUtil.toHexString(cowKey.getAddress());
        System.out.println("   Cow:    0x" + cowAddress);
        System.out.println("   Match:  " + cowAddress.equals(ByteUtil.toHexString(sender.getBytes())));
        
        // Step 5: acceptTransactionSignature check (regtest chainId = 33)
        byte regtestChainId = 33;
        boolean sigAccepted = decoded.acceptTransactionSignature(regtestChainId);
        System.out.println("\n🔐 Signature Validation:");
        System.out.println("   tx.chainId:          " + decoded.getChainId());
        System.out.println("   regtest chainId:     " + regtestChainId);
        System.out.println("   acceptSignature:     " + sigAccepted);
        
        // Step 6: Encode after decode (must match original wire bytes)
        byte[] reEncoded = decoded.getEncoded();
        String reEncodedHex = ByteUtil.toHexString(reEncoded);
        System.out.println("\n🔄 Encode after decode:");
        System.out.println("   Original: " + submittedHex);
        System.out.println("   Encoded:  " + reEncodedHex);
        System.out.println("   Match:    " + submittedHex.equals(reEncodedHex));
        
        // Step 7: Now also build the SAME transaction from scratch and compare
        System.out.println("\n🏗️ Build equivalent transaction from scratch:");
        Transaction built = Transaction.builder()
                .nonce(BigInteger.ONE)
                .gasPrice(new BigInteger("60000000000"))
                .gasLimit(BigInteger.valueOf(21000))
                .receiveAddress(Hex.decode("13978aee95f38490e9769c39b2773ed763d9cd5f"))
                .value(new BigInteger("1000000000000000000"))
                .build();
        built.sign(cowPrivKey);
        
        System.out.println("   Built hash:       " + built.getHash());
        System.out.println("   Built rawHash:    " + built.getRawHash());
        System.out.println("   Built sender:     0x" + ByteUtil.toHexString(built.getSender().getBytes()));
        System.out.println("   Built encoded:    " + ByteUtil.toHexString(built.getEncoded()));
        System.out.println("   Hashes match:     " + decoded.getHash().equals(built.getHash()));
        System.out.println("   Encodings match:  " + submittedHex.equals(ByteUtil.toHexString(built.getEncoded())));
        
        System.out.println("=".repeat(70) + "\n");
        
        // Assertions
        assertNotNull(decoded.getSignature());
        assertNotNull(sender);
        assertTrue(sigAccepted, "Signature should be accepted for regtest chainId");
    }

    @Test
    void testRawTransactionEncoding() {
        // Given: A signed transaction
        byte[] privateKey = Hex.decode(TEST_PRIVATE_KEY);
        
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(new BigInteger("1000000000000"))
                .gasLimit(BigInteger.valueOf(10000))
                .receiveAddress(Hex.decode("13978aee95f38490e9769c39b2773ed763d9cd5f"))
                .value(new BigInteger("10000000000000000"))
                .build();
        
        tx.sign(privateKey);
        
        // When: Getting the raw transaction
        byte[] rawTx = tx.getEncoded();
        String rawTxHex = ByteUtil.toHexString(rawTx);
        
        // Then: Raw transaction should be properly formatted
        assertNotNull(rawTx, "Raw transaction should not be null");
        assertTrue(rawTx.length > 0, "Raw transaction should have data");
        assertNotNull(rawTxHex, "Hex encoding should work");
        
        // Verify the transaction can be reconstructed from raw bytes
        Transaction reconstructed = new ImmutableTransaction(rawTx);
        assertEquals(tx.getHash(), reconstructed.getHash(), "Hash should match after reconstruction");
        assertEquals(ByteUtil.toHexString(tx.getSender().getBytes()),
                     ByteUtil.toHexString(reconstructed.getSender().getBytes()),
                     "Sender should match after reconstruction");
    }

    /**
     * Verifies that we can create a raw legacy transaction whose encoding starts with a byte &gt; 0x7f
     * and different from 0xf8. Per RSKIP543/EIP-2718, legacy transactions have no type prefix;
     * the RLP list first byte is 0xc0–0xff. We add data so the list payload exceeds 255 bytes,
     * which yields first byte 0xf9 (long list with 2-byte length) instead of 0xf8.
     */
    @Test
    void testLegacyRawTransactionFirstByteGreaterThan0x7f() {
        byte[] privateKey = Hex.decode(TEST_PRIVATE_KEY);
        // Add enough data so RLP list payload >= 256 bytes → first byte 0xf9 instead of 0xf8
        byte[] extraData = new byte[200];
        java.util.Arrays.fill(extraData, (byte) 0x00);

        //BigInteger value = new BigInteger("1000000000000000000"); // 1 RBTC
        //BigInteger gasPrice = new BigInteger("60000000000"); // 60 Gwei
        //BigInteger gasLimit = BigInteger.valueOf(21000);

        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(new BigInteger("60000000000"))
                .gasLimit(BigInteger.valueOf(210000))
                .receiveAddress(Hex.decode("13978aee95f38490e9769c39b2773ed763d9cd5f"))
                .value(new BigInteger("1000000000000000000"))
                .data(extraData)
                .build();
        tx.sign(privateKey);

        byte[] encoded = tx.getEncoded();
        assertNotNull(encoded, "Encoded transaction should not be null");
        assertTrue(encoded.length > 0, "Encoded transaction should have data");

        String rawTxHex = "0x" + ByteUtil.toHexString(encoded);
        int firstByte = encoded[0] & 0xFF;
        System.out.println("\nLegacy raw transaction (first byte > 0x7f, different from 0xf8):");
        System.out.println("  First byte: 0x" + Integer.toHexString(firstByte));
        System.out.println("  Raw tx:     " + rawTxHex);
        printPostmanRequest(tx, "Legacy transaction (first byte 0x" + Integer.toHexString(firstByte) + ")");

        assertTrue(firstByte > 0x7f,
                "Legacy raw transaction must have first byte > 0x7f (RLP list); got 0x" + Integer.toHexString(firstByte));
        assertNotEquals(0xf8, firstByte,
                "This test uses extra data so the first byte is not 0xf8 (e.g. 0xf9 for payload >= 256 bytes)");

        Transaction decoded = new ImmutableTransaction(encoded);
        assertEquals(tx.getHash(), decoded.getHash(), "Encode/decode should preserve hash");
    }

    /**
     * Generates signed raw transactions and prints them to the console
     * ready for eth_sendRawTransaction via Postman/curl.
     *
     * Pass prefix as a byte array: empty for legacy, or any arbitrary bytes.
     * The signature is computed with the prefix included in the hash,
     * so valid prefixes produce submittable transactions.
     */
    @Test
    void testGenerateRawTransactionsForAllTypes() {
        byte[] pk = Hex.decode(TEST_PRIVATE_KEY);
        byte[] dest = Hex.decode("13978aee95f38490e9769c39b2773ed763d9cd5f");
        BigInteger gp = new BigInteger("60000000000");
        BigInteger gl = BigInteger.valueOf(21000);
        BigInteger val = new BigInteger("1000000000000000000");

        // ---- Valid transactions (nonces 0–9, consecutive) ----
        printRawTx(pk, dest, gp, gl, val, BigInteger.ZERO,      new byte[0],             "Legacy (no prefix)");
        printRawTx(pk, dest, gp, gl, val, BigInteger.ONE,       new byte[]{0x01},        "Type-1 (EIP-2930)");
        printRawTx(pk, dest, gp, gl, val, BigInteger.TWO,       new byte[]{0x02},        "Type-2 Standard (EIP-1559)");
        printRawTx(pk, dest, gp, gl, val, BigInteger.valueOf(3), new byte[]{0x03},       "Type-3 (Blob)");
        printRawTx(pk, dest, gp, gl, val, BigInteger.valueOf(4), new byte[]{0x04},       "Type-4 (EIP-7702)");
        printRawTx(pk, dest, gp, gl, val, BigInteger.valueOf(5), new byte[]{0x02, 0x00}, "RSK Namespace (subtype 0x00)");
        printRawTx(pk, dest, gp, gl, val, BigInteger.valueOf(6), new byte[]{0x02, 0x03}, "RSK Namespace (subtype 0x03)");
        printRawTx(pk, dest, gp, gl, val, BigInteger.valueOf(7), new byte[]{0x02, 0x7f}, "RSK Namespace (subtype 0x7f)");
        printLegacyRawTx(pk, dest, gp, gl, val, BigInteger.valueOf(8),
                0, "Legacy (first byte 0xf8, no data)");
        BigInteger glData = BigInteger.valueOf(100000);
        printLegacyRawTx(pk, dest, gp, glData, val, BigInteger.valueOf(9),
                150, "Legacy (first byte 0xf9, 150 bytes data)");

        // ---- Invalid transactions (will be rejected, nonce doesn't matter) ----
        printRawTx(pk, dest, gp, gl, val, BigInteger.valueOf(99), new byte[]{0x7f},       "INVALID: Unknown type 0x7f");
        printRawTx(pk, dest, gp, gl, val, BigInteger.valueOf(99), new byte[]{(byte) 0x80},"INVALID: RLP string range 0x80");
    }

    /**
     * Builds a legacy transaction, signs it with the prefix included in the
     * signing hash (prefix || unsigned RLP), then produces the final encoding
     * as prefix || signed RLP.
     *
     * @param prefix arbitrary prefix bytes (empty for legacy)
     */
    private void printRawTx(byte[] privateKey, byte[] dest,
                             BigInteger gasPrice, BigInteger gasLimit,
                             BigInteger value, BigInteger nonce,
                             byte[] prefix, String description) {
        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .receiveAddress(dest)
                .value(value)
                .build();

        byte[] unsignedRlp = tx.getEncodedRaw();
        byte[] hashInput = prefix.length == 0
                ? unsignedRlp
                : ByteUtil.merge(prefix, unsignedRlp);
        byte[] hash = HashUtil.keccak256(hashInput);

        ECKey key = ECKey.fromPrivate(privateKey).decompress();
        tx.setSignature(key.sign(hash));

        byte[] signedRlp = tx.getEncoded();
        byte[] raw = prefix.length == 0
                ? signedRlp
                : ByteUtil.merge(prefix, signedRlp);

        String rawHex = "0x" + ByteUtil.toHexString(raw);
        String prefixHex = prefix.length == 0 ? "(none)" : "0x" + ByteUtil.toHexString(prefix);

        String expectedBehavior;
        if (prefix.length == 0) {
            expectedBehavior = "VALID legacy (no prefix)";
        } else {
            int first = prefix[0] & 0xFF;
            if (first <= 0x7f) {
                expectedBehavior = (first == 0x01 || first == 0x02 || first == 0x03 || first == 0x04)
                        ? "VALID typed (EIP-2718 type byte 0x" + String.format("%02x", first) + ")"
                        : "REJECTED: unknown type 0x" + String.format("%02x", first);
            } else if (first < 0xc0) {
                expectedBehavior = "REJECTED: 0x" + String.format("%02x", first)
                        + " is in [0x80,0xbf] (invalid range)";
            } else {
                expectedBehavior = "REJECTED: 0x" + String.format("%02x", first)
                        + " is in [0xc0,0xff] (legacy RLP range, raw bytes are malformed)";
            }
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println(description);
        System.out.println("  Prefix:      " + prefixHex);
        System.out.println("  First byte:  0x" + String.format("%02x", raw[0] & 0xFF));
        if (raw.length > 1) {
            System.out.println("  Second byte: 0x" + String.format("%02x", raw[1] & 0xFF));
        }
        System.out.println("  Expected:    " + expectedBehavior);
        System.out.println("  Raw hex:     " + rawHex);
        System.out.println("\n  curl -X POST http://localhost:4444 \\");
        System.out.println("    -H \"Content-Type: application/json\" \\");
        System.out.println("    -d '{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\","
                + "\"params\":[\"" + rawHex + "\"],\"id\":1}'");
        System.out.println("=".repeat(70));
    }

    /**
     * Builds a proper legacy transaction with the given data size, signs it,
     * and prints the raw hex. The first byte is determined naturally by the
     * RLP encoding — no manual byte manipulation.
     */
    private void printLegacyRawTx(byte[] privateKey, byte[] dest,
                                   BigInteger gasPrice, BigInteger gasLimit,
                                   BigInteger value, BigInteger nonce,
                                   int dataSize, String description) {
        byte[] data = dataSize > 0 ? new byte[dataSize] : null;

        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .receiveAddress(dest)
                .value(value)
                .data(data)
                .build();
        tx.sign(privateKey);

        byte[] raw = tx.getEncoded();
        String rawHex = "0x" + ByteUtil.toHexString(raw);

        System.out.println("\n" + "=".repeat(70));
        System.out.println(description);
        System.out.println("  First byte:  0x" + String.format("%02x", raw[0] & 0xFF));
        if (raw.length > 1) {
            System.out.println("  Second byte: 0x" + String.format("%02x", raw[1] & 0xFF));
        }
        System.out.println("  Tx size:     " + raw.length + " bytes");
        System.out.println("  Data size:   " + dataSize + " bytes");
        System.out.println("  Expected:    VALID legacy");
        System.out.println("  Raw hex:     " + rawHex);
        System.out.println("\n  curl -X POST http://localhost:4444 \\");
        System.out.println("    -H \"Content-Type: application/json\" \\");
        System.out.println("    -d '{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\","
                + "\"params\":[\"" + rawHex + "\"],\"id\":1}'");
        System.out.println("=".repeat(70));
    }
}
