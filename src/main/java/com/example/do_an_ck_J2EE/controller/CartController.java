package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.CartItem;
import com.example.do_an_ck_J2EE.entity.Plant;
import com.example.do_an_ck_J2EE.entity.User;
import com.example.do_an_ck_J2EE.entity.Voucher;
import com.example.do_an_ck_J2EE.repository.CartItemRepository;
import com.example.do_an_ck_J2EE.repository.PlantRepository;
import com.example.do_an_ck_J2EE.repository.UserRepository;
import com.example.do_an_ck_J2EE.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CartController {

    private final CartItemRepository cartItemRepository;
    private final PlantRepository plantRepository;
    private final UserRepository userRepository;
    private final VoucherRepository voucherRepository;

    private static final double SHIPPING_FEE = 30000.0;
    private static final double FREE_SHIP_THRESHOLD = 300000.0;

    @GetMapping
    public String viewCart(@RequestParam(required = false) String voucher,
                           Model model,
                           Authentication auth) {

        User user = getCurrentUser(auth);
        List<CartItem> items = cartItemRepository.findByUser(user);

        double subtotal = items.stream()
                .mapToDouble(item -> item.getPlant().getPrice() * item.getQuantity())
                .sum();

        double shippingFee = calculateShippingFee(subtotal);
        double discountAmount = 0.0;
        String appliedVoucherCode = null;
        String voucherMessage = null;
        String voucherError = null;

        if (voucher != null && !voucher.isBlank()) {
            Optional<Voucher> voucherOptional = voucherRepository.findByCodeIgnoreCase(voucher.trim());

            if (voucherOptional.isPresent()) {
                Voucher foundVoucher = voucherOptional.get();
                String validationError = validateVoucher(foundVoucher, subtotal);

                if (validationError == null) {
                    discountAmount = calculateDiscount(foundVoucher, subtotal);
                    appliedVoucherCode = foundVoucher.getCode();
                    voucherMessage = "Áp mã voucher thành công: " + foundVoucher.getCode();
                } else {
                    voucherError = validationError;
                }
            } else {
                voucherError = "Mã voucher không tồn tại.";
            }
        }

        double finalTotal = Math.max(0, subtotal + shippingFee - discountAmount);

        model.addAttribute("cartItems", items);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("discountAmount", discountAmount);
        model.addAttribute("finalTotal", finalTotal);
        model.addAttribute("appliedVoucherCode", appliedVoucherCode);
        model.addAttribute("voucherMessage", voucherMessage);
        model.addAttribute("voucherError", voucherError);

        return "cart";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam Long plantId,
                            Authentication auth,
                            RedirectAttributes redirectAttributes) {

        User user = getCurrentUser(auth);
        Plant plant = plantRepository.findById(plantId).orElseThrow();

        if (plant.getStock() == null || plant.getStock() <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Sản phẩm \"" + plant.getName() + "\" đã hết hàng.");
            return "redirect:/";
        }

        Optional<CartItem> existingItem = cartItemRepository.findByUserAndPlant(user, plant);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();

            if (item.getQuantity() >= plant.getStock()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Đã đạt số lượng tối đa trong kho.");
                return "redirect:/cart";
            }

            item.setQuantity(item.getQuantity() + 1);
            cartItemRepository.save(item);
        } else {
            cartItemRepository.save(CartItem.builder()
                    .user(user)
                    .plant(plant)
                    .quantity(1)
                    .build());
        }

        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm vào giỏ hàng");
        return "redirect:/cart";
    }

    @PostMapping("/increase")
    public String increase(@RequestParam Long cartItemId,
                           Authentication auth,
                           RedirectAttributes redirectAttributes) {

        User user = getCurrentUser(auth);
        CartItem item = cartItemRepository.findById(cartItemId).orElseThrow();

        if (!item.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không có quyền");
            return "redirect:/cart";
        }

        Plant plant = item.getPlant();
        if (plant.getStock() != null && item.getQuantity() < plant.getStock()) {
            item.setQuantity(item.getQuantity() + 1);
            cartItemRepository.save(item);
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Số lượng vượt quá tồn kho.");
        }

        return "redirect:/cart";
    }

    @PostMapping("/decrease")
    public String decrease(@RequestParam Long cartItemId,
                           Authentication auth,
                           RedirectAttributes redirectAttributes) {

        User user = getCurrentUser(auth);
        CartItem item = cartItemRepository.findById(cartItemId).orElseThrow();

        if (!item.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không có quyền");
            return "redirect:/cart";
        }

        if (item.getQuantity() > 1) {
            item.setQuantity(item.getQuantity() - 1);
            cartItemRepository.save(item);
        } else {
            cartItemRepository.delete(item);
        }

        return "redirect:/cart";
    }

    @PostMapping("/update")
    public String update(@RequestParam Long cartItemId,
                         @RequestParam Integer quantity,
                         Authentication auth,
                         RedirectAttributes redirectAttributes) {

        User user = getCurrentUser(auth);
        CartItem item = cartItemRepository.findById(cartItemId).orElseThrow();

        if (!item.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không có quyền");
            return "redirect:/cart";
        }

        Plant plant = item.getPlant();
        int stock = plant.getStock() == null ? 0 : plant.getStock();

        if (quantity == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Số lượng không hợp lệ.");
            return "redirect:/cart";
        }

        if (quantity <= 0) {
            cartItemRepository.delete(item);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi giỏ hàng.");
            return "redirect:/cart";
        }

        if (stock <= 0) {
            cartItemRepository.delete(item);
            redirectAttributes.addFlashAttribute("errorMessage", "Sản phẩm đã hết hàng.");
            return "redirect:/cart";
        }

        if (quantity > stock) {
            item.setQuantity(stock);
            cartItemRepository.save(item);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Số lượng vượt tồn kho. Hệ thống đã điều chỉnh về tối đa là " + stock + ".");
            return "redirect:/cart";
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);

        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật số lượng sản phẩm.");
        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String remove(@RequestParam Long cartItemId,
                         Authentication auth,
                         RedirectAttributes redirectAttributes) {

        User user = getCurrentUser(auth);
        CartItem item = cartItemRepository.findById(cartItemId).orElseThrow();

        if (item.getUser().getId().equals(user.getId())) {
            cartItemRepository.delete(item);
        }

        return "redirect:/cart";
    }

    private double calculateShippingFee(double subtotal) {
        if (subtotal <= 0) return 0;
        return subtotal >= FREE_SHIP_THRESHOLD ? 0 : SHIPPING_FEE;
    }

    private String validateVoucher(Voucher voucher, double subtotal) {
        if (voucher.getActive() == null || !voucher.getActive()) {
            return "Voucher không khả dụng";
        }
        if (voucher.getQuantity() == null || voucher.getQuantity() <= 0) {
            return "Voucher đã hết";
        }
        if (voucher.getExpiryDate() != null && voucher.getExpiryDate().isBefore(LocalDateTime.now())) {
            return "Voucher hết hạn";
        }
        if (voucher.getMinOrderValue() != null && subtotal < voucher.getMinOrderValue()) {
            return "Chưa đủ điều kiện áp mã";
        }
        return null;
    }

    private double calculateDiscount(Voucher voucher, double subtotal) {
        double discount = 0;

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