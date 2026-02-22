# Navigation Edit Rules

このルールは、遷移ロジック変更時の干渉を防ぐための作業規約です。

## Rule 1: 先に状態表を確認
- 変更前に `android/docs/navigation-state-map.md` を読む。
- 変更対象がどのフロー（LAUNCHER/HOME/Settings保存/ホーム設定導線）か明記する。

## Rule 2: フラグ責務を増やさない
- 既存フラグに別責務を混ぜない。
- 新フラグを追加する場合は、
  - 目的
  - セット箇所
  - クリア箇所
  - 消費箇所
  を state map に追記する。

## Rule 3: 遷移起点は最小化
- `MainActivity` の遷移判定は `onCreate` / `onResume` / `onNewIntent` で同一優先順を維持する。
- `SettingsActivity` は「保存完了時の戻し」に限定する。

## Rule 4: HOMEとLAUNCHERを分離
- HOME起動とLAUNCHER起動を同じ条件分岐で扱わない。
- どちらを優先するかをコードコメントかログで明示する。

## Rule 5: システム画面は別タスク
- `ACTION_HOME_SETTINGS` や `ACTION_SETTINGS` は `FLAG_ACTIVITY_NEW_TASK` で起動する。
- システム画面がアプリタスクに混ざらないことを優先する。

## Rule 6: 変更後の最低確認
- 以下を必須確認:
  1. 通常起動で `MainActivity` が開く
  2. ホーム設定導線（設定する/無視する）が意図通り
  3. 結界ONでHOME押下時に通常ホームへ行かない
  4. 結界OFFでHOME押下時に通常ホームへ行く
  5. 御支度保存直後は、起動元に関係なく `MainActivity` が1回表示される（`return_to_main_after_save_once`）
  6. 画面オフ中アラーム後、通知未タップの初回解除だけ追加判定が1回実行される（`pending_unlock_decision_once`）
  7. 通知タップ起動は既存導線を優先し、追加判定を通らない

## Rule 7: docs同時更新
- 遷移ロジック変更時は、同一コミットで以下を更新する:
  - `android/docs/navigation-state-map.md`
  - `android/docs/navigation-edit-rules.md`
  - `android/docs/task-unlock-first-home-decision.md`

## Rule 9: 通知タップと未タップを混在させない
- 通知タップ経路は既存挙動を優先する（追加判定の対象外）。
- 通知未タップ経路だけ `pending_unlock_decision_once` を消費する。
- 1回判定フラグの消費箇所は1か所に固定する。
- 背景Broadcast（例: `ACTION_USER_PRESENT`）を判定トリガーにしない。判定は `MainActivity` のライフサイクルで完結させる。

## Rule 8: `lookup` の固定手順
- `lookup` は以下をまとめて実施する作業名とする。
  1. `adb devices` で端末確認
  2. 端末の既定ホームを「デフォルト」へ戻す
  3. `adb uninstall com.aokikensaku.launcherlock`（未インストール時の失敗は無視して続行）
  4. `adb logcat -c`
  5. `adb install -r <latest-debug-apk>` で最新debug APKを新規導入
  6. 必要に応じてログ監視を起動（例: `adb logcat -v time MainActivity:I SettingsActivity:I LockStateEvaluator:I ActivityTaskManager:I '*:S'`）
- 目的は「本アプリが入っていない状態から初回インストール直後」を毎回再現し、遷移確認条件を固定すること。
