// bridal-app の AI 用中継プログラム（Sign in with ChatGPT）
//
// bridal-app（Java）からは Sign in with ChatGPT を直接使えない（公式の SDK が Node.js 用しかない）ので、
// この小さなプログラムを bridal-app の横で動かし、bridal-app からは http://127.0.0.1:8787 を呼ぶ。
//
// 使い方：
//   node relay.mjs sign-in    ブラウザで ChatGPT にサインインする
//   node relay.mjs status     サインインの状態を表示する
//   node relay.mjs models     使えるモデルの一覧を表示する
//   node relay.mjs serve      中継サーバーを起動する（何も付けないときもこれ）
//   node relay.mjs sign-out   サインアウトする
import { existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { windowsCredentialEncryption } from './dpapi.mjs';
import { AUTH_DIR, SDK_ENTRY } from './paths.mjs';

// 中継サーバーのポート番号（bridal-app の app.ai.relay-url と合わせる）
const PORT = Number(process.env.AI_RELAY_PORT ?? 8787);
// サインインのとき、ブラウザから戻ってくる先のポート番号
const SIGN_IN_PORT = Number(process.env.AI_RELAY_SIGN_IN_PORT ?? 8788);
// 使うモデル。開発中に利用枠を使いすぎないよう、軽いモデルを初期値にしている
const DEFAULT_MODEL = process.env.AI_RELAY_MODEL ?? 'gpt-6-luna';
// 1回のリクエストで受け取る本文の上限
const MAX_BODY_BYTES = 2 * 1024 * 1024;

if (!existsSync(SDK_ENTRY)) {
  console.error('DevKit がまだ準備されていません。先に「npm run setup」を実行してください。');
  process.exit(1);
}
const { createChatGPT, ChatGPTError } = await import(pathToFileURL(SDK_ENTRY).href);

const chatgpt = createChatGPT({
  appName: 'Creepy Code Bridal App',
  appId: 'creepy-code-bridal-app',
  redirectPort: SIGN_IN_PORT,
  storageDir: AUTH_DIR,
  credentialEncryption: windowsCredentialEncryption,
});

/** サインインの状態を、人が読める文章にする */
function describe(session) {
  if (session.status !== 'connected') return `サインインしていません（状態：${session.status}）。「npm run sign-in」を実行してください。`;
  const who = session.identity?.email ?? session.identity?.name ?? '（名前なし）';
  if (!session.sharing) return `${who} でサインイン済みですが、プランの利用枠を使う許可がありません。「npm run sign-in」をやり直し、利用の許可を選んでください。`;
  return `${who} でサインイン済みです。AI を使えます。`;
}

/** 使うモデルを決める（リクエストの指定 → 環境変数 → 初期値） */
function resolveModel(requested) {
  return requested || DEFAULT_MODEL;
}

/** エラーを HTTP のステータスコードに置き換える */
function statusOf(error) {
  if (['sign_in_required', 'sharing_not_enabled'].includes(error.code)) return 401;
  if (error.code === 'invalid_request') return 400;
  if (error.code === 'connection_busy') return 409;
  return Number.isInteger(error.status) && error.status >= 400 ? error.status : 502;
}

function sendJson(response, status, body, headers = {}) {
  const text = JSON.stringify(body);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(text),
    'cache-control': 'no-store',
    ...headers,
  });
  response.end(text);
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    let exceeded = false;
    request.on('data', (chunk) => {
      if (exceeded) return;
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        exceeded = true;
        // 接続を切断せず、残りの受信データを読み捨ててストリームを終了へ向かわせる
        request.resume();
        reject(new ChatGPTError('invalid_request', '送る内容が大きすぎます。'));
        return;
      }
      chunks.push(chunk);
    });
    request.on('end', () => {
      if (exceeded) return;
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
      catch { reject(new ChatGPTError('invalid_request', '本文が JSON として読めません。')); }
    });
    request.on('error', (err) => {
      if (exceeded) return;
      reject(err);
    });
  });
}

