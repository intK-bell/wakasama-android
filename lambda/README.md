# Lambda (Node.js) sample

## Environment variables
- `AWS_REGION` (or `AWS_DEFAULT_REGION`): AWSリージョン（必須）
- `SECURITY_TABLE`: 署名公開鍵・nonceを保存するDynamoDBテーブル（必須）
- `IDEMPOTENCY_TTL_DAYS`: `idempotencyKey` の保持日数（省略時: `90`）
- `MAIL_FROM`: SESで検証済み送信元

ローカル実行時は、リポジトリルート `.env` または `lambda/.env` からも読み込み可能。

## API Gateway 想定
- `POST /register-device-key`
- `POST /submit-answers`
- 認証: `X-Signature` / `X-Device-Id` / `X-Timestamp` / `X-Nonce`
- Lambda proxy integration

## Deploy checklist
1. SESの送信元(必要なら宛先)検証
2. Lambdaに環境変数設定
3. DynamoDBテーブル作成（`infra/security-table.yaml` で作成推奨）
   - PK: `pk` / SK: `sk`
   - TTL属性: `expiresAt`（有効化必須）
4. API Gatewayは `/register-device-key` と `/submit-answers` を同一Lambdaに接続
5. Android側 `api_base_url` を API Gateway URL に設定

## CloudFormation (DynamoDB)

リポジトリルートで実行:

```bash
aws cloudformation deploy \
  --stack-name launcher-lock-security \
  --template-file infra/security-table.yaml \
  --parameter-overrides TableName=wakasama-security
```

デプロイ後、Lambda環境変数 `SECURITY_TABLE` へ同じテーブル名を設定してください。

## Device key registration policy

- `register-device-key` は初回登録のみ許可します。
- 既に同じ公開鍵が登録済みの場合は `already registered` を返します。
- 別の公開鍵で再登録しようとした場合は `409 device key already registered` を返します。

## Request example
Header:
- `X-Device-Id: <device-id>`
- `X-Timestamp: <epoch-seconds>`
- `X-Nonce: <uuid>`
- `X-Signature: <base64-signature>`

```json
{
  "deviceId": "child-phone-01",
  "to": "family@example.com",
  "answeredAt": "2026-02-19T14:05:00+09:00",
  "questions": [
    {"q": "今日やることは？", "a": "宿題と片付け"},
    {"q": "昨日の反省は？", "a": "ゲームしすぎた"}
  ]
}
```
