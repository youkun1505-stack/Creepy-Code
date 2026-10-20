# Pull したらやること

アプリを自分の PC で動かすまでの、いちばん短い手順です。
くわしい説明やトラブルのときは [環境構築手順書.md](環境構築手順書.md) を見てください。

> **2026-10-14 変更**：データベースを Oracle（Docker）から **Supabase**（インターネット上のデータベース。チーム全員で1つを共有）に変えました。すでに環境を作った人も、下の ① をやってください。

---

## はじめての人（1回だけ）

### ① DB の接続情報を設定する
1. 管理担当から `DB_URL`・`DB_USERNAME`・`DB_PASSWORD` の3つの値を受け取る。
2. Windows の「環境変数を編集」で、3つを「ユーザー環境変数」に追加する。
3. Eclipse を開いていたら再起動する。

> 接続情報は**リポジトリや GitHub に絶対に書かない**（公開リポジトリのため）。
> `db/` の SQL は共有のデータベースで実行済み。**各自では実行しない**。

### ② Eclipse に読み込む
「ファイル → インポート → Maven → 既存 Maven プロジェクト」で `bridal-app` フォルダを選んで「完了」。
右下の読み込みが終わるまで待つ。

### ③ 起動する
`BridalApplication.java` を右クリック →「実行 → Spring Boot アプリケーション」。

### ④ 確認する
コンソールに **`HikariPool-1 - Start completed.`** が出たら完成。

---

## 2回目からの Pull のあと

1. Eclipse で `bridal-app` を右クリック →「**Maven → プロジェクトの更新**」→ OK。
2. `BridalApplication.java` を起動する。

> Docker や Oracle の起動は、もう必要ありません。
> データベースは全員共通です。自分が変えたデータは、ほかのメンバーにも見えます。

---

## 困ったら

| こうなった | こうする |
|---|---|
| `Could not resolve placeholder 'DB_URL'` | ① ができていない。設定して Eclipse を再起動 |
| `password authentication failed` | `DB_PASSWORD` の値を確認する |
| `Tenant or user not found` | `DB_USERNAME`・`DB_URL` の値を確認する |
| `The connection attempt failed` | ネットの接続を確認。直らなければ管理担当に伝える |
| `Port 8080 was already in use` | 別のアプリを止めてから起動し直す |
| 赤いエラーが消えない | 「Maven → プロジェクトの更新」をもう一度 |
