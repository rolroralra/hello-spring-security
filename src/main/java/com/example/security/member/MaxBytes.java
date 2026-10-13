package com.example.security.member;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열의 UTF-8 바이트 길이를 제한한다.
 * BCrypt 는 72바이트를 넘는 비밀번호를 인코딩하지 못하므로(IllegalArgumentException),
 * 한글처럼 한 글자가 3바이트인 입력도 안전하게 막기 위해 글자 수가 아닌 바이트 수로 검사한다.
 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

    int value();

    String message() default "너무 깁니다. (최대 {value} 바이트, 한글은 한 글자에 3바이트)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
