package org.example.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.repository.TradeRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductPriceService {

    private final PriceCacheService priceCacheService;
    private final TradeRepository tradeRepository;

    public int getCurrentPrice(Long productSizeId) {
        // 1. 캐시 먼저 확인
        return priceCacheService.getCurrentPrice(productSizeId)
                .orElseGet(() -> {
                    // 2. 캐시 미스 시 DB에서 조회
                    int price = tradeRepository
                            .findFirstByProductSize_IdOrderByTradedAtDesc(productSizeId)
                            .map(trade -> trade.getPrice())
                            .orElseThrow(() -> new EntityNotFoundException("거래 내역이 없습니다."));

                    // 3. 조회한 값 캐시에 채워넣기 (다음 요청부터는 캐시 히트)
                    priceCacheService.updateCurrentPrice(productSizeId, price);
                    return price;
                });
    }
}