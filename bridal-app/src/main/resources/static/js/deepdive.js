// AIに相談する画面（P-10）：発言をサーバーに送り、返事を吹き出しに足す（画面は読み込み直さない）
(function () {
    const form = document.getElementById('chatForm');
    if (!form) {
        return;
    }
    const log = document.getElementById('chatLog');
    const input = document.getElementById('chatInput');
    const countNow = document.getElementById('chatCountNow');
    const submitButton = document.getElementById('chatSubmit');
    const pending = document.getElementById('chatPending');
    const errorBox = document.getElementById('chatError');
    const errorMessage = document.getElementById('chatErrorMessage');
    const retryButton = document.getElementById('chatRetry');
    // 履歴が無いときの案内。最初の発言が入ったら取り除く
    let guide = document.getElementById('chatGuide');
    // CSRF トークンは、Spring Security が有効なときだけ画面に入っている
    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    const sendUrl = form.dataset.sendUrl;
    const GENERIC_ERROR = '通信がうまくいかなかったか、サーバーで問題が起きました。もう一度お試しください。';
    const GIVE_UP_ERROR = 'AI の返事を作れませんでした。お手数ですが、プランナーにプランの作成をご依頼ください。';

    let busy = false;
    // やり直しのときに同じ発言をもう一度送るため、直前に送った発言を覚えておく
    let pendingContent = '';

    // 発言を1件、吹き出しとして一覧の下に足す。本文は textContent で入れる（HTML として解釈しない）
    function addBubble(role, text) {
        const item = document.createElement('li');
        item.className = 'deepdive-message ' + (role === 'USER' ? 'deepdive-message--user' : 'deepdive-message--ai');
        const bubble = document.createElement('div');
        bubble.className = 'deepdive-message__bubble';
        const speaker = document.createElement('p');
        speaker.className = 'deepdive-message__speaker';
        speaker.textContent = role === 'USER' ? 'あなた' : 'AI';
        const body = document.createElement('p');
        body.className = 'deepdive-message__text';
        body.textContent = text;
        bubble.append(speaker, body);
        item.append(bubble);
        log.append(item);
    }

    // 一覧の一番下（新しい発言）が見えるように、一覧をスクロールする
    function scrollToLatest() {
        log.scrollTop = log.scrollHeight;
    }

    function hideGuide() {
        if (guide) {
            guide.remove();
            guide = null;
        }
    }

    function setPending(on) {
        pending.classList.toggle('d-none', !on);
    }

    function hideError() {
        errorBox.classList.add('d-none');
    }

    // 失敗の表示。showRetry が true のときだけ「やり直す」を出す
    function showError(message, showRetry) {
        errorMessage.textContent = message;
        retryButton.classList.toggle('d-none', !showRetry);
        errorBox.classList.remove('d-none');
    }

    function updateCount() {
        countNow.textContent = String(input.value.length);
    }

    function buildHeaders() {
        const headers = { 'Content-Type': 'application/json' };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader.content] = csrfToken.content;
        }
        return headers;
    }

    async function send(content) {
        if (busy) {
            return;
        }
        busy = true;
        pendingContent = content;
        submitButton.disabled = true;
        hideError();
        setPending(true);
        try {
            const response = await fetch(sendUrl, {
                method: 'POST',
                headers: buildHeaders(),
                body: JSON.stringify({ content: content }),
            });
            // 壊れた応答（JSON として読めない）のときは null になり、下の汎用の文を出す
            const result = await response.json().catch(function () { return null; });
            if (response.ok && result && result.ok === true && typeof result.reply === 'string') {
                addBubble('USER', content);
                addBubble('AI', result.reply);
                hideGuide();
                // 返事を待つあいだに利用者が書き換えていたら、その文は消さない
                if (input.value === content) {
                    input.value = '';
                    updateCount();
                }
                scrollToLatest();
            } else if (response.status === 502 && result && result.giveUp === true) {
                // 何度も失敗したときは、やり直しではなくプランナーへの依頼を案内する
                showError(GIVE_UP_ERROR, false);
            } else if ((response.status === 400 || response.status === 502)
                    && result && typeof result.message === 'string' && result.message) {
                showError(result.message, true);
            } else {
                showError(GENERIC_ERROR, true);
            }
        } catch (error) {
            showError(GENERIC_ERROR, true);
        } finally {
            busy = false;
            submitButton.disabled = false;
            setPending(false);
        }
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        const content = input.value;
        if (!content.trim()) {
            showError('発言を入力してください。', false);
            return;
        }
        send(content);
    });

    retryButton.addEventListener('click', function () {
        send(pendingContent);
    });

    // 入力欄で Ctrl + Enter（Mac は Command + Enter）を押したら送信する
    input.addEventListener('keydown', function (event) {
        // 日本語入力（IME）の変換確定中の Enter は送信しない
        if (event.isComposing || event.keyCode === 229) {
            return;
        }
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
            event.preventDefault();
            if (busy || submitButton.disabled) {
                return;
            }
            if (typeof form.requestSubmit === 'function') {
                form.requestSubmit(submitButton);
            } else {
                form.dispatchEvent(new Event('submit', { cancelable: true }));
            }
        }
    });

    input.addEventListener('input', updateCount);
    updateCount();
    scrollToLatest();
})();
