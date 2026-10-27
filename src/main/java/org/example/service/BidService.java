package org.example.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.domain.*;
import org.example.dto.BuyBidRequestDto;
import org.example.dto.SellBidRequestDto;
import org.example.repository.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BidService {

    private final BidRepository bidRepository;
    private final ProductSizeRepository productSizeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TradeRepository tradeRepository;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate; // 트랜잭션 수동 관리용
    private final PriceCacheService priceCacheService;


    public Long createSellBid(Long productId, int size, SellBidRequestDto bidRequestDto, Long userId) {

        User user = userRepository.findById(userId).orElseThrow(EntityNotFoundException::new);
        ProductSize productSize = productSizeRepository.findByProductAndSize(productId, size);

        Bid sellBid = Bid.builder()
                .user(user)
                .price(bidRequestDto.getPrice())
                .productSize(productSize)
                .bidType(bidRequestDto.getBidType())
                .bidStatus(BidStatus.WAITING)
                .size(size)
                .createdAt(LocalDateTime.now())
                .build();

        Bid saveBid = bidRepository.save(sellBid);

        if (bidRequestDto.getBidType() == BidType.SELL_INSTANT) {
            matchWithBestBuyBid(productSize.getId(), saveBid);
        }

        return saveBid.getId();
    }

    
    public Long createBuyBid(Long productId, int size, BuyBidRequestDto bidRequestDto, Long userId) {

        User user = userRepository.findById(userId).orElseThrow(EntityNotFoundException::new);
        ProductSize productSize = productSizeRepository.findByProductAndSize(productId, size);

        Bid buyBid = Bid.builder()
                .user(user)
                .price(bidRequestDto.getPrice())
                .productSize(productSize)
                .bidType(bidRequestDto.getBidType())
                .bidStatus(BidStatus.WAITING)
                .size(size)
                .createdAt(LocalDateTime.now())
                .build();

        Bid saveBid = bidRepository.save(buyBid);

        if(bidRequestDto.getBidType() == BidType.BUY_INSTANT) {
            matchWithBestSellBid(productSize.getId(), buyBid);
        }

        return saveBid.getId();
    }


    private void matchWithBestBuyBid(Long productSizeId, Bid sellBid) {

        //구매 입찰 탐색
        Bid candidate = bidRepository.findFirstByProductSize_IdAndBidTypeAndBidStatusOrderByPriceAscCreatedAtAsc(productSizeId, BidType.BUY_BID, BidStatus.WAITING);

        String lockKey = "lock:bid:" + candidate.getId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean available = lock.tryLock(5, 5, TimeUnit.SECONDS);
            if(!available) {
                throw new IllegalStateException("현재 요청이 많아 처리할 수 없습니다.");
            }

            // 락 안에서 트랜잭션 경계를 명시적으로 만들어서 재조회 + 상태변경 + 저장을 한 번에 커밋
            transactionTemplate.execute(status -> {
                Bid buyBid = bidRepository.findById(candidate.getId())
                        .orElseThrow(EntityNotFoundException::new);

                if (buyBid.getBidStatus() != BidStatus.WAITING) {
                    throw new IllegalStateException("이미 매칭된 입찰입니다.");
                }

                buyBid.match();      // ⭐ 빠져있던 상태 변경
                sellBid.match();     // ⭐ 이것도 필요
                bidRepository.save(buyBid);   // 명시적으로 저장 (안전하게)
                bidRepository.save(sellBid);

                matchBid(buyBid, sellBid);
                return null;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    private void matchWithBestSellBid(Long productSizeId, Bid buyBid) {

        //구매 입찰 탐색
        Bid candidate = bidRepository.findFirstByProductSize_IdAndBidTypeAndBidStatusOrderByPriceAscCreatedAtAsc(productSizeId, BidType.SELL_BID, BidStatus.WAITING);

        String lockKey = "lock:bid:" + candidate.getId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean available = lock.tryLock(5, 5, TimeUnit.SECONDS);
            if(!available) {
                throw new IllegalStateException("현재 요청이 많아 처리할 수 없습니다.");
            }

            // 락 안에서 트랜잭션 경계를 명시적으로 만들어서 재조회 + 상태변경 + 저장을 한 번에 커밋
            transactionTemplate.execute(status -> {
                Bid sellBid = bidRepository.findById(candidate.getId())
                        .orElseThrow(EntityNotFoundException::new);

                if (sellBid.getBidStatus() != BidStatus.WAITING) {
                    throw new IllegalStateException("이미 매칭된 입찰입니다.");
                }

                buyBid.match();      // ⭐ 빠져있던 상태 변경
                sellBid.match();     // ⭐ 이것도 필요
                bidRepository.save(buyBid);   // 명시적으로 저장 (안전하게)
                bidRepository.save(sellBid);

                matchBid(buyBid, sellBid);
                return null;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    public void matchBid(Bid buyBid, Bid sellBid) {
        //락을 위해 상태값 변경은 create 메서드 안에서 진행
        // 2. Trade 레코드 생성
        Trade trade = Trade.builder()
                .buyBid(buyBid)
                .sellBid(sellBid)
                .buyer(buyBid.getUser())
                .seller(sellBid.getUser())
                .productSize(buyBid.getProductSize())
                .price(sellBid.getPrice()) // 보통 먼저 등록된 쪽 가격으로 체결
                .tradeStatus(TradeStatus.MATCHED)
                .tradedAt(LocalDateTime.now())
                .build();

        tradeRepository.save(trade);

        // ⭐ Write-Through: DB 저장 직후 캐시도 최신화
        priceCacheService.updateCurrentPrice(
                trade.getProductSize().getId(),
                trade.getPrice()
        );
    }
}
