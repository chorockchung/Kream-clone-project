package org.example.domain;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Getter
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_size_id")
    private ProductSize productSize;

    private int size;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private int price;

    @Enumerated(EnumType.STRING)
    private BidType bidType;

    @Enumerated(EnumType.STRING)
    private BidStatus bidStatus;

    private LocalDateTime createdAt;

    // Bid.java에 추가
    public void match() {
        this.bidStatus = BidStatus.MATCHED;
    }

    public void cancel() {
        this.bidStatus = BidStatus.CANCELLED;
    }
}
