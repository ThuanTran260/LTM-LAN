@echo off
REM =========================================================================
REM Script tự động mở cổng tường lửa Windows (Windows Defender Firewall)
REM Cần Click chuột phải vào file này -> Chọn "Run as Administrator"
REM =========================================================================
chcp 65001 >nul
echo Đang kiểm tra quyền Quản trị viên (Administrator)...

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo ====================================================================
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
echo Đang thêm luật mở cổng UDP vào Windows Firewall:
echo - Cổng Unicast P2P: UDP 5000 - 5010
echo - Cổng Broadcast LAN: UDP 7000
echo - Cổng Multicast Group: UDP 8000
echo.

REM Xóa rule cũ nếu đã tồn tại để tránh trùng lặp
netsh advfirewall firewall delete rule name="Java UDP Network Hub (UDP Inbound)" >nul 2>&1

REM Tạo rule Inbound mới
netsh advfirewall firewall add rule name="Java UDP Network Hub (UDP Inbound)" dir=in action=allow protocol=UDP localport=5000-5010,7000,8000 profile=any

if %errorlevel% equ 0 (
    echo.
    echo ====================================================================
    echo [THANH CONG] Da mo cac cong UDP 5000-5010, 7000, 8000 tren Windows Firewall!
    echo Cac may tinh trong mang LAN 192.168.1.x gio day co the ket noi
    echo va trao doi du lieu UDP thoai mai ma khong bi chan.
    echo ====================================================================
) else (
    echo.
    echo [CANH BAO] Khong the tu dong them luat Firewall. Vui long kiem tra lai.
)

echo.
pause
