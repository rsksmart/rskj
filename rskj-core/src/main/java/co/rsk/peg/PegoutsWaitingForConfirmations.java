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
package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.common.primitives.UnsignedBytes;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representation of a queue of BTC release
 * transactions waiting for confirmations
 * on the rsk network.
 *
 * @author Ariel Mendelzon
 */
public class PegoutsWaitingForConfirmations {

    private static final Logger logger = LoggerFactory.getLogger(PegoutsWaitingForConfirmations.class);

    private final EntriesStore entries;

    public PegoutsWaitingForConfirmations(Set<Entry> entries) {
        this.entries = new EntriesStore(entries);
    }

    /**
     * Return entries ordered according to {@link Entry.BTC_TX_COMPARATOR}.
     */
    public Collection<Entry> getEntriesWithoutHashOrdered() {
        return entries.entriesSet.stream().filter(e -> e.getPegoutCreationRskTxHash() == null)
                .sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    /**
     * Return entries ordered according to {@link Entry.BTC_TX_COMPARATOR}.
     */
    public Collection<Entry> getEntriesWithHashOrdered() {
        return entries.entriesSet.stream().filter(e -> e.getPegoutCreationRskTxHash() != null)
                .sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    public Collection<Entry> getEntries(ForBlock activations) {
        // TODO: after fork, leave only sorted output no need for rskip559 switch
        // And rename it to getEntriesOrdered

        var rskip559 = activations.isActive(ConsensusRule.RSKIP559);
        if (rskip559) {
            return entries.entriesSet.stream().sorted(Entry.BTC_TX_COMPARATOR).toList();
        }
        return entries.entriesSet.stream().toList();
    }

    /**
     * Given a block number and a minimum number of confirmations,
     * returns a subset of transactions within the set that have
     * at least that number of confirmations.
     *
     * Optionally supply a maximum slice size to limit the output size.
     * Sliced items are also removed from the set (thus the name, slice).
     *
     * @param currentBlockNumber   the current execution block number (height).
     * @param minimumConfirmations the minimum desired confirmations for the slice elements.
     * @param activations          activations for a current block that determine entries ordering/filtering.
     *
     * @return an optional with an entry with enough confirmations if found. If not, an empty optional.
     */
    public Optional<Entry> getNextPegoutWithEnoughConfirmations(
        Long currentBlockNumber,
        Integer minimumConfirmations,
        ForBlock activations
    ) {
        var pegoutOverwrite = this.getOutputOverwrite(currentBlockNumber, activations);

        var rskip559 = activations.isActive(ConsensusRule.RSKIP559);
        var pegout = this.entries.getNextPegoutWithEnoughConfirmations(currentBlockNumber, minimumConfirmations, rskip559);

        // TODO: must be moved to the top of function after hardfork
        // If diff overwrite exists - just return it
        if (pegoutOverwrite != null) {
            logger.info(
                    "PreRSKIP559 pegout applied! Current block:{} Pegout ref:{}@{}",
                    currentBlockNumber,
                    pegoutOverwrite.getBtcTransaction().getHash(),
                    pegoutOverwrite.getPegoutCreationRskBlockNumber());

            // TODO: no need for this log message after a hardfork
            // it is used only to validate how node syncing with hardcoded outputs
            if (pegout.isPresent()) {
                logger.info(
                        "Replaced pegout is: current block:{} Pegout ref:{}@{}",
                        currentBlockNumber,
                        pegout.get().getBtcTransaction().getHash(),
                        pegout.get().getPegoutCreationRskBlockNumber());
            }
            return Optional.of(pegoutOverwrite);
        }

        return pegout;
    }

    @Nullable
    private Entry getOutputOverwrite(Long blk, ForBlock activations) {
        if (activations.isActive(ConsensusRule.RSKIP559)) {
            // The code below will work the same without this early return
            // But it was requested to put this condition "just in case"
            return null;
        }

        var hardcoded = getHardcodedPegouts(activations.getNetworkName());
        if (hardcoded == null) {
            // Only main and testnet (v3) has hardcoded outputs
            // other configuration don't
            return null;
        }

        var records = hardcoded.get(blk);
        if (records == null) {
            return null;
        }

        /*
         * When we have multiple TX that require next pegout in the block
         * then this code will be called multiple time during block processing.
         * Next pegout is consumed by TX (it will be removed from collection).
         * Thus we have ordered sequence of hardcoded `records`
         * that matches how historically pegouts were retrieved during processing some block.
         */
        for (var ref : records) {
            var pegout = this.getPegoutByRef(ref);
            if (pegout.isPresent()) {
                return pegout.get();
            } else {
                // Expected behaviour if called second time for the same block
                logger.debug("Skip hardcoded reference {}", ref);
            }
        }

        return null;
    }

    /**
     * Search entries by pegout reference.
     */
    Optional<Entry> getPegoutByRef(PegoutRef pegoutRef) {
        return this.entries.entriesSet.stream().filter(e -> {
            return pegoutRef.btcTxHash().equals(e.getBtcTransaction().getHash())
                    && Long.valueOf(pegoutRef.rskBlock()).equals(e.getPegoutCreationRskBlockNumber());
        }).findFirst();
    }

    public void add(Entry entry) {
        this.entries.addEntry(entry);
    }

    public boolean removeEntry(Entry entry) {
        return entries.removeEntry(entry);
    }

    /**
     * Encapsulate entries while preserving sorting order before fork.
     *
     * @deprecated: should be removed after RSKIP559 activation,
     * historical outputs will be hardcoded thus no need for this Java 21+ tricks at all.
     */
    @Deprecated
    public static class EntriesStore {

        // From java SDK
        private static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private final HashSet<Entry> entriesSet;

        /**
         * Must be equal to new HashSet() call in Java 17.
         * Use it to preserve old behaviour in Java21+.
         */
        public static HashSet<Entry> setOfEntries() {
            return new HashSet<>(0, DEFAULT_LOAD_FACTOR);
        }

        /**
         * This is a standard code for `new HashSet<>(entries);` in Java 17.
         * Coefficients were changed in Java 21.
         * Use it to preserve old behaviour in Java21+.
         */
        public static HashSet<Entry> setOfEntries(Collection<Entry> entries) {
            // Need to hardcode Java 17 init params here to preserve old behaviour in Java 21+
            var ehs = new HashSet<Entry>(Math.max((int) (entries.size() / DEFAULT_LOAD_FACTOR) + 1, 16));
            ehs.addAll(entries);
            return ehs;
        }

        private EntriesStore(Collection<Entry> entries) {
            this.entriesSet = EntriesStore.setOfEntries(entries);
        }

        private boolean hasEnoughConfirmations(Entry entry, Long currentBlockNumber, Integer minimumConfirmations) {
            return (currentBlockNumber - entry.getPegoutCreationRskBlockNumber()) >= minimumConfirmations;
        }

        /**
         * @param isRskip559 turns on deterministic order for entries before filtering.
         */
        public Optional<Entry> getNextPegoutWithEnoughConfirmations(
                Long currentBlockNumber,
                Integer minimumConfirmations,
                boolean isRskip559 // TODO remove after RSKIP559 activation
        ) {
            var entries = entriesSet.stream()
                    .filter(entry -> hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations));
            // TODO: In next release after RSKIP559 activation we must use only properly sorted entries
            // all historical difference will be hardcoded
            if (isRskip559) {
                entries = entries.sorted(Entry.BTC_TX_COMPARATOR);
            }
            return entries.findFirst();
        }

        public void addEntry(Entry entry) {
            if (this.entriesSet.stream().noneMatch(e -> e.getBtcTransaction().equals(entry.getBtcTransaction()))) {
                this.entriesSet.add(entry);
            }
        }

        public boolean removeEntry(Entry entry) {
            return this.entriesSet.remove(entry);
        }
    }

    public static class Entry {

        private final BtcTransaction btcTransaction;

        private final Long pegoutCreationRskBlockNumber;

        private final Keccak256 pegoutCreationRskTxHash;

        /**
         * Compares entries using the lexicographical order of the btc tx's serialized bytes.
         */
        public static final Comparator<Entry> BTC_TX_COMPARATOR = new Comparator<Entry>() {

            private Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

            @Override
            public int compare(Entry e1, Entry e2) {
                return comparator.compare(
                    e1.getBtcTransaction().bitcoinSerialize(),
                    e2.getBtcTransaction().bitcoinSerialize()
                );
            }
        };

        public Entry(BtcTransaction btcTransaction, Long pegoutCreationRskBlockNumber,
                Keccak256 pegoutCreationRskTxHash) {
            this.btcTransaction = btcTransaction;
            this.pegoutCreationRskBlockNumber = pegoutCreationRskBlockNumber;
            this.pegoutCreationRskTxHash = pegoutCreationRskTxHash;
        }

        public Entry(BtcTransaction btcTransaction, Long pegoutCreationRskBlockNumber) {
            this(btcTransaction, pegoutCreationRskBlockNumber, null);
        }

        public BtcTransaction getBtcTransaction() {
            return btcTransaction;
        }

        public Long getPegoutCreationRskBlockNumber() {
            return pegoutCreationRskBlockNumber;
        }

        public Keccak256 getPegoutCreationRskTxHash() {
            return pegoutCreationRskTxHash;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            Entry otherEntry = (Entry) o;
            return otherEntry.getBtcTransaction().equals(getBtcTransaction())
                && otherEntry.getPegoutCreationRskBlockNumber().equals(getPegoutCreationRskBlockNumber())
                && Objects.equals(getPegoutCreationRskTxHash(), otherEntry.getPegoutCreationRskTxHash());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getBtcTransaction(), getPegoutCreationRskBlockNumber());
        }
    }