async function handle(request, response) {
  // この PC の中からの呼び出しだけを受け付ける。
  // ブラウザで開いた別のサイトから呼ばれると、サインインした人の利用枠を勝手に使われてしまうため、
  // ブラウザが付ける Origin ヘッダーがあるリクエストと、宛先が 127.0.0.1 でないリクエストは断る
  const allowedHosts = [`127.0.0.1:${PORT}`, `localhost:${PORT}`];
  if (!allowedHosts.includes(request.headers.host ?? '') || request.headers.origin !== undefined) {
    return sendJson(response, 403, { error: { code: 'forbidden', message: 'この PC の bridal-app からだけ呼び出せます。', retryable: false } });
  }
  const { pathname } = new URL(request.url ?? '/', `http://127.0.0.1:${PORT}`);

  // サインインの状態を返す
  if (request.method === 'GET' && pathname === '/status') {
    const session = await chatgpt.getSession();
    return sendJson(response, 200, { status: session.status, sharing: session.sharing, ready: session.status === 'connected' && session.sharing });
  }

  // 使えるモデルの一覧を返す
  if (request.method === 'GET' && pathname === '/models') {
    return sendJson(response, 200, { models: await chatgpt.listModels() });
  }

  // AI に文章を作らせる
  // 受け取る JSON：{ "instructions": "AI への指示（省略可）", "input": "AI に渡す文章", "model": "モデル名（省略可）" }
  // 返す JSON　　：{ "text": "AI が作った文章", "model": "使ったモデル名" }
  if (request.method === 'POST' && pathname === '/generate') {
    if (!(request.headers['content-type'] ?? '').startsWith('application/json')) {
      throw new ChatGPTError('invalid_request', 'Content-Type は application/json にしてください。');
    }
    const body = await readJson(request);
    if (typeof body?.input !== 'string' || !body.input.trim()) throw new ChatGPTError('invalid_request', 'input（AI に渡す文章）が空です。');
    if (body.instructions !== undefined && typeof body.instructions !== 'string') throw new ChatGPTError('invalid_request', 'instructions は文字列にしてください。');
    if (body.model !== undefined && typeof body.model !== 'string') throw new ChatGPTError('invalid_request', 'model は文字列にしてください。');
    const model = resolveModel(body.model);
    const result = await chatgpt.streamResponse({ model, input: body.input, ...(body.instructions ? { instructions: body.instructions } : {}) });
    return sendJson(response, 200, { text: result.text, model });
  }

  return sendJson(response, 404, { error: { code: 'not_found', message: 'そのパスはありません。', retryable: false } });
}

function serve() {
  const server = createServer((request, response) => {
    handle(request, response).catch((error) => {
      // AI に送った内容・返ってきた内容は、ログにもエラーにも出さない（エラーの種類とメッセージだけ返す）
      const typed = error instanceof ChatGPTError ? error : new ChatGPTError('relay_error', '中継プログラムの中でエラーが起きました。');
      console.error(`[${new Date().toISOString()}] ${request.method} ${request.url} -> ${typed.code}`);
      if (!response.headersSent) {
        const status = statusOf(typed);
        sendJson(response, status, { error: typed.toJSON() }, status >= 400 ? { connection: 'close' } : {});
      } else response.destroy();
    });
  });
  server.on('error', (error) => {
    console.error(error.code === 'EADDRINUSE' ? `ポート ${PORT} は使用中です。すでに中継プログラムが動いていないか確認してください。` : `起動に失敗しました：${error.message}`);
    process.exit(1);
  });
  // 127.0.0.1 だけで待ち受ける（ほかの PC からはつながらない）
  server.listen(PORT, '127.0.0.1', async () => {
    console.log(`中継プログラムを起動しました：http://127.0.0.1:${PORT}`);
    console.log(describe(await chatgpt.getSession()));
    console.log('止めるときは Ctrl + C を押します。');
  });
}

const command = process.argv[2] ?? 'serve';
try {
  if (command === 'serve') {
    serve();
  } else if (command === 'sign-in') {
    console.log('ブラウザを開きます。ChatGPT にサインインし、このアプリによるプランの利用を許可してください。');
    console.log(describe(await chatgpt.signIn({ label: 'bridal-app' })));
  } else if (command === 'sign-out') {
    await chatgpt.disconnect();
    console.log('サインアウトしました。');
  } else if (command === 'status') {
    console.log(describe(await chatgpt.getSession()));
  } else if (command === 'models') {
    for (const model of await chatgpt.listModels()) console.log(`${model.slug}\t${model.displayName}`);
  } else {
    console.error(`知らないコマンドです：${command}（sign-in / status / models / serve / sign-out のどれかを指定してください）`);
    process.exitCode = 1;
  }
} catch (error) {
  console.error(`失敗しました：${error instanceof Error ? error.message : error}`);
  process.exitCode = 1;
}
