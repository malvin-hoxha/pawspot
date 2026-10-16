package com.pawspot.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

//Specifies where the annotation can be placed
@Target({
    ElementType.FIELD, //private String password;
    ElementType.PARAMETER, //on parameter
    ElementType.RECORD_COMPONENT //record Request(String password)
})
@Retention(RetentionPolicy.RUNTIME) //run during run-time
@Constraint(validatedBy = BcryptPasswordValidator.class) //run the BcryptPasswordValidator validator
public @interface ValidBcryptPassword {

    String message() default "Password must not exceed 72 UTF-8 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
