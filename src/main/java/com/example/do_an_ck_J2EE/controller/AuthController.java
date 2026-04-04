package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.ForgotPasswordForm;
import com.example.do_an_ck_J2EE.entity.User;
import com.example.do_an_ck_J2EE.repository.UserRepository;
import com.example.do_an_ck_J2EE.service.EmailService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Random;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final String SESSION_OTP = "FORGOT_PASSWORD_OTP";
    private static final String SESSION_OTP_USERNAME = "FORGOT_PASSWORD_USERNAME";
    private static final String SESSION_OTP_EMAIL = "FORGOT_PASSWORD_EMAIL";
    private static final String SESSION_OTP_EXPIRE = "FORGOT_PASSWORD_OTP_EXPIRE";
    private static final String SESSION_PROVIDER = "FORGOT_PASSWORD_PROVIDER";

    @GetMapping("/login")
    public String login(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String
                && "anonymousUser".equals(authentication.getPrincipal()))) {
            return "redirect:/";
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("user") User user,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        if (userRepository.existsByUsername(user.getUsername())) {
            model.addAttribute("error", "Username đã tồn tại");
            return "register";
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            model.addAttribute("error", "Email đã tồn tại");
            return "register";
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("ROLE_USER");
        user.setEnabled(true);
        user.setLocked(false);
        user.setProvider("LOCAL");

        try {
            userRepository.save(user);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi khi lưu tài khoản: " + e.getMessage());
            return "register";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Đăng ký thành công. Bạn hãy đăng nhập.");
        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ForgotPasswordForm());
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password/send-otp")
    public String sendOtp(@ModelAttribute("form") ForgotPasswordForm form,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        User user = userRepository.findByUsernameAndEmail(form.getUsername(), form.getEmail()).orElse(null);

        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản khớp với username và email.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        if (Boolean.TRUE.equals(user.getLocked())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản của bạn đang bị khóa.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản của bạn đã bị vô hiệu hóa.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        String otp = generateOtp();
        long expireTime = System.currentTimeMillis() + 5 * 60 * 1000;

        session.setAttribute(SESSION_OTP, otp);
        session.setAttribute(SESSION_OTP_USERNAME, form.getUsername());
        session.setAttribute(SESSION_OTP_EMAIL, form.getEmail());
        session.setAttribute(SESSION_OTP_EXPIRE, expireTime);
        session.setAttribute(SESSION_PROVIDER, user.getProvider());

        try {
            emailService.sendOtpEmail(form.getEmail(), form.getUsername(), otp);
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        if ("LOCAL".equalsIgnoreCase(user.getProvider())) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi OTP về email của bạn. Vui lòng kiểm tra hộp thư.");
        } else {
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã gửi OTP về email của bạn. Sau khi xác minh, bạn sẽ đặt thêm mật khẩu để đăng nhập bằng tài khoản thường ngoài " + user.getProvider() + "."
            );
        }

        redirectAttributes.addFlashAttribute("form", form);
        return "redirect:/forgot-password";
    }

    @PostMapping("/forgot-password/verify-otp")
    public String verifyOtpAndResetPassword(@ModelAttribute("form") ForgotPasswordForm form,
                                            HttpSession session,
                                            RedirectAttributes redirectAttributes) {

        String sessionOtp = (String) session.getAttribute(SESSION_OTP);
        String sessionUsername = (String) session.getAttribute(SESSION_OTP_USERNAME);
        String sessionEmail = (String) session.getAttribute(SESSION_OTP_EMAIL);
        Long sessionExpire = (Long) session.getAttribute(SESSION_OTP_EXPIRE);
        String provider = (String) session.getAttribute(SESSION_PROVIDER);

        if (sessionOtp == null || sessionUsername == null || sessionEmail == null || sessionExpire == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn chưa gửi OTP hoặc phiên đã hết hạn.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        if (System.currentTimeMillis() > sessionExpire) {
            clearForgotPasswordSession(session);
            redirectAttributes.addFlashAttribute("errorMessage", "OTP đã hết hạn. Vui lòng gửi lại OTP mới.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        if (!sessionUsername.equals(form.getUsername()) || !sessionEmail.equals(form.getEmail())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Username hoặc email không khớp với OTP đã gửi.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        if (form.getOtp() == null || !sessionOtp.equals(form.getOtp().trim())) {
            redirectAttributes.addFlashAttribute("errorMessage", "OTP không đúng.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        if (form.getNewPassword() == null || form.getConfirmPassword() == null ||
                !form.getNewPassword().equals(form.getConfirmPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu xác nhận không khớp.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        User user = userRepository.findByUsernameAndEmail(form.getUsername(), form.getEmail()).orElse(null);

        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/forgot-password";
        }

        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        userRepository.save(user);

        clearForgotPasswordSession(session);

        if ("LOCAL".equalsIgnoreCase(provider)) {
            redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công. Hãy đăng nhập lại.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đặt mật khẩu thành công. Từ bây giờ bạn có thể đăng nhập bằng cả " + provider + " hoặc username/password.");
        }

        return "redirect:/login";
    }

    private String generateOtp() {
        Random random = new Random();
        int otpNumber = 100000 + random.nextInt(900000);
        return String.valueOf(otpNumber);
    }

    private void clearForgotPasswordSession(HttpSession session) {
        session.removeAttribute(SESSION_OTP);
        session.removeAttribute(SESSION_OTP_USERNAME);
        session.removeAttribute(SESSION_OTP_EMAIL);
        session.removeAttribute(SESSION_OTP_EXPIRE);
        session.removeAttribute(SESSION_PROVIDER);
    }
}