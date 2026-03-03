#!/bin/bash
# Stop script for ReportServer

set -e

echo "🛑 Stopping ReportServer..."

# Check if container is running
if docker ps --filter "name=report-server" --filter "status=running" | grep -q report-server; then
    docker-compose down
    echo "✅ ReportServer stopped successfully!"
    echo "💾 Data is preserved in ./data directory"
else
    echo "ℹ️  Container is not running"
fi
