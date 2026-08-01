package org.example.service;

import org.example.config.DummyDataInitializer;
import org.example.domain.Product;
import org.example.domain.ProductSize;
import org.example.repository.ProductRepository;
import org.example.repository.ProductSizeRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")   // CI에서 제외
@SpringBootTest
@Import(DummyDataInitializer.class) // 👈 이 설정을 추가해주세요!
class ProductPriceCachePerformanceTest {

    @Autowired private ProductPriceService productPriceService;
    @Autowired private PriceCacheService priceCacheService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSizeRepository productSizeRepository;

    @Test
    void cache_apply_before_and_after_response_time_comparison() {
        // 1. 인기 상품(HOT) 중 ProductSize 하나 선택
        Product hotProduct = productRepository.findAll().stream()
                .filter(p -> p.getName().contains("HOT"))
                .findFirst()
                .orElseThrow();

        List<ProductSize> hotSizes = productSizeRepository.findAll().stream()
                .filter(ps -> ps.getProduct().getId().equals(hotProduct.getId()))
                .toList();

        Long productSizeId = hotSizes.get(0).getId();

        int iterations = 100;

        // 2. DB 직접 조회 성능 측정 (매번 캐시 비우고 측정)
        long dbTotalNanos = 0;
        for (int i = 0; i < iterations; i++) {
            priceCacheService.evict(productSizeId);
            long start = System.nanoTime();
            productPriceService.getCurrentPrice(productSizeId);
            dbTotalNanos += System.nanoTime() - start;
        }

        // 3. 캐시 조회 성능 측정 (캐시 채워진 상태로 반복)
        priceCacheService.evict(productSizeId);
        productPriceService.getCurrentPrice(productSizeId); // 캐시 채우기용 워밍업 1회

        long cacheTotalNanos = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            productPriceService.getCurrentPrice(productSizeId);
            cacheTotalNanos += System.nanoTime() - start;
        }

        double dbAvgMs = (dbTotalNanos / (double) iterations) / 1_000_000.0;
        double cacheAvgMs = (cacheTotalNanos / (double) iterations) / 1_000_000.0;

        System.out.println("========================================");
        System.out.printf("DB 조회 평균 응답시간   : %.3f ms (%d회 평균)%n", dbAvgMs, iterations);
        System.out.printf("캐시 조회 평균 응답시간 : %.3f ms (%d회 평균)%n", cacheAvgMs, iterations);
        System.out.printf("성능 개선율            : %.1f배%n", dbAvgMs / cacheAvgMs);
        System.out.println("========================================");

        // 캐시가 DB보다 확실히 빨라야 함
        assertThat(cacheAvgMs).isLessThan(dbAvgMs);
    }
}


