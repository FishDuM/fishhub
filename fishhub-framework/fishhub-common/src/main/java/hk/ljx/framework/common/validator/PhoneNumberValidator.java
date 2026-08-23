package hk.ljx.framework.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


import java.util.regex.Pattern;

public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{11}$");

    @Override
    public void initialize(PhoneNumber constraintAnnotation) {
    }

    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext context) {
        return phoneNumber != null && PHONE_PATTERN.matcher(phoneNumber).matches();
    }
}
