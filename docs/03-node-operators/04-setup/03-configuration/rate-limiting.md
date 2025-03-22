# Transaction rate limiting
Let’s take a look at what a rate limiter is, the importance of using a rate limiter, its features, and how to configure the rate limiter in RSKj nodes using config keys.

> Also known as: Virtual Gas limiter

## What is a Rate Limiter?

The Rate-Limiter deters accounts from issuing problematic transactions, preventing those transactions from being added to the transaction pool and relayed to other peers. It is designed to not affect normal usage, but limit resource-intensive transactions that exhaust the network capacity. An account can still perform any transaction, even the most expensive transactions (128KB in size) after about 32 minutes of inactivity.

RSKj now prevents such attacks, this is made possible by the Rate-Limiter, by preventing source accounts from broadcasting transactions which consume large amounts of resources. The higher the gas limit, the more the source account gets rate-limited. The rate-limit also takes into account additional factors such as: 

-   size
-   gas price compared with the average
-   nonce compared to the expected one
-   nonce value
-   gas price bump percent
    
The Rate-Limiter does this by granting a Virtual Gas Quota that will be consumed every time a transaction is received. It then allows this to replenish slowly, while the account is inactive.

If you have interacted with the Rootstock public nodes, you may have already encountered rate limiting in action. Note that the rate limits on the public nodes are implemented using a 3rd party service, and not within the RSKj node itself. Another notable difference is that the rate limits there are IP address based; and not account based.

## How it Works

The feature works as follows:

1.  A transaction is received by the node
2.  The Rate-Limiter calculates the Virtual Gas consumed by the transaction taking into account several transaction factors, as explained above
3.  The Rate-Limiter refreshes the sender’s Virtual Gas quota taking into account how much time passed since their last transaction (up to a maximum) or it sets a predefined value if this is their first transaction
4.  The Rate-Limiter compares consumed and quota values and then:
	1.  if quota >= consumed :
		1.  the consumed value will be subtracted from the sender’s quota
		2.  the transaction will be added to the pool
		3.  the transaction will be relayed to peers
	2.  if quota < consumed:
		1.  the transaction will be rejected and will not be added to the pool
		2.  the transaction will not be relayed to peers
    
The implementation of the rate-limiter algorithm can be found within RSKj. See [TxQuotaChecker](https://github.com/rsksmart/rskj/blob/10fcc4f/rskj-core/src/main/java/co/rsk/net/handler/quota/TxQuotaChecker.java) for the implementation.

## Why use the rate limiter?

The rate limiter is intended to prevent two variants of Network Denial of Service (DoS) attack against Rootstock networks:

-   CPU-only attack: This happens when the nodes are forced to consume a high percentage of CPU in verifying signatures, preventing normal transactions from being verified and forwarded in time.
-   CPU + bandwidth attack: In addition to the previous one, the network becomes congested with relayed transactions. Furthermore, the CPU is overloaded while compressing, decompressing, and hashing transactions. This prevents normal transactions from being relayed.

Both variants affect congested networks more than blockchains with empty blocks. All variants are highly practical on networks with cheap gas fees, but may be impractical on networks with expensive fees.

This feature has some inherent consequences: 

1.  Accounts gain trust over time.  
    When an account sends multiple transactions, and these do not go over the allowed limits, the RSKj node implementation remembers it as being more trustworthy for future transactions.
2.  Accounts replenish virtual gas over time.  
    When an account does not send any transactions for a period of time, it gains the ability to send more frequent and larger future transactions.

## How to configure the rate limiter

The feature can be configured in RSKj nodes via the following configuration keys:

Open the configuration file. See [expected Configuration file](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/resources/expected.conf) for more information. Add the following keys below to the config file.

 `transaction.accountTxRateLimit.enabled:<boolean>` 
 
- This enables or disables the rate limiting feature.  
- Default value is `true`.
- Note: Disabling this feature is highly discouraged.

 `transaction.accountTxRateLimit.cleanerPeriod:<int>` 
  
- This sets how often, in minutes, to clean the collection storing the quotas, with max quota granted.
- The collection is implemented with a maximum size and will automatically discard less relevant entries in favour of more relevant ones.
- Default value is `30` minutes.
- Note: Unless the host has heavy memory constraints, retain the default value.  
