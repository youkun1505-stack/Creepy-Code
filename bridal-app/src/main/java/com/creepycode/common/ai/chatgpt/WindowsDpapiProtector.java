package com.creepycode.common.ai.chatgpt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Windows の DPAPI（ログインしている Windows ユーザーの鍵で暗号化する機能）を、PowerShell 経由で使う。
 * 追加のライブラリは入れず、Windows に最初から入っている PowerShell を呼ぶ（以前の Node.js 版と同じ方式）。
 * データは標準入力で渡す（コマンドの引数に書くと、ほかのプログラムから見えてしまうため）。
 */
public class WindowsDpapiProtector implements CredentialProtector {

    private static final String FAILURE_MESSAGE = "Windows の暗号化（DPAPI）に失敗しました。";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    // PowerShell をフルパスで起動する（PATH の別の実行ファイルを呼ばないため）
    static final Path POWERSHELL = Path.of(
            System.getenv("SystemRoot") != null ? System.getenv("SystemRoot") : "C:\\Windows",
            "System32", "WindowsPowerShell", "v1.0", "powershell.exe");

    /** PowerShell を起動する方法。本番は既定の PowerShell を使い、テストだけ差し替える。 */
    @FunctionalInterface
    interface ProcessStarter {
        Process start(String method) throws IOException;
    }

    private final ProcessStarter starter;
    private final long timeoutSeconds;

    public WindowsDpapiProtector() {
        this(WindowsDpapiProtector::startPowerShell, DEFAULT_TIMEOUT_SECONDS);
    }

    /** テスト用。起動方法と時間切れの秒数を差し替える（パッケージの中だけで使う）。 */
    WindowsDpapiProtector(ProcessStarter starter, long timeoutSeconds) {
        this.starter = starter;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public byte[] protect(byte[] plain) {
        return run("Protect", plain);
    }

    @Override
    public byte[] unprotect(byte[] encrypted) {
        return run("Unprotect", encrypted);
    }

    /** method は ProtectedData の Protect か Unprotect。データは Base64 の文字で標準入力から渡す。 */
    private byte[] run(String method, byte[] input) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            throw new IllegalStateException("DPAPI は Windows でだけ使えます。");
        }
        Process process;
        try {
            process = starter.start(method);
        } catch (IOException e) {
            throw new IllegalStateException("PowerShell を起動できませんでした。", e);
        }
        byte[] encoded = Base64.getEncoder().encode(input);
        // 書き込みと読み取りを別スレッドで行い、全体を時間で区切る（固まっても待ち続けないため）
        FutureTask<Result> exchange = new FutureTask<>(() -> exchange(process, encoded));
        Thread io = new Thread(exchange, "creepy-dpapi-io");
        io.setDaemon(true);
        try {
            io.start();
            Result result = exchange.get(timeoutSeconds, TimeUnit.SECONDS);
            if (result.exitCode() != 0) {
                throw new IllegalStateException(FAILURE_MESSAGE + "（終了コード " + result.exitCode() + "）");
            }
            return Base64.getDecoder().decode(new String(result.output(), StandardCharsets.US_ASCII).trim());
        } catch (TimeoutException e) {
            throw new IllegalStateException(FAILURE_MESSAGE + "（時間切れ）");
        } catch (ExecutionException e) {
            throw new IllegalStateException(FAILURE_MESSAGE, e.getCause());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(FAILURE_MESSAGE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(FAILURE_MESSAGE + "（中断）", e);
        } finally {
            // どの経路でも、まだ動いている PowerShell は止める
            exchange.cancel(true);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** 標準入力にデータを書き、標準出力を最後まで読み、終了を待つ。時間切れは呼び出し側で区切る。 */
    private static Result exchange(Process process, byte[] encoded) throws IOException, InterruptedException {
        try (var stdin = process.getOutputStream()) {
            stdin.write(encoded);
        }
        byte[] output = process.getInputStream().readAllBytes();
        return new Result(process.waitFor(), output);
    }

    private static Process startPowerShell(String method) throws IOException {
        return new ProcessBuilder(POWERSHELL.toString(), "-NoProfile", "-NonInteractive", "-Command", script(method))
                // エラーの出力には中身が含まれることがあるので、読まずに捨てる
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    /** 標準入力の Base64 を読み、CurrentUser の範囲で暗号化・復号して Base64 で返す PowerShell の命令。 */
    private static String script(String method) {
        return "$ErrorActionPreference='Stop';"
                + "Add-Type -AssemblyName System.Security;"
                + "$bytes=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());"
                + "$scope=[System.Security.Cryptography.DataProtectionScope]::CurrentUser;"
                + "$result=[System.Security.Cryptography.ProtectedData]::" + method + "($bytes,$null,$scope);"
                + "[Console]::Out.Write([Convert]::ToBase64String($result))";
    }

    private record Result(int exitCode, byte[] output) {
    }
}
