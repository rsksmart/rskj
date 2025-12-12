#!/bin/bash

RPC_URL="http://localhost:4444"
#RPC_URL="https://public-node.testnet.rsk.co"
#RPC_URL="https://public-node.rsk.co"
SAMPLE_BLOCKS=100

echo ""
echo "Sampling last $SAMPLE_BLOCKS blocks..."
echo ""

# Convert hex to decimal
hex_to_dec() {
  local hex_val="$1"
  # Remove 0x prefix if present
  hex_val="${hex_val#0x}"
  # Use bc for large number conversion to avoid scientific notation
  if [ -z "$hex_val" ] || [ "$hex_val" = "null" ]; then
    echo "0"
  else
    hex_val=$(echo "$hex_val" | tr '[:lower:]' '[:upper:]')
    echo "obase=10; ibase=16; $hex_val" | bc 2>/dev/null || echo "0"
  fi
}

# Collect data
LATEST_HEX=$(curl -s -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' \
  $RPC_URL | jq -r '.result')

LATEST=$(hex_to_dec "$LATEST_HEX")

if [ -z "$LATEST" ] || [ "$LATEST" = "0" ]; then
  echo "Error: Could not fetch latest block number from $RPC_URL"
  exit 1
fi

START=$((LATEST - SAMPLE_BLOCKS + 1))

TOTAL_TXS=0
TOTAL_GAS=0
FIRST_TIME=0
LAST_TIME=0
BLOCK_TIMES=""
TX_COUNTS=""
PREV_TIME=0
GAS_LIMITS=""

# Transaction type counters
CONTRACT_DEPLOY=0
CONTRACT_CALL=0
SIMPLE_TRANSFER=0
REMASC_TXS=0
BRIDGE_TXS=0
TOTAL_INPUT_SIZE=0
TOTAL_TX_GAS_LIMIT=0

# RSK addresses
REMASC_ADDR="0x0000000000000000000000000000000001000008"
BRIDGE_ADDR="0x0000000000000000000000000000000001000006"

echo "Fetching blocks from $START to $LATEST..."
echo ""

for ((i=START; i<=LATEST; i++)); do
  BLOCK_HEX=$(printf "0x%x" $i)

  RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
    --data "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockByNumber\",\"params\":[\"$BLOCK_HEX\", true],\"id\":1}" \
    $RPC_URL)

  # Extract hex values
  TIMESTAMP_HEX=$(echo $RESULT | jq -r '.result.timestamp')
  TX_COUNT=$(echo $RESULT | jq -r '.result.transactions | length')
  GAS_USED_HEX=$(echo $RESULT | jq -r '.result.gasUsed')
  GAS_LIMIT_HEX=$(echo $RESULT | jq -r '.result.gasLimit')

  TIMESTAMP=$(hex_to_dec "$TIMESTAMP_HEX")
  GAS_USED=$(hex_to_dec "$GAS_USED_HEX")
  BLOCK_GAS_LIMIT=$(hex_to_dec "$GAS_LIMIT_HEX")
  GAS_LIMITS="$GAS_LIMITS $BLOCK_GAS_LIMIT"

  # Process each transaction for type classification
  for ((j=0; j<TX_COUNT; j++)); do
    TX=$(echo $RESULT | jq ".result.transactions[$j]")
    
    TO_ADDR=$(echo $TX | jq -r '.to // "null"')
    INPUT=$(echo $TX | jq -r '.input // "0x"')
    TX_GAS_HEX=$(echo $TX | jq -r '.gas // "0x0"')
    
    # Calculate input size (bytes): subtract "0x" prefix, divide by 2
    INPUT_LEN=${#INPUT}
    if [ $INPUT_LEN -gt 2 ]; then
      INPUT_BYTES=$(( (INPUT_LEN - 2) / 2 ))
    else
      INPUT_BYTES=0
    fi
    TOTAL_INPUT_SIZE=$((TOTAL_INPUT_SIZE + INPUT_BYTES))
    
    # Gas limit of transaction
    TX_GAS_DEC=$(hex_to_dec "$TX_GAS_HEX")
    TOTAL_TX_GAS_LIMIT=$((TOTAL_TX_GAS_LIMIT + TX_GAS_DEC))

    # Classify transaction type (using tr for case-insensitive comparison)
    TO_ADDR_LOWER=$(echo "$TO_ADDR" | tr '[:upper:]' '[:lower:]')
    REMASC_LOWER=$(echo "$REMASC_ADDR" | tr '[:upper:]' '[:lower:]')
    BRIDGE_LOWER=$(echo "$BRIDGE_ADDR" | tr '[:upper:]' '[:lower:]')
    
    if [ "$TO_ADDR" == "null" ] || [ "$TO_ADDR" == "" ]; then
      CONTRACT_DEPLOY=$((CONTRACT_DEPLOY + 1))
    elif [ "$TO_ADDR_LOWER" == "$REMASC_LOWER" ]; then
      REMASC_TXS=$((REMASC_TXS + 1))
    elif [ "$TO_ADDR_LOWER" == "$BRIDGE_LOWER" ]; then
      BRIDGE_TXS=$((BRIDGE_TXS + 1))
    elif [ "$INPUT" == "0x" ] || [ "$INPUT" == "" ]; then
      SIMPLE_TRANSFER=$((SIMPLE_TRANSFER + 1))
    else
      CONTRACT_CALL=$((CONTRACT_CALL + 1))
    fi
  done
  
  if [ $i -eq $START ]; then
    FIRST_TIME=$TIMESTAMP
  else
    BLOCK_TIME=$((TIMESTAMP - PREV_TIME))
    BLOCK_TIMES="$BLOCK_TIMES $BLOCK_TIME"
  fi
  
  PREV_TIME=$TIMESTAMP
  LAST_TIME=$TIMESTAMP
  TOTAL_TXS=$((TOTAL_TXS + TX_COUNT))
  TOTAL_GAS=$((TOTAL_GAS + GAS_USED))
  TX_COUNTS="$TX_COUNTS $TX_COUNT"
  
  # Show progress
  if [ $(($i % 10)) -eq 0 ]; then
    echo "Progress: Block $i/$LATEST"
  fi
done

TIME_SPAN=$((LAST_TIME - FIRST_TIME))

# Calculate metrics
AVG_BLOCK_TIME=$(echo "scale=2; $TIME_SPAN / ($SAMPLE_BLOCKS - 1)" | bc)
AVG_TXS_BLOCK=$(echo "scale=2; $TOTAL_TXS / $SAMPLE_BLOCKS" | bc)

# Avoid division by zero
if [ $TIME_SPAN -eq 0 ]; then
  TPS="0.00"
  GPS="0"
else
  TPS=$(echo "scale=2; $TOTAL_TXS / $TIME_SPAN" | bc)
  GPS=$(echo "scale=0; $TOTAL_GAS / $TIME_SPAN" | bc)
fi

# Calculate gas limit stats
if [ -n "$GAS_LIMITS" ]; then
  MIN_GAS_LIMIT=$(echo $GAS_LIMITS | tr ' ' '\n' | grep -v '^$' | sort -n | head -1)
  MAX_GAS_LIMIT=$(echo $GAS_LIMITS | tr ' ' '\n' | grep -v '^$' | sort -n | tail -1)
  TOTAL_GAS_LIMIT=$(echo $GAS_LIMITS | tr ' ' '\n' | grep -v '^$' | awk '{sum+=$1} END {print sum}')
  AVG_GAS_LIMIT=$(echo "scale=0; $TOTAL_GAS_LIMIT / $SAMPLE_BLOCKS" | bc)
else
  MIN_GAS_LIMIT="0"
  MAX_GAS_LIMIT="0"
  AVG_GAS_LIMIT="0"
fi

# Calculate gas utilization (using average gas limit)
if [ "$AVG_GAS_LIMIT" != "0" ] && [ -n "$AVG_GAS_LIMIT" ]; then
  AVG_GAS_UTIL=$(echo "scale=1; $TOTAL_GAS * 100 / ($SAMPLE_BLOCKS * $AVG_GAS_LIMIT)" | bc)
else
  AVG_GAS_UTIL="0.0"
fi

# Find min/max block times
if [ -n "$BLOCK_TIMES" ]; then
  MIN_BT=$(echo $BLOCK_TIMES | tr ' ' '\n' | grep -v '^$' | sort -n | head -1)
  MAX_BT=$(echo $BLOCK_TIMES | tr ' ' '\n' | grep -v '^$' | sort -n | tail -1)
else
  MIN_BT="N/A"
  MAX_BT="N/A"
fi

# Find min/max txs
if [ -n "$TX_COUNTS" ]; then
  MIN_TX=$(echo $TX_COUNTS | tr ' ' '\n' | grep -v '^$' | sort -n | head -1)
  MAX_TX=$(echo $TX_COUNTS | tr ' ' '\n' | grep -v '^$' | sort -n | tail -1)
else
  MIN_TX="0"
  MAX_TX="0"
fi

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║           RSK Network Performance Dashboard              ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "┌─────────────────────────────────────────────────────────┐"
echo "│ BLOCK METRICS                                           │"
echo "├─────────────────────────────────────────────────────────┤"
printf "│ %-30s %24s │\n" "Current Block Height:" "$LATEST"
printf "│ %-30s %24s │\n" "Average Block Time:" "${AVG_BLOCK_TIME}s"
printf "│ %-30s %24s │\n" "Min/Max Block Time:" "${MIN_BT}s / ${MAX_BT}s"
printf "│ %-30s %20s gas │\n" "Avg Gas Limit per Block:" "$AVG_GAS_LIMIT"
printf "│ %-30s %20s gas │\n" "Min/Max Gas Limit:" "$MIN_GAS_LIMIT / $MAX_GAS_LIMIT"
echo "└─────────────────────────────────────────────────────────┘"
echo ""
echo "┌─────────────────────────────────────────────────────────┐"
echo "│ TRANSACTION METRICS                                     │"
echo "├─────────────────────────────────────────────────────────┤"
printf "│ %-30s %24s │\n" "Total Transactions:" "$TOTAL_TXS"
printf "│ %-30s %24s │\n" "Avg Transactions/Block:" "$AVG_TXS_BLOCK"
printf "│ %-30s %24s │\n" "Min/Max TXs per Block:" "$MIN_TX / $MAX_TX"
printf "│ %-30s %24s │\n" "Transactions/Second (TPS):" "$TPS"
echo "└─────────────────────────────────────────────────────────┘"
echo ""
echo "┌─────────────────────────────────────────────────────────┐"
echo "│ GAS METRICS                                             │"
echo "├─────────────────────────────────────────────────────────┤"
printf "│ %-30s %20s gas │\n" "Total Gas Used:" "$TOTAL_GAS"
printf "│ %-30s %18s gas/s │\n" "Gas/Second:" "$GPS"
printf "│ %-30s %24s │\n" "Avg Block Utilization:" "${AVG_GAS_UTIL}%"
echo "└─────────────────────────────────────────────────────────┘"
echo ""
echo "┌─────────────────────────────────────────────────────────┐"
echo "│ TRANSACTION TYPE BREAKDOWN                              │"
echo "├─────────────────────────────────────────────────────────┤"
printf "│ %-30s %24s │\n" "Contract Deployments:" "$CONTRACT_DEPLOY"
printf "│ %-30s %24s │\n" "Contract Calls:" "$CONTRACT_CALL"
printf "│ %-30s %24s │\n" "Simple Transfers (RBTC):" "$SIMPLE_TRANSFER"
printf "│ %-30s %24s │\n" "REMASC Reward Transactions:" "$REMASC_TXS"
printf "│ %-30s %24s │\n" "Bridge Transactions:" "$BRIDGE_TXS"
echo "└─────────────────────────────────────────────────────────┘"

if [ $TOTAL_TXS -gt 0 ]; then
  DEPLOY_PCT=$(echo "scale=1; $CONTRACT_DEPLOY * 100 / $TOTAL_TXS" | bc)
  CALL_PCT=$(echo "scale=1; $CONTRACT_CALL * 100 / $TOTAL_TXS" | bc)
  TRANSFER_PCT=$(echo "scale=1; $SIMPLE_TRANSFER * 100 / $TOTAL_TXS" | bc)
  REMASC_PCT=$(echo "scale=1; $REMASC_TXS * 100 / $TOTAL_TXS" | bc)
  BRIDGE_PCT=$(echo "scale=1; $BRIDGE_TXS * 100 / $TOTAL_TXS" | bc)
  AVG_INPUT_SIZE=$(echo "scale=0; $TOTAL_INPUT_SIZE / $TOTAL_TXS" | bc)
  AVG_TX_GAS_LIMIT=$(echo "scale=0; $TOTAL_TX_GAS_LIMIT / $TOTAL_TXS" | bc)
  AVG_GAS_USED_PER_TX=$(echo "scale=0; $TOTAL_GAS / $TOTAL_TXS" | bc)

  echo ""
  echo "┌─────────────────────────────────────────────────────────┐"
  echo "│ TRANSACTION TYPE PERCENTAGES                            │"
  echo "├─────────────────────────────────────────────────────────┤"
  printf "│ %-30s %23s%% │\n" "Contract Deployments:" "$DEPLOY_PCT"
  printf "│ %-30s %23s%% │\n" "Contract Calls:" "$CALL_PCT"
  printf "│ %-30s %23s%% │\n" "Simple Transfers:" "$TRANSFER_PCT"
  printf "│ %-30s %23s%% │\n" "REMASC Rewards:" "$REMASC_PCT"
  printf "│ %-30s %23s%% │\n" "Bridge Transactions:" "$BRIDGE_PCT"
  echo "└─────────────────────────────────────────────────────────┘"
  
  echo ""
  echo "┌─────────────────────────────────────────────────────────┐"
  echo "│ TRANSACTION SIZE & GAS                                  │"
  echo "├─────────────────────────────────────────────────────────┤"
  printf "│ %-30s %18s bytes │\n" "Total Input Data Size:" "$TOTAL_INPUT_SIZE"
  printf "│ %-30s %18s bytes │\n" "Avg Input Data per TX:" "$AVG_INPUT_SIZE"
  printf "│ %-30s %20s gas │\n" "Avg Gas Used per TX:" "$AVG_GAS_USED_PER_TX"
  printf "│ %-30s %20s gas │\n" "Avg Gas Limit per TX:" "$AVG_TX_GAS_LIMIT"
  echo "└─────────────────────────────────────────────────────────┘"
fi

echo ""
echo "Time span analyzed: ${TIME_SPAN}s (${SAMPLE_BLOCKS} blocks)"
echo "Network: $RPC_URL"
echo "Timestamp: $(date)"

