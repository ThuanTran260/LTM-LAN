# ỨNG DỤNG TỔNG HỢP UDP DUAL-VIEW (LOGIC & VẬT LÝ)

Ứng dụng lập trình mạng hoàn chỉnh viết **100% bằng Java SE (Java Swing Native)**, tích hợp toàn diện kiến thức và bài tập từ 3 slide bài giảng:
1. **Slide 1: UDP Chat Peer-to-Peer (Unicast P2P)**
2. **Slide 2: UDP Broadcast trong mạng LAN (Auto Discovery & LAN Announcements)**
3. **Slide 3: UDP Multicast (Phòng Chat Nhóm & IP Class D)**

Đặc biệt, giao diện ứng dụng được thiết kế theo mô hình **Split-Screen Dashboard** trực quan hóa song song:
- **CỘT 1: UDP LOGIC (Tầng Giao Vận & Tầng Ứng Dụng)**
- **CỘT 2: UDP VẬT LÝ (Tầng Vật Lý & Tầng Liên Kết Dữ Liệu)**

---

## 1. CẤU TRÚC THƯ MỤC DỰ ÁN

```text
d:\LapTrinhMang\LAN\
├── compile.bat                  # Script biên dịch toàn bộ dự án bằng javac
├── run_alice.bat                # Khởi chạy Peer Alice (Unicast Port 5001)
├── run_bob.bat                  # Khởi chạy Peer Bob (Unicast Port 5002)
├── run_charlie.bat              # Khởi chạy Peer Charlie (Unicast Port 5003)
├── run_lan.bat                  # Khởi chạy trên mạng LAN vật lý thật (nhiều máy khác nhau)
├── README.md                    # Tài liệu hướng dẫn và phân tích lý thuyết/vật lý
├── bin\                         # Thư mục chứa các file .class sau khi biên dịch
└── src\
    ├── Main.java                # Điểm khởi chạy ứng dụng (Main Entry Point)
    ├── model\
    │   ├── PeerInfo.java         # Quản lý thông tin từng Peer (IP, Port, MAC, LastSeen, Seq)
    │   ├── PhysicalFrameInfo.java# Thông số khung vật lý (MAC, MTU, TTL, Trễ L/R, Trễ d/v)
    │   └── ProtocolMessage.java  # Đóng gói/giải mã khung tin logic ([HEADER|TYPE|SEQ|TIME|...])
    ├── network\
    │   ├── BroadcastHandler.java # Lắng nghe/phát Broadcast cổng 7000 (Slide 2)
    │   ├── MessageListener.java  # Interface callback nhận tin bất đồng bộ
    │   ├── MulticastHandler.java # Quản lý MulticastSocket cổng 8000, Join/Leave phòng (Slide 3)
    │   ├── PhysicalNetworkHelper.java # Lấy NIC, MAC, tính MAC Multicast IANA, tính trễ vật lý
    │   ├── RateLimiter.java      # Giới hạn tần suất broadcast chống bão mạng vật lý
    │   ├── TrafficStats.java     # Đếm byte, khung truyền Rx/Tx, giám sát bão mạng
    │   └── UnicastHandler.java   # Quản lý DatagramSocket cho chat riêng P2P 1-1 (Slide 1)
    └── ui\
        ├── LogicPanel.java       # Giao diện Cột 1: Chat P2P, Broadcast, Phòng, Peer list
        ├── PhysicalPanel.java    # Giao diện Cột 2: NIC, MAC, MTU, TTL, Traffic, Frame Dissector
        └── MainFrame.java        # Cửa sổ chính tích hợp và điều phối đa luồng
```

---

## 2. ÁNH XẠ KIẾN THỨC VẬT LÝ & LÝ THUYẾT MẠNG VÀO GIAO DIỆN

### A. Góc Nhìn UDP Logic (Cột Trái)
- **Cổng Logic (Ports)**:
  - Cổng Unicast: `5001`, `5002`, `5003`... (Slide 1: Giao tiếp điểm - điểm giữa các DatagramSocket).
  - Cổng Broadcast: `7000` (Slide 2: Giao tiếp quảng bá toàn bộ Subnet LAN).
  - Cổng Multicast: `8000` (Slide 3: Giao tiếp phòng chat nhóm IP Class D).
