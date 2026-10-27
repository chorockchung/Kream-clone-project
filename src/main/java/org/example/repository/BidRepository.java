package org.example.repository;

import org.example.domain.Bid;
import org.example.domain.BidStatus;
import org.example.domain.BidType;
import org.example.dto.ProductSizeResponseDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {
    //구매 입찰 최고 가격(대기 중인 것)
    @Query("SELECT new org.example.dto.ProductSizeResponseDto(b.size, MAX(b.price)) " +
            "From Bid b JOIN ProductSize ps ON b.productSize.id = ps.id  " +
            "WHERE ps.product.id = :productId AND b.bidStatus = WAITING " +
            "AND b.bidType = BUY_BID GROUP BY b.size")
    List<ProductSizeResponseDto> findSizeAndPriceByProductIdForSale(@Param("productId") Long productId);

    //판매 입찰 최저 가격(대기 중인 것)
    @Query("SELECT new org.example.dto.ProductSizeResponseDto(b.size, MIN(b.price)) " +
                  "From Bid b JOIN ProductSize ps ON b.productSize.id = ps.id  " +
                  "WHERE ps.product.id = :productId AND b.bidStatus = WAITING " +
                  "AND b.bidType = SELL_BID GROUP BY b.size")
    List<ProductSizeResponseDto> findSizeAndPriceByProductIdForBuy(@Param("productId") Long productId);

    Bid findFirstByProductSize_IdAndBidTypeAndBidStatusOrderByPriceAscCreatedAtAsc(
            Long productSizeId, BidType bidType, BidStatus bidStatus);
}
