#!/bin/sh
# Creepy Code の Git のルール（AGENTS.md の「5. Git のルール」）を確かめる共通の部品。
# pre-commit・pre-merge-commit・commit-msg・pre-push と、Claude Code のフックから使う。

# 人が直接触ってはいけないブランチ（作業はすべて PR で取り込む）
is_protected_branch() {
    case "$1" in
        main|master|develop) return 0 ;;
    esac
    return 1
}

# Gitflow で人が作るブランチ（AI は作業しない）
is_release_branch() {
    case "$1" in
        release/*|hotfix/*) return 0 ;;
    esac
    return 1
}

# 作業ブランチの名前：feature/・fix/・docs/ の後ろは英小文字・数字のハイフン区切り
is_work_branch_name() {
    printf '%s\n' "$1" | grep -Eq '^(feature|fix|docs)/[a-z0-9]+(-[a-z0-9]+)*$'
}

current_branch() {
    git symbolic-ref --short -q HEAD 2>/dev/null
}

reject() {
    {
        echo ""
        echo "=================================================================="
        echo " Creepy Code の Git のルールにより止めました"
        echo "------------------------------------------------------------------"
        for line in "$@"; do echo " $line"; done
        echo "------------------------------------------------------------------"
        echo " 詳しくは AGENTS.md の「5. Git のルール」を見てください。"
        echo "=================================================================="
        echo ""
    } >&2
    exit 1
}

check_branch_for_commit() {
    branch=$(current_branch)
    # ブランチの外（作業用コピーなど）でのコミットは対象外
    [ -z "$branch" ] && return 0
    if is_protected_branch "$branch"; then
        reject "いまのブランチは「$branch」です。main・develop には直接コミットできません。" \
               "develop から作業ブランチ（feature/〇〇・fix/〇〇・docs/〇〇）を作ってから作業してください。"
    fi
    is_release_branch "$branch" && return 0
    if ! is_work_branch_name "$branch"; then
        reject "ブランチ名「$branch」はチームの決まりに合いません。" \
               "feature/〇〇・fix/〇〇・docs/〇〇 の形にしてください（英小文字・数字・ハイフン。例：feature/12-plan-view）。"
    fi
    return 0
}
