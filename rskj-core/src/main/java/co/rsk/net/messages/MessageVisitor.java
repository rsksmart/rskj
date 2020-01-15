package co.rsk.net.messages;

public interface MessageVisitor<T> {
    T apply(BlockMessage message);

    T apply(StatusMessage message);

    T apply(GetBlockMessage message);

    T apply(BlockRequestMessage message);

    T apply(BlockResponseMessage message);

    T apply(SkeletonRequestMessage message);

    T apply(BlockHeadersRequestMessage message);

    T apply(BlockHashRequestMessage message);

    T apply(BlockHashResponseMessage message);

    T apply(NewBlockHashMessage message);

    T apply(SkeletonResponseMessage message);

    T apply(BlockHeadersResponseMessage message);

    T apply(BodyRequestMessage message);

    T apply(BodyResponseMessage message);

    T apply(NewBlockHashesMessage message);

    T apply(TransactionsMessage message);
}
