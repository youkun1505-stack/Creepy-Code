// AIプラン生成中（P-11）：画面を開いたら生成を頼み、できたらマイプラン（P-12）へ進む
//   ・30秒たっても終わらないときは「時間がかかっています」と［やり直す］を出す（待ち続けてもよい）
//   ・作れなかったときは［やり直す］を出す。3回続けて作れなかったら、プランナーに頼む案内を出す
//     （AI の失敗はサーバーが数える。通信の失敗などサーバーが数えられない失敗は、この画面で数える）
//   ・別の呼び出しがまだ作っている（running）ときは、少し待ってからもう一度聞く（失敗には数えない）
//   ・移るときは location.replace を使い、この画面を履歴に残さない（［戻る］で戻ると、また生成を頼んでしまうため）
document.querySelectorAll('[data-plan-generating]').forEach((page) => {
    const generateUrl = page.dataset.generateUrl;
    const status = page.querySelector('[data-generating-status]');
    const message = page.querySelector('[data-generating-message]');
    const slow = page.querySelector('[data-generating-slow]');
    const failed = page.querySelector('[data-generating-failed]');
    const giveUp = page.querySelector('[data-generating-give-up]');
    const retryButtons = page.querySelectorAll('[data-generating-retry]');

    // 基本設計書 6-5：30秒以内に終わらなければ「時間がかかっています」を出す
    const SLOW_AFTER_MS = 30000;
    // 1回の問い合わせを打ち切るまでの時間。AI の待ち（30秒）より長くして、ふつうは返事を待てるようにする
    const REQUEST_TIMEOUT_MS = 60000;
    // 作っている最中だったときに、もう一度聞くまでの待ち時間
    const RUNNING_RETRY_MS = 3000;
    // 通信の失敗などが何回続いたら、プランナーに頼む案内を出すか（サーバーの数え方と同じ3回）
    const MAX_CLIENT_FAILURES = 3;
    // 「作っている最中」が続いても、この時間を超えたら待つのをやめて失敗として扱う（サーバーが止まったときの逃げ道）
    const MAX_RUNNING_MS = 90000;

    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');

    // 403 のあとで、この画面を読み込み直したところか
    const alreadyReloaded = new URLSearchParams(window.location.search).has('reloaded');

    let slowTimer = null;
    let pollTimer = null;
    // 頼んでから、できる・作れないが決まるまで true（作っている最中の聞き直しのあいだも true のまま）
    let waiting = false;
    let clientFailures = 0;
    // 「作っている最中」の返事を最初に受けた時刻（聞き直しの上限を測る）
    let runningSince = null;

    const buildHeaders = () => {
        const headers = { Accept: 'application/json' };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader.content] = csrfToken.content;
        }
        return headers;
    };

    // いま出す案内を1つに切り替える（null なら案内を全部隠して「作っています」だけ）
    const show = (notice) => {
        [slow, failed, giveUp].forEach((element) => {
            element.hidden = element !== notice;
        });
        // 作れなかったときは「作っています」の枠を隠し、案内だけを出す
        status.hidden = notice === failed || notice === giveUp;
    };

    const clearTimers = () => {
        clearTimeout(slowTimer);
        clearTimeout(pollTimer);
        slowTimer = null;
        pollTimer = null;
    };

    const startSlowTimer = () => {
        clearTimeout(slowTimer);
        slowTimer = setTimeout(() => show(slow), SLOW_AFTER_MS);
    };

    const finishWithFailure = (isGiveUp) => {
        waiting = false;
        clearTimers();
        message.textContent = 'プランを作れませんでした';
        show(isGiveUp ? giveUp : failed);
    };

    const moveTo = (url, text) => {
        clearTimers();
        message.textContent = text;
        status.classList.add('is-stopped');
        window.location.replace(url);
    };

    // サーバーに1回問い合わせる
    const ask = async () => {
        pollTimer = null;
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
        let response;
        let result;
        try {
            response = await fetch(generateUrl, {
                method: 'POST',
                headers: buildHeaders(),
                credentials: 'same-origin',
                signal: controller.signal,
            });
            // ログインが切れていると、ログイン画面へ転送された HTML が返る。そのときはログイン画面へ移る
            if (response.redirected && response.url.includes('/auth/login')) {
                moveTo(response.url, 'ログインし直してください…');
                return;
            }
            // 403：ログインが切れて CSRF の値が合わなくなったときなど。画面を読み込み直すと、
            // ログインが切れていればログイン画面へ、そうでなければ新しい CSRF の値で、もう一度頼める
            // 読み込み直しても 403 のときは（?reloaded が付いている）、何度も読み込み直さないよう失敗として扱う
            if (response.status === 403 && !alreadyReloaded) {
                moveTo(window.location.pathname + '?reloaded=1', '読み込み直します…');
                return;
            }
            if (!response.ok) {
                throw new Error('status ' + response.status);
            }
            result = await response.json();
            if (result === null || typeof result !== 'object') {
                throw new Error('unexpected response');
            }
        } catch (error) {
            // 通信の失敗・打ち切り・想定外のエラー。サーバーは数えないので、この画面で数える
            clientFailures += 1;
            finishWithFailure(clientFailures >= MAX_CLIENT_FAILURES);
            return;
        } finally {
            clearTimeout(timeout);
        }
        clientFailures = 0;

        if (result.status === 'done') {
            moveTo(result.next, 'できました。マイプランへ進みます…');
            return;
        }
        if (result.status === 'redirect') {
            moveTo(result.next, '移動します…');
            return;
        }
        if (result.status === 'running') {
            // 前に頼んだ分がまだ作っている。少し待ってから、もう一度聞く（「時間がかかっています」の時計はそのまま）
            runningSince = runningSince ?? Date.now();
            if (Date.now() - runningSince > MAX_RUNNING_MS) {
                clientFailures += 1;
                finishWithFailure(clientFailures >= MAX_CLIENT_FAILURES);
                return;
            }
            pollTimer = setTimeout(ask, RUNNING_RETRY_MS);
            return;
        }
        finishWithFailure(result.giveUp === true);
    };

    // 生成を頼む（待っている最中なら何もしない）
    const request = () => {
        if (waiting) {
            return;
        }
        waiting = true;
        runningSince = null;
        message.textContent = 'プランを作っています…';
        show(null);
        clearTimers();
        startSlowTimer();
        ask();
    };

    // ［やり直す］：まだ待っているとき（時間がかかっているとき）は、サーバーは前の分を作り続けているので、
    // もう一度頼まずに待ち直す（頼んでも「作っている最中」が返るだけのため）。止まっているときは、もう一度頼む
    const retry = () => {
        if (waiting) {
            message.textContent = 'まだ作っています。できしだい、マイプランへ進みます…';
            show(null);
            startSlowTimer();
            return;
        }
        request();
    };

    retryButtons.forEach((button) => button.addEventListener('click', retry));

    request();
});
