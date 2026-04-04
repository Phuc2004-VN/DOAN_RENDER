package com.example.do_an_ck_J2EE.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordForm {
    private String username;
    private String email;
    private String otp;
    private String newPassword;
    private String confirmPassword;
}