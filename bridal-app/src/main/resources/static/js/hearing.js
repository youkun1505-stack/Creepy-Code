// かんたん質問（STEP1〜4）：選ぶ欄（name="choice"）を1つ選ぶまで、［次へ］を押せないようにする
// フォームに data-require-choice、［次へ］のボタンに data-next-button を付けておく
document.querySelectorAll('[data-require-choice]').forEach((form) => {
    const nextButton = form.querySelector('[data-next-button]');
    if (!nextButton) {
        return;
    }

    const update = () => {
        nextButton.disabled = !form.querySelector('input[name="choice"]:checked');
    };

    form.addEventListener('change', update);
    // 戻るボタンで戻ってきたときなど、ブラウザが選択を覚えている場合にも合わせる
    update();
});

// かんたん質問 STEP2：2段階で好みを聞く
//   1回目：テイストごとに1枚を、1枚ずつ［好き］［ちがう］（キーボードの ← →）で選ぶ
//   2回目：1回目で「好き」のテイストだけ、2枚を並べて「どちらが近い？」（画像を押すか ← →、または［どちらも好き］［どちらもちがう］）
// JS が動かないときは、画面は「チェックボックス付きの一覧」のままで、［選び終わった］で送れる
// フォームに data-visual-swipe、1回目の1枚ずつに data-visual-item、2回目の組に data-visual-pair（どちらも data-taste 付き）を付けておく
document.querySelectorAll('[data-visual-swipe]').forEach((form) => {
    const items = Array.from(form.querySelectorAll('[data-visual-item]'));
    const pairs = Array.from(form.querySelectorAll('[data-visual-pair]'));
    const round1Controls = form.querySelector('[data-visual-round1-controls]');
    const likeButton = form.querySelector('[data-visual-like]');
    const skipButton = form.querySelector('[data-visual-skip]');
    const backButton = form.querySelector('[data-visual-back]');
    const counter = form.querySelector('[data-visual-counter]');
    const submitButton = form.querySelector('[data-visual-submit]');
    if (items.length === 0 || !round1Controls || !likeButton || !skipButton || !backButton || !counter) {
        return;
    }

    // 出す順の一覧。1回目の全部のあとに、「好き」を選んだテイストの組が続く（1回目の答えが変わるたびに作り直す）
    let steps = [];
    let index = 0;
    // 最後の画面で選んで送ったあとは、もう受け付けない（二重に送らないため）
    let submitted = false;

    const likedInput = (element) => element.querySelector('input[name="liked"]');
    const sideInput = (pair, side) => pair.querySelector(`input[data-pair-side="${side}"]`);

    const rebuildSteps = () => {
        const likedTastes = items.filter((item) => likedInput(item).checked).map((item) => item.dataset.taste);
        steps = items.map((element) => ({ type: 'item', element }));
        pairs.forEach((pair) => {
            if (likedTastes.includes(pair.dataset.taste)) {
                steps.push({ type: 'pair', element: pair });
            } else {
                // 1回目で「ちがう」に変えたテイストの、2回目の答えは送らない
                sideInput(pair, 'left').checked = false;
                sideInput(pair, 'right').checked = false;
            }
        });
    };

    const show = () => {
        items.concat(pairs).forEach((element) => {
            element.hidden = true;
        });
        const step = steps[index];
        step.element.hidden = false;
        round1Controls.hidden = step.type !== 'item';
        if (step.type === 'item') {
            counter.textContent = `${index + 1} / ${items.length}枚`;
        } else {
            const pairSteps = steps.filter((s) => s.type === 'pair');
            counter.textContent = `掘り下げ ${pairSteps.indexOf(step) + 1} / ${pairSteps.length}`;
        }
        backButton.disabled = index === 0;
    };

    const goNext = () => {
        if (index === steps.length - 1) {
            submitted = true;
            form.submit();
            return;
        }
        index += 1;
        show();
    };

    // 1回目：好き／ちがう
    const chooseItem = (liked) => {
        if (submitted || steps[index].type !== 'item') {
            return;
        }
        likedInput(steps[index].element).checked = liked;
        rebuildSteps();
        goNext();
    };

    // 2回目：left／right／both／none
    const pickPair = (pick) => {
        if (submitted || steps[index].type !== 'pair') {
            return;
        }
        const pair = steps[index].element;
        sideInput(pair, 'left').checked = pick === 'left' || pick === 'both';
        sideInput(pair, 'right').checked = pick === 'right' || pick === 'both';
        goNext();
    };

    // 1回目・2回目の操作のボタンを出し、［選び終わった］を隠す
    form.classList.add('is-swipe');
    form.querySelectorAll('[data-visual-controls]').forEach((element) => {
        element.hidden = false;
    });
    if (submitButton) {
        submitButton.hidden = true;
    }

    likeButton.addEventListener('click', () => chooseItem(true));
    skipButton.addEventListener('click', () => chooseItem(false));
    pairs.forEach((pair) => {
        pair.querySelectorAll('[data-pair-pick]').forEach((button) => {
            button.addEventListener('click', () => pickPair(button.dataset.pairPick));
        });
        // 2回目は、画像を押すとその画像を選んだことにする（チェックボックスの切り替えは止める）
        pair.querySelectorAll('[data-pair-option]').forEach((option) => {
            option.addEventListener('click', (event) => {
                event.preventDefault();
                pickPair(option.dataset.pairOption);
            });
        });
    });
    backButton.addEventListener('click', () => {
        if (!submitted && index > 0) {
            index -= 1;
            show();
        }
    });
    // ← → で選ぶ（1回目は ← ＝ ちがう・→ ＝ 好き、2回目は ← ＝ 左・→ ＝ 右）。
    // 押しっぱなし（続けて何枚も進んでしまう）と、Alt＋← などブラウザの操作（Windows の「戻る」）は受け付けない
    document.addEventListener('keydown', (event) => {
        if (event.repeat || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
            return;
        }
        if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
            return;
        }
        event.preventDefault();
        const isLeft = event.key === 'ArrowLeft';
        if (steps[index].type === 'item') {
            chooseItem(!isLeft);
        } else {
            pickPair(isLeft ? 'left' : 'right');
        }
    });

    // ブラウザの「戻る」で、送ったあとのページがそのまま復元されたときは、最初からやり直せるようにする
    window.addEventListener('pageshow', (event) => {
        if (event.persisted) {
            submitted = false;
            index = 0;
            rebuildSteps();
            show();
        }
    });

    rebuildSteps();
    show();
});
