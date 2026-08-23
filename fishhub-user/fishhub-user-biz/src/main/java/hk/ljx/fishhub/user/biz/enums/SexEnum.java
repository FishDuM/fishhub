package hk.ljx.fishhub.user.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;
import java.util.Set;


@Getter
@AllArgsConstructor
public enum SexEnum {

    WOMAN(0),
    MAN(1);

    private final Integer value;

    private static final Set<Integer> VALID_VALUES = Set.of(WOMAN.value, MAN.value);

    public static boolean isValid(Integer value) {
        return value != null && VALID_VALUES.contains(value);
    }

}
