// 会員登録（P-02）・ログイン（P-03）：パスワード欄の「表示／隠す」を切り替える
// ボタンの data-toggle-password に、切り替える入力欄の id を書いておく
document.querySelectorAll('[data-toggle-password]').forEach((button) => {
    const input = document.getElementById(button.dataset.togglePassword);
    if (!input) {
        return;
    }
    // 読み上げ用の名前（例：「パスワードを表示」）の、「表示／隠す」より前の部分
    const target = button.getAttribute('aria-label').replace(/を表示$/, '');

    button.addEventListener('click', () => {
        const willShow = input.type === 'password';
        input.type = willShow ? 'text' : 'password';
        button.textContent = willShow ? '隠す' : '表示';
        button.setAttribute('aria-label', target + (willShow ? 'を隠す' : 'を表示'));
    });
});
