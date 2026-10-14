// AI深掘り質問画面（P-10）：質問の生成をサーバーに頼み、できたら画面を読み込み直す
(function () {
    const loading = document.getElementById('deepDiveLoading');
    if (!loading) {
        return;
    }
    const waiting = document.getElementById('deepDiveWaiting');
    const retry = document.getElementById('deepDiveRetry');
    const giveUp = document.getElementById('deepDiveGiveUp');
    const retryButton = document.getElementById('deepDiveRetryButton');
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

    // 表示を「待っている」「やり直せる」「あきらめる」のどれか1つに切り替える
    function show(target) {
        [waiting, retry, giveUp].forEach(function (element) {
            element.classList.toggle('d-none', element !== target);
        });
    }

    async function generate() {
        show(waiting);
        try {
            const response = await fetch(loading.dataset.generateUrl, {
                method: 'POST',
                headers: { [csrfHeader]: csrfToken },
            });
            if (response.ok) {
                // 質問ができたので、質問と回答欄のある画面を表示し直す
                location.reload();
                return;
            }
            const result = await response.json().catch(function () { return {}; });
            // 3回続けて失敗したときは、やり直しではなくプランナーへの依頼を案内する
            show(result.giveUp ? giveUp : retry);
        } catch (error) {
            show(retry);
        }
    }

    retryButton.addEventListener('click', generate);
    generate();
})();
