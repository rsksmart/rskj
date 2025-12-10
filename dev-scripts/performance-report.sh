#!/bin/bash
RPC_URL="http://localhost:4444"
SAMPLE_BLOCKS=100

echo "╔══════════════════════════════════════════════════════════╗"
echo "║           RSK Network Performance Dashboard              ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "Sampling last $SAMPLE_BLOCKS blocks..."
echo ""

# Collect data
LATEST=$(($(curl -s -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' \
  $RPC_URL | jq -r '.result')))

START=$((LATEST - SAMPLE_BLOCKS + 1))

TOTAL_TXS=0
TOTAL_GAS=0
FIRST_TIME=0
LAST_TIME=0
BLOCK_TIMES=""
TX_COUNTS=""
PREV_TIME=0

for i in $(seq $START $LATEST); do
  BLOCK_HEX=$(printf "0x%x" $i)

  RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
    --data "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockByNumber\",\"params\":[\"$BLOCK_HEX\", false],\"id\":1}" \
    $RPC_URL)

  TIMESTAMP=$(($(echo $RESULT | jq -r '.result.timestamp')))
  TX_COUNT=$(echo $RESULT | jq -r '.result.transactions | length')
  GAS_USED=$(($(echo $RESULT | jq -r '.result.gasUsed')))
  GAS_LIMIT=$(($(echo $RESULT | jq -r '.result.gasLimit')))

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
done

TIME_SPAN=$((LAST_TIME - FIRST_TIME))

# Calculate metrics
AVG_BLOCK_TIME=$(echo "scale=2; $TIME_SPAN / ($SAMPLE_BLOCKS - 1)" | bc)
AVG_TXS_BLOCK=$(echo "scale=2; $TOTAL_TXS / $SAMPLE_BLOCKS" | bc)
TPS=$(echo "scale=2; $TOTAL_TXS / $TIME_SPAN" | bc)
GPS=$(echo "scale=0; $TOTAL_GAS / $TIME_SPAN" | bc)
AVG_GAS_UTIL=$(echo "scale=1; $TOTAL_GAS * 100 / ($SAMPLE_BLOCKS * $GAS_LIMIT)" | bc)

# Find min/max block times
MIN_BT=$(echo $BLOCK_TIMES | tr ' ' '\n' | sort -n | head -1)
MAX_BT=$(echo $BLOCK_TIMES | tr ' ' '\n' | sort -n | tail -1)

# Find min/max txs
MIN_TX=$(echo $TX_COUNTS | tr ' ' '\n' | sort -n | head -1)
MAX_TX=$(echo $TX_COUNTS | tr ' ' '\n' | sort -n | tail -1)

echo "┌─────────────────────────────────────────────────────────┐"
echo "│ BLOCK METRICS                                           │"
echo "├─────────────────────────────────────────────────────────┤"
printf "│ %-30s %24s │\n" "Current Block Height:" "$LATEST"
printf "│ %-30s %24s │\n" "Average Block Time:" "${AVG_BLOCK_TIME}s"
printf "│ %-30s %24s │\n" "Min/Max Block Time:" "${MIN_BT}s / ${MAX_BT}s"
printf "│ %-30s %24s │\n" "Gas Limit per Block:" "$GAS_LIMIT"
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
printf "│ %-30s %24s │\n" "Total Gas Used:" "$TOTAL_GAS"
printf "│ %-30s %24s │\n" "Gas/Second:" "$GPS"
printf "│ %-30s %24s │\n" "Avg Block Utilization:" "${AVG_GAS_UTIL}%"
echo "└─────────────────────────────────────────────────────────┘"
echo ""
echo "Time span analyzed: ${TIME_SPAN}s (${SAMPLE_BLOCKS} blocks)"
echo "Timestamp: $(date)"
