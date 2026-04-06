package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserMail;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import com.vn.jet.mosco.spinserver.repository.UserMailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MailboxService - Xử lý nghiệp vụ hòm thư và quà tặng.
 * Đảm bảo tính nguyên tử (Atomic) khi nhận quà để tránh lỗi dữ liệu.
 */
@Service
public class MailboxService {

    private final UserMailRepository userMailRepository;
    private final UserRepository userRepository;

    public MailboxService(UserMailRepository userMailRepository, UserRepository userRepository) {
        this.userMailRepository = userMailRepository;
        this.userRepository = userRepository;
    }

    /**
     * Nhận quà từ thư. 
     * @param mailId ID của bức thư cần nhận.
     * @throws RuntimeException nếu không tìm thấy thư hoặc đã nhận rồi.
     */
    @Transactional
    public void claimMail(Long mailId) {
        // 1. Tìm thư trong DB
        UserMail mail = userMailRepository.findById(mailId)
                .orElseThrow(() -> new RuntimeException("Hệ thống không tìm thấy bức thư này, sếp ơi!"));

        // 2. Kiểm tra trạng thái nhận
        if (mail.isReceived()) {
            throw new IllegalStateException("Thư này sếp đã nhận quà rồi nhé!");
        }

        // 3. Xử lý cộng quà (Coins / Diamonds)
        if (mail.getItemCode() != null && mail.getQuantity() != null && mail.getQuantity() > 0) {
            User user = mail.getUser();
            String itemCode = mail.getItemCode().toUpperCase();

            // Cộng tài nguyên tương ứng (Hỗ trợ cả COIN, ITEM_COIN, DIAMOND, etc.)
            if (itemCode.contains("COIN")) {
                user.setCoins(user.getCoins() + mail.getQuantity());
            } else if (itemCode.contains("DIAMOND")) {
                user.setDiamonds(user.getDiamonds() + mail.getQuantity());
            }
            // Mở rộng thêm logic khác nếu cần ở đây
            
            userRepository.save(user);
        }

        // 4. Đánh dấu thư đã nhận để không cho nhận lần 2
        mail.setReceived(true);
        userMailRepository.save(mail);
    }
}
