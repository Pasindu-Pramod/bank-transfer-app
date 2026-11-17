package com.example.bankapp.config;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) {
        if (customerRepository.count() == 0) {
            customerRepository.saveAll(List.of(
                    new Customer("Kamal Silva", "kamal@example.com", "100001", new BigDecimal("50000.00")),
                    new Customer("Nimali Perera", "nimali@example.com", "100002", new BigDecimal("25000.00")),
                    new Customer("Saman Kumara", "saman@example.com", "100003", new BigDecimal("10000.00")),
                    new Customer("Bank Fee Account", "fees@bank.com", "999999", new BigDecimal("0.00"))
            ));
        }
    }
}