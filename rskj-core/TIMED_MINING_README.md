# Timed Mining Feature

## Overview

The Timed Mining feature is a new mining mode for RSK nodes that creates blocks at intervals following an exponential distribution with a configurable median time. This is different from the existing automine feature, which only creates blocks when new transactions arrive in the mempool.

## How It Works

When enabled, the TimedMinerClient:
1. Schedules block mining operations using an exponential distribution
2. The actual time between blocks varies around the configured median
3. Blocks are created regardless of whether there are pending transactions
4. Uses a scheduled executor to manage timing
5. Automatically reschedules the next mining operation after each block

## Configuration

To enable timed mining, add the following to your configuration file:

```hocon
miner {
    server.enabled = true
    
    client {
        enabled = true
        
        # Enable timed mining (overrides autoMine and delayBetweenBlocks)
        timedMine = true
        
        # Configure the median time between blocks
        # This follows an exponential distribution
        medianBlockTime = 10 seconds
        
        # These settings are ignored when timedMine is true
        autoMine = false
        delayBetweenBlocks = 0 seconds
        delayBetweenRefreshes = 1 second
    }
}
```

## Configuration Options

- **`timedMine`**: Boolean flag to enable/disable timed mining
- **`medianBlockTime`**: Duration specifying the median time between blocks
- **`enabled`**: Must be true for the miner client to work
- **`server.enabled`**: Must be true for the miner server to work

## Priority Order

The mining client selection follows this priority order:
1. **Timed Mining** (`timedMine = true`) - highest priority
2. **Auto Mining** (`autoMine = true`) - medium priority  
3. **Standard Mining** (default) - lowest priority

## Exponential Distribution

The exponential distribution ensures that:
- Most block intervals are close to the median
- Some intervals are shorter, some are longer
- The distribution is memoryless (no correlation between consecutive intervals)
- Provides realistic mining simulation

## Example Use Cases

1. **Testing and Development**: Create blocks at predictable intervals for testing
2. **Simulation**: Simulate mining behavior with controlled timing
3. **Regtest Networks**: Maintain consistent block production for development
4. **Performance Testing**: Test block processing at various intervals

## Sample Configuration Files

See `config/timed-mining.conf` for a complete example configuration.

## Running with Timed Mining

1. Create a configuration file with the timed mining settings
2. Start the RSK node with: `java -jar rskj.jar --config your-config.conf`
3. The node will start mining blocks at the configured intervals
4. Monitor logs for mining activity

## Monitoring

The TimedMinerClient logs:
- Start/stop events with configured median time
- Block mining success/failure
- Scheduling information (debug level)
- Error handling and recovery

## Troubleshooting

- Ensure both `miner.server.enabled` and `miner.client.enabled` are true
- Check that `timedMine` is set to true
- Verify `medianBlockTime` is a valid duration
- Monitor logs for any mining errors
- Ensure the node has sufficient computational resources

## Performance Considerations

- Minimum delay is enforced (100ms) to prevent excessive CPU usage
- The scheduler uses a single-threaded executor for efficiency
- Block mining operations are asynchronous and don't block the main thread
- Error handling ensures the mining process continues even after failures
