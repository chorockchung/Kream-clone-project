package org.example.config;

import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import org.example.domain.*;
import org.example.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class DummyDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final BidRepository bidRepository;
    private final TradeRepository tradeRepository;

    private static final int[] SIZES = {230, 240, 250, 260, 270, 280, 290};
    private static final int BATCH_SIZE = 1000;
    private final Random random = new Random();

    @Override
    @Transactional
    public void run(String... args) {
        Faker faker = new Faker(new Locale("ko"));

        // 1. 유저 생성 (100명) //테스트 : 10명 으로 수정
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            users.add(User.builder()
                    .name(faker.name().fullName())
                    .email(faker.internet().emailAddress())
                    .accountNumber(faker.finance().iban())
                    .build());
        }
        users = userRepository.saveAll(users);

        // 2. 상품 생성 (총 20개 - 0번 상품을 '인기 상품'으로 지정) //테스트 : 5명으로 수정
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String prefix = (i == 0) ? "[🔥HOT] " : "";
            products.add(Product.builder()
                    .name(prefix + faker.commerce().productName())
                    .brand(faker.company().name())
                    .modelNumber(faker.bothify("??-####-###").toUpperCase())
                    .build());
        }
        products = productRepository.saveAll(products);

        // 3. 상품별 사이즈 생성
        List<ProductSize> productSizes = new ArrayList<>();
        for (Product product : products) {
            for (int size : SIZES) {
                productSizes.add(ProductSize.builder()
                        .product(product)
                        .size(size)
                        .build());
            }
        }
        productSizes = productSizeRepository.saveAll(productSizes);

        // 4. 입찰(Bid) 데이터 생성
        //    Trade 매칭을 위해 대기 중인 Bid 외에도 충분한 수의 Bid를 먼저 저장합니다.
        List<Bid> allBids = new ArrayList<>();
        for (ProductSize productSize : productSizes) {
            int basePrice = 100_000 + random.nextInt(200_000);

            // 매수/매도 입찰 대량 생성
            createBidsForSize(allBids, productSize, users, basePrice, BidType.BUY_BID, -0.1, 0.02);
            createBidsForSize(allBids, productSize, users, basePrice, BidType.SELL_BID, -0.02, 0.1);

            if (allBids.size() >= BATCH_SIZE) {
                bidRepository.saveAll(allBids);
                allBids.clear();
            }
        }
        if (!allBids.isEmpty()) {
            bidRepository.saveAll(allBids);
            allBids.clear();
        }

        // 빠른 매칭 처리를 위해 저장된 전체 WAITING 상태의 Bid들을 메모리 맵으로 그룹화
        List<Bid> savedBids = bidRepository.findAll();
        Map<Long, List<Bid>> waitingBuyBidsMap = savedBids.stream()
                .filter(b -> b.getBidType() == BidType.BUY_BID && b.getBidStatus() == BidStatus.WAITING)
                .collect(Collectors.groupingBy(b -> b.getProductSize().getId()));

        Map<Long, List<Bid>> waitingSellBidsMap = savedBids.stream()
                .filter(b -> b.getBidType() == BidType.SELL_BID && b.getBidStatus() == BidStatus.WAITING)
                .collect(Collectors.groupingBy(b -> b.getProductSize().getId()));

        // 5. Trade(체결 이력) 시딩 및 Bid 상태(MATCHED) 업데이트
        Product hotProduct = products.get(0);
        List<ProductSize> hotSizes = productSizes.stream()
                .filter(ps -> ps.getProduct().getId().equals(hotProduct.getId()))
                .toList();

        List<ProductSize> normalSizes = productSizes.stream()
                .filter(ps -> !ps.getProduct().getId().equals(hotProduct.getId()))
                .toList();

        List<Trade> tradesToSave = new ArrayList<>();

        // 5-A. 인기 상품: 20,000건의 Trade 및 Bid 매칭 처리
        System.out.println("🔥 인기 상품 대량 Trade 시딩 & Bid MATCHED 처리 시작 (30건)...");
        generateTradesAndMatchBids(tradesToSave, hotSizes, users, 30, 250_000, waitingBuyBidsMap, waitingSellBidsMap); //테스트 : totalCount 20000 -> 30

        // 5-B. 일반 상품: ~2,000건 Trade 및 Bid 매칭 처리
        System.out.println("📦 일반 상품 Trade 시딩 & Bid MATCHED 처리 시작 (30건)...");
        generateTradesAndMatchBids(tradesToSave, normalSizes, users, 30, 150_000, waitingBuyBidsMap, waitingSellBidsMap); //테스트 : totalCount 2000 -> 30

        // MATCHED로 변경된 Bid 상태들을 DB에 일괄 저장
        bidRepository.saveAll(savedBids);

        System.out.println("✅ 더미 데이터 시딩 완료!");
        System.out.println("   - Users: " + users.size());
        System.out.println("   - Products: " + products.size());
        System.out.println("   - ProductSizes: " + productSizes.size());
        System.out.println("   - Total Trades: ~22,000건");
    }

    private void createBidsForSize(List<Bid> bidsToSave, ProductSize productSize, List<User> users,
                                   int basePrice, BidType type, double minRatio, double maxRatio) {
        // Trade 횟수 대비 매칭시킬 Bid가 부족하지 않도록 넉넉히 생성 (개당 150~200건)
//        int count = 150 + random.nextInt(50);
        int count = 3 + random.nextInt(3); // 150~200 → 3~5건
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            bidsToSave.add(Bid.builder()
                    .productSize(productSize)
                    .size(productSize.getSize())
                    .user(user)
                    .price(adjustPrice(basePrice, minRatio, maxRatio))
                    .bidType(type)
                    .bidStatus(BidStatus.WAITING)
                    .createdAt(randomRecentTime(30))
                    .build());
        }
    }

    private void generateTradesAndMatchBids(List<Trade> tradesToSave, List<ProductSize> sizes, List<User> users,
                                            int totalCount, int basePrice,
                                            Map<Long, List<Bid>> buyBidsMap, Map<Long, List<Bid>> sellBidsMap) {
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < totalCount; i++) {
            ProductSize size = sizes.get(random.nextInt(sizes.size()));
            Long sizeId = size.getId();

            List<Bid> buyBids = buyBidsMap.getOrDefault(sizeId, Collections.emptyList());
            List<Bid> sellBids = sellBidsMap.getOrDefault(sizeId, Collections.emptyList());

            User buyer;
            User seller;

            // 1. 해당 ProductSize에 WAITING 상태인 BUY/SELL 입찰이 있다면 MATCHED 상태로 변경
            if (!buyBids.isEmpty()) {
                Bid buyBid = buyBids.remove(buyBids.size() - 1);
                buyBid.match(); // 상태값 MATCHED로 변경
                buyer = buyBid.getUser();
            } else {
                buyer = users.get(random.nextInt(users.size()));
            }

            if (!sellBids.isEmpty()) {
                Bid sellBid = sellBids.remove(sellBids.size() - 1);
                sellBid.match(); // 상태값 MATCHED로 변경
                seller = sellBid.getUser();
            } else {
                seller = users.get(random.nextInt(users.size()));
            }

            // 구매자와 판매자가 같은 경우 예외 처리
            if (seller.getId().equals(buyer.getId())) {
                seller = users.get(random.nextInt(users.size()));
            }

            LocalDateTime tradeTime = now.minusMinutes(random.nextInt(60 * 24 * 30));
            int price = adjustPrice(basePrice, -0.15, 0.15);

            tradesToSave.add(Trade.builder()
                    .productSize(size)
                    .buyer(buyer)
                    .seller(seller)
                    .price(price)
                    .tradeStatus(TradeStatus.MATCHED)
                    .tradedAt(tradeTime)
                    .build());

            if (tradesToSave.size() >= BATCH_SIZE) {
                tradeRepository.saveAll(tradesToSave);
                tradesToSave.clear();
            }
        }

        if (!tradesToSave.isEmpty()) {
            tradeRepository.saveAll(tradesToSave);
            tradesToSave.clear();
        }
    }

    private int adjustPrice(int basePrice, double minRatio, double maxRatio) {
        double ratio = minRatio + (maxRatio - minRatio) * random.nextDouble();
        int price = (int) (basePrice * (1 + ratio));
        return Math.max(10_000, (price / 1000) * 1000);
    }

    private LocalDateTime randomRecentTime(int days) {
        return LocalDateTime.now().minusMinutes(random.nextInt(60 * 24 * days));
    }
}