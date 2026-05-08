package com.vn.jet.mosco.spinserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO cho danh sách thẻ bài.
 * Sử dụng JPA Projection để tối ưu hiệu năng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardSummaryDto {
    private String id;
    private String memberName;
    private String seasonName;
    private String thumbnailId;
}
