package org.example;

import org.example.domain.*;
import org.example.repository.*;
import org.example.service.BidService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.example.config.DummyDataInitializer;   // ⭐ 이 줄 추


//TIP 코드를 <b>실행</b>하려면 <shortcut actionId="Run"/>을(를) 누르거나
// 에디터 여백에 있는 <icon src="AllIcons.Actions.Execute"/> 아이콘을 클릭하세요.
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    @Profile("seed")
    public CommandLineRunner dummyDataInitializer(
            UserRepository userRepository,
            ProductRepository productRepository,
            ProductSizeRepository productSizeRepository,
            BidRepository bidRepository,
            TradeRepository tradeRepository) {
        return new DummyDataInitializer(
                userRepository, productRepository, productSizeRepository,
                bidRepository, tradeRepository);
    }
}