@echo off
chcp 65001 >nul
cd /d "%~dp0"

where python >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Python 3 not found! Please install Python 3.8+ and add it to PATH.
    echo.
    pause
    exit /b 1
)

python run_all.py

echo.
pause
