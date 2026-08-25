/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;

import java.util.Map;

/**
 * Mainnet historical pegout selections, complete for {@code [genesis, RSKIP559 activation]}.
 */
public class HistoricalPegoutSelectionsMainNetConstants extends HistoricalPegoutSelectionsConstants {

    private static final HistoricalPegoutSelectionsMainNetConstants instance = new HistoricalPegoutSelectionsMainNetConstants();

    private HistoricalPegoutSelectionsMainNetConstants() {
        // Map.ofEntries rejects a duplicated key at construction; put() would silently overwrite it.
        selections = Map.ofEntries(
            Map.entry(new Keccak256("8472c6d227fe867f04859ad819a0585b7c2dc953896c37d9fd04d4019941ac18"), Sha256Hash.wrap("49796a89abfd770308cf4f4a8c49e3f97ac2f0edb33bdba98434603c82135802")),
            Map.entry(new Keccak256("7e25dedc1a2760d1fa94786d3cd61c3419c37eb795351bdac006232e6c26903c"), Sha256Hash.wrap("2c6f69d2f7746b01dba7c9cdf248973a239fcc78f7dd5971f99992e941a0715e")),
            Map.entry(new Keccak256("a44ebfa65662ed2d2d480a1535be81f77c75aaa91119292dc1217d30625bb1d3"), Sha256Hash.wrap("20806dadc7c9cbe777c2599983d38831461f7b701afb1ac93a3ec386c376edb9")),
            Map.entry(new Keccak256("504dfc8314c67b81554e15cfebf093ba2b0ce443d5c66e0b3e25061fd61f91b8"), Sha256Hash.wrap("5965a75e7e56ed4a308cc1bf8d94415c03c6a56f7302ae488e1d3fa05cd70e61")),
            Map.entry(new Keccak256("d5292674f3a5c2d12271ce0a64eb1d82d0e18d022bae0fe771146597634e713a"), Sha256Hash.wrap("8508d6a45b5a3e4ab8396c3f5ad895107e6a0ded9311729804b806661763779d")),
            Map.entry(new Keccak256("f921e810974397e37dbc53199b28feb2e8fe2d35917595f7435f73660a33d059"), Sha256Hash.wrap("00a726e67845f3d16a263ffde47315457a3388b7b9ce10f73c05560a69d09a40")),
            Map.entry(new Keccak256("c3a8d93edac614f0a65243e300c75752f67835d4a5d9d08fd43cfe2c80d5aef9"), Sha256Hash.wrap("5f1119f0d62babb36c513a41d4ccb4b0545a2a2dbd2b07ffd98d259e1c194e1f")),
            Map.entry(new Keccak256("8f9a0fd8b3f2478d16e62d7b95ba61bfc8e6089a58318a62aae4efc20b8b7a3a"), Sha256Hash.wrap("3aa787c1409f942991086de6c26fe7330ca2334e0261f1df57e1f1d15a5298d1")),
            Map.entry(new Keccak256("91bdfd83c09b38b2f8cc809195d816fe14c3d11ea0fad18deb8a293fa04d3fcf"), Sha256Hash.wrap("6ec8b3cbb583d396c33ee29488cc92ae4e358b84b7eec739c3a6029c349e295b")),
            Map.entry(new Keccak256("b0e4fd1f5247607b3bc45bbb4f9e11cdd7bfaa503c573f5f46bd9b78c20f445d"), Sha256Hash.wrap("bff89f2d889c4f72c35dad13f2d0f9058ede1f61f57c7f0db0fdeca36a44410a")),
            Map.entry(new Keccak256("06f1cc6d737a73a30adf23882916a21d0f9723eac209e430840f64d9dde23397"), Sha256Hash.wrap("3c17261a2361da8e82e705007ad1c5f35c4712f20d34522e4b588edbb7ab1380")),
            Map.entry(new Keccak256("542b7b90466a322d4d3d5dfca4b77665655909c69bf93e48366b8f7147d960a2"), Sha256Hash.wrap("56c0defa95f2039f26b091f03b1a049d3cba4b577ebe8fccf178f9da98da1910")),
            Map.entry(new Keccak256("62213988a1a5da72e969acfae23d006f0578735997df5f009ae2f0e53d663075"), Sha256Hash.wrap("58350f1b447372373a2a2f60b6a301fd708a9527294320f1171a6aedef197749")),
            Map.entry(new Keccak256("e6b72ec3e27d1e6a08a64d73db972140b9ff149bdd9fc2c8c78a9cd869cc31b1"), Sha256Hash.wrap("b8bdca02f08b4313b9e9051d5747d7472bae300ff2866480c73f37c3b4da10cb")),
            Map.entry(new Keccak256("5732993d1305b266116d8ebf84974f8f3900b24510eb9adf9d8430fae7f67b90"), Sha256Hash.wrap("99fd3ef49673538e60321a0dca5b5b3ee74b43e9788d98dfe7caed9f429b6d75")),
            Map.entry(new Keccak256("a3c5b4b7a55662e04e405131bccaa007d520efe22b8527d4461238e4982be63c"), Sha256Hash.wrap("40921869eae466df43132a88faef2f71b5c481f52bd8925b3752e5a27713c5d7")),
            Map.entry(new Keccak256("67ccabfd373e9d92b8bc9e86bb0f38856d3de2ed62491ab6d1a4ce6bf6c3a4b5"), Sha256Hash.wrap("d2ca62b50287a300122672a9b05e08422ec36e41d0424c2ba7612bf1ca96d607"))
        );
    }

    public static HistoricalPegoutSelectionsMainNetConstants getInstance() {
        return instance;
    }
}
