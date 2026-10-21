#!/bin/sh
# bridal-app のテストを実行する命令（テスト役・実装役・終わるときのフックが共通で使う）。
# - 引数なし：テストを実行する。通ったときだけ、今の変更内容の指紋を .claude/.last-test-pass に書く。
#   失敗したときは、その印を消して、出力の最後 60 行を出し、終了コード 1 で終わる。
# - 引数 --fingerprint：テストは実行せず、今の変更内容の指紋だけを出す。
#   終わるときのフック（test-bridal-app.sh）が、同じ変更で通ったテストをもう一度実行しないために使う。
# どのフォルダから実行しても、リポジトリの一番上（CLAUDE_PROJECT_DIR、無ければ git の一番上）を基準に動く。

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
stamp="$root/.claude/.last-test-pass"
cd "$root/bridal-app" || exit 1

# 今の変更内容の指紋：bridal-app/src の未コミットの変更と、未追跡ファイルの中身から作る sha1
# （指紋の計算はこの関数だけに書く。フックもこの命令を呼んで指紋を得る）
fingerprint() {
  { git diff HEAD -- src; git ls-files -o --exclude-standard -z -- src | xargs -0 -r cat; } | sha1sum | cut -c1-40
}

if [ "$1" = "--fingerprint" ]; then
  fingerprint
  exit 0
fi

# テストの前に指紋を取る（テスト中に変わった分は、次の実行で改めて確かめられるように）
hash=$(fingerprint)
if out=$(sh ./mvnw -q test 2>&1); then
  echo "$hash" > "$stamp"
  exit 0
fi

# 失敗したときは印を消す。古い印を残すと、直した後も「通った」と誤って扱うおそれがあるため
rm -f "$stamp"
echo "bridal-app のテストが失敗しました。" >&2
printf '%s\n' "$out" | tail -n 60 >&2
exit 1
