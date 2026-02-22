#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <api-id> <stage-name>" >&2
  exit 1
fi

API_ID="$1"
STAGE_NAME="$2"

aws apigatewayv2 update-stage \
  --api-id "$API_ID" \
  --stage-name "$STAGE_NAME" \
  --route-settings '{
    "POST /register-device-key": {
      "ThrottlingBurstLimit": 1,
      "ThrottlingRateLimit": 0.02
    },
    "POST /submit-answers": {
      "ThrottlingBurstLimit": 2,
      "ThrottlingRateLimit": 0.1
    }
  }'

echo "Applied strict route throttling to API ${API_ID} stage ${STAGE_NAME}."
