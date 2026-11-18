# Creepy Code — Claude Code 用の指示

> このファイルは `.claude/CLAUDE.md`（Claude Code が読む場所）。共通ルールは、リポジトリ直下の `AGENTS.md` を下の1行で読み込んでいる。

@../AGENTS.md

---

- メインの会話（司会役）は、作業を始める前に `moderator` スキル（`.claude/skills/moderator/SKILL.md`）を読み込む。役を呼ぶ決まり・格上げ・1機能ごとの繰り返し・コミット前のブランチ確認などは、そこにある。
- サブエージェントとして呼ばれた場合は、`moderator` スキルは読み込まない。自分の定義ファイル（`.claude/agents/`）に書かれた役割だけを行う。
