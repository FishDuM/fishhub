package hk.ljx.framework.common.util;

import java.util.regex.Pattern;


public final class ParamUtils {
    private ParamUtils() {
    }

    // ============================== 校验昵称 ==============================
    // 定义昵称长度范围
    private static final int NICK_NAME_MIN_LENGTH = 2;
    private static final int NICK_NAME_MAX_LENGTH = 24;

    // 定义特殊字符的正则表达式
    private static final String NICK_NAME_REGEX = "[!@#$%^&*(),.?\":{}|<>]";
    private static final Pattern NICK_NAME_PATTERN = Pattern.compile(NICK_NAME_REGEX);

    /**
     * 昵称校验
     *
     * @param nickname
     * @return
     */
    public static boolean checkNickname(String nickname) {
        if (nickname == null) {
            return false;
        }
        // 检查长度
        if (nickname.length() < NICK_NAME_MIN_LENGTH || nickname.length() > NICK_NAME_MAX_LENGTH) {
            return false;
        }

        // 检查是否含有特殊字符
        return !NICK_NAME_PATTERN.matcher(nickname).find();
    }
    private static final int ID_MIN_LENGTH = 6;
    private static final int ID_MAX_LENGTH = 32;
    private static final Pattern ID_FORMAT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern ID_LETTER_PATTERN = Pattern.compile("[a-zA-Z]");

    
    public static boolean checkFishhubId(String fishhubId) {
        if (fishhubId == null) {
            return false;
        }
        if (fishhubId.length() < ID_MIN_LENGTH || fishhubId.length() > ID_MAX_LENGTH) {
            return false;
        }
        return ID_FORMAT_PATTERN.matcher(fishhubId).matches()
                && ID_LETTER_PATTERN.matcher(fishhubId).find();
    }

}
