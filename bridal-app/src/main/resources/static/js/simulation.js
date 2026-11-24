/**
 * リアルタイム料金シミュレーション（P-13）用スクリプト
 * オプションの選択状態や数量の変更を監視し、小計・合計予定金額・予算残額をリアルタイムに再計算する。
 */
document.addEventListener('DOMContentLoaded', () => {
    // 要素の取得
    const budgetElem = document.getElementById('summaryBudget');
    const totalElem = document.getElementById('summaryTotal');
    const remainingElem = document.getElementById('summaryRemaining');
    const overAlertElem = document.getElementById('overBudgetAlert');
    const overAmountElem = document.getElementById('overAmountNum');
    const cards = document.querySelectorAll('.simulation-card');

    if (!totalElem || !remainingElem) {
        return;
    }

    // 設定予算の取得（カンマを除去して数値化）
    const parseNumber = (text) => {
        if (!text) return 0;
        const cleaned = text.replace(/,/g, '').trim();
        const num = parseInt(cleaned, 10);
        return isNaN(num) ? 0 : num;
    };

    const formatNumber = (num) => {
        return Number(num).toLocaleString('ja-JP');
    };

    const budget = budgetElem ? parseNumber(budgetElem.textContent) : 3000000;

    // 再計算処理
    const recalculate = () => {
        let total = 0;

        cards.forEach((card) => {
            const basePrice = parseInt(card.dataset.basePrice, 10) || 0;
            const isVariable = card.dataset.variable === 'true';
            let quantity = 0;

            if (isVariable) {
                const qtyInput = card.querySelector('.simulation-input--qty');
                if (qtyInput) {
                    let val = parseInt(qtyInput.value, 10);
                    if (isNaN(val) || val < 0) {
                        val = 0;
                    } else if (val > 300) {
                        val = 300;
                    }
                    quantity = val;
                }
            } else {
                const checkInput = card.querySelector('.simulation-input--check');
                if (checkInput && checkInput.checked) {
                    quantity = 1;
                }
            }

            // カードごとの小計計算
            const subtotal = basePrice * quantity;
            total += subtotal;

            const subtotalElem = card.querySelector('.js-item-subtotal');
            if (subtotalElem) {
                subtotalElem.textContent = formatNumber(subtotal);
            }

            // 選択中のカードを強調
            if (quantity > 0) {
                card.classList.add('simulation-card--selected');
            } else {
                card.classList.remove('simulation-card--selected');
            }
        });

        // 合計予定金額と予算残額の更新
        totalElem.textContent = formatNumber(total);

        const remaining = budget - total;
        remainingElem.textContent = formatNumber(remaining);

        // 予算超過時の表示切り替え
        if (remaining < 0) {
            remainingElem.classList.add('simulation-remaining-num--negative');
            if (overAlertElem) {
                overAlertElem.classList.remove('d-none');
            }
            if (overAmountElem) {
                overAmountElem.textContent = formatNumber(Math.abs(remaining));
            }
        } else {
            remainingElem.classList.remove('simulation-remaining-num--negative');
            if (overAlertElem) {
                overAlertElem.classList.add('d-none');
            }
            if (overAmountElem) {
                overAmountElem.textContent = '0';
            }
        }
    };

    // 入力変更イベントの監視
    document.querySelectorAll('.js-calc-input').forEach((input) => {
        input.addEventListener('change', recalculate);
        input.addEventListener('input', recalculate);
    });

    // 数量増減ボタン（-10, -1, +1, +10）の操作
    document.querySelectorAll('.js-qty-btn').forEach((button) => {
        button.addEventListener('click', () => {
            const delta = parseInt(button.dataset.delta, 10) || 0;
            const container = button.closest('.simulation-qty-group');
            if (!container) return;

            const qtyInput = container.querySelector('.simulation-input--qty');
            if (!qtyInput) return;

            let currentVal = parseInt(qtyInput.value, 10);
            if (isNaN(currentVal)) currentVal = 0;

            let nextVal = currentVal + delta;
            if (nextVal < 0) nextVal = 0;
            if (nextVal > 300) nextVal = 300;

            qtyInput.value = nextVal;
            recalculate();
        });
    });

    // 初期実行（画面表示時）
    recalculate();
});
