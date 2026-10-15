# ai-relay（AI 用の中継プログラム）

bridal-app の AI 機能のための、小さな中継プログラムです。

bridal-app の AI は **Sign in with ChatGPT**（ChatGPT のアカウントでサインインし、その人のプランの利用枠で AI を呼ぶしくみ）を使います。ただし、公式の部品（SDK）が Node.js 用しかなく、Java（bridal-app）からは直接使えません。そこで、この中継プログラムを bridal-app の横で動かし、bridal-app からは `http://127.0.0.1:8787` を呼びます。

```
bridal-app（Java・8080番） → ai-relay（Node.js・8787番） → OpenAI
```

## だれが動かすか

- **本物の AI を使う PC でだけ**動かします。サインインするのは、ChatGPT の **Plus または Pro** に入っている人です（チームでは仲村のアカウントを使います）。
- それ以外のメンバーは動かす必要はありません。bridal-app は「ダミーモード」（固定の文章を返す）のままで開発できます。

## 必要なもの

- Windows（サインイン情報の暗号化に Windows の機能を使っています）
- Node.js 22.12 以上
- Git

## 使い方

コマンドは、この `ai-relay` フォルダで実行します。

| 順番 | コマンド | 内容 |
|---|---|---|
| 1（最初の1回だけ） | `npm run setup` | OpenAI の DevKit を取得してビルドする。数分かかる |
| 2（最初の1回だけ） | `npm run sign-in` | ブラウザが開くので、ChatGPT にサインインし、プランの利用を許可する |
| 3（AI を使うたび） | `npm start` | 中継プログラムを起動する。止めるときは Ctrl + C |

そのほかのコマンド：

| コマンド | 内容 |
|---|---|
| `npm run status` | サインインの状態を表示する |
| `npm run models` | 使えるモデルの一覧を表示する |
| `npm run sign-out` | サインアウトする |

## bridal-app からの呼び出し方

| メソッドとパス | 内容 |
|---|---|
| `GET /status` | サインインの状態。`ready` が `true` なら AI を使える |
| `GET /models` | 使えるモデルの一覧 |
| `POST /generate` | AI に文章を作らせる |

`POST /generate` に送る JSON（`Content-Type: application/json`）：

```json
{ "instructions": "AI への指示（省略可）", "input": "AI に渡す文章", "model": "モデル名（省略可）" }
```

返ってくる JSON：

```json
{ "text": "AI が作った文章", "model": "使ったモデル名" }
```

失敗したときは、`{ "error": { "code": "...", "message": "...", "retryable": true/false } }` が返ります。サインインしていないときは 401 です。

## 設定（環境変数。ふつうは変えなくてよい）

| 変数名 | 初期値 | 内容 |
|---|---|---|
| `AI_RELAY_PORT` | `8787` | 中継プログラムのポート番号 |
| `AI_RELAY_SIGN_IN_PORT` | `8788` | サインインのとき、ブラウザから戻ってくる先のポート番号 |
| `AI_RELAY_MODEL` | `gpt-6-luna` | 使うモデル名。使えるモデルは `npm run models` で確認できる |
| `CREEPY_CODE_HOME` | `C:\Users\<ユーザー名>\.creepy-code` | DevKit とサインイン情報の保存場所 |

## 守ること

- **サインインの情報は、リポジトリの外（`C:\Users\<ユーザー名>\.creepy-code`）に保存されます。** このフォルダの中身を、リポジトリや GitHub に入れないでください。
- AI を使った分は、**サインインした人の ChatGPT の利用枠**から引かれます。使いすぎていないかは https://chatgpt.com/settings/usage で確認できます。
- AI に送る内容は、**架空のテストデータだけ**にしてください。実在の人の個人情報は送らないでください。
- 中継プログラムは、この PC の中（127.0.0.1）からの呼び出しだけを受け付けます。ほかの PC やブラウザで開いたサイトからは呼び出せません。

## OpenAI の DevKit について

`npm run setup` は、[openai/sign-in-with-chatgpt-devkit](https://github.com/openai/sign-in-with-chatgpt-devkit) を `C:\Users\<ユーザー名>\.creepy-code\devkit` に取得してビルドします。DevKit は npm では配布されておらず、「Sign-in with ChatGPT DevKit Noncommercial License」（非商用の利用だけを認めるライセンス）で公開されています。そのため、DevKit のコードはこのリポジトリにはコピーしていません。授業の課題として使うぶんには非商用にあたりますが、**このアプリを商用で使うことはできません。**
