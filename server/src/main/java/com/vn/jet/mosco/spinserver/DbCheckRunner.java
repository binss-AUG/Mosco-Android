package com.vn.jet.mosco.spinserver;

import com.vn.jet.mosco.spinserver.repository.CardRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DbCheckRunner implements CommandLineRunner {
    private final CardRepository cardRepository;

    public DbCheckRunner(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @Override
    public void run(String... args) {
        // TẠI SAO: Đổi sang ghi log.info của SLF4J để ghi nhận chuẩn log, không gây lock I/O do System.out
        log.info("DEBUG_DB_CHECK: Card count = {}", cardRepository.count());
    }
}
