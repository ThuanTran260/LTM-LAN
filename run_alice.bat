@echo off
cd /d %~dp0
if not exist bin\Main.class call compile.bat
java -cp bin Main Alice 5001
