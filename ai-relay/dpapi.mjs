// サインインの情報（トークン）を、Windows の DPAPI（データ保護の機能）で暗号化する
//
// DevKit の SDK は、OS が鍵を管理する暗号化を必ず要求する（平文での保存はできない）。
// DPAPI で暗号化したデータは、暗号化したのと同じ Windows ユーザーでしか元に戻せない。
// 追加のライブラリを入れずに済むよう、Windows に最初から入っている PowerShell 経由で呼び出す。
import { execFile } from 'node:child_process';
import { join } from 'node:path';

const POWERSHELL = join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');

// データは標準入力で渡す（コマンドの引数に書くと、ほかのプログラムから見えてしまうため）
const script = (method) => `
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$bytes = [Convert]::FromBase64String([Console]::In.ReadToEnd().Trim())
$scope = [System.Security.Cryptography.DataProtectionScope]::CurrentUser
$result = [System.Security.Cryptography.ProtectedData]::${method}($bytes, $null, $scope)
[Console]::Out.Write([Convert]::ToBase64String($result))
`;

function dpapi(method, data) {
  return new Promise((resolve, reject) => {
    const child = execFile(
      POWERSHELL,
      ['-NoProfile', '-NonInteractive', '-Command', script(method)],
      { windowsHide: true, maxBuffer: 16 * 1024 * 1024 },
      (error, stdout) => {
        // エラーの中身にはデータが含まれることがあるので、外には出さない
        if (error) reject(new Error('Windows の暗号化（DPAPI）に失敗しました。'));
        else resolve(Buffer.from(stdout.trim(), 'base64'));
      },
    );
    child.stdin.end(Buffer.from(data).toString('base64'));
  });
}

/** DevKit の CredentialEncryption の実装 */
export const windowsCredentialEncryption = {
  // 保存済みのファイルがこの名前に結び付くので、変えないこと
  id: 'windows-dpapi-current-user-v1',
  isAvailable: () => process.platform === 'win32',
  encrypt: async (plaintext) => new Uint8Array(await dpapi('Protect', Buffer.from(plaintext, 'utf8'))),
  decrypt: async (ciphertext) => (await dpapi('Unprotect', ciphertext)).toString('utf8'),
};
