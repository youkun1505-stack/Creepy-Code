#!/bin/sh
# Stop フック：Claude が返事を終える前に、bridal-app のテストを実行する。
# - bridal-app/src に変更（未コミット）が無いときは何もしない
# - 前回テストが通ったときから変更が増えていなければ、もう一度は実行しない
# - テストが失敗したら終了コード 2 を返し、Claude に差し戻して直させる

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

# 今の変更内容の指紋。前回テストが通ったときと同じならスキップする
hash=$( { git diff HEAD -- src; git ls-files -o --exclude-standard -z -- src | xargs -0 -r cat; } | sha1sum | cut -c1-40)
stamp="$root/.claude/.last-test-pass"
if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$hash" ]; then
  exit 0
fi

if out=$(sh ./mvnw -q test 2>&1); then
  echo "$hash" > "$stamp"
  exit 0
fi

echo "bridal-app のテストが失敗しました。原因を直してから作業を終えてください。" >&2
printf '%s\n' "$out" | tail -n 60 >&2
exit 2
