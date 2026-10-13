#!/bin/sh
# Claude Code のフックから呼ばれる。main・develop・release・hotfix の上では、AI にファイルを変更させない。
# 終了コード 2 で、Claude Code はその操作を止める。
dir="${CLAUDE_PROJECT_DIR:-.}"
. "$(dirname "$0")/_rules.sh"
branch=$(git -C "$dir" symbolic-ref --short -q HEAD 2>/dev/null)
[ -z "$branch" ] && exit 0
if is_protected_branch "$branch" || is_release_branch "$branch"; then
    echo "いまのブランチは「$branch」です。このブランチの上ではファイルを変更しない決まりです（AGENTS.md の 5）。develop から作業ブランチ（feature/〇〇・fix/〇〇・docs/〇〇）を作ってから変更してください。" >&2
    exit 2
fi
exit 0
