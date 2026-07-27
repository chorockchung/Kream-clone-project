package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.BuyBidRequestDto;
import org.example.dto.SellBidRequestDto;
import org.example.dto.ProductSizeResponseDto;
import org.example.service.BidService;
import org.example.service.ProductSizeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bid/{productId}")
@RequiredArgsConstructor
public class BidController {
    private final BidService bidService;
    private final ProductSizeService productSizeService;

    //판매 버튼 클릭 후
    @GetMapping("/sell")
    public ResponseEntity<List<ProductSizeResponseDto>> getProductSizeForSale(@PathVariable Long productId) {

        return ResponseEntity.ok(productSizeService.getProductSizesPriceForSale(productId));
    }

    //구매 버튼 클릭 후
    @GetMapping("/buy")
    public ResponseEntity<List<ProductSizeResponseDto>> getProductSizeForBuy(@PathVariable Long productId) {

        return ResponseEntity.ok(productSizeService.getProductSizesPriceForBuy(productId));
    }


    //판매 과정
    @PostMapping("/sell/{size}")
    public ResponseEntity<Long> createSellBid(
            @PathVariable Long productId,
            @PathVariable int size,
            @RequestBody @Valid SellBidRequestDto requestDto,
            /*@AuthenticationPrincipal*/ Long memberId
    ) {
        Long response = bidService.createSellBid(productId, size, requestDto, memberId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //구매 과정
    @PostMapping("/buy/{size}")
    public ResponseEntity<Long> createBuyBid(
            @PathVariable Long productId,
            @PathVariable int size,
            @RequestBody @Valid BuyBidRequestDto requestDto,
            /*@AuthenticationPrincipal */ Long memberId
    ) {
        Long response = bidService.createBuyBid(productId, size, requestDto, memberId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
