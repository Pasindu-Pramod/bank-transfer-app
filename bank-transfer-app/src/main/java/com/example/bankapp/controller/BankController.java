package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String keyword, Model model) {
        loadDashboard(model, keyword);
        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }
        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", new Customer());
        }
        return "bank";
    }

    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("customer") Customer customer,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            loadDashboard(model, null);
            model.addAttribute("transferRequest", new TransferRequest());
            return "bank";
        }
        try {
            bankService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("success",
                    "Customer " + customer.getFullName() + " added successfully.");
        } catch (BankException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferRequest") TransferRequest request,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            loadDashboard(model, null);
            model.addAttribute("customer", new Customer());
            return "bank";
        }
        try {
            bankService.transfer(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.isSimulateFailure()
            );
            redirectAttributes.addFlashAttribute("success",
                    "Successfully transferred LKR " + request.getAmount()
                            + " from " + request.getFromAccount()
                            + " to " + request.getToAccount() + ".");
        } catch (BankException ex) {
            // Transfer transaction rolled back; record FAILED audit entry in REQUIRES_NEW transaction
            auditService.recordFailure(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    ex.getMessage()
            );
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/accounts/{accountNumber}/history")
    public String accountHistory(@PathVariable String accountNumber, Model model) {
        Customer customer = bankService.getCustomerByAccount(accountNumber);
        model.addAttribute("customer", customer);
        model.addAttribute("transactions", bankService.getAccountHistory(accountNumber));
        model.addAttribute("totalSent", bankService.getTotalSent(accountNumber));
        return "history";
    }

    // Task 12.1: Large transfers page
    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(defaultValue = "10000") BigDecimal min, Model model) {
        model.addAttribute("min", min);
        model.addAttribute("transfers", bankService.getLargeTransfers(min));
        return "large-transfers";
    }

    // Task 12.3: Deposit money
    @GetMapping("/deposit")
    public String showDepositForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers());
        return "deposit";
    }

    @PostMapping("/deposit")
    public String processDeposit(@RequestParam String accountNumber,
                                 @RequestParam BigDecimal amount,
                                 RedirectAttributes redirectAttributes) {
        try {
            bankService.deposit(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success",
                    "Deposited LKR " + amount + " into account " + accountNumber + ".");
        } catch (BankException ex) {
            auditService.recordFailure("CASH", accountNumber, amount, ex.getMessage());
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/deposit";
    }

    // Task 12.3: Withdraw money
    @GetMapping("/withdraw")
    public String showWithdrawForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers());
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String processWithdraw(@RequestParam String accountNumber,
                                  @RequestParam BigDecimal amount,
                                  RedirectAttributes redirectAttributes) {
        try {
            bankService.withdraw(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success",
                    "Withdrew LKR " + amount + " from account " + accountNumber + ".");
        } catch (BankException ex) {
            auditService.recordFailure(accountNumber, "CASH", amount, ex.getMessage());
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/withdraw";
    }

    private void loadDashboard(Model model, String keyword) {
        List<Customer> customers = bankService.getCustomers(keyword);
        model.addAttribute("customers", customers);
        model.addAttribute("allCustomers", bankService.getAllCustomers());
        model.addAttribute("keyword", keyword);
        model.addAttribute("totalCustomers", bankService.getTotalCustomers());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        model.addAttribute("successCount", bankService.getTransactionCountByStatus(TransactionStatus.SUCCESS));
        model.addAttribute("failedCount", bankService.getTransactionCountByStatus(TransactionStatus.FAILED));
        model.addAttribute("transactions", bankService.getLatestTransactions());
    }
}