package org.example.service;

import org.example.domain.*;
import org.example.dto.BuyBidRequestDto;
import org.example.dto.SellBidRequestDto;
import org.example.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BidServiceConcurrencyTest {

    @Autowired private BidService bidService;
    @Autowired private BidRepository bidRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSizeRepository productSizeRepository;

    private Long productId;
    private int size = 250;
    private Long buyBidId;
    private Long sellBidId;

    @BeforeEach
    void setUp10Seller1Buyer() {
        // 1. 상품/사이즈 준비
        Product product = productRepository.save(
                Product.builder().name("Test Sneaker").build()
        );
        ProductSize productSize = productSizeRepository.save(
                ProductSize.builder().product(product).size(size).build()
        );
        productId = product.getId();

//        // 2. 구매자 1명 + 대기 중인 BUY_BID 1건 생성 (이게 여러 판매자가 노릴 타겟)
//        User buyer = userRepository.save(
//                User.builder().name("buyer1").build()
//        );
//
//        Bid buyBid = Bid.builder()
//                .user(buyer)
//                .price(100000)
//                .productSize(productSize)
//                .bidType(BidType.BUY_BID)
//                .bidStatus(BidStatus.WAITING)
//                .size(size)
//                .createdAt(LocalDateTime.now())
//                .build();
//
//        buyBidId = bidRepository.save(buyBid).getId();

//        // 3. 판매자 10명 준비 (동시에 이 buyBid 하나를 노릴 사람들)
//        for (int i = 0; i < 10; i++) {
//            userRepository.save(User.builder().name("seller" + i).build());
//        }

        // 4. 판매자 1명 + 대기 중인 SELL_BID 1건
        User seller = userRepository.save(User.builder().name("seller1").build());

        Bid sellBid = Bid.builder()
                .user(seller)
                .price(100000)
                .productSize(productSize)
                .bidType(BidType.SELL_BID)
                .bidStatus(BidStatus.WAITING)
                .size(size)
                .createdAt(LocalDateTime.now())
                .build();

        sellBidId = bidRepository.save(sellBid).getId();

        // 구매자 10명 준비
        for (int i = 0; i < 10; i++) {
            userRepository.save(User.builder().name("buyer" + i).build());
        }
    }

    @AfterEach
    void tearDown() {
        tradeRepository.deleteAll();
        bidRepository.deleteAll();
        productSizeRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

//    @Test
//    void multiple_sellers_seek_the_same_buyBid() throws InterruptedException {
//        int threadCount = 10;
//        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch readyLatch = new CountDownLatch(threadCount); // 스레드들이 동시에 출발하도록 정렬
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch doneLatch = new CountDownLatch(threadCount);
//
//        AtomicInteger successCount = new AtomicInteger();
//        AtomicInteger failCount = new AtomicInteger();
//
//        // seller 목록 조회 (setUp에서 만든 10명)
//        var sellers = userRepository.findAll().stream()
//                .filter(u -> u.getName().startsWith("seller"))
//                .toList();
//
//        for (User seller : sellers) {
//            executor.submit(() -> {
//                try {
//                    readyLatch.countDown();
//                    startLatch.await(); // 모든 스레드가 동시에 시작하도록 대기
//
//                    SellBidRequestDto dto = new SellBidRequestDto(100000, BidType.SELL_INSTANT, "01", "1111-2222", "james");
//                    bidService.createSellBid(productId, size, dto, seller.getId());
//
//                    successCount.incrementAndGet();
//                } catch (IllegalStateException e) {
//                    failCount.incrementAndGet();
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                } finally {
//                    doneLatch.countDown();
//                }
//            });
//        }
//
//        readyLatch.await();       // 모든 스레드가 준비될 때까지 대기
//        startLatch.countDown();   // 동시에 출발!
//        doneLatch.await(10, TimeUnit.SECONDS); // 전체 종료 대기
//        executor.shutdown();
//
//        // ===== 검증 =====
//
//        // 1. 정확히 1명만 성공해야 함
//        assertThat(successCount.get()).isEqualTo(1);
//        assertThat(failCount.get()).isEqualTo(9);
//
//        // 2. Trade는 정확히 1건만 생성되어야 함
//        assertThat(tradeRepository.count()).isEqualTo(1);
//
//        // 3. buyBid 상태는 MATCHED로 단 한 번만 바뀌어야 함
//        Bid matchedBuyBid = bidRepository.findById(buyBidId).orElseThrow();
//        assertThat(matchedBuyBid.getBidStatus()).isEqualTo(BidStatus.MATCHED);
//
//        // 4. sellBid 중 MATCHED 상태인 것도 정확히 1건이어야 함
//        long matchedSellBidCount = bidRepository.findAll().stream()
//                .filter(b -> b.getBidType() == BidType.SELL_INSTANT)
//                .filter(b -> b.getBidStatus() == BidStatus.MATCHED)
//                .count();
//        assertThat(matchedSellBidCount).isEqualTo(1);
//
//        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
//        System.out.println("생성된 Trade 수: " + tradeRepository.count());
//    }

    @Test
    void multiple_buyers_seek_the_same_sellBid() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        var buyers = userRepository.findAll().stream()
                .filter(u -> u.getName().startsWith("buyer"))
                .toList();

        for (User buyer : buyers) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    BuyBidRequestDto dto = new BuyBidRequestDto(100000, BidType.BUY_INSTANT);
                    bidService.createBuyBid(productId, size, dto, buyer.getId());

                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // ===== 수정된 검증 로직 =====

        // 1. 정확히 1명만 성공하고 9명은 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // 2. Trade는 정확히 1건만 생성
        assertThat(tradeRepository.count()).isEqualTo(1);

        // 3. 기존 SELL_BID 상태가 MATCHED로 변경되었는지 확인
        Bid matchedSellBid = bidRepository.findById(sellBidId).orElseThrow();
        assertThat(matchedSellBid.getBidStatus()).isEqualTo(BidStatus.MATCHED);

        // 4. 구매자가 생성한 BUY_INSTANT 중 MATCHED 상태인 것이 1건인지 확인
        long matchedBuyBidCount = bidRepository.findAll().stream()
                .filter(b -> b.getBidType() == BidType.BUY_INSTANT)
                .filter(b -> b.getBidStatus() == BidStatus.MATCHED)
                .count();
        assertThat(matchedBuyBidCount).isEqualTo(1);

        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
        System.out.println("생성된 Trade 수: " + tradeRepository.count());
    }

}