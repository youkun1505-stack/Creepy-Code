# Codex（Windows）のフックから呼ばれる。ai-guard.sh と同じ確認を PowerShell で行う。
# main・develop・release・hotfix の上では、AI にファイルを変更させない（終了コード 2 で止める）。
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$branch = git symbolic-ref --short -q HEAD 2>$null
if (-not $branch) { exit 0 }
if ($branch -match '^(main|master|develop|release/.+|hotfix/.+)$') {
    [Console]::Error.WriteLine("いまのブランチは「$branch」です。このブランチの上ではファイルを変更しない決まりです（AGENTS.md の 5）。develop から作業ブランチ（feature/〇〇・fix/〇〇・docs/〇〇）を作ってから変更してください。")
    exit 2
}
exit 0
