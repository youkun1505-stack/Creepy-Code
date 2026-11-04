# AI サービスを Gemini API から Sign in with ChatGPT に変更
- 種類：決定
- 決めた日・確かめた日：2026-10-14
- 確かさ：高（実装済み。PR #35・コミット fc88ec7）
- 当てはまる場面：AI 呼び出し（P-10 など）の開発・設定・実行
- 根拠：チーム決定（2026-10-14）、GitHub issue #37、PR #35、コミット fc88ec7、ai-relay/、bridal-app/src/main/resources/application.properties

## 中身
AI を呼ぶ方式を、Gemini API から中継プログラム（`ai-relay/`）を経由した **Sign in with ChatGPT**（ChatGPT のプランの利用枠を使う方式）に変更した。

- **API キーは使わない**：API キー（`GEMINI_API_KEY` 等）は不要となり、環境変数や設定ファイルにも書かない。
- **サインイン方式**：`ai-relay` で `npm run sign-in` を実行し、ブラウザで ChatGPT にサインインする（チームでは仲村のアカウントを使用）。
- **PC ごとのサインインが必要**：サインイン情報（`%USERPROFILE%\.creepy-code\chatgpt`）は Windows の DPAPI で暗号化されるため、本物の AI を動かす PC ごとにサインインが必要。
- **実行時の設定**：本物の AI を呼ぶ PC では、`ai-relay` で `npm start` を動かした上で、環境変数 `APP_AI_MODE=chatgpt` を設定して Eclipse / アプリを起動する。設定しない場合は `dummy`（固定文章）で動作する。

## 確かめられていないこと
- サインインの有効期限が切れたときの挙動（Issue #37 より）。

## 直した記録
- なし
