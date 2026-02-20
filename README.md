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

- Android build config (`APP_TOKEN`, `API_BASE_URL`, `DEVICE_ID`) is loaded from Gradle property / process env / `.env` in this order.
- Lambda secrets/tokens are loaded from process env; for local development, `.env` is also supported.
- Submit API uses request signatures (`X-Signature` headers).
