package org.example.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 체결된 구매 입찰
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_bid_id")
    private Bid buyBid;

    // 체결된 판매 입찰
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sell_bid_id")
    private Bid sellBid;

    // 구매자 (조회 편의를 위해 비정규화해서 직접 참조해도 좋음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    // 판매자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_size_id")
    private ProductSize productSize;

    private int price; // 체결가

    @Enumerated(EnumType.STRING)
    private TradeStatus tradeStatus; // MATCHED, SHIPPING, INSPECTING, COMPLETED, CANCELLED 등

    private LocalDateTime tradedAt;
}