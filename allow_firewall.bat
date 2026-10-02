@echo off
REM =========================================================================
REM Script tự động sửa lỗi chặn mạng và mở toàn diện Windows Defender Firewall
REM Cần Click chuột phải vào file này -> Chọn "Run as administrator"
REM =========================================================================
chcp 65001 >nul
echo ====================================================================
echo Đang kiểm tra quyền Quản trị viên (Administrator)...
echo ====================================================================

REM LUU Y: kiem tra quyen bang fltmc (tuc thi, khong treo nhu "net session"
REM von co the treo lau tren may co dich vu Server/SMB cham hoac loi mang).

fltmc >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo [LOI] BAN CHUA CHAY VOI QUYEN ADMINISTRATOR!
    echo Vui long:
    echo 1. Nhan chuot phai vao tep: allow_firewall.bat
    echo 2. Chon "Run as administrator" (Chay voi tu cach quan tri vien)
    echo ====================================================================
    echo.
    pause
    exit /b 1
)

echo.
echo [1/4] Đang quét và gỡ bỏ các luật BLOCK (chặn ngầm) Java trong Firewall...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetFirewallRule | Where-Object { ($_.DisplayName -like '*Java*' -or $_.DisplayName -like '*javaw*') -and $_.Action -eq 'Block' } | ForEach-Object { Remove-NetFirewallRule -Name $_.Name; Write-Host ('  - Da go luat chan: ' + $_.DisplayName) }"

echo.
echo [2/4] Đang cho phép Java (JDK 24) nhận gói tin qua mạng...
netsh advfirewall firewall delete rule name="Java UDP Hub - JDK 24 java.exe" >nul 2>&1
netsh advfirewall firewall delete rule name="Java UDP Hub - JDK 24 javaw.exe" >nul 2>&1
netsh advfirewall firewall delete rule name="Java UDP Hub - Oracle Javapath" >nul 2>&1

netsh advfirewall firewall add rule name="Java UDP Hub - JDK 24 java.exe" dir=in action=allow program="D:\java\bin\java.exe" enable=yes profile=any >nul 2>&1
netsh advfirewall firewall add rule name="Java UDP Hub - JDK 24 javaw.exe" dir=in action=allow program="D:\java\bin\javaw.exe" enable=yes profile=any >nul 2>&1
netsh advfirewall firewall add rule name="Java UDP Hub - Oracle Javapath" dir=in action=allow program="C:\Program Files\Common Files\Oracle\Java\javapath_target_1869370954\java.exe" enable=yes profile=any >nul 2>&1

echo.
echo [3/4] Đang mở các cổng UDP (5000-5010, 7000, 8000)...
netsh advfirewall firewall delete rule name="Java UDP Network Hub (UDP Inbound)" >nul 2>&1
netsh advfirewall firewall add rule name="Java UDP Network Hub (UDP Inbound)" dir=in action=allow protocol=UDP localport=5000-5010,7000,8000 profile=any >nul 2>&1

echo.
echo [4/4] Đang chuyển cấu hình mạng Wi-Fi/LAN sang chế độ Private (nội bộ an toàn)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetConnectionProfile | Where-Object { $_.NetworkCategory -eq 'Public' } | Set-NetConnectionProfile -NetworkCategory Private -ErrorAction SilentlyContinue"

echo.
echo ====================================================================
echo [THANH CONG HOAN TOAN]
echo - Da go bo moi luat BLOCK ngam cua Java
echo - Da mo toan quyen UDP Inbound cho Java JDK 24
echo - Da mo cong UDP 5000-5010 (Unicast), 7000 (Broadcast), 8000 (Multicast)
echo - Da chuyen mang sang Private (cho phep ket noi P2P khong bi chan)
echo ====================================================================
echo.
pause
