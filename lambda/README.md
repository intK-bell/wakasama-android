# Lambda (Node.js) sample

## Environment variables
- `AWS_REGION` (or `AWS_DEFAULT_REGION`): AWSリージョン（必須）
- `MAIL_FROM`: SESで検証済み送信元
- `APP_TOKEN_CURRENT`: 現行アプリトークン（必須）
- `APP_TOKEN_NEXT`: ローテーション用の次トークン（任意）

ローカル実行時は、リポジトリルート `.env` または `lambda/.env` からも読み込み可能。

## API Gateway 想定
- `POST /submit-answers`
- 認証: `X-App-Token` をLambda側で検証
- Lambda proxy integration

## Deploy checklist
1. SESの送信元(必要なら宛先)検証
2. Lambdaに環境変数設定
3. API Gatewayはルートを公開し、Lambdaで`X-App-Token`検証
4. Android側 `api_base_url` を API Gateway URL に設定

## Request example
Header:
- `X-App-Token: <APP_TOKEN_CURRENT>`

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
