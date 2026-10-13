package com.example.security.member;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class MaxBytesValidator implements ConstraintValidator<MaxBytes, String> {

    private int maxBytes;

    @Override
    public void initialize(MaxBytes constraint) {
        this.maxBytes = constraint.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 검사는 @NotBlank 의 몫이다
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
