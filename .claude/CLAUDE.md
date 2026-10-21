# Creepy Code — Claude Code 用の指示

> このファイルは `.claude/CLAUDE.md`（Claude Code が読む場所）。共通ルールは、リポジトリ直下の `AGENTS.md` を下の1行で読み込んでいる。

@../AGENTS.md

---

## Claude Code での役割の分け方

> サブエージェント（調査役・設計役・実装役・テスト役・レビュー役など）として呼ばれた場合は、`moderator` スキルは読み込まない。
> 自分の定義ファイル（`.claude/agents/`）に書かれた役割だけを行う。

メインの会話が **司会役** になる。専門の作業は、次のサブエージェントに渡す。
サブエージェントはそれぞれ新しい記憶で動く（会話の流れを引き継がない）ので、実装の途中の考えがレビューに混ざらない。
その代わり、呼ぶときに必要な情報を AGENTS.md の「受け渡しメモ」にまとめて渡す。

| サブエージェント | 定義ファイル | モデル／推論の強さ | 使える道具 | 呼んでよい役 |
| --- | --- | --- | --- | --- |
| （司会役＝メインの会話） | `.claude/settings.json` の `model`・`effortLevel` | claude-opus-5-5／medium | すべて（権限の設定に従う） | すべて |
| `explorer`（調査役） | `.claude/agents/explorer.md` | claude-haiku-5-5／low | 読む・調べる（変更しない） | なし |
| `architect`（設計役） | `.claude/agents/architect.md` | claude-sonnet-5-5／high | 読む（変更しない） | explorer |
| `implementer`（実装役） | `.claude/agents/implementer.md` | claude-haiku-5-5／medium | 読む・書く・決めたテストの実行 | explorer |
| `tester`（テスト役） | `.claude/agents/tester.md` | claude-haiku-5-5／low | 読む・決めたテストの実行（変更しない） | なし |
| `reviewer`（レビュー役） | `.claude/agents/reviewer.md` | claude-sonnet-5-5／high | 読む・`git diff` などの確認（変更しない） | explorer |
| `github-manager`（GitHub 係） | `.claude/agents/github-manager.md` | claude-haiku-5-5／low | 読む・`git`／`gh` の読むだけの操作 | なし |
| `memory-manager`（記録係） | `.claude/agents/memory-manager.md` | claude-haiku-5-5／medium | 読む・`.agent-memory/` だけ書く | なし |
| `context-manager`（文脈係） | `.claude/agents/context-manager.md` | claude-haiku-5-5／medium | 読む（変更しない） | explorer |
| `harness-optimizer`（ハーネス点検） | `.claude/agents/harness-optimizer.md` | claude-haiku-5-5／high | 読む（変更しない） | なし |

- モデルは、司会役が Opus 5.5、設計役・レビュー役が Sonnet 5.5、ほかの役が Haiku 5.5。定義ファイルと `settings.json` には正式な名前（`claude-opus-5-5`・`claude-sonnet-5-5`・`claude-haiku-5-5`）で書き、
  新しいモデルが出ても勝手に変わらないようにしている。略した名前（`opus`・`sonnet`・`haiku`）も、`settings.json` の `env`
  （`ANTHROPIC_DEFAULT_OPUS_MODEL` など）で 5.5 に固定している（公式の説明ではそうなる。実際に効くかは、ハーネス検証記録で未確認）。モデルを新しくするときは、この3か所をリーダーがそろえて書き換える。
  推論の強さは、決まった手順の短い作業が `low`、ふつうの作業が `medium`、長い作業・厳しく守らせたい作業が `high`。
- モデルを変えたいときは、定義ファイルの `model:`・`effort:`、司会役は `.claude/settings.json` の `model`・`effortLevel` を書き換える（リーダーが行う）。
  自分だけ変えたいときは `.claude/settings.local.json` に書く。
- 同時に動くサブエージェントは5つまで（役の中から呼んだ役も数に入ると考えて数える）、呼び出しの深さは2段（司会役 → 役 → 調査役など）まで（`.claude/settings.json` の `env`）。
  上限（同時に5つ）に当たって呼べなかったときは、止まらずに、ほかの役が終わるのを待って1つずつ呼ぶ。
- 「呼んでよい役」は指示で守る決まり。Claude Code の仕組みでは、役の中から呼ぶ相手の種類までは制限できない（`Agent(役の名前)` の書き方は役の中では効かない）。
- 役の中から別の役を呼ぶときは、格上げしない。格上げは司会役だけが行う。
  役の中から呼んだ役が `止まった` を返したら、呼んだ役はそれを自分の報告にそのまま書いて司会役に返す（格上げするかは司会役が決める）。
- 会話をまるごと引き継ぐ呼び出し（`fork`、`/subtask`）は使わない。実装役とレビュー役の記憶を分けるため（`.claude/settings.json` の `deny` に `Agent(fork)` を入れて止めている）。

## 司会役のスキル

司会役（メインの会話）は、作業を始める前に `moderator` スキル（`.claude/skills/moderator/SKILL.md`）を読み込む。役を呼ぶ決まり・格上げ・1機能ごとの繰り返し・コミット前のブランチ確認などは、そこにある。サブエージェントとして呼ばれた場合は読み込まない。

## 守りの仕組みについて

| 仕組み | 場所 | 止めるもの |
| --- | --- | --- |
| Git のフック | `.githooks/` | `main` へのコミット、`main`・`develop` への送信、feature/・fix/・docs/ で始まらない作業ブランチの送信、強制送信、GitHub のブランチ削除 |
| Claude Code のフック | `.claude/settings.json` の `hooks` | 開いたときに Git のフックを有効にする。`main`・`develop`・`release/*`・`hotfix/*` の上でのファイル変更。返事を終える前に、`bridal-app/src` に変更があればテストを実行し、失敗したら差し戻す（`.claude/hooks/test-bridal-app.sh`）。同じ変更で通った印（`.claude/.last-test-pass`）があれば、もう一度は実行しない |
| 役の道具の制限 | `.claude/agents/*.md` の `tools`・`disallowedTools` | 変更しない役の Edit・Write。ほかの役を呼ぶ必要のない役（調査役・テスト役など）の Agent。Bash での書き込み、記録係が書く場所、呼んでよい役の種類は、指示で守る決まり（仕組みでは止まらない） |
| 許可の設定 | `.claude/settings.json` の `permissions` | 付け替え・`reset --hard`・`--no-verify`・PR の取り込みなどのコマンド、会話を引き継ぐ呼び出し（`Agent(fork)`）。送信・PR 作成・`develop` の取り込みは毎回メンバーに確認 |

フックや設定で止められたら、理由をメンバーに伝え、AGENTS.md の Git のルールに沿って直す。止められなかった場合でも、ルールは必ず守る。
