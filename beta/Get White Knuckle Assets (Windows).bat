@echo off
setlocal
cd /d "%~dp0"
echo Builds the White Knuckle hands, sounds and effects pack from YOUR copy of White Knuckle.
echo.

rem The extractor's libraries ship ready-made for Python 3.10 - 3.14 (not yet 3.15),
rem so pick one of those even if a newer Python is the default.
where py >nul 2>nul
if errorlevel 1 goto :nopython
set "PYVER="
for %%V in (3.13 3.12 3.14 3.11 3.10) do call :trypython %%V
if not defined PYVER goto :nopython
echo Using Python %PYVER%.

set "VENV=%LOCALAPPDATA%\wkclimb-extractor"
set "MARK=%VENV%\setup-ok-%PYVER%.txt"
if exist "%MARK%" goto :extract

rem No finished setup for this Python (first run, or an earlier attempt failed): start clean.
if exist "%VENV%" rmdir /s /q "%VENV%"
echo First run: setting up the extractor (a minute or two)...
py -%PYVER% -m venv "%VENV%"
if not "%errorlevel%"=="0" goto :fail
rem --only-binary: never try to compile anything (that needs Visual C++ and fails on most PCs)
"%VENV%\Scripts\python.exe" -m pip install --quiet --only-binary=:all: -r requirements.txt
if not "%errorlevel%"=="0" goto :fail
echo ok> "%MARK%"

:extract
echo Extracting (about 15 seconds)...
if exist "%~dp0White Knuckle Assets.zip" del /q "%~dp0White Knuckle Assets.zip"
"%VENV%\Scripts\python.exe" extract_wk_assets.py --out "%~dp0White Knuckle Assets" --zip %*
rem A crash gives a negative exit code, which "if errorlevel 1" would miss: compare with 0.
if not "%errorlevel%"=="0" goto :fail
if not exist "%~dp0White Knuckle Assets.zip" goto :fail
echo.
echo Done! "White Knuckle Assets.zip" is in this folder:
echo   %~dp0
echo Now in Minecraft: Options ^> Resource Packs ^> Open Pack Folder,
echo copy the zip into it, then move the pack to the right-hand side.
pause
exit /b 0

:trypython
if defined PYVER exit /b 0
py -%1 -c "import sys" >nul 2>nul
if "%errorlevel%"=="0" set "PYVER=%1"
exit /b 0

:nopython
echo This needs Python 3.10 to 3.14. (Python 3.15 is too new for the extractor's libraries;
echo it's fine to have both installed - this script picks the right one.)
echo.
where winget >nul 2>nul
if errorlevel 1 goto :manualpython
choice /c YN /m "Install Python 3.13 now"
if errorlevel 2 goto :manualpython
winget install -e --id Python.Python.3.13 --accept-package-agreements --accept-source-agreements
echo.
echo When the install has finished, close this window and run this script again.
pause
exit /b 1

:manualpython
echo Install Python 3.13 from https://www.python.org/downloads/windows/
echo (pick the latest 3.13 "Windows installer (64-bit)"), then run this script again.
pause
exit /b 1

:fail
echo.
echo Something went wrong. If it says White Knuckle wasn't found, find the game folder
echo (Steam: right-click White Knuckle ^> Manage ^> Browse local files), then open a Command Prompt
echo in this folder and run:
echo   "Get White Knuckle Assets (Windows).bat" --game "D:\SteamLibrary\steamapps\common\White Knuckle"
echo (with your own path). Otherwise please report it with a screenshot of this window at
echo   https://github.com/Fizz12215/White-Knuckle-Climbing/issues
pause
exit /b 1
