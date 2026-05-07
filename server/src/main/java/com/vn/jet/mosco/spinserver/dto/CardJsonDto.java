package com.vn.jet.mosco.spinserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardJsonDto {
    private String id;
    private String season;
    private String member;
    private String artist;
    private String collectionNo;
    @com.fasterxml.jackson.annotation.JsonProperty("class")
    private String cardClass;
    private String frontImage;
    private String backImage;

    // Jackson mapping for "class" which is a reserved keyword
    public void setClass(String cardClass) {
        this.cardClass = cardClass;
    }
}
