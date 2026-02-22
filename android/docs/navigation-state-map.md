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
- `skip_forward_to_normal_home_once`: 御支度保存直後に、起動元（HOME/LAUNCHER/OTHER）に関係なく1回だけ自動転送を抑止
- `return_to_main_after_save_once`: 御支度保存直後に、必ず1回だけMain表示を優先する
- `home_settings_in_progress`: アプリからホーム設定へ遷移中フラグ
- `home_settings_requested_at`: ホーム設定遷移開始時刻
- `home_settings_expect_default_home`: 既定ホーム切替を期待するか
- `normal_home_*`: 通常ホームの保存先（自アプリ / Settings / ResolverActivity / FallbackHome は保存対象外）

## Flag Lifecycle: `return_to_main_after_save_once`
- 目的:
  - 御支度保存直後は、結界ON/OFFや起動元に関係なくMainを1回表示する。
- セット:
  - `SettingsActivity` の保存完了時に `true` を保存する。
- 消費:
  - `MainActivity` の `onCreate` / `onResume` / `onNewIntent` で1回だけ消費し、`false` に戻す。
  - 消費したターンは `suppressNextAutoForwardToNormalHome = true` として自動転送を止める。
- 注意:
  - このフラグは「保存直後のMain維持」専用。通常のHOME挙動制御は `is_locked` と `maybeForwardToNormalHome` が担う。

## Main Flow
1. LAUNCHER起動で `MainActivity` が開く
2. 既定ホームが若様でない場合は「ホームアプリ設定」ダイアログ表示
3. 御支度保存後は `MainActivity` を前面化し、1回だけ自動転送抑止
4. HOME起動時は、`is_locked=false` なら通常ホームへ転送、`is_locked=true` なら転送しない

## Dialog Flow (ホームアプリ設定)
- `設定する`:
  - 遷移直前に `normal_home_*` が未設定なら、現在の既定ホーム（fallbackで候補検出）を自動セット
  - `home_settings_*` フラグをセット
  - システムのホーム設定画面を別タスクで開く
- `無視する`:
  - 通常ホームへ戻す（`openHomeScreen()`）

## Settings Save Flow
- バリデーション通過後に設定保存
- メールは空欄許可（入力ありの場合のみ形式チェック）
- 上級設定の通常ホーム選択は即保存しない（画面内一時保持）
- 保存時に以下を実施:
  - 一時保持中の通常ホーム選択を `normal_home_*` へ保存
  - `return_to_main_after_save_once = true`
  - `skip_forward_to_normal_home_once = true`
  - `home_settings_in_progress = false`
  - `home_settings_requested_at` / `home_settings_expect_default_home` を削除
  - `MainActivity` を `CLEAR_TOP | SINGLE_TOP` で前面化

## Verification Focus (保存直後)
1. 御支度で保存する（結界ON/OFFどちらでも）
2. 保存直後は必ず `MainActivity` が前面になる
3. その次のHOME押下で、`is_locked=true` ならMain維持、`is_locked=false` なら通常ホームへ遷移

## Guard Conditions of `maybeForwardToNormalHome`
通常ホームへ転送しない条件:
- `is_locked == true`
- 若様が既定ホームではない
- `home_settings_in_progress == true`
- LAUNCHER起動
- すでに転送中の `forwarding_to_normal_home=true`
