@echo off
setlocal
cd /d "%~dp0"
if exist "C:\Program Files\Java\jdk-11\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-11"
if not defined JAVA_HOME (
    echo Set JAVA_HOME to your JDK 11 installation and try again.
    pause
    exit /b 1
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo JAVA_HOME must point to a JDK, not a JRE: %JAVA_HOME%
    pause
    exit /b 1
)
set "GRADLE_USER_HOME=%~dp0.gradle-user-home"
call gradlew.bat run --no-daemon
if errorlevel 1 (
    echo.
    echo Launch failed. Read the error above and the troubleshooting section in README.md.
    pause
)
endlocal
