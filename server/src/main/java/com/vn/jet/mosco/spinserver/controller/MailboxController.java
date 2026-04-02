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

    public MailboxController(UserMailRepository userMailRepository) {
        this.userMailRepository = userMailRepository;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<UserMail>> getUserMails(@PathVariable Long userId) {
        List<UserMail> mails = userMailRepository.findByUserId(userId);
        return ResponseEntity.ok(mails);
    }
}
