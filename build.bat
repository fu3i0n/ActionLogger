@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

cls
echo.
echo ======================================
echo ActionLogger Build ^& Deploy Script
echo ======================================
echo.

REM Configuration
set plugin=ActionLogger
set version=1.0.0
set target=C:\Users\FSOS\Desktop\ActionLogger\Jar
set source=.\build\libs\%plugin%-%version%-shaded.jar
set destination=%target%\%plugin%-%version%.jar

REM Check if we're in the right directory
if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found!
    echo Make sure you're in the project root directory.
    pause
    exit /b 1
)

echo Project directory: %cd%
echo.

REM Create target directory if it doesn't exist
if not exist "%target%" (
    echo Creating target directory...
    mkdir "%target%"
)

REM [1/4] Ktlint Format
echo [1/4] Running ktlint format...
call gradlew.bat ktlintFormat
if !ERRORLEVEL! neq 0 (
    echo.
    echo ERROR: ktlint format failed!
    pause
    exit /b 1
)
echo SUCCESS: Code formatting completed.
echo.

REM [2/4] Build
echo [2/4] Building plugin...
call gradlew.bat clean shadowJar
if !ERRORLEVEL! neq 0 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)
echo SUCCESS: Build completed.
echo.

REM [3/4] Verify source exists
echo [3/4] Verifying JAR file...
if not exist "%source%" (
    echo ERROR: Source JAR not found: %source%
    pause
    exit /b 1
)

for %%A in ("%source%") do set "size=%%~zA"
set /a sizeMB="!size! / 1048576"
echo JAR Size: !sizeMB! MB
echo.

REM [4/4] Copy to server
echo [4/4] Copying to server...
echo From: %source%
echo To:   %destination%

copy /y "%source%" "%destination%"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to copy JAR!
    pause
    exit /b 1
)

echo SUCCESS: JAR copied successfully!
echo.

REM Cleanup modules
if exist "%target%\%plugin%\modules" (
    echo Cleaning up old config files...
    rd /s /q "%target%\%plugin%\modules" 2>nul
)

echo.
echo ======================================
echo ✓ Build Complete!
echo ======================================
echo.
echo JAR Location: %destination%
echo JAR Size: !sizeMB! MB
echo.
echo Next: Restart your server
echo.
pause
exit /b 0