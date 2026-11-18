# AI の利用の前提（アカウント・発表・上限・画面の説明）
- 種類：決定
- 決めた日・確かめた日：2026-11-18
- 確かさ：高（メンバーの決定。ただし OpenAI の利用規約と Codex の利用枠は公式で確かめられていない。下の「確かめられていないこと」を見る）
- 当てはまる場面：AI 呼び出し（深掘りチャットなど）の開発・設定・実行、発表の準備、深掘りチャット画面の説明文
- 根拠：仲村さんの決定（2026-11-18）、GitHub issue #69、`bridal-app/src/main/java/com/creepycode/common/ai/chatgpt/ChatGptAiClient.java:396`（429 のときの文言）、`bridal-app/src/main/java/com/creepycode/common/ai/chatgpt/ChatGptCredentialStore.java:36-37, 62-73`（保存先の名前と決め方）、`bridal-app/ai-account.cmd`・`bridal-app/src/main/java/com/creepycode/common/ai/chatgpt/ChatGptSignInMain.java:8-9, 20-24`（サインインの手順）

## 中身
1. **アカウントは共有しない**：ふだんの開発では、各メンバーが自分のアカウントで Sign in with ChatGPT を行う（`bridal-app` フォルダで `ai-account.cmd sign-in`。サインイン情報は `%USERPROFILE%\.creepy-code\chatgpt-java` に保存され、環境変数 `CREEPY_CODE_HOME` があればその下に保存される）。アカウントは共有しない。
2. **発表のときは仲村のアカウントで動かす**。
3. **利用者ごとの上限はアプリでは作らない**：上限は ChatGPT 側の利用枠に任せる。アプリには利用者ごとの回数の上限が無い。利用枠を使い切ると、アプリは「ChatGPT の利用枠の上限に達しました。ChatGPT の設定の Usage で確認してください。」と表示する（HTTP 429 のとき。`ChatGptAiClient.java:396`）。
4. **深掘りチャットの画面に説明を出す**：画面に次の文を出す。
   「このサービスではChatGPTを使用しています。入力した内容と事前ヒアリングの回答は、OpenAI に送られます。」

## 確かめられていないこと
- OpenAI の利用規約の原文と Codex のヘルプの原文は、2026-11-18 に HTTP 403 で読めず、確かめられていない。二次情報では、アカウントの共有を禁じるとされる。原文を読んだら、この記録を直す。
- Codex の5時間ごとの利用回数は、公式で確かめていない。
- ChatGPT 側の利用枠の具体的な数値は、確かめていない。

## 直した記録
- 2026-11-18：中身 1 行目の手順を `npm run sign-in`（中継プログラム ai-relay）から `bridal-app/ai-account.cmd sign-in` に直し、保存先を書き足した。理由：サインインは今、`ai-account.cmd`（Java。Spring は起動しない）で行うため。`npm run sign-in` の手順は今のアプリでは使わない。根拠：`bridal-app/ai-account.cmd`（1〜17 行）、`ChatGptSignInMain.java:8-9, 20-24`（サインインの入口と使い方）、`ChatGptCredentialStore.java:36-37, 62-73`（保存先。`CREEPY_CODE_HOME` があればその下の `chatgpt-java`）。根拠の行番号は 2026-11-18 に読んで確かめた。
- 2026-11-18：根拠の欄に、保存先の決め方（62-73 行）とサインインの手順（ai-account.cmd と ChatGptSignInMain.java）を足した。理由：根拠が保存先の名前だけで、手順と保存先の中身の裏付けになっていなかったため（T-1 の低の指摘）。
