package com.example.do_an_ck_J2EE.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String username, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Mã OTP đặt lại mật khẩu");
            message.setText(
                    "Xin chào " + username + ",\n\n" +
                    "Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản của mình.\n" +
                    "Mã OTP của bạn là: " + otp + "\n\n" +
                    "Mã này có hiệu lực trong 5 phút.\n" +
                    "Nếu không phải bạn thực hiện, hãy bỏ qua email này.\n\n" +
                    "Trân trọng."
            );

            mailSender.send(message);
        } catch (MailAuthenticationException e) {
            throw new RuntimeException("Xác thực Gmail thất bại. Hãy kiểm tra app password hoặc 2-Step Verification.", e);
        } catch (MailSendException e) {
            throw new RuntimeException("Không gửi được email OTP. Hãy kiểm tra địa chỉ email người nhận hoặc kết nối SMTP.", e);
        } catch (MailException e) {
            throw new RuntimeException("Lỗi hệ thống gửi mail: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi không xác định khi gửi mail: " + e.getMessage(), e);
        }
    }
}