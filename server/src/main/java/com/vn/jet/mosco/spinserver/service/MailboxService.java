package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserMail;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import com.vn.jet.mosco.spinserver.repository.UserMailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vn.jet.mosco.spinserver.utils.MessageConstants;

import java.util.List;

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
        // 1. Tìm thư trong DB với Pessimistic Lock
        UserMail mail = userMailRepository.findByIdForUpdate(mailId)
                .orElseThrow(() -> new RuntimeException(MessageConstants.ITEM_NOT_FOUND));

        // 2. Kiểm tra trạng thái nhận
        if (mail.isReceived()) {
            throw new IllegalStateException(MessageConstants.MAIL_ERR_ALREADY_CLAIMED);
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

    /**
     * Nhận tất cả quà từ các thư hệ thống chưa nhận.
     */
    @Transactional
    public void claimAllMails(Long userId) {
        // 1. Tìm tất cả các thư chưa nhận của user kèm Pessimistic Lock
        List<UserMail> unreceivedMails = userMailRepository.findUnreceivedMailsForUpdate(userId);
        if (unreceivedMails.isEmpty()) {
            return;
        }

        // 2. Tìm thông tin User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(MessageConstants.USER_NOT_FOUND));

        long totalCoins = 0;
        long totalDiamonds = 0;

        // 3. Gom tài nguyên từ tất cả thư
        for (UserMail mail : unreceivedMails) {
            if (mail.getItemCode() != null && mail.getQuantity() != null && mail.getQuantity() > 0) {
                String itemCode = mail.getItemCode().toUpperCase();
                if (itemCode.contains("COIN")) {
                    totalCoins += mail.getQuantity();
                } else if (itemCode.contains("DIAMOND")) {
                    totalDiamonds += mail.getQuantity();
                }
            }
            mail.setReceived(true);
        }

        // 4. Cộng một lần duy nhất vào tài khoản người chơi
        if (totalCoins > 0) {
            user.setCoins(user.getCoins() + totalCoins);
        }
        if (totalDiamonds > 0) {
            user.setDiamonds(user.getDiamonds() + totalDiamonds);
        }

        userRepository.save(user);
        userMailRepository.saveAll(unreceivedMails);
    }
}
