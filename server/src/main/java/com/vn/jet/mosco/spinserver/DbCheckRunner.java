package com.vn.jet.mosco.spinserver;

import com.vn.jet.mosco.spinserver.repository.CardRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DbCheckRunner implements CommandLineRunner {
    private final CardRepository cardRepository;

    public DbCheckRunner(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @Override
    public void run(String... args) {
        System.out.println("DEBUG_DB_CHECK: Card count = " + cardRepository.count());
    }
}
