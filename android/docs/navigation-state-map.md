# Navigation State Map

このドキュメントは、若様の宿題アプリの画面遷移状態を整理したものです。
遷移変更時は必ず本書を更新してください。

## Screen
- `MainActivity`: 宿題回答画面（HOME/LAUNCHERエントリ）
- `SettingsActivity`: 御支度画面
- `System Home Settings`: 端末のホーム設定画面（PermissionController）
- `Normal Home`: 通常ホーム（例: Nexus Launcher）

## Core Flags (SharedPreferences: `launcher_lock`)
- `is_locked`: `true` なら通常ホームへ転送しない
- `skip_forward_to_normal_home_once`: 御支度保存直後に1回だけ自動転送を抑止
- `home_settings_in_progress`: アプリからホーム設定へ遷移中フラグ
- `home_settings_requested_at`: ホーム設定遷移開始時刻
- `home_settings_expect_default_home`: 既定ホーム切替を期待するか

## Main Flow
1. LAUNCHER起動で `MainActivity` が開く
2. 既定ホームが若様でない場合は「ホームアプリ設定」ダイアログ表示
3. 御支度保存後は `MainActivity` を前面化し、1回だけ自動転送抑止
4. HOME起動時は、`is_locked=false` なら通常ホームへ転送、`is_locked=true` なら転送しない

## Dialog Flow (ホームアプリ設定)
- `設定する`:
  - `home_settings_*` フラグをセット
  - システムのホーム設定画面を別タスクで開く
- `無視する`:
  - 通常ホームへ戻す（`openHomeScreen()`）

## Settings Save Flow
- バリデーション通過後に設定保存
- メールは空欄許可（入力ありの場合のみ形式チェック）
- 保存時に以下を実施:
  - `skip_forward_to_normal_home_once = true`
  - `home_settings_in_progress = false`
  - `home_settings_requested_at` / `home_settings_expect_default_home` を削除
  - `MainActivity` を `CLEAR_TOP | SINGLE_TOP` で前面化

## Guard Conditions of `maybeForwardToNormalHome`
通常ホームへ転送しない条件:
- `is_locked == true`
- 若様が既定ホームではない
- `home_settings_in_progress == true`
- LAUNCHER起動
- すでに転送中の `forwarding_to_normal_home=true`
