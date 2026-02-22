# Rate Limit Hardening Plan (Strict)

このドキュメントは、`/register-device-key` と `/submit-answers` に対して、
厳しめレート制限を段階的に導入するための実装手順です。

## 目的

- 登録APIの乱用（総当たり・先取り登録）を抑制する
- 送信APIの過剰呼び出しやBOTトラフィックを抑制する
- 正常ユーザーへの影響を最小にしつつ、遮断を優先する

## 前提

- API Gateway + Lambda Proxy Integration
- 本リポジトリのLambdaは以下2エンドポイントを想定
  - `POST /register-device-key`
  - `POST /submit-answers`

## 推奨レート（初期値: 厳しめ）

1. `POST /register-device-key`
- API Gateway ルートスロットル:
  - `ThrottlingRateLimit`: `0.02` req/sec (約1.2 req/min)
  - `ThrottlingBurstLimit`: `1`
- AWS WAF (IP単位, 5分窓):
  - 1IPあたり `10` リクエスト超でブロック（WAFの最小値）

2. `POST /submit-answers`
- API Gateway ルートスロットル:
  - `ThrottlingRateLimit`: `0.1` req/sec (6 req/min)
  - `ThrottlingBurstLimit`: `2`
- AWS WAF (IP単位, 5分窓):
  - 1IPあたり `30` リクエスト超でブロック

注意:
- 家庭内運用で端末数が少ない前提のため、一般的サービスよりかなり低めです。
- 共有IP（同一Wi-Fi配下の複数端末）が増える場合は緩和が必要です。

## 実装ステップ

1. API Gateway ルート単位スロットル設定

- ステージ（例: `prod`）の `RouteSettings` を更新する
- `register-device-key` を最優先で厳しくする
- スクリプト:
  - `infra/scripts/set-api-route-throttle.sh <api-id> <stage-name>`

2. AWS WAF Web ACL をAPIに関連付け（補足あり）

- ルールA: `POST /register-device-key` を対象に `RateBasedStatement`
- ルールB: `POST /submit-answers` を対象に `RateBasedStatement`
- まず `COUNT` モードで30分観測し、誤検知がなければ `BLOCK` へ変更
- CloudFormationテンプレート:
  - `infra/api-waf-rate-limit.yaml`
- 注意:
  - 現在の本アプリは API Gateway **HTTP API** を使用しており、
    `AWS::WAFv2::WebACLAssociation` の直接関連付けは利用できません。
  - WAFのIPレート制限を使う場合は、`REST API` へ移行するか、`CloudFront + WAF` 構成を採用してください。

3. Lambda側で `deviceId` 形式バリデーション追加（任意だが推奨）

- 目的: 推測しやすいID（`child1` など）を拒否
- 例: UUIDv4のみ許可（36文字ハイフン形式）

4. 監視設定

- CloudWatch メトリクス監視
  - API Gateway: `4XXError`, `5XXError`, `Count`, `Latency`
  - WAF: `BlockedRequests`, `AllowedRequests`
- アラート閾値（初期）
  - 5分で `BlockedRequests >= 10`
  - 5分で API `429 >= 10`

## 作業チェックリスト

1. 事前確認
- [ ] 現行ステージ名（例: `prod`）を確認
- [ ] API種別（HTTP API / REST API）を確認
- [ ] 誤検知時の連絡先を決める

2. リリース手順
- [x] API Gateway ルートスロットル適用
- [ ] WAFルールを `COUNT` で適用（REST API/CloudFront採用時のみ）
- [ ] 30分観測（通常操作と保存/送信フロー）
- [ ] 誤検知なしなら `BLOCK` へ切替
- [ ] 監視アラート有効化

3. 受け入れ確認
- [ ] 通常フローで登録/送信が成功する
- [ ] 短時間連打で `429` になる
- [ ] WAFルールヒット時にブロックされる
- [ ] アプリ側は失敗時にキュー再送へ遷移する

## ロールバック

1. 即時対応
- WAFルールを `BLOCK -> COUNT` に戻す
- API Gateway の `ThrottlingRateLimit` を一時的に2倍へ緩和

2. 恒久対応
- アクセスログを元にしきい値再設計
- `register-device-key` と `submit-answers` を別ステージで検証後再適用

## 補足

- 「ハッシュ化したdeviceId」は推測耐性の本質対策ではありません。
- 本質対策は、推測しづらいランダムIDの採用と、入口でのレート制限です。

## 実行コマンド例

1. API Gateway ルートスロットル適用

```bash
infra/scripts/set-api-route-throttle.sh <api-id> prod
```

2. WAF (`COUNT`) をデプロイ（REST API/CloudFront採用時のみ）

```bash
aws cloudformation deploy \
  --stack-name launcher-lock-api-waf \
  --template-file infra/api-waf-rate-limit.yaml \
  --parameter-overrides \
    ApiId=<api-id> \
    StageName=prod \
    WebAclName=launcher-lock-strict-rate-limit \
    RuleActionMode=COUNT \
    RegisterIpLimitPer5Min=10 \
    SubmitIpLimitPer5Min=30
```

3. 観測後に `BLOCK` へ切替（REST API/CloudFront採用時のみ）

```bash
aws cloudformation deploy \
  --stack-name launcher-lock-api-waf \
  --template-file infra/api-waf-rate-limit.yaml \
  --parameter-overrides \
    ApiId=<api-id> \
    StageName=prod \
    WebAclName=launcher-lock-strict-rate-limit \
    RuleActionMode=BLOCK \
    RegisterIpLimitPer5Min=10 \
    SubmitIpLimitPer5Min=30
```
