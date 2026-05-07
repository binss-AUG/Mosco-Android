package com.vn.jet.mosco.spinserver.dto;

import lombok.Data;
import java.util.List;

@Data
public class DatabaseJsonWrapper {
    private List<CardJsonDto> collections;
}
