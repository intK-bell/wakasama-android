# Task: MainActivity auto-forward suppression の整理

## 背景
`MainActivity` は、若様が既定ホームのときに条件次第で通常ホームへ転送する挙動を持つ。
その際、`suppressNextAutoForwardToNormalHome` が `true` の場合だけ、1回転送を止めてメイン画面を表示する。

## 質問
`MainActivity` 表示直後の以下分岐は必要か？

- `suppressNextAutoForwardToNormalHome == false` のとき `maybeForwardToNormalHome(intent)` を実行

## 結論
現行設計では **必要**。

## 理由
- この抑止がないと、若様を既定ホームにした状態で `MainActivity` を開くたび即座に通常ホームへ戻る。
- その結果、次の導線が成立しなくなる。
  - 御支度保存直後に1回だけメインへ戻す導線
  - 初回ホーム設定から戻った直後の導線

## 既知の課題
- 1回抑止フラグは、立てる箇所・消費する箇所が分散すると挙動が読みにくくなる。
- HOME/LAUNCHER/OTHER の起動種別との組み合わせで再発バグが起きやすい。

## 改善方針（次タスク）
1. 抑止フラグの書き込み箇所を限定する。
2. 抑止フラグの消費箇所を1か所へ集約する。
3. 起動種別（HOME/LAUNCHER/OTHER）ごとの期待遷移を表で固定する。
4. 手動検証ログと期待遷移の差分を都度記録する。

## 追加タスク: メールバリデーション
- 御支度保存時はメールアドレス空欄を許可する。
- メールアドレスが入力されている場合のみ形式バリデーションを実施する。

## 参照
- `android/app/src/main/java/com/example/launcherlock/MainActivity.kt`
- `android/app/src/main/java/com/example/launcherlock/SettingsActivity.kt`
