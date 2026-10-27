package org.example.repository;

import org.example.domain.ProductSize;
import org.example.dto.ProductSizeResponseDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductSizeRepository extends JpaRepository<ProductSize, Long> {

    @Query("SELECt ps FROM ProductSize ps WHERE ps.product.id = :productId AND ps.size = :size")
    ProductSize findByProductAndSize(@Param("productId") Long productId, @Param("size") int size);

    //ProductSize findByProductAndSize(Long productId, int size);

    //@Query("SELECT size, price FROM ProductSize ps WHERE ps.product.id "= :productId)
}
