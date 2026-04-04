package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.Order;
import com.example.do_an_ck_J2EE.entity.User;
import com.example.do_an_ck_J2EE.repository.CartItemRepository;
import com.example.do_an_ck_J2EE.repository.OrderItemRepository;
import com.example.do_an_ck_J2EE.repository.OrderRepository;
import com.example.do_an_ck_J2EE.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "admin/users";
    }

    @PostMapping("/toggle-status")
    public String toggleStatus(@RequestParam Long id,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElseThrow();
        User currentUser = getCurrentUser(authentication);

        if (user.getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không thể tự khóa chính mình.");
            return "redirect:/admin/users";
        }

        user.setEnabled(!user.getEnabled());
        userRepository.save(user);

        if (user.getEnabled()) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã mở khóa user: " + user.getUsername());
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Đã khóa user: " + user.getUsername());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/toggle-role")
    public String toggleRole(@RequestParam Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElseThrow();
        User currentUser = getCurrentUser(authentication);

        if (user.getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không thể tự đổi role của chính mình.");
            return "redirect:/admin/users";
        }

        if ("ROLE_ADMIN".equals(user.getRole())) {
            user.setRole("ROLE_USER");
            redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển " + user.getUsername() + " thành USER");
        } else {
            user.setRole("ROLE_ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển " + user.getUsername() + " thành ADMIN");
        }

        userRepository.save(user);
        return "redirect:/admin/users";
    }

    @Transactional
    @PostMapping("/delete")
    public String deleteUser(@RequestParam Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElseThrow();
        User currentUser = getCurrentUser(authentication);

        if (user.getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không thể tự xóa chính mình.");
            return "redirect:/admin/users";
        }

        List<Order> orders = orderRepository.findByUser(user);

        cartItemRepository.deleteByUser(user);

        if (!orders.isEmpty()) {
            orderItemRepository.deleteByOrderIn(orders);
            orderRepository.deleteAll(orders);
        }

        userRepository.delete(user);

        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa user: " + user.getUsername());
        return "redirect:/admin/users";
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
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy admin social theo username: " + localUsername));
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
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy admin social theo email: " + emailToFind));
            }
        }

        String principal = auth.getName();

        return userRepository.findByUsername(principal)
                .or(() -> userRepository.findByEmail(principal))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy admin đang đăng nhập: " + principal));
    }
}