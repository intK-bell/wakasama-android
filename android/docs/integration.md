# Android Integration Notes

## 1) Dependencies
`app/build.gradle` に最低限これを追加。

```gradle
implementation "androidx.work:work-runtime-ktx:2.10.0"
implementation "androidx.room:room-runtime:2.6.1"
kapt "androidx.room:room-compiler:2.6.1"
implementation "androidx.room:room-ktx:2.6.1"
implementation "com.squareup.retrofit2:retrofit:2.11.0"
implementation "com.squareup.retrofit2:converter-moshi:2.11.0"
implementation "com.squareup.okhttp3:okhttp:4.12.0"
implementation "com.squareup.moshi:moshi-kotlin:1.15.1"
```

## 2) 初期設定
ログイン/設定画面で以下を `SharedPreferences(launcher_lock)` に保存。
- `api_base_url` 本番: `https://guh3h3lma1.execute-api.ap-northeast-1.amazonaws.com/prod`
- `api_app_token` 例: `8f1c...`（Lambdaの `APP_TOKEN_CURRENT` と一致）
- `mail_to` 例: `family@example.com`（アプリ設定で入力した宛先）
- `lock_mode` 例: `EVERY_DAY`, `WEEKDAY`, `HOLIDAY`
- `is_locked` 初期値: `false`

## 3) スケジューラ起動
`Application#onCreate` で呼ぶ。

```kotlin
LockScheduler.schedule(this)
```

## 4) 回答完了処理
質問回答画面で `AnswerUnlockUseCase.submitAnswersAndUnlock(...)` を呼ぶ。
- 成功時: `is_locked = false`
- 失敗時: キュー保存。次回 `SubmissionRetryWorker` が再送。

## 5) ランチャー側のブロック
ランチャーの起動分岐で `is_locked == true` の間は
- 許可画面(質問UI)のみ表示
- それ以外のアプリ起動導線は無効化

## 6) 認証ヘッダー
送信時は `X-App-Token` ヘッダーを付与する実装。
トークンは `api_app_token` から読み出し、Lambda側 `APP_TOKEN_CURRENT` と照合される。
