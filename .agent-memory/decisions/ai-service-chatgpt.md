# AI サービスを Gemini API から Sign in with ChatGPT に変更
- 種類：決定
- 決めた日・確かめた日：2026-10-14
- 確かさ：高（実装済み。PR #35・コミット fc88ec7）
- 当てはまる場面：AI 呼び出し（P-10 など）の開発・設定・実行
- 根拠：チーム決定（2026-10-14）、GitHub issue #37、PR #35、コミット fc88ec7、ai-relay/、bridal-app/src/main/resources/application.properties、bridal-app/ai-account.cmd、bridal-app/src/main/java/com/creepycode/common/ai/chatgpt/ChatGptSignInMain.java、ChatGptAutoSignIn.java、ChatGptConfig.java、ChatGptEndpoints.java（2026-11-18 に確かめた）

## 中身
AI を呼ぶ方式を、Gemini API から **Sign in with ChatGPT**（ChatGPT のプランの利用枠を使う方式）に変更した。2026-10-14 の決定の時点では中継プログラム（`ai-relay/`）を経由していたが、develop の #45 で中継を経由せず、ChatGPT の Responses API を直接呼ぶ形に変わった（詳しくは「直した記録」）。

- **API キーは使わない**：API キー（`GEMINI_API_KEY` 等）は不要となり、環境変数や設定ファイルにも書かない。
- **サインイン方式**：`bridal-app` フォルダで `ai-account.cmd sign-in` を実行し、ブラウザで ChatGPT にサインインする（Java のコマンド。Spring は起動しない。各メンバーが自分のアカウント。発表は仲村のアカウント。詳しくは [AI の利用の前提](ai-usage-premise.md)）。
- **PC ごとのサインインが必要**：サインイン情報（`%USERPROFILE%\.creepy-code\chatgpt-java`。環境変数 `CREEPY_CODE_HOME` があればその下の `chatgpt-java`）は Windows の DPAPI で暗号化されるため、本物の AI を動かす PC ごとにサインインが必要。
- **実行時の設定**：本物の AI を呼ぶ PC では、環境変数 `APP_AI_MODE=chatgpt` を設定してアプリを起動する。`npm start` は不要（アプリは中継を経由せず、OpenAI の Responses API を直接呼ぶ）。設定しない場合は `dummy`（固定文章）で動作する。`chatgpt` のとき、サインインしていなければ起動後に自動でサインインを始める（環境変数 `APP_AI_AUTO_SIGN_IN`、初期値 `true`。戻り先は `127.0.0.1` のポート 1455）。

## 確かめられていないこと
- サインインの有効期限が切れたときの挙動（Issue #37 より）。
- 上の手順と設定は、コードを読んで確かめた（2026-11-18）。実際に `ai-account.cmd sign-in` でサインインして、`APP_AI_MODE=chatgpt` で本物の AI が返答するところまで動かしたことは、まだ確かめていない。
- `ai-relay/` のフォルダと `npm` のスクリプト（`sign-in`・`start`）は、リポジトリに今も残っている。ただし、今のアプリはそれを使わない（`ChatGptConfig.java` に中継の記述が無く、接続先は `ChatGptEndpoints.java:17` の OpenAI の URL）。残す・消すの判断はこの記録の対象外。

## 直した記録
- 2026-11-18：12 行目の「チームでは仲村のアカウントを使用」を「各メンバーが自分のアカウント。発表は仲村のアカウント」に直した。理由：issue #69 で、アカウントは共有せず各メンバーが自分のものを使うと決まったため。根拠：仲村さんの決定（2026-11-18）、GitHub issue #69、新しい記録 [AI の利用の前提](ai-usage-premise.md)。
- 2026-11-18：13 行目の保存先 `%USERPROFILE%\.creepy-code\chatgpt` を `…\chatgpt-java`（`CREEPY_CODE_HOME` があればその下）に直した。理由：コードの保存先と合わせるため。根拠：`bridal-app/src/main/java/com/creepycode/common/ai/chatgpt/ChatGptCredentialStore.java:36-37`（`HOME_FOLDER_NAME = ".creepy-code"`、`STORE_FOLDER_NAME = "chatgpt-java"`）と `resolveDirectory`（68-73 行）。
- 2026-11-18：直したのは上の2点だけ。14 行目以降の `ai-relay` と `APP_AI_MODE=chatgpt` の記述は、今のコードと合っているか未確認のまま残した（下の「確かめられていないこと」を見る）。
  - 注：この行は 2026-11-18 の時点の記述で、下の項目で置き換えた（元の内容を消さずに残している）。
- 2026-11-18：T-1 の指示で、次の 5 点を直した（理由：`npm run sign-in`・`npm start` の手順が今のコードと合わないため。根拠は下の各点）。
  - 中身 1 行目（9 行目）：中継経由の説明に、「2026-10-14 の決定の時点では中継経由、develop の #45 で中継を経由せず Responses API を直接呼ぶ形に変わった」と注記を足した。根拠：`docs/00_ブライダル業界/03_基本設計書/v1.7/変更内容まとめ.md:90`（#45 で直接呼ぶ形に変わったという記述。取り込みはコミット `2798171`）、`bridal-app/src/main/java/com/creepycode/common/ai/chatgpt/ChatGptConfig.java`（中継の記述が無い）、`ChatGptEndpoints.java:17`（接続先は `https://api.openai.com/v1/responses`）。
  - 12 行目：`ai-relay` の `npm run sign-in` を `bridal-app` の `ai-account.cmd sign-in` に直した。根拠：`bridal-app/ai-account.cmd`、`bridal-app/src/main/java/com/creepycode/common/ai/chatgpt/ChatGptSignInMain.java:8-9, 20-24`。
  - 13 行目（保存先）：変えていない。`ChatGptCredentialStore.java:36-37, 62-73` で、`CREEPY_CODE_HOME` があればその下の `chatgpt-java`、なければ `%USERPROFILE%\.creepy-code\chatgpt-java` と確かめた。
  - 14 行目：`npm start` の記述を削除し、「`npm start` は不要。アプリは中継を経由せず直接呼ぶ」と書いた。`APP_AI_AUTO_SIGN_IN`（初期値 true）と戻り先のポート 1455 を足した。Eclipse の記述は、起動の仕方を確かめていないので削除した。根拠：`bridal-app/src/main/resources/application.properties:55-61`、`ChatGptConfig.java:14`、`ChatGptAutoSignIn.java:58-61, 93`、`ChatGptSignInMain.java:16`。
  - 確かめられていないこと：中継の記述を、「コードを読んで確かめた。実際に動かしたことは未確認」と書き換えた。理由：AGENTS.md の「設定ファイルに書いただけで効いているとは書かない」に合わせるため。
- 注：`ai-relay/`（`npm run sign-in`・`npm start` を持つ）は、リポジトリに今も残っている（`ai-relay/package.json`）。今のアプリはそれを使わないが、消すかどうかは、この記録では判断していない。
