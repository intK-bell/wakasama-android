# dadone

Android app + Lambda backend for a homework/launcher-lock workflow.

## Repository policy

This repository is **not open source**.
See `LICENSE` for usage restrictions. Unauthorized use is prohibited.

## Structure

- `android/`: Android application
- `lambda/`: AWS Lambda functions

## Local setup (summary)

1. Create `.env` from `.env.example` and set values.

### Android

1. Build with `.env` values (or override with `-PKEY=value`):
   - `cd android && ./gradlew assembleDebug`
2. Configure mail destination in app settings.

### Lambda

Set required environment variables (details: `lambda/README.md`):

- `SECURITY_TABLE` (required)
- SES sender/region related variables

## Notes

- Android build config (`API_BASE_URL`, `DEVICE_ID`) is loaded from Gradle property / process env / `.env` in this order.
- Lambda secrets/tokens are loaded from process env; for local development, `.env` is also supported.
- Submit API uses request signatures (`X-Signature` headers).

## Data retention policy

- Server-side retention policy is 90 days for operational logs and replay-control data.
- Lambda CloudWatch Logs retention is set to 90 days (`/aws/lambda/launcher-lock-submit-answers`).
- DynamoDB replay-control records (`expiresAt`, e.g. `idempotencyKey`/nonce) are TTL-managed and expire automatically.
- CloudTrail trails are not configured in the current environment (`describe-trails` is empty), so no CloudTrail S3 archive is in use.
- API Gateway access/execution logging is currently disabled for this app API stage.
- On-device settings/cache (e.g. local `mail_to` setting, retry queue) are persisted locally and are not currently capped to 90 days.

## Google Play submission docs

- Privacy policy draft: `android/docs/privacy-policy-ja.md`