- **Cơ Chế Khung Tin Nhắn Logic**:
  - Gói tin tuân theo định dạng: `[UDPHUB]|[TYPE]|[SEQ]|[TIMESTAMP]|[SENDER]|[UNICAST_PORT]|[PAYLOAD]`.
  - Có đánh số thứ tự **Sequence Number (`#Seq`)** và dấu thời gian **Timestamp (`HH:mm:ss`)**.
- **Tính Năng Slide 1 (P2P)**: Chat riêng 1-1 qua nút `Gửi P2P`, tự động phát hiện Peer offline (Timeout 15 giây), các lệnh `/help`, `/list`, `/exit`.
- **Tính Năng Slide 2 (Broadcast)**: Khởi động tự động phát gói `DISCOVER` tới cổng 7000, các máy online nhận được tự gửi Unicast phản hồi để cập nhật danh sách Peer. Nút `Gửi Broadcast` gửi thông báo cho toàn phòng máy.
- **Tính Năng Slide 3 (Multicast)**: Có sẵn 3 phòng mặc định:
  - Phòng 1: `239.1.1.1:8000` (Chung)
  - Phòng 2: `239.1.1.2:8000` (Học tập)
  - Phòng 3: `239.1.1.3:8000` (Giải trí)
  - Cho phép gõ IP phòng tùy ý và bấm `Join`.
- **Công Cụ Kiểm Thử Mất Gói**: Checkbox *"Mô phỏng mất gói (Drop Next Packet)"* cố ý nhảy cóc số Sequence để bên nhận kích hoạt cảnh báo mất gói tin.

### B. Góc Nhìn UDP Vật Lý (Cột Phải)
- **Thông Số Card Mạng Phần Cứng (NIC)**:
  - Tên Card mạng thực tế (Realtek/Intel Ethernet hoặc Wi-Fi).
  - Địa chỉ **MAC phần cứng cục bộ**.
  - **MTU (Maximum Transmission Unit)**: 1500 Bytes trên mạng Ethernet tiêu chuẩn.
  - **Phạm vi vật lý (TTL = 1 Hop)**: Gói tin chỉ được truyền trong mạng LAN cục bộ, bị chặn lại ở Router biên để tránh phát tán ra ngoài Internet.
- **Đo Lường Lưu Lượng & Băng Thông**:
  - Tổng số Byte và số Frames đã truyền (Tx) và đã nhận (Rx).
  - Đo tần suất phát Broadcast. Nếu gửi quá nhanh, hệ thống kích hoạt **Rate Limiter (chống bão Broadcast Storm)** và bật đèn cảnh báo màu Đỏ.
- **Bộ Giải Phẫu Khung Ethernet (Frame Dissector)**:
  - Hiển thị chi tiết từng khung truyền thời gian thực:
    - **MAC Nguồn**: Địa chỉ card mạng máy gửi.
    - **MAC Đích**:
      - Gửi Unicast: MAC đích của máy nhận.
      - Gửi Broadcast: `FF-FF-FF-FF-FF-FF` (Switch nhân bản tín hiệu ra tất cả cổng vật lý).
      - Gửi Multicast: Ánh xạ IANA chuẩn IEEE `01-00-5E-xx-xx-xx` từ 23 bit cuối của IP Class D.
    - **Trễ truyền bit (Transmission Delay)**: $t_{trans} = \frac{L}{R}$ (với $L$ là Frame size, $R$ là tốc độ đường truyền 100Mbps/1Gbps).
    - **Trễ lan truyền (Propagation Delay)**: $t_{prop} = \frac{d}{v}$ (với khoảng cách cáp đồng $d = 20\text{m}$, vận tốc $v \approx 2 \times 10^8\text{ m/s}$).

---

## 3. HƯỚNG DẪN BIÊN DỊCH VÀ CHẠY THỬ NGHIỆM

### Bước 1: Biên Dịch Dự Án
Mở cửa sổ Command Prompt hoặc PowerShell tại thư mục `d:\LapTrinhMang\LAN`, chạy:
```cmd
compile.bat
```
*(Yêu cầu máy tính có cài sẵn JDK - đã kiểm tra máy có Java 24)*.

