package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.ProductPriceService;
import org.example.service.ProductSizeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ProductController {

    private final ProductPriceService productPriceService;

    @GetMapping("/products/{productSizeId}/current-price")
    public ResponseEntity<Integer> getCurrentPrice(@PathVariable Long productSizeId) {
        return ResponseEntity.ok(productPriceService.getCurrentPrice(productSizeId));
    }
}
