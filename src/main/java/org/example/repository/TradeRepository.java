package org.example.repository;

import org.example.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    Optional<Trade> findFirstByProductSize_IdOrderByTradedAtDesc(Long productSizeId);
}
