// AIに相談する画面（P-10）：発言（と任意で添付ファイル1件）をサーバーに送り、返事を吹き出しに足す（画面は読み込み直さない）
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
    const fileInput = document.getElementById('chatFile');
    const fileName = document.getElementById('chatFileName');
    const fileClear = document.getElementById('chatFileClear');
    // 履歴が無いときの案内。最初の発言が入ったら取り除く
    let guide = document.getElementById('chatGuide');
    // CSRF トークンは、Spring Security が有効なときだけ画面に入っている
    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    const sendUrl = form.dataset.sendUrl;
    const GENERIC_ERROR = '通信がうまくいかなかったか、サーバーで問題が起きました。もう一度お試しください。';
    const GIVE_UP_ERROR = 'AI の返事を作れませんでした。お手数ですが、プランナーにプランの作成をご依頼ください。';
    // 添付の上限と種類（サーバーでも同じ確認をする。ここは送る前に案内を出すためのもの）
    const MAX_FILE_BYTES = 10 * 1024 * 1024;
    const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'application/pdf'];
    const FILE_TYPE_ERROR = 'ファイルは PNG・JPEG・PDF のどれかを選んでください。';
    const FILE_SIZE_ERROR = 'ファイルは10MB以下にしてください。';
    const FILE_EMPTY_ERROR = '中身が空のファイルは送れません。別のファイルを選んでください。';

    let busy = false;
    // やり直しのときに同じ発言・添付をもう一度送るため、直前に送った内容を覚えておく
    let pendingContent = '';
    let pendingFile = null;

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

    // 選ばれているファイル（無いときは null）
    function selectedFile() {
        return fileInput.files && fileInput.files.length > 0 ? fileInput.files[0] : null;
    }

    // 送る前の確認。問題があれば案内の文を返し、問題が無ければ空の文字を返す
    function checkFile(file) {
        if (!file) {
            return '';
        }
        if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
            return FILE_TYPE_ERROR;
        }
        if (file.size === 0) {
            return FILE_EMPTY_ERROR;
        }
        if (file.size > MAX_FILE_BYTES) {
            return FILE_SIZE_ERROR;
        }
        return '';
    }

    // 選んだファイル名と、取り消しのボタンの表示を、選ばれている状態に合わせる
    function updateFileView() {
        const file = selectedFile();
        fileName.textContent = file ? file.name : '選ばれていません';
        fileClear.classList.toggle('d-none', !file);
    }

    // 添付を空にする。古い失敗の「やり直す」は消し、覚えておいた添付も外す（外したファイルが後から送られないように）
    function clearFile() {
        fileInput.value = '';
        pendingFile = null;
        hideError();
        updateFileView();
    }

    // asJson が true のときだけ JSON の Content-Type を付ける（FormData のときはブラウザに任せる）
    function buildHeaders(asJson) {
        const headers = {};
        if (asJson) {
            headers['Content-Type'] = 'application/json';
        }
        if (csrfToken && csrfHeader) {
            headers[csrfHeader.content] = csrfToken.content;
        }
        return headers;
    }

    // 添付があるときの送信内容（content と file の2つ）
    function buildFormData(content, file) {
        const data = new FormData();
        data.append('content', content);
        data.append('file', file, file.name);
        return data;
    }

    async function send(content, file) {
        if (busy) {
            return;
        }
        busy = true;
        pendingContent = content;
        pendingFile = file;
        submitButton.disabled = true;
        hideError();
        setPending(true);
        try {
            const response = await fetch(sendUrl, {
                method: 'POST',
                headers: buildHeaders(!file),
                body: file ? buildFormData(content, file) : JSON.stringify({ content: content }),
            });
            // 壊れた応答（JSON として読めない）のときは null になり、下の汎用の文を出す
            const result = await response.json().catch(function () { return null; });
            if (response.ok && result && result.ok === true && typeof result.reply === 'string') {
                // 履歴と同じ形で、添付があれば「（添付：ファイル名）」を発言の後ろに付けて出す
                const shown = file ? content + '\n（添付：' + file.name + '）' : content;
                addBubble('USER', shown);
                addBubble('AI', result.reply);
                hideGuide();
                // 返事を待つあいだに利用者が書き換えていたら、その文は消さない
                if (input.value === content) {
                    input.value = '';
                    updateCount();
                }
                // 送ったファイルは空にする（返事を待つあいだに選び直されていたら、その選択は消さない）
                if (file && selectedFile() === file) {
                    clearFile();
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
        const file = selectedFile();
        const problem = checkFile(file);
        if (problem) {
            // 種類や大きさが合わないファイルは送らず、案内を出す
            showError(problem, false);
            return;
        }
        send(content, file);
    });

    retryButton.addEventListener('click', function () {
        send(pendingContent, pendingFile);
    });

    // ファイルを選んだとき：送れない種類・大きさなら案内を出し、欄から外す（送らせない）
    fileInput.addEventListener('change', function () {
        const problem = checkFile(selectedFile());
        if (problem) {
            clearFile();
            showError(problem, false);
            return;
        }
        // 別のファイルを選び直したときも、古い失敗の「やり直す」は消す（覚えていた添付は外す）
        pendingFile = null;
        hideError();
        updateFileView();
    });

    fileClear.addEventListener('click', function () {
        clearFile();
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
    updateFileView();
    scrollToLatest();
})();
