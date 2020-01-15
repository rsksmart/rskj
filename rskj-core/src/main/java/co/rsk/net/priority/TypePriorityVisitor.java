package co.rsk.net.priority;

import co.rsk.net.messages.*;

public class TypePriorityVisitor implements MessageVisitor<Double> {

    private static final double TIER_0 = 100;
    private static final double TIER_1 = 0.1;
    private static final double TIER_2 = 0.01;

    @Override
    public Double apply(BlockMessage message) {
        return TIER_0;
    }

    @Override
    public Double apply(StatusMessage message) {
        return TIER_1;
    }

    @Override
    public Double apply(GetBlockMessage message) {
        return TIER_1;
    }

    @Override
    public Double apply(BlockRequestMessage message) {
        return TIER_1;
    }

    @Override
    public Double apply(BlockResponseMessage message) {
        return TIER_0;
    }

    @Override
    public Double apply(SkeletonRequestMessage message) {
        return TIER_1;
    }

    @Override
    public Double apply(BlockHeadersRequestMessage message) {
        return TIER_1;
    }

    @Override
    public Double apply(BlockHashRequestMessage message) {
        return TIER_1;
    }

    @Override
    public Double apply(BlockHashResponseMessage message) {
        return TIER_0;
    }

    @Override
    public Double apply(NewBlockHashMessage message) {
        return TIER_2;
    }

    @Override
    public Double apply(SkeletonResponseMessage message) {
        return TIER_0;
    }

    @Override
    public Double apply(BlockHeadersResponseMessage message) {
        return TIER_0;
    }

    @Override
    public Double apply(BodyRequestMessage message) {
        return TIER_1;
    }

    @Override
    public Double apply(BodyResponseMessage message) {
        return TIER_0;
    }

    @Override
    public Double apply(NewBlockHashesMessage message) {
        return TIER_2;
    }

    @Override
    public Double apply(TransactionsMessage message) {
        return TIER_2;
    }
}
