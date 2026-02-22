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
- `MainActivity` の遷移判定は `onResume` / `onNewIntent` のみで扱う。
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

## Rule 7: docs同時更新
- 遷移ロジック変更時は、同一コミットで以下を更新する:
  - `android/docs/navigation-state-map.md`
  - `android/docs/navigation-edit-rules.md`
