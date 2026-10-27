#!/bin/sh
# Creepy Code の Git のルール（AGENTS.md の「5. Git のルール」）を確かめる共通の部品。
# pre-commit・pre-merge-commit・pre-push と、Claude Code・Codex のフック（ai-guard）から使う。

# 直接送信してはいけないブランチ（作業はすべて PR で取り込む）
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

# 直接コミットしてはいけないブランチ（develop へのコミットは許可する）
is_no_commit_branch() {
    case "$1" in
        main|master) return 0 ;;
    esac
    return 1
}

# 作業ブランチの名前：Gitflow の feature/・fix/・docs/ で始まること（後ろの文字は自由）
is_work_branch_name() {
    case "$1" in
        feature/?*|fix/?*|docs/?*) return 0 ;;
    esac
    return 1
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
    if is_no_commit_branch "$branch"; then
        reject "いまのブランチは「$branch」です。main には直接コミットできません。" \
               "develop か、develop から作った作業ブランチ（feature/〇〇・fix/〇〇・docs/〇〇）で作業してください。"
    fi
    return 0
}