### Bước 2: Chạy Thử 2 Hoặc 3 Máy Trên Cùng 1 Máy Tính (Giả Lập)
1. Mở file `run_alice.bat` $\rightarrow$ Cửa sổ của Alice mở lên (Cổng Unicast 5001).
2. Mở file `run_bob.bat` $\rightarrow$ Cửa sổ của Bob mở lên (Cổng Unicast 5002).
3. (Tùy chọn) Mở `run_charlie.bat` $\rightarrow$ Cửa sổ của Charlie mở lên (Cổng Unicast 5003).

### Bước 3: Chạy Thử Trên Mạng LAN Thật (Nhiều Máy Tính Nối Switch/Wi-Fi)
1. **Mở Tường Lửa (Chỉ cần làm 1 lần trên mỗi máy)**:
   - Nhấp chuột phải vào file `allow_firewall.bat` $\rightarrow$ Chọn **"Run as administrator"**.
   - Script sẽ tự động thêm luật mở các cổng UDP `5000-5010`, `7000`, `8000` để không bị Windows chặn gói tin.
2. **Cấu hình IP (Dải 192.168.1.x)**:
   - Các máy đặt IP cùng dải: ví dụ Máy 1 đặt `192.168.1.10`, Máy 2 đặt `192.168.1.20`.
   - Subnet Mask: **BẮT BUỘC đặt `255.255.255.0`** (để cùng Subnet Broadcast `192.168.1.255`).
3. **Khởi chạy ứng dụng**:
   - Chạy `run_lan.bat` trên mỗi máy.
   - Khi mở lên, tại mục **`Card Mạng (NIC):`**, chọn đúng Card mạng Ethernet/Wi-Fi đang mang IP `192.168.1.x`.
   - Nhập tên máy và bấm **"BẮT ĐẦU / KẾT NỐI"**.
   - Các máy sẽ tự động tìm thấy nhau qua cả Limited Broadcast `255.255.255.255` lẫn Subnet Broadcast `192.168.1.255`.

---

## 4. KỊCH BẢN KIỂM THỬ TRÌNH DIỄN CHO GIẢNG VIÊN

1. **Minh Họa Dò Tìm Tự Động (Slide 2 - Broadcast Discovery)**:
   - Khi vừa mở Bob lên, cả Alice và Bob đều tự động hiển thị tên nhau trong danh sách `DANH SÁCH PEER ONLINE` mà không cần cấu hình IP thủ công.
   - Nhìn sang Cột 2 (Vật lý): Thấy gói `DISCOVER` gửi tới MAC `FF-FF-FF-FF-FF-FF`.
2. **Minh Họa Chat Riêng P2P (Slide 1 - Unicast)**:
   - Trên Alice, nhấp chọn `Bob` trong danh sách Peer.
   - Gõ `Chào Bob!` và bấm `Gửi P2P`.
   - Bob nhận được tin nhắn kèm số `#Seq` và thời gian. Cột vật lý hiển thị MAC đích chính là MAC của Bob.
3. **Minh Họa Phòng Chat Nhóm (Slide 3 - Multicast)**:
   - Cả Alice và Bob cùng bấm nút `1. Chung: 239.1.1.1`.
   - Charlie không bấm (chưa vào phòng 1).
   - Alice gửi tin nhắn bằng nút `Chat Phòng`: Bob nhận được tin nhắn, còn Charlie hoàn toàn không nhận được.
   - Cột vật lý hiển thị MAC đích là `01-00-5E-01-01-01` (chứng minh bộ lọc Multicast của card mạng).
4. **Minh Họa Cơ Chế Phát Hiện Mất Gói Tin (Packet Loss Detection)**:
   - Trên Alice, tích vào ô `Kích hoạt "Mô phỏng mất gói (Drop Next Packet)"`.
   - Gửi một tin nhắn: Alice sẽ cố tình nhảy cóc 1 số thứ tự Sequence.
   - Bên Bob ngay lập tức hiển thị dòng cảnh báo màu đỏ: `[CẢNH BÁO MẤT GÓI] Phát hiện mất gói từ SEQ #...`.
5. **Minh Họa Chống Bão Mạng (Broadcast Storm & Rate Limiter)**:
   - Nhấp nút `Gửi Broadcast` liên tục: Ứng dụng sẽ kích hoạt Rate Limiter, cảnh báo `Vui lòng chờ 2.0s` và đèn cảnh báo bão mạng trên cột vật lý sẽ chuyển màu để bảo vệ đường truyền cáp LAN.
