package org.example.config;

import org.example.domain.BidStatus;
import org.example.repository.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Disabled("최초 1회 시딩용 - 재실행 시 데이터 중복 누적됨")
@Import(DummyDataInitializer.class) // 👈 이 설정을 추가해주세요!
class DummyDataInitializerTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSizeRepository productSizeRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Test
    @DisplayName("trade_cache")
    void run_success() throws Exception {
        // 직접 new로 생성해서 실행!
//        DummyDataInitializer initializer = new DummyDataInitializer(
//                userRepository, productRepository, productSizeRepository, bidRepository, tradeRepository
//        );
//
//        initializer.run(); // 더미 데이터 시딩 수행

        // then
        long userCount = userRepository.count();
        long productCount = productRepository.count();
        long productSizeCount = productSizeRepository.count();
        long tradeCount = tradeRepository.count();
        long matchedBidCount = bidRepository.findAll().stream()
                .filter(bid -> bid.getBidStatus() == BidStatus.MATCHED)
                .count();

        System.out.println("============== [검증 결과] ==============");
        System.out.println("유저 수: " + userCount);
        System.out.println("상품 수: " + productCount);
        System.out.println("상품 사이즈 수: " + productSizeCount);
        System.out.println("생성된 총 Trade 수: " + tradeCount);
        System.out.println("MATCHED 처리된 Bid 수: " + matchedBidCount);
        System.out.println("=========================================");

        // 수치 검증
        assertThat(userCount).isEqualTo(100);
        assertThat(productCount).isEqualTo(20);
        assertThat(productSizeCount).isEqualTo(140); // 20개 상품 * 7개 사이즈
        assertThat(tradeCount).isGreaterThanOrEqualTo(22000);
        assertThat(matchedBidCount).isGreaterThan(0);
    }
}