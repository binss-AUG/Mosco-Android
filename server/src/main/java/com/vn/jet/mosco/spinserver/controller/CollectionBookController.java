package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.CollectionBookResponse;
import com.vn.jet.mosco.spinserver.service.CollectionBookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller cho tính năng Bộ Sưu Tập (Collection Book).
 * Cung cấp endpoint để Client lấy danh sách toàn bộ thẻ bài
 * kèm trạng thái sở hữu của user.
 */
@RestController
@RequestMapping("/api/collection")
public class CollectionBookController {

    private final CollectionBookService collectionBookService;

    public CollectionBookController(CollectionBookService collectionBookService) {
        this.collectionBookService = collectionBookService;
    }

    /**
     * Lấy Bộ Sưu Tập đầy đủ cho user.
     * Trả về tất cả thẻ trong hệ thống + đánh dấu thẻ đã sở hữu.
     *
     * Method: GET
     * URL: /api/collection/book/{userId}
     * Response: CollectionBookResponse (totalCards, ownedCount, entries[])
     */
    @GetMapping("/book/{userId}")
    public ResponseEntity<CollectionBookResponse> getCollectionBook(@PathVariable Long userId) {
        CollectionBookResponse response = collectionBookService.getCollectionBook(userId);
        return ResponseEntity.ok(response);
    }
}
