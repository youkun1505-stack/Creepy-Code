@echo off
rem Sign in with ChatGPT: sign-in / status / sign-out (Spring is not started)
rem Usage: ai-account.cmd sign-in [--port 1455] | ai-account.cmd status | ai-account.cmd sign-out
setlocal
set "APP_DIR=%~dp0"

rem Compile the classes and copy the runtime jars into target (pom.xml is not changed)
call "%APP_DIR%mvnw.cmd" -q -B -f "%APP_DIR%pom.xml" compile dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="%APP_DIR%target\ai-deps"
if errorlevel 1 (
    echo Failed to prepare the program. Check that mvnw.cmd can build bridal-app.
    exit /b 1
)

set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -cp "%APP_DIR%target\classes;%APP_DIR%target\ai-deps\*" com.creepycode.common.ai.chatgpt.ChatGptSignInMain %*
exit /b %ERRORLEVEL%
