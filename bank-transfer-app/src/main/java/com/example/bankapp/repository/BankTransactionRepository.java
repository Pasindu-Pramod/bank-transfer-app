package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    // 1. All transactions, newest first
    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findAllLatest();

    // 2. Account history (sender OR receiver)
    @Query("SELECT t FROM BankTransaction t WHERE t.fromAccount = :accountNumber OR t.toAccount = :accountNumber ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findHistory(@Param("accountNumber") String accountNumber);

    // 3. Filter by status
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    // 4. Count by status for dashboard stats
    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    // Task 12.1: Large successful transfers strictly above :min
    @Query("SELECT t FROM BankTransaction t WHERE t.status = com.example.bankapp.model.TransactionStatus.SUCCESS AND t.amount > :min ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findLargeTransfers(@Param("min") BigDecimal min);

    // Task 12.2: Total amount sent by an account (SUM of SUCCESS transfers)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BankTransaction t WHERE t.fromAccount = :accountNumber AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS")
    BigDecimal getTotalSentByAccount(@Param("accountNumber") String accountNumber);
}