    /**
     * Represent hardcoded reference to pegout
     */
    static record PegoutRef(Sha256Hash btcTxHash, long rskBlock) {

        static PegoutRef from(String hash, long rskBlock) {
            return new PegoutRef(Sha256Hash.wrap(hash), rskBlock);
        }

        static PegoutRef[] listFrom(String hash, long rskBlock) {
            return new PegoutRef[] { PegoutRef.from(hash, rskBlock) };
        }

        @Override
        public String toString() {
            return String.format("%s@%d", btcTxHash, rskBlock);
        }
    }

    @Nullable
    static  Map<Long, PegoutRef[]>  getHardcodedPegouts(String networkName) {
        if (networkName == null) {
            return null;
        }

        return switch (networkName) {
            case "main" -> MAINNET;
            case "testnet" -> TESTNET;
            default -> null;
        };
    }

    private static final Map<Long, PegoutRef[]> MAINNET;

    static {
        var m = new HashMap<Long, PegoutRef[]>();
        // These are known diff between historical outputs and RSKIP559 stable output algo
        m.put(3345557L, PegoutRef.listFrom("5965a75e7e56ed4a308cc1bf8d94415c03c6a56f7302ae488e1d3fa05cd70e61", 3341556L));
        m.put(3381087L, PegoutRef.listFrom("8508d6a45b5a3e4ab8396c3f5ad895107e6a0ded9311729804b806661763779d", 3377083L));
        m.put(3381093L, PegoutRef.listFrom("00a726e67845f3d16a263ffde47315457a3388b7b9ce10f73c05560a69d09a40", 3377083L));
        m.put(3441427L, PegoutRef.listFrom("3aa787c1409f942991086de6c26fe7330ca2334e0261f1df57e1f1d15a5298d1", 3437424L));
        m.put(3441438L, PegoutRef.listFrom("6ec8b3cbb583d396c33ee29488cc92ae4e358b84b7eec739c3a6029c349e295b", 3437428L));
        m.put(3655284L, PegoutRef.listFrom("bff89f2d889c4f72c35dad13f2d0f9058ede1f61f57c7f0db0fdeca36a44410a", 3651282L));
        m.put(5002812L, PegoutRef.listFrom("40921869eae466df43132a88faef2f71b5c481f52bd8925b3752e5a27713c5d7", 4998807L));
        m.put(7073815L, PegoutRef.listFrom("d2ca62b50287a300122672a9b05e08422ec36e41d0424c2ba7612bf1ca96d607", 7069808L));
        // These are not required after RSKIP559 activation when old output algo will be dropped
        // ... TBD
        MAINNET = Collections.unmodifiableMap(m);
    }

