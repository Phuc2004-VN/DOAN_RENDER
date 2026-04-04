package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.Order;
import com.example.do_an_ck_J2EE.repository.OrderRepository;
import com.example.do_an_ck_J2EE.service.OrderCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderCleanupService orderCleanupService;

    @GetMapping
    public String listOrders(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(required = false) String fromDate,
                             @RequestParam(required = false) String toDate,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String payment,
                             Model model) {

        orderCleanupService.autoCancelExpiredBankQrOrders();

        int pageSize = 10;
        Pageable pageable = PageRequest.of(page, pageSize);

        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        boolean hasFrom = fromDate != null && !fromDate.isBlank();
        boolean hasTo = toDate != null && !toDate.isBlank();

        if (hasFrom || hasTo) {
            LocalDate from = hasFrom ? LocalDate.parse(fromDate) : LocalDate.of(2000, 1, 1);
            LocalDate to = hasTo ? LocalDate.parse(toDate) : LocalDate.now();

            if (from.isAfter(to)) {
                LocalDate temp = from;
                from = to;
                to = temp;
            }

            startDateTime = from.atStartOfDay();
            endDateTime = to.atTime(23, 59, 59);
        }

        Page<Order> ordersPage = orderRepository.searchOrders(
                startDateTime,
                endDateTime,
                keyword != null ? keyword.trim() : null,
                status,
                payment,
                pageable
        );

        model.addAttribute("orders", ordersPage.getContent());
        model.addAttribute("ordersPage", ordersPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ordersPage.getTotalPages());

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("payment", payment);

        model.addAttribute("hasFilter",
                (fromDate != null && !fromDate.isBlank())
                        || (toDate != null && !toDate.isBlank())
                        || (keyword != null && !keyword.isBlank())
                        || (status != null && !status.isBlank())
                        || (payment != null && !payment.isBlank()));

        return "admin/orders";
    }

    @PostMapping("/update-status")
    public String updateStatus(@RequestParam Long id,
                               @RequestParam String status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(required = false) String fromDate,
                               @RequestParam(required = false) String toDate,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String currentStatus,
                               @RequestParam(required = false) String payment,
                               RedirectAttributes redirectAttributes) {

        Order order = orderRepository.findById(id).orElseThrow();

        if ("CANCELLED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(order.getStatus())) {
            orderCleanupService.cancelAndRestoreStock(order);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã hủy đơn #" + id + " và hoàn lại tồn kho.");
            return buildRedirect(page, fromDate, toDate, keyword, currentStatus, payment);
        }

        order.setStatus(status);
        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("successMessage",
                "Cập nhật trạng thái đơn #" + id + " thành " + status);

        return buildRedirect(page, fromDate, toDate, keyword, currentStatus, payment);
    }

    @PostMapping("/update-payment")
    public String updatePayment(@RequestParam Long id,
                                @RequestParam String paymentStatus,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(required = false) String fromDate,
                                @RequestParam(required = false) String toDate,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String payment,
                                RedirectAttributes redirectAttributes) {

        Order order = orderRepository.findById(id).orElseThrow();

        order.setPaymentStatus(paymentStatus);

        if ("PAID".equalsIgnoreCase(paymentStatus)) {
            order.setPaidAt(LocalDateTime.now());

            if ("PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("PROCESSING");
            }
        }

        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("successMessage",
                "Cập nhật thanh toán đơn #" + id + " thành " + paymentStatus);

        return buildRedirect(page, fromDate, toDate, keyword, status, payment);
    }

    @PostMapping("/quick-paid")
    public String quickPaid(@RequestParam Long id,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) String fromDate,
                            @RequestParam(required = false) String toDate,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String payment,
                            RedirectAttributes redirectAttributes) {

        Order order = orderRepository.findById(id).orElseThrow();

        order.setPaymentStatus("PAID");
        order.setPaidAt(LocalDateTime.now());

        if ("PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
            order.setStatus("PROCESSING");
        }

        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã xác nhận thanh toán cho đơn #" + id);

        return buildRedirect(page, fromDate, toDate, keyword, status, payment);
    }

    @PostMapping("/quick-done")
    public String quickDone(@RequestParam Long id,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) String fromDate,
                            @RequestParam(required = false) String toDate,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String payment,
                            RedirectAttributes redirectAttributes) {

        Order order = orderRepository.findById(id).orElseThrow();

        order.setStatus("DONE");

        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())
                && "BANK_QR".equalsIgnoreCase(order.getPaymentMethod())) {
            order.setPaymentStatus("PAID");
            order.setPaidAt(LocalDateTime.now());
        }

        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã hoàn tất đơn #" + id);

        return buildRedirect(page, fromDate, toDate, keyword, status, payment);
    }

    @PostMapping("/quick-cancel")
    public String quickCancel(@RequestParam Long id,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(required = false) String fromDate,
                              @RequestParam(required = false) String toDate,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String payment,
                              RedirectAttributes redirectAttributes) {

        Order order = orderRepository.findById(id).orElseThrow();

        if (!"CANCELLED".equalsIgnoreCase(order.getStatus())) {
            orderCleanupService.cancelAndRestoreStock(order);
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã hủy đơn #" + id + " và hoàn lại tồn kho.");

        return buildRedirect(page, fromDate, toDate, keyword, status, payment);
    }

    private String buildRedirect(int page,
                                 String fromDate,
                                 String toDate,
                                 String keyword,
                                 String status,
                                 String payment) {
        StringBuilder url = new StringBuilder("redirect:/admin/orders?page=" + page);

        if (fromDate != null && !fromDate.isBlank()) {
            url.append("&fromDate=").append(fromDate);
        }

        if (toDate != null && !toDate.isBlank()) {
            url.append("&toDate=").append(toDate);
        }

        if (keyword != null && !keyword.isBlank()) {
            url.append("&keyword=").append(keyword);
        }

        if (status != null && !status.isBlank()) {
            url.append("&status=").append(status);
        }

        if (payment != null && !payment.isBlank()) {
            url.append("&payment=").append(payment);
        }

        return url.toString();
    }
}