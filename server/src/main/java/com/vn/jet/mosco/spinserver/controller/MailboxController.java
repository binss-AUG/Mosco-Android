package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.model.UserMail;
import com.vn.jet.mosco.spinserver.repository.UserMailRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mailbox")
public class MailboxController {

    private final UserMailRepository userMailRepository;
    private final com.vn.jet.mosco.spinserver.service.MailboxService mailboxService;

    public MailboxController(UserMailRepository userMailRepository, 
                             com.vn.jet.mosco.spinserver.service.MailboxService mailboxService) {
        this.userMailRepository = userMailRepository;
        this.mailboxService = mailboxService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<UserMail>> getUserMails(@PathVariable Long userId) {
        // Chỉ lấy những thư chưa xử lý (unreceived) hoặc sếp muốn xem hết? 
        // Hiện tại lấy hết để người dùng xem lịch sử.
        List<UserMail> mails = userMailRepository.findByUserId(userId);
        return ResponseEntity.ok(mails);
    }

    /**
     * Nhận quà từ thư (Claim Gift).
     */
    @PostMapping("/claim/{mailId}")
    public ResponseEntity<?> claimMail(@PathVariable Long mailId) {
        try {
            mailboxService.claimMail(mailId);
            return ResponseEntity.ok().body(java.util.Map.of(
                "status", 200,
                "message", "Nhận quà thành công!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "status", 400,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Nhận toàn bộ quà từ các thư hệ thống chưa nhận (Claim All).
     */
    @PostMapping("/claim-all/{userId}")
    public ResponseEntity<?> claimAllMails(@PathVariable Long userId) {
        try {
            mailboxService.claimAllMails(userId);
            return ResponseEntity.ok().body(java.util.Map.of(
                "status", 200,
                "message", "Nhận tất cả quà thành công!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "status", 400,
                "message", e.getMessage()
            ));
        }
    }
}
