#!/bin/bash

echo "Testing Simple RSS Feed Endpoint..."

# Test with sample RSS feeds
curl -X POST http://localhost:5600/feeds \
  -H "Content-Type: application/json" \
  -d '{
    "urls": [
      "https://feeds.feedburner.com/oreilly/radar",
      "https://rss.cnn.com/rss/edition.rss"
    ]
  }' | jq '.' 