    private static final Map<Long, PegoutRef[]> TESTNET;

    static {
        var t = new HashMap<Long, PegoutRef[]>();
        // These are known diff between historical outputs and RSKIP559 stable output algo
        t.put(1120318L, PegoutRef.listFrom("fc48fd9099b0ed41511e5d6da7a536880ef7dd1deb1ed1289e3c651e934aaa49", 1120291L));
        t.put(1865157L, PegoutRef.listFrom("816a0708cfe301b1076d795d74b36955f4ef1fc477487772d06121a5dedfeca0", 1865146L));
        t.put(2589068L, new PegoutRef[]{
            PegoutRef.from("c6f1fe4aba2e98cc9e190ae7aa6664901d417172847232a03a8112a3342ef53e", 2589056L),
            PegoutRef.from("4dbe93aaaab473d53039e88ab6f4b81704c3c9ae34a60fa94e32fc350763a9d3", 2589056L)
        });
        t.put(2589071L, PegoutRef.listFrom("a476e91aeca06b6c52d276fb6734c2a4849dfe9f9feca2a055ad5f3add2ec328", 2589056L));
        t.put(2589077L, PegoutRef.listFrom("0359d4b1621b4faa203f94394dd0c9f5094fcf6cad4acf6af604da9ce1ec3217", 2589056L));
        t.put(2589086L, PegoutRef.listFrom("6ceb7c9be0f828f7d6e6d847671aba87760b72ae4e63a14afb1e11170d306d4f", 2589068L));
        t.put(2589112L, new PegoutRef[]{
            PegoutRef.from("9d5b2dc437edc59f216257e202e9ab1bc6d10a791624cb1e976879049a74178d", 2589086L),
            PegoutRef.from("30f776e7e2842db08ffe1424cba3caf8857654fa2269c0cfd22a9bae177ee39c", 2589086L)
        });
        t.put(2589115L, PegoutRef.listFrom("caf72f6d65f287981afdba49d617a1bad8490e2b0a445629e797a7864d95b15f", 2589086L));
        t.put(2589121L, PegoutRef.listFrom("c6184c576891dea44f2aa6269315a84c946486d3aa3845e8e29c65b63da778f7", 2589086L));
        t.put(2589135L, PegoutRef.listFrom("0cbb38f1abc521691983be493e85b7115f317910e22ebeb55765c63615c07524", 2589112L));
        t.put(2589142L, PegoutRef.listFrom("259bbdd9eb7c0deba308d084365cdb6e02a34f7827b2ee0ca7e341dfa90daa8e", 2589112L));
        t.put(2589148L, PegoutRef.listFrom("0762be9af2d43df6b5bd6ef2ead5876137235ee7efeed0f17f1401bef5f18ef8", 2589112L));
        t.put(2589153L, PegoutRef.listFrom("6dd692b7c2aa1b8a5e14b2f23be590a5b0f45a74c5a7933f824abf9d40cc139f", 2589112L));
        t.put(2589163L, PegoutRef.listFrom("b0b0caa4338dca07086da1ef503aaa5c81250e3b7390cff304d6bc83169d8bc7", 2589112L));
        t.put(2589169L, PegoutRef.listFrom("19a8eac8d1735bd80cdd087b132b1c7b73ebf5c0fe1691892cad1a7dad3e5086", 2589086L));
        t.put(2589174L, PegoutRef.listFrom("6555870f4ab9eb25a2a3eaec6aa2d9c0e347fbaf45eba1737e77a234945214a7", 2589112L));
        t.put(2589179L, PegoutRef.listFrom("9a275de558b3de838eaf9674dfe7889da9283cac7ac661dc77db07337f3eaa8f", 2589112L));
        t.put(2589190L, PegoutRef.listFrom("9dd5ad7357ee9d9a2c28b13b6c44aa69838a84f4b274a5e34c629300d8267702", 2589112L));
        t.put(2589196L, PegoutRef.listFrom("2dbbc05c756d339755f8e900c78163749592ce952f91f3c6d424053282749efa", 2589112L));
        t.put(2589202L, PegoutRef.listFrom("95e665b9dd56c5e33f22c209ac57ef46903bd1b7a3a3a04a62150a2277d75a69", 2589112L));
        t.put(2589209L, PegoutRef.listFrom("c074389cf3f0979272292cde8988b9ef73b2fb176516a9042f8935707e8a5c8c", 2589086L));
        t.put(2589308L, PegoutRef.listFrom("8262e3e1ff484a30eb2833c7a504002f3de07aa3fcd5a76c4243b7027e728bee", 2589112L));
        t.put(2589325L, PegoutRef.listFrom("3fc1d66b07e38ae41b534040e55f9159985cd5cc9f0eab8497c5d53d3b8721b8", 2589308L));
        t.put(2589332L, PegoutRef.listFrom("300fdbc7c63a9b6e5738d25788e5b1299c2dfc7eb3331b11cd22766696674591", 2589308L));
        t.put(2589338L, PegoutRef.listFrom("420c5bdc5ae84a49417c120b978231a50b1a67142179fda46fa6635f44f9dfaf", 2589308L));
        t.put(2589375L, PegoutRef.listFrom("9c0016a1abecd5c7d62ce5db5f80655f9e69d285ff3ebc0db00e7fb860549c4f", 2589308L));
        t.put(2589380L, PegoutRef.listFrom("7c49a9651afe24236a56afe3b6ad3d1f822ea956cf1927be5d0ee183a152b39e", 2589308L));
        t.put(2589390L, PegoutRef.listFrom("a342a0668fc637e28a610a2d36b235af899b7ec1b24e939d8a37e076fe57bc8c", 2589308L));
        t.put(2589501L, PegoutRef.listFrom("c00f8917a3213500388121060821e371b973f27504947018905abd04bb07a7f1", 2589308L));
        t.put(2589515L, PegoutRef.listFrom("0a582d15ccc473e2a6383828bea3825ba5fa2d4124cf66cec66ce7a0535f8bfd", 2589308L));
        t.put(2589521L, PegoutRef.listFrom("b66e4df75960f16f67c546bc3b44b1ce1f4fa55c15637fb0c662c036dd4d41e8", 2589056L));
        t.put(2589527L, PegoutRef.listFrom("302c8ba636331d92b0576599aa3fb06459040e1071cfa7975c132b50cfba1c40", 2589308L));
        t.put(2589645L, PegoutRef.listFrom("d52f8fe56df1254fa01efb4653f4b4d6d088f50255c83b50bd3f66c33455c8ad", 2589056L));
        t.put(3106055L, PegoutRef.listFrom("5369d7bc18f8b1ec6cdaaff6c984a554991eb5ae2d177d916b7759011f468940", 3106043L));
        t.put(5788165L, PegoutRef.listFrom("35e2108cfbb009bac4c2b52efc31ff92cd36c8c803cee459afccfc9d3a81a2cf", 5788151L));
        t.put(6858657L, PegoutRef.listFrom("9a163ae0df24833af87992fa0e3a09e4c0bed27a660e6fd94814b0da48c629e6", 6858644L));

        // These are not required after RSKIP559 activation when old output algo will be dropped
        // ... TBD
        TESTNET = Collections.unmodifiableMap(t);
    }
}
