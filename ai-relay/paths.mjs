// 中継プログラムが使うフォルダの場所。すべてリポジトリの外（公開リポジトリに混ざらないようにするため）
import { homedir } from 'node:os';
import { join } from 'node:path';

// 既定は C:\Users\<ユーザー名>\.creepy-code
export const HOME_DIR = process.env.CREEPY_CODE_HOME ?? join(homedir(), '.creepy-code');
// OpenAI の Sign in with ChatGPT DevKit を取得・ビルドする場所
export const DEVKIT_DIR = join(HOME_DIR, 'devkit');
// サインインの情報（暗号化したトークン）の保存場所
export const AUTH_DIR = join(HOME_DIR, 'chatgpt');
// DevKit の SDK（@siwc/local）のビルド結果
export const SDK_ENTRY = join(DEVKIT_DIR, 'packages', 'local', 'dist', 'index.js');
