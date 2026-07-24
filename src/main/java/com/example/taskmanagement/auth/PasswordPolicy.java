package com.example.taskmanagement.auth;

import com.example.taskmanagement.common.InvalidRequestException;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public static final int MIN_LENGTH = 15;
    public static final int MAX_LENGTH = 200;

    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new InvalidRequestException("The password must contain between 15 and 200 characters.");
        }
    }
}
