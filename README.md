# Creepy-Code

企画開発用リポジトリ

AIブライダルプランニング支援システム（P2C：Creepy Code）の設計書・マニュアルと、アプリ本体（Spring Boot）をまとめて管理しています。

## 使っている技術

- Java 17 / Spring Boot 3.5 / Maven
- Spring Data JPA / Supabase（PostgreSQL。チーム全員で1つのプロジェクトを共有）
- Thymeleaf ＋ Thymeleaf Layout Dialect ＋ Bootstrap 5
- Spring Security（フォーム認証・BCrypt）
- AI：Sign in with ChatGPT（ChatGPT のプランの利用枠で呼ぶ。API キーは使わない。初期状態はダミーモード）
  - Java から直接は使えないため、Node.js の中継プログラム（`ai-relay/`）を経由する

アプリは各自の PC（ローカル環境）で動かし、データベースだけインターネット上の Supabase を使います（AWS は使いません）。
データベースの接続情報は環境変数で渡し、このリポジトリには書きません（公開リポジトリのため）。

## フォルダ構成

```
Creepy-Code/
├─ bridal-app/        … アプリ本体（Eclipse で「既存 Maven プロジェクト」として読み込む）
├─ ai-relay/          … AI 用の中継プログラム（Node.js。本物の AI を使う PC でだけ動かす）
├─ db/                … Supabase の SQL Editor で実行する SQL（管理担当が実行）
│   ├─ 00〜03_*.sql         … スキーマ・テーブル・仮データ・テストユーザーの作成（共有DBで実行済み）
│   └─ 保留/                … 全テーブル削除の SQL（ふだんは実行しない）
└─ docs/
    ├─ 00_ブライダル業界/   … 業界調査・基本設計書など
    ├─ 01_テンプレート/
    ├─ 02_提出物/
    ├─ 03_Git&Sourcetree操作マニュアル/
    └─ 04_開発ルール/       … コーディング規約
```

## 本物の AI を使うまでの手順

初期状態ではダミーモード（固定の文章を返す）で動作します。本物の AI（ChatGPT）を使う場合は、以下の手順で設定します。

1. `ai-relay` フォルダで `npm run setup` を実行する（最初の1回だけ）。
2. `ai-relay` フォルダで `npm run sign-in` を実行し、ブラウザで ChatGPT にサインインして、プランの利用を許可する（チームでは仲村のアカウントを使う）。
   - サインインの情報は、このとき `C:\Users\<ユーザー名>\.creepy-code\chatgpt` に作られます（`ai-relay/paths.mjs:10`、`relay.mjs:37`）。
   - 情報は Windows の機能（DPAPI）で暗号化され、元に戻せるのは同じ PC の同じ Windows ユーザーだけです。そのため、**本物の AI を動かす PC ごとにサインインが必要**です（`ai-relay/dpapi.mjs:1-4`）。
   - サインインしていないと、AI を呼んでもエラーになります（`relay.mjs:43,56`）。
3. `ai-relay` フォルダで `npm start` を実行する（画面は開いたままにする）。
4. Windows の環境変数 `APP_AI_MODE` に `chatgpt` を設定し、Eclipse を再起動してから bridal-app を起動する。

## 設定する環境変数

| 環境変数の名前 | 設定する値 | いつ設定するか | 根拠 |
| --- | --- | --- | --- |
| `APP_AI_MODE` | `chatgpt`（本物の AI）／`dummy`（固定の文章。設定しなければこちら） | **本物の AI を使う PC だけ** | `bridal-app/src/main/resources/application.properties:56` |
| `APP_AI_RELAY_URL` | 初期値 `http://127.0.0.1:8787` | 中継のポートを変えたときだけ | 同:58 |
| `AI_RELAY_PORT` | 初期値 `8787` | 8787 番が使えないときだけ（変えたら `APP_AI_RELAY_URL` も合わせる） | `ai-relay/relay.mjs:19` |
| `AI_RELAY_SIGN_IN_PORT` | 初期値 `8788` | 8788 番が使えないときだけ | `ai-relay/relay.mjs:21` |
| `AI_RELAY_MODEL` | 初期値 `gpt-6-luna` | 使う AI のモデルを変えるときだけ | `ai-relay/relay.mjs:23` |
| `CREEPY_CODE_HOME` | 初期値 `C:\Users\<ユーザー名>\.creepy-code` | 保存先を変えるときだけ | `ai-relay/paths.mjs:6` |

- `application.properties` の `app.ai.mode`・`app.ai.relay-url` は、**アプリの中の設定の名前で、環境変数ではありません**。たとえば `app.ai.mode=${APP_AI_MODE:dummy}` は、「環境変数 `APP_AI_MODE` の値を使い、なければ `dummy`」という意味です。メンバーは `application.properties` を書き換えないでください。
- API キーは使いません。`GEMINI_API_KEY` を読むコードも、`app.ai.api-key` という設定もありません。
- `APP_AI_MODE` に `dummy`・`chatgpt` 以外（例：`api`）を入れても、エラーにならずにダミーで動くと考えられます（`matchIfMissing=true` からの推測）。

## 資料

- [ai-relay の使い方](ai-relay/README.md) … 本物の AI を使うときの中継プログラム
- [コーディング規約](docs/04_開発ルール/コーディング規約.md) … 命名規則・パッケージ構成（機能ごと）など
- [基本設計書 v1.6](docs/00_ブライダル業界/03_基本設計書/v1.6/基本設計書_v1.6.docx)
