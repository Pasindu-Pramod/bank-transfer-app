package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    public static final String BANK_FEE_ACCOUNT = "999999";
    private static final BigDecimal FEE_RATE = new BigDecimal("0.01");

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    public List<Customer> getCustomers(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return customerRepository.searchByName(keyword.trim());
        }
        return customerRepository.findAllOrdered();
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAllOrdered();
    }

    public Customer getCustomerByAccount(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    public long getTotalCustomers() {
        return customerRepository.countCustomersNative();
    }

    public BigDecimal getTotalBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<BankTransaction> getLatestTransactions() {
        return transactionRepository.findAllLatest();
    }

    public long getTransactionCountByStatus(TransactionStatus status) {
        return transactionRepository.countByStatus(status);
    }

    public List<BankTransaction> getAccountHistory(String accountNumber) {
        return transactionRepository.findHistory(accountNumber);
    }

    public BigDecimal getTotalSent(String accountNumber) {
        return transactionRepository.getTotalSentByAccount(accountNumber);
    }

    public List<BankTransaction> getLargeTransfers(BigDecimal min) {
        return transactionRepository.findLargeTransfers(min);
    }

    @Transactional(rollbackFor = Exception.class)
    public Customer createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account number " + customer.getAccountNumber() + " already exists");
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankException("Email " + customer.getEmail() + " already exists");
        }
        return customerRepository.save(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure) {
        // Step 1: Validate accounts are different and both exist
        if (fromAccount.equals(toAccount)) {
            throw new BankException("Sender and receiver must be different accounts");
        }
        customerRepository.getByAccountNumber(fromAccount)
                .orElseThrow(() -> new AccountNotFoundException(fromAccount));
        customerRepository.getByAccountNumber(toAccount)
                .orElseThrow(() -> new AccountNotFoundException(toAccount));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Transfer amount must be greater than zero");
        }

        // Calculate 1% transfer fee (Task 12)
        BigDecimal fee = amount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDebit = amount.add(fee);

        // Step 2: Atomically debit sender (amount + 1% fee)
        int debitedRows = customerRepository.debit(fromAccount, totalDebit);
        if (debitedRows == 0) {
            throw new InsufficientBalanceException(fromAccount);
        }

        // Step 3: Simulated crash experiment (after debit, before credit)
        if (simulateFailure) {
            throw new BankException("Simulated system failure after the withdrawal. All changes were rolled back.");
        }

        // Step 4: Credit receiver and credit 1% fee to bank account 999999
        int creditedReceiver = customerRepository.credit(toAccount, amount);
        if (creditedReceiver == 0) {
            throw new AccountNotFoundException(toAccount);
        }

        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            int creditedFee = customerRepository.credit(BANK_FEE_ACCOUNT, fee);
            if (creditedFee == 0) {
                throw new AccountNotFoundException(BANK_FEE_ACCOUNT);
            }
        }

        // Step 5: Save SUCCESS transaction record (records totalDebit including fee)
        BankTransaction tx = new BankTransaction(
                fromAccount,
                toAccount,
                totalDebit,
                TransactionStatus.SUCCESS,
                "Transfer completed (incl. LKR " + fee + " fee to " + BANK_FEE_ACCOUNT + ")"
        );
        return transactionRepository.save(tx);
    }

    // Task 12.3: Deposit money inside @Transactional
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Deposit amount must be greater than zero");
        }
        int updated = customerRepository.credit(accountNumber, amount);
        if (updated == 0) {
            throw new AccountNotFoundException(accountNumber);
        }
        BankTransaction tx = new BankTransaction(
                "CASH",
                accountNumber,
                amount,
                TransactionStatus.SUCCESS,
                "Cash deposit"
        );
        return transactionRepository.save(tx);
    }

    // Task 12.3: Withdraw money inside @Transactional
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Withdrawal amount must be greater than zero");
        }
        customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        int updated = customerRepository.debit(accountNumber, amount);
        if (updated == 0) {
            throw new InsufficientBalanceException(accountNumber);
        }
        BankTransaction tx = new BankTransaction(
                accountNumber,
                "CASH",
                amount,
                TransactionStatus.SUCCESS,
                "Cash withdrawal"
        );
        return transactionRepository.save(tx);
    }
}