#!/bin/sh
# Stop フック：Claude が返事を終える前に、bridal-app のテストを実行する。
# - bridal-app/src に変更（未コミット）が無いときは何もしない
# - 前回テストが通ったときから変更が増えていなければ、もう一度は実行しない
# - テストが失敗したら終了コード 2 を返し、Claude に差し戻して直させる
# テストの実行と「通った」印の管理は、run-bridal-app-test.sh と共通にしている。

input=$(cat)

# 差し戻し後に Claude がもう一度終わろうとしたときは止めない（無限ループ防止）
if printf '%s' "$input" | grep -q '"stop_hook_active" *: *true'; then
  exit 0
fi

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
cd "$root/bridal-app" || exit 0

if [ -z "$(git status --porcelain -- src)" ]; then
  exit 0
fi

# 今の変更内容の指紋を、共通の命令から得る。前回テストが通ったときと同じならスキップする
run="$root/.claude/hooks/run-bridal-app-test.sh"
hash=$(sh "$run" --fingerprint)
stamp="$root/.claude/.last-test-pass"
if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$hash" ]; then
  exit 0
fi

# 通ったときは、共通の命令が印を書く。失敗したときは、印を消して差し戻す
if out=$(sh "$run" 2>&1); then
  exit 0
fi

printf '%s\n' "$out" >&2
echo "原因を直してから作業を終えてください。" >&2
exit 2
