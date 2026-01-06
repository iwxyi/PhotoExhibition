package com.photoexhibition.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonSimilarityDTO {
    private Long personId;
    private String personName;
    private double similarity;
}
