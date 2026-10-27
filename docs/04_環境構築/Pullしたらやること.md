# Pull したらやること

アプリを自分の PC で動かすまでの、いちばん短い手順です。
くわしい説明やトラブルのときは [環境構築手順書.md](環境構築手順書.md) を見てください。

---

## はじめての人（1回だけ）

### ① Oracle を起動する
Docker Desktop を開いて、授業の Oracle のコンテナの ▶ を押す。1〜2分待つ。

### ② CREEPY ユーザーを作る
1. SQL Developer で **SYSTEM** の接続を開く。
2. `db/00_create_user.sql` を開いて **F5**。
3. エラー（`ORA-`）が出なければ OK。

> `db/保留/` の中は **まだ実行しない**。

### ③ Eclipse に読み込む
「ファイル → インポート → Maven → 既存 Maven プロジェクト」で `bridal-app` フォルダを選んで「完了」。
右下の読み込みが終わるまで待つ。

### ④ 起動する
`BridalApplication.java` を右クリック →「実行 → Spring Boot アプリケーション」。

### ⑤ 確認する
コンソールに **`HikariPool-1 - Start completed.`** が出たら完成。

---

## 2回目からの Pull のあと

1. Docker Desktop で Oracle を起動する。
2. Eclipse で `bridal-app` を右クリック →「**Maven → プロジェクトの更新**」→ OK。
3. `BridalApplication.java` を起動する。

> `db/` に新しい SQL が増えていたら、チームの連絡を確認してから実行する。

---

## 困ったら

| こうなった | こうする |
|---|---|
| `ORA-12514` | Oracle が起動しきっていない。1〜2分待ってもう一度 |
| `ORA-01017` | CREEPY がまだ無い。② をやる |
| `Port 8080 was already in use` | 別のアプリを止めてから起動し直す |
| 赤いエラーが消えない | 「Maven → プロジェクトの更新」をもう一度 |
