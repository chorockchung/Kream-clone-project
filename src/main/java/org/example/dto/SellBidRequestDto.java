package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.domain.BidType;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SellBidRequestDto {

    @NotNull(message = "가격은 필수입니다.")
    @Positive(message = "가격은 0보다 커야 합니다.")
    private int price;

    private BidType bidType;

    @NotBlank
    private String bankCode;
    @NotBlank
    private String accountNumber;
    @NotBlank
    private String accountHolder;

}
