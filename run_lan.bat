@echo off
REM Chay tren LAN that (4-5 may cam cap/switch): moi may dat TEN RIENG, port giu 5001 cung duoc (IP khac nhau).
cd /d %~dp0
if not exist bin\Main.class call compile.bat
set /p LANNAME=Nhap TEN RIENG cho may nay (VD: May01-An): 
if "%LANNAME%"=="" set LANNAME=%COMPUTERNAME%
java -cp bin Main "%LANNAME%" 5001
