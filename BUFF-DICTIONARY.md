# 🌟 MOSCO SYNERGY & BUFF DICTIONARY (V1.0)

Tài liệu đặc tả hệ thống cộng hưởng chỉ số dành cho dự án Mosco (K-pop Idol Gacha Game). 
Hệ thống này được thiết kế để tối ưu hóa tính chiến thuật trong việc xây dựng đội hình 6 thành viên.

---

## 🚫 QUY TẮC TỐI THƯỢNG: SINGLE-ACTIVE TAG
Để đảm bảo tính cân bằng và ngăn chặn các đội hình "Exodia" (quá nhiều buff chồng chéo), Mosco áp dụng luật **Single-Active Tag**:
- Mỗi thẻ Objet (Card) sở hữu nhiều Tag (Tộc/Hệ).
- Khi đưa vào đội hình, người chơi **BẮT BUỘC** chỉ được chọn **01 Tag duy nhất** để kích hoạt (Sáng đèn).
- Các Tag còn lại của thẻ đó sẽ ở trạng thái vô hiệu hóa trong suốt trận đấu.

---

## 🪐 1. HỆ THỐNG KHẮC CHẾ: GRAND GRAVITY (BẤT BIẾN)
Cơ chế khắc chế tự động dựa trên **Hệ Gốc (Dimension)** của thẻ. Áp dụng khi thẻ của người chơi đối đầu trực tiếp với thẻ địch.

| Dimension (Hệ) | Khắc Chế (Counters) | Hiệu Ứng |
| :--- | :--- | :--- |
| ☀️ **SUN** | 🌕 MOON | Thẻ khắc hệ nhận **x1.36 lần OVR** khi tính toán sát thương/phòng thủ với mục tiêu bị khắc. |
| 🌕 **MOON** | 🌊 NEPTUNE | |
| 🌊 **NEPTUNE** | 🌌 ZENITH | |
| 🌌 **ZENITH** | ☀️ SUN | |

---

## 🏗️ 2. CÁC TẦNG CỘNG HƯỞNG (SYNERGY LAYERS)

### A. Nhóm Dimension (Tứ Tượng - Buff Chỉ Số Gốc)
Tập trung vào sự đồng nhất về màu sắc/hệ của đội hình.

| Tag | Số thẻ yêu cầu | Hiệu ứng kích hoạt |
| :--- | :--- | :--- |
| **MOON, SUN, NEPTUNE, ZENITH** 
| | **2 Thẻ** | +5% tổng OVR toàn đội. |
| | **4 Thẻ** | +15% tổng OVR toàn đội.|
| | **6 Thẻ** | +25% tổng OVR toàn đội & Kích hoạt hiệu ứng visual sân đấu. |

---

### B. Nhóm Major Units (Tộc Lớn - Chiến Thuật Chủ Lực)
Các đơn vị tác động mạnh đến cục diện trận đấu thông qua Buff mạnh hoặc Debuff địch.

| Tộc (Tag) | Số thẻ | Hiệu Ứng (Buff/Debuff) | Chiến thuật áp dụng |
| :--- | :--- | :--- | :--- |
| **AAA** | 2 / 4 | Trừ **10% / 25%** OVR của thẻ địch đối diện. | "Bắt chết" chủ lực/Center của địch. |
| **KRE** | 2 / 4 | Tăng **15% / 30%** Visual (Phòng thủ) toàn team. | Chống chịu sát thương (Vocal) cực đại. |
| **LOVElution** | 3 / 5 / 7 | Tăng **10% / 20% / 35%** OVR toàn team. | Đội hình cân bằng, sức mạnh thuần túy. |
| **EVOLution** | 3 / 5 / 7 | Tăng **15% / 30% / 45%** chỉ số Vocal & Charm. | Sát thương bùng nổ, dứt điểm nhanh. |
| **ACID EYES** | 3 / 5 / 7 | Trừ **10% / 20% / 35%** OVR của *toàn bộ* địch. | Chống lại các đội hình Boss/OVR ảo. |

---

### C. Nhóm Minor Units (Hệ Nhỏ - Tương Tác Chéo)
Dùng để tối ưu các slot lẻ, mang lại các tiện ích đặc biệt.

| Hệ (Tag) | Số thẻ | Hiệu Ứng |
| :--- | :--- | :--- |
| **Aria** | 2 / 4 | Tăng **10% / 25%** OVR riêng cho các thẻ có Tag `Aria`. |
| **NXT** | 2 / 4 | Tăng **15% / 30%** Dance (Tốc độ ra đòn) toàn team. |
| **Glow** | 2 / 4 | Tăng **15% / 30%** Stamina (Máu) toàn team. |
| **VV** | 4 / 8 | Thẻ Tag `VV` nhận **x1.2 OVR** và kháng mọi Debuff. |
| **$\infty$! / Alpha** | 2 | **Nhân đôi (x2)** hiệu ứng của 1 Tag Minor khác đang active. |

---

## 🧮 3. CÔNG THỨC TÍNH OVR CUỐI CÙNG (COMBAT LOGIC)

Lúc vào trận, hệ thống sẽ tính toán theo trình tự:
1. **OVR Tĩnh:** `OVR_Card = Base_Class + Season_Bonus + Badge_Bonus`.
2. **Tổng OVR Team:** `Total_OVR_Base = Sum(OVR_Card[1...6])`.
3. **Áp dụng Buff Nhóm:** `Final_OVR = Total_OVR_Base * (1 + Synergy_Multiplier)`.
4. **Xử lý Khắc Hệ:** Nếu Thẻ A đối diện Thẻ B bị khắc, `OVR_Card_A_Combat = OVR_Card_A * 1.36`.

---
*Tài liệu này thuộc sở hữu của dự án Mosco. Nghiêm cấm thay đổi các chỉ số khi chưa có sự đồng ý của Tech Lead.*