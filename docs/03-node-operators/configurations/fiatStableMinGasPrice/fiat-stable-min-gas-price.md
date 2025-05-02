# Fiat Stable MinGasPrice

In the context of **RSKj** and **cryptocurrency**, **Fiat Stable MinGasPrice** usually refers to a way of **stabilizing the minimum gas price** based on the value of a **fiat currency** (like USD, EUR, etc.).

**Important Definitions:**

- **Gas Price**: In RSKj, when you send a transaction or interact with a smart contract, you pay a *gas fee* to the network. The *gas price* is how much you pay per unit of gas.
- **Fiat Stable**: This suggests trying to **peg** (or stabilize) the *minimum gas price* to a **fixed value in fiat currency**, even though RSKj's currency (RBTC) is volatile.
- **MinGasPrice**: This would be the **minimum** allowed gas price — the lowest cost you can set for your transaction to be accepted or processed.

**How it works:**

**Fiat Stable MinGasPrice** means setting a minimum gas price that, no matter how much RBTC's price goes up or down, tries to represent a *fixed real-world value* (like "always costing about 0.01 USD worth of gas minimum").

## Why does it matter?

- If RBTC price suddenly spikes, gas fees could become ridiculously high in dollar terms.
- By using a **fiat-stable minimum**, systems can ensure fair and predictable pricing for users and maintain network security and validator incentives.

## Motivation and context for the changes

Currently, RSK miners can set the minimum gas price (MinGasPrice) using the miner.minGasPrice configuration, which is denominated in WEI and inherently linked to the Bitcoin price. This linkage causes transaction costs to fluctuate with Bitcoin's value, potentially leading to increased costs when Bitcoin appreciates, thereby adversely affecting user experience.

To address this issue, the proposed feature aims to empower miners with the ability to specify and configure the minimum gas price in fiat currency. This change would stabilize transaction costs in fiat terms, regardless of cryptocurrency market variations, and is intended to enhance predictability and user satisfaction. The system used to support only one configuration parameter for setting the MinGasPrice. See an example [here](https://www.notion.so/Minimum-Gas-Price-Feature-Validation-on-Testnet-122c132873f9807ba817f63a13aa7720?pvs=21),

The goal is to give miners a capability to specify / configure min gas price value denominated in a fiat currency.

### PR that added the changes

[https://github.com/rsksmart/rskj/pull/2310](https://github.com/rsksmart/rskj/pull/2310)

## **🔗 HTTP Call (Off-Chain Method)**

This approach uses a **centralized or semi-centralized API** to fetch the current **RBTC-to-fiat price** and then calculate a gas price based on that.

### How it works:

- The system (like a wallet, dApp backend, or node) sends an **HTTP request** to a price oracle or API like:
    - CoinGecko
    - Chainlink's external adapters
    - Custom backend service
- It retrieves the current **RBTC/USD price**, then calculates:
  `minGasPrice = fiatMinPrice / ethPrice`
- Example: If you want the gas price to represent $0.01, and RBTC = $2000, then:
  `minGasPrice = 0.01 / 2000 = 0.000005 RBTC (or 5 gwei)`

#### ✅ Pros:

- Simple, flexible, easy to integrate.
- Can pull from a wide range of data sources.

#### ❌ Cons:

- **Centralization risk** – you're trusting the HTTP source.
- **Not trustless** – the blockchain doesn't verify the result.
- **Latency and reliability** – dependent on network and endpoint availability.

---

## ⛓️ **On-Chain Oracle Call (Decentralized/Trustless Method)**

This approach fetches the **RBTC-to-fiat price from a smart contract**, often powered by a **decentralized oracle network** like **Chainlink**.

### How it works:

- Smart contracts call an **on-chain oracle** (e.g., Chainlink RBTC/USD price feed).
- The gas price is derived using the oracle's latest reported price.
- This is done **inside smart contracts**, ensuring consistency and trust.

#### ✅ Pros:

- **Trustless and secure** – all nodes agree on the same value.
- **Tamper-resistant** – oracle data is resistant to manipulation.
- **Fully on-chain** – compatible with DeFi, DAO governance, etc.

#### ❌ Cons:

- **Gas cost** – reading from an oracle contract uses gas.
- **Oracle update frequency** – price may not always be up to the second.
- **Complexity** – needs integration with oracle networks and possibly fallback logic.

## Summary Table:

| Method | Trust Level | Cost | Speed | Use Case |
| --- | --- | --- | --- | --- |
| HTTP API (Off-chain) | Centralized | Low | Fast | Backend services, UI, analytics |
| On-chain Oracle | Decentralized | Moderate | Near real-time | Smart contracts, DeFi, DAOs |

## RSK Configuration

Miner configurations depends on the desired method used to request the min gas price. An [example miner configuration can be seen here](https://github.com/rsksmart/rskj/pull/2310/files#diff-242a8c6b5d9003504710db696af1f94412a52cdbd3369df55cff4be2ea8697eaR204-R234)

**HTTP CALL**

Configurations for using HTTP to request the min gas price

```yaml
miner {
    stableGasPrice {
        enabled = false

        source {
	        # method options: HTTP_GET | ETH_CALL
            # Examples:
            method = "HTTP_GET"
            params {
                url = "https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=usd"
                # response: {"ethereum":{"usd":1848.79}}
                jsonPath = "ethereum/usd"
                timeout = 2 seconds # Value's type is `Duration`, e.g. 1.1 seconds, 2.5 minutes, 3 hours, 4 days.
            }
        }
				
		minStableGasPrice = 4265280000000 # 0.00000426528 USD per gas unit
        # The time the miner client will wait to refresh price from the source.
        # Value's type is `Duration`, e.g. 1.1 seconds, 2.5 minutes, 3 hours, 4 days.
        refreshRate = 6 hours
    }
}
```

**ETH CALL**

Will use ETH to request the min gas price

```yaml
miner {
    stableGasPrice {
        enabled = false

        source {
	         method = "ETH_CALL"
	         params {
	            from: "0xcd2a3d9f938e13cd947ec05abc7fe734df8dd825",
	            to: "0xcd2a3d9f938e13cd947ec05abc7fe734df8dd826",
	            data: "0x8300df49"
	         }
        }
        
				minStableGasPrice = 4265280000000 # 0.00000426528 USD per gas unit
        # The time the miner client will wait to refresh price from the source.
        # Value's type is `Duration`, e.g. 1.1 seconds, 2.5 minutes, 3 hours, 4 days.
        refreshRate = 6 hours
    }
}
```