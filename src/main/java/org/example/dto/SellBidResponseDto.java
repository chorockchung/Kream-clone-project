package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SellBidResponseDto {
    @NotNull(message = "가격은 필수입니다.")
    @Positive(message = "가격은 0보다 커야 합니다.")
    private int price;

    @NotBlank
    private String bankCode;
    @NotBlank
    private String accountNumber;
    @NotBlank
    private String accountHolder;
}
