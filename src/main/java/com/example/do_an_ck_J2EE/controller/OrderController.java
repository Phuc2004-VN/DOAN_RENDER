package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.CartItem;
import com.example.do_an_ck_J2EE.entity.Order;
import com.example.do_an_ck_J2EE.entity.OrderItem;
import com.example.do_an_ck_J2EE.entity.Plant;
import com.example.do_an_ck_J2EE.entity.User;
import com.example.do_an_ck_J2EE.entity.Voucher;
import com.example.do_an_ck_J2EE.repository.CartItemRepository;
import com.example.do_an_ck_J2EE.repository.OrderItemRepository;
import com.example.do_an_ck_J2EE.repository.OrderRepository;
import com.example.do_an_ck_J2EE.repository.PlantRepository;
import com.example.do_an_ck_J2EE.repository.UserRepository;
import com.example.do_an_ck_J2EE.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {

    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final VoucherRepository voucherRepository;
    private final PlantRepository plantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final double SHIPPING_FEE = 30000.0;
    private static final double FREE_SHIP_THRESHOLD = 300000.0;

    private static final String BANK_BIN = "970422";
    private static final String BANK_ACCOUNT_NO = "0123456789";
    private static final String BANK_ACCOUNT_NAME = "PHAN THANH TUNG";

    @PostMapping("/checkout")
    @Transactional
    public String checkout(@RequestParam(required = false) String voucherCode,
                           @RequestParam(defaultValue = "COD") String paymentMethod,
                           @RequestParam String receiverName,
                           @RequestParam String phone,
                           @RequestParam String address,
                           @RequestParam(required = false) String note,
                           Authentication auth,
                           RedirectAttributes redirectAttributes) {

        User user = getCurrentUser(auth);
        List<CartItem> cartItems = cartItemRepository.findByUser(user);

        String normalizedReceiverName = receiverName != null ? receiverName.trim() : "";
        String normalizedPhone = phone != null ? phone.trim() : "";
        String normalizedAddress = address != null ? address.trim() : "";
        String normalizedNote = note != null ? note.trim() : "";

        if (cartItems.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Giỏ hàng đang trống, không thể đặt hàng.");
            return "redirect:/cart";
        }

        if (normalizedReceiverName.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập họ tên người nhận.");
            return "redirect:/cart";
        }

        if (normalizedPhone.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập số điện thoại.");
            return "redirect:/cart";
        }

        if (!normalizedPhone.matches("^(0|\\+84)[0-9]{9,10}$")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Số điện thoại không hợp lệ.");
            return "redirect:/cart";
        }

        if (normalizedAddress.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập địa chỉ giao hàng.");
            return "redirect:/cart";
        }

        for (CartItem item : cartItems) {
            Plant plant = plantRepository.findById(item.getPlant().getId()).orElseThrow();

            if (plant.getStock() == null || plant.getStock() <= 0) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Sản phẩm \"" + plant.getName() + "\" đã hết hàng.");
                return "redirect:/cart";
            }

            if (item.getQuantity() > plant.getStock()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Sản phẩm \"" + plant.getName() + "\" chỉ còn " + plant.getStock() + " trong kho.");
                return "redirect:/cart";
            }
        }

        double subtotal = cartItems.stream()
                .mapToDouble(item -> item.getPlant().getPrice() * item.getQuantity())
                .sum();

        double shippingFee = calculateShippingFee(subtotal);
        double discountAmount = 0.0;
        String appliedVoucherCode = null;
        Voucher voucher = null;

        if (voucherCode != null && !voucherCode.isBlank()) {
            Optional<Voucher> voucherOptional = voucherRepository.findByCodeIgnoreCase(voucherCode.trim());

            if (voucherOptional.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Mã voucher không tồn tại.");
                return "redirect:/cart";
            }

            voucher = voucherOptional.get();
            String validationError = validateVoucher(voucher, subtotal);

            if (validationError != null) {
                redirectAttributes.addFlashAttribute("errorMessage", validationError);
                return "redirect:/cart";
            }

            discountAmount = calculateDiscount(voucher, subtotal);
            appliedVoucherCode = voucher.getCode();
        }

        double finalTotal = Math.max(0, subtotal + shippingFee - discountAmount);
        boolean isBankQr = "BANK_QR".equalsIgnoreCase(paymentMethod);

        Order order = Order.builder()
                .user(user)
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .voucherCode(appliedVoucherCode)
                .totalAmount(finalTotal)
                .receiverName(normalizedReceiverName)
                .phone(normalizedPhone)
                .address(normalizedAddress)
                .note(normalizedNote.isBlank() ? null : normalizedNote)
                .status(isBankQr ? "PENDING_PAYMENT" : "PENDING")
                .paymentMethod(isBankQr ? "BANK_QR" : "COD")
                .paymentStatus(isBankQr ? "WAITING_TRANSFER" : "UNPAID")
                .createdAt(LocalDateTime.now())
                .build();

        orderRepository.save(order);

        order.setPaymentReference("DH" + order.getId());
        orderRepository.save(order);

        for (CartItem item : cartItems) {
            Plant plant = plantRepository.findById(item.getPlant().getId()).orElseThrow();

            plant.setStock(plant.getStock() - item.getQuantity());
            plantRepository.save(plant);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .plant(plant)
                    .quantity(item.getQuantity())
                    .price(plant.getPrice())
                    .subtotal(plant.getPrice() * item.getQuantity())
                    .build();

            orderItemRepository.save(orderItem);
        }

        if (voucher != null) {
            voucher.setQuantity(voucher.getQuantity() - 1);
            voucherRepository.save(voucher);
        }

        cartItemRepository.deleteAll(cartItems);

        sendAdminOrderNotification(order);

        if (isBankQr) {
            return "redirect:/order/payment/" + order.getId();
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Đặt hàng thành công. Tổng thanh toán: " + String.format("%,.0f", finalTotal) + " VND");

        return "redirect:/order/history";
    }

    @GetMapping("/history")
    public String orderHistory(@RequestParam(defaultValue = "0") int page,
                               Authentication auth,
                               Model model) {
        User user = getCurrentUser(auth);

        Pageable pageable = PageRequest.of(page, 10);
        Page<Order> orderPage = orderRepository.findByUserOrderByCreatedAtDesc(user, pageable);

        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", orderPage.getTotalPages());
        model.addAttribute("totalItems", orderPage.getTotalElements());

        return "order-history";
    }

    @GetMapping("/invoice/{orderId}")
    public String invoicePage(@PathVariable Long orderId,
                              Authentication auth,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(auth);
        Order order = orderRepository.findById(orderId).orElseThrow();

        if (!order.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền xem hóa đơn này.");
            return "redirect:/order/history";
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orderItems);
        return "invoice";
    }

    @GetMapping("/payment/{orderId}")
    public String paymentPage(@PathVariable Long orderId,
                              Authentication auth,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(auth);
        Order order = orderRepository.findById(orderId).orElseThrow();

        if (!order.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền xem thanh toán đơn này.");
            return "redirect:/";
        }

        if (!"BANK_QR".equalsIgnoreCase(order.getPaymentMethod())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đơn này không phải thanh toán QR ngân hàng.");
            return "redirect:/";
        }

        String addInfo = order.getPaymentReference();
        String accountNameEncoded = URLEncoder.encode(BANK_ACCOUNT_NAME, StandardCharsets.UTF_8);
        String addInfoEncoded = URLEncoder.encode(addInfo, StandardCharsets.UTF_8);

        String qrUrl = "https://img.vietqr.io/image/"
                + BANK_BIN + "-" + BANK_ACCOUNT_NO
                + "-compact2.png?amount=" + order.getTotalAmount().longValue()
                + "&addInfo=" + addInfoEncoded
                + "&accountName=" + accountNameEncoded;

        model.addAttribute("order", order);
        model.addAttribute("qrUrl", qrUrl);
        model.addAttribute("bankName", "MB Bank");
        model.addAttribute("bankAccountNo", BANK_ACCOUNT_NO);
        model.addAttribute("bankAccountName", BANK_ACCOUNT_NAME);
        model.addAttribute("transferContent", addInfo);

        return "payment";
    }

    @PostMapping("/payment/mark-paid")
    public String markPaidRequest(@RequestParam Long orderId,
                                  Authentication auth,
                                  RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(auth);
        Order order = orderRepository.findById(orderId).orElseThrow();

        if (!order.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền thao tác đơn này.");
            return "redirect:/";
        }

        if (!"BANK_QR".equalsIgnoreCase(order.getPaymentMethod())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đơn này không phải thanh toán QR.");
            return "redirect:/";
        }

        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            redirectAttributes.addFlashAttribute("successMessage", "Đơn hàng đã được xác nhận thanh toán trước đó.");
            return "redirect:/order/history";
        }

        order.setStatus("PROCESSING");
        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã ghi nhận yêu cầu thanh toán cho đơn #" + order.getId() + ". Admin sẽ xác nhận sớm.");

        return "redirect:/order/history";
    }

    private void sendAdminOrderNotification(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ORDER");
        payload.put("orderId", order.getId());
        payload.put("username", order.getUser() != null ? order.getUser().getUsername() : "user");
        payload.put("receiverName", order.getReceiverName());
        payload.put("phone", order.getPhone());
        payload.put("paymentMethod", order.getPaymentMethod());
        payload.put("status", order.getStatus());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);

        messagingTemplate.convertAndSend("/topic/admin", (Object) payload);
    }

    private double calculateShippingFee(double subtotal) {
        if (subtotal <= 0) return 0;
        return subtotal >= FREE_SHIP_THRESHOLD ? 0 : SHIPPING_FEE;
    }

    private String validateVoucher(Voucher voucher, double subtotal) {
        if (voucher.getActive() == null || !voucher.getActive()) {
            return "Voucher hiện không khả dụng.";
        }

        if (voucher.getQuantity() == null || voucher.getQuantity() <= 0) {
            return "Voucher đã hết lượt sử dụng.";
        }

        if (voucher.getExpiryDate() != null && voucher.getExpiryDate().isBefore(LocalDateTime.now())) {
            return "Voucher đã hết hạn.";
        }

        if (voucher.getMinOrderValue() != null && subtotal < voucher.getMinOrderValue()) {
            return "Đơn hàng chưa đạt giá trị tối thiểu để áp mã.";
        }

        return null;
    }

    private double calculateDiscount(Voucher voucher, double subtotal) {
        double discount = 0.0;

        if ("PERCENT".equalsIgnoreCase(voucher.getDiscountType())) {
            discount = subtotal * voucher.getDiscountValue() / 100.0;
            if (voucher.getMaxDiscount() != null && voucher.getMaxDiscount() > 0) {
                discount = Math.min(discount, voucher.getMaxDiscount());
            }
        } else if ("FIXED".equalsIgnoreCase(voucher.getDiscountType())) {
            discount = voucher.getDiscountValue();
        }

        return Math.min(discount, subtotal);
    }

    private User getCurrentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Bạn chưa đăng nhập.");
        }

        Object principalObj = auth.getPrincipal();

        if (principalObj instanceof OAuth2User oauth2User) {
            Map<String, Object> attributes = oauth2User.getAttributes();

            Object localUsernameObj = attributes.get("localUsername");
            String localUsername = localUsernameObj != null ? localUsernameObj.toString() : null;

            if (localUsername != null && !localUsername.isBlank()) {
                return userRepository.findByUsername(localUsername)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy user social theo username: " + localUsername));
            }

            Object emailObj = attributes.get("email");
            String resolvedEmail = emailObj != null ? emailObj.toString() : null;

            if (resolvedEmail == null || resolvedEmail.isBlank()) {
                Object localEmailObj = attributes.get("localEmail");
                resolvedEmail = localEmailObj != null ? localEmailObj.toString() : null;
            }

            if (resolvedEmail != null && !resolvedEmail.isBlank()) {
                final String emailToFind = resolvedEmail;
                return userRepository.findByEmail(emailToFind)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy user social theo email: " + emailToFind));
            }
        }

        String principal = auth.getName();

        return userRepository.findByUsername(principal)
                .or(() -> userRepository.findByEmail(principal))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đang đăng nhập: " + principal));
    }
}