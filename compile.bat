@echo off
REM Bien dich toan bo project Java UDP Hub (JDK 24, Java SE, khong thu vien ngoai)
cd /d %~dp0
if not exist bin mkdir bin
javac -encoding UTF-8 -d bin src\Main.java src\model\*.java src\network\*.java src\ui\*.java
if %errorlevel% neq 0 (
  echo BIEN DICH THAT BAI.
  exit /b 1
)
echo Bien dich thanh cong. Chay bang run_alice.bat / run_bob.bat / run_charlie.bat
