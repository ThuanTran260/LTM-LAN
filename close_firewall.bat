@echo off
REM =========================================================================
REM Script DONG (go bo) cac luat Firewall da mo boi allow_firewall.bat
REM Can Click chuot phai vao file nay -> Chon "Run as administrator"
REM =========================================================================

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [LOI] BAN CHUA CHAY VOI QUYEN ADMINISTRATOR!
    echo Vui long: nhan chuot phai vao tep close_firewall.bat -^> "Run as administrator"
    pause
    exit /b 1
)

echo Dang go bo cac luat Firewall cua Java UDP Hub...
netsh advfirewall firewall delete rule name="Java UDP Hub - JDK 24 java.exe" >nul 2>&1
netsh advfirewall firewall delete rule name="Java UDP Hub - JDK 24 javaw.exe" >nul 2>&1
netsh advfirewall firewall delete rule name="Java UDP Hub - Oracle Javapath" >nul 2>&1
netsh advfirewall firewall delete rule name="Java UDP Network Hub (UDP Inbound)" >nul 2>&1

echo.
echo ====================================================================
echo [HOAN THANH] Da dong Firewall:
echo - Da xoa luat cho phep java.exe / javaw.exe / Oracle Javapath
echo - Da dong cong UDP 5000-5010, 7000, 8000
echo (Can dung lai thi chay allow_firewall.bat voi quyen Administrator)
echo ====================================================================
pause
