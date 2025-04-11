#!/bin/sh

if [ "$(docker inspect -f '{{.State.Running}}' rskj-container)" = "true" ]; then
    max_attempts=20
    attempts=0
    count=0

    while [ $attempts -lt $max_attempts ]; do
        docker logs rskj-container > logs.txt

        count=$(grep -c "IMPORTED_BEST" logs.txt)

        if [ $count -gt 0 ]; then
        echo "✅ Found 'IMPORTED_BEST' block in logs!"
        break
        fi

        echo "Waiting for 5 seconds for blocks to be imported..."
        sleep 5
        echo "Continuing to check imported blocks"

        attempts=$(($attempts + 1))
    done

    if [ $count -le 0 ]; then
        echo "❌ 'IMPORTED_BEST' block not found in logs after $max_attempts attempts"
        exit 1
    fi

    echo "Container is running properly"
else
    echo "Container failed to start"
    exit 1
fi
