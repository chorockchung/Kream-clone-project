package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.ProductSizeResponseDto;
import org.example.repository.BidRepository;
import org.example.repository.ProductSizeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSizeService {

    private final ProductSizeRepository productSizeRepository;
    private final BidRepository bidRepository;

    public List<ProductSizeResponseDto> getProductSizesPriceForSale(Long productId) {
        return bidRepository.findSizeAndPriceByProductIdForSale(productId);
    }

    public List<ProductSizeResponseDto> getProductSizesPriceForBuy(Long productId) {
        return bidRepository.findSizeAndPriceByProductIdForBuy(productId);
    }
}
