// OpenAI の Sign in with ChatGPT DevKit を取得してビルドする（最初の1回だけ実行する）
//
// DevKit の SDK（@siwc/local）は npm では配布されていないので、GitHub から取得して自分の PC でビルドする。
// DevKit は「Sign-in with ChatGPT DevKit Noncommercial License」（非商用のみ）で配布されているため、
// このリポジトリにはコピーせず、リポジトリの外（~/.creepy-code/devkit）に置く。
import { execSync } from 'node:child_process';
import { existsSync, mkdirSync } from 'node:fs';
import { DEVKIT_DIR, HOME_DIR, SDK_ENTRY } from './paths.mjs';

const REPO_URL = 'https://github.com/openai/sign-in-with-chatgpt-devkit.git';
// 動作を確認したコミット（2026-10-07）。勝手に新しい版に変わらないよう固定する
const COMMIT = '0a36fefeb913055c8c7a1a29b63d82b96b2e841a';

const run = (command, cwd) => {
  console.log(`> ${command}`);
  execSync(command, { cwd, stdio: 'inherit' });
};

const [major, minor] = process.versions.node.split('.').map(Number);
if (major < 22 || (major === 22 && minor < 12)) {
  console.error(`Node.js 22.12 以上が必要です（今は ${process.versions.node}）。`);
  process.exit(1);
}

mkdirSync(HOME_DIR, { recursive: true });
if (!existsSync(DEVKIT_DIR)) run(`git clone ${REPO_URL} "${DEVKIT_DIR}"`);
run('git fetch origin', DEVKIT_DIR);
run(`git checkout --detach ${COMMIT}`, DEVKIT_DIR);
// SDK（packages/local）に必要なものだけを入れる。サンプルアプリ（Electron など）は入れない
run('npm ci --ignore-scripts -w @siwc/local --include-workspace-root', DEVKIT_DIR);
run('npm run build -w @siwc/local', DEVKIT_DIR);

if (!existsSync(SDK_ENTRY)) {
  console.error(`ビルド結果が見つかりません：${SDK_ENTRY}`);
  process.exit(1);
}
console.log('\n準備ができました。次は「npm run sign-in」でサインインしてください。');
