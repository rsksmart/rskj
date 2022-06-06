package co.rsk.blockchain.utils;

import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.crypto.HashUtil;

import java.util.function.Consumer;

public enum InvalidBlockFields {

    UNCLES_HASH((BlockHeaderBuilder builder) -> builder.setUnclesHash(HashUtil.randomHash()));

    private final Consumer<BlockHeaderBuilder> valueGenerator;

    InvalidBlockFields(Consumer<BlockHeaderBuilder> valueGenerator) {
        this.valueGenerator = valueGenerator;
    }

    public void accept(BlockHeaderBuilder builder) {
        valueGenerator.accept(builder);
    }
}
