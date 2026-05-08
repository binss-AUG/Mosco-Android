package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.CardSummaryDto;
import com.vn.jet.mosco.spinserver.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardRepository cardRepository;

    /**
     * Endpoint lấy danh sách thẻ bài với phân trang và bộ lọc.
     * Sử dụng JPA Projection để tối ưu hóa payload và RAM.
     */
    @GetMapping
    public Page<CardSummaryDto> getCards(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return cardRepository.findCards(memberId, seasonId, search, pageable);
    }

    /**
     * Endpoint đồng bộ dữ liệu Delta (chỉ lấy những gì thay đổi).
     * @param lastSyncTime Timestamp dạng epoch (milliseconds)
     */
    @GetMapping("/sync")
    public java.util.List<CardSummaryDto> getUpdatedCards(@RequestParam Long lastSyncTime) {
        java.time.LocalDateTime syncTime = java.time.Instant.ofEpochMilli(lastSyncTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
        return cardRepository.findUpdatedCards(syncTime);
    }
}
