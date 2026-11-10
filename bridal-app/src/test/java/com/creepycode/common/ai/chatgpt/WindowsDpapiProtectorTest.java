package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * 本物の Windows の DPAPI（PowerShell 経由）の暗号化・復号のテスト。
 * Windows 専用の機能なので、Windows 以外では飛ばす（期待値を緩めるためではない）。
 */
@EnabledOnOs(OS.WINDOWS)
class WindowsDpapiProtectorTest {

    @Test
    void 暗号化して復号すると元に戻り_暗号文に平文が入らない() {
        WindowsDpapiProtector protector = new WindowsDpapiProtector();
        byte[] plain = "ACCESS-TOKEN-テスト-XYZ".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = protector.protect(plain);

        assertThat(encrypted).isNotEmpty();
        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("ACCESS-TOKEN");
        assertThat(protector.unprotect(encrypted)).isEqualTo(plain);
    }

    @Test
    void 長いデータでも元に戻る() {
        WindowsDpapiProtector protector = new WindowsDpapiProtector();
        byte[] plain = "長い文字列-0123456789".repeat(300).getBytes(StandardCharsets.UTF_8);

        assertThat(protector.unprotect(protector.protect(plain))).isEqualTo(plain);
    }

    @Test
    void 終わらないコマンドは時間切れの例外になり_動いていたPowerShellを止める() throws InterruptedException {
        AtomicReference<Process> started = new AtomicReference<>();
        // 本物の PowerShell を、結果を返さずに長く待たせる（時間切れの確認だけに使う）
        WindowsDpapiProtector protector = new WindowsDpapiProtector(method -> {
            Process process = new ProcessBuilder(WindowsDpapiProtector.POWERSHELL.toString(),
                    "-NoProfile", "-NonInteractive", "-Command", "Start-Sleep -Seconds 60")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            started.set(process);
            return process;
        }, 2);

        long began = System.nanoTime();
        assertThatThrownBy(() -> protector.protect("テスト".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("時間切れ");

        assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(30));
        assertThat(started.get().waitFor(10, TimeUnit.SECONDS)).isTrue();
    }
}
