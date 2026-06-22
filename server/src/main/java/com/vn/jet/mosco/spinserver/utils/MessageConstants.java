package com.vn.jet.mosco.spinserver.utils;

/**
 * Lớp định nghĩa tất cả các hằng số thông báo phản hồi (success, error, validation)
 * của phía Server nhằm tránh lỗi hardcode chuỗi ký tự theo chuẩn Clean Code.
 */
public final class MessageConstants {

    private MessageConstants() {
        // Chặn khởi tạo đối tượng
    }

    // Auth & Authorization
    public static final String AUTH_REQUIRED = "Authentication required";
    public static final String INVALID_REQUEST = "Yêu cầu không hợp lệ.";
    public static final String INVALID_INPUT = "Vui lòng nhập liệu hợp lệ.";
    public static final String EMPTY_INPUT = "Vui lòng nhập liệu.";
    public static final String MISSING_SOCIAL_INFO = "Vui lòng truyền thông tin Social Login.";
    public static final String MISSING_FIELDS = "Vui lòng nhập đầy đủ thông tin.";
    public static final String MISSING_FIELDS_AND_OTP = "Vui lòng nhập đầy đủ thông tin và mã xác nhận.";
    public static final String INVALID_EMAIL = "Định dạng email không hợp lệ.";
    public static final String INVALID_EMAIL_FORMAT = "Invalid email format.";
    public static final String INVALID_PASSWORD_LENGTH = "Password must be at least 6 characters.";
    public static final String USERNAME_EXISTS = "auth_err_username_exists";
    public static final String EMAIL_IN_USE = "auth_err_email_in_use";
    public static final String REGISTRATION_SUCCESS = "Đăng ký tài khoản thành công!";
    public static final String MISSING_EMAIL = "Vui lòng nhập email.";
    public static final String OTP_SENT_SUCCESS = "Mã xác nhận đã được gửi thành công đến ";
    public static final String OTP_SEND_FALLBACK = "Yêu cầu gửi mã thành công (Nếu không nhận được mail, vui lòng kiểm tra console server).";
    public static final String EMAIL_NOT_FOUND = "Email này không tồn tại trong hệ thống.";
    
    // Login
    public static final String LOGIN_EMPTY_FIELDS = "Username and password must not be empty.";
    public static final String LOGIN_INVALID_CREDENTIALS = "Invalid username or password.";
    public static final String LOGIN_INVALID_EMAIL_OR_PASS = "Invalid email or password.";
    public static final String LOGIN_SUCCESS = "Login successful";

    // Soft Delete & Restore Account
    public static final String ACCOUNT_DELETION_PENDING = "Tài khoản đang chờ xóa.";
    public static final String ACCOUNT_RESTORE_SUCCESS = "Khôi phục tài khoản thành công!";
    public static final String ACCOUNT_DELETION_REQUESTED = "Yêu cầu xóa tài khoản đã được tiếp nhận. Bạn có 14 ngày để khôi phục.";
    public static final String OTP_INVALID_OR_EXPIRED = "Mã xác nhận không chính xác hoặc đã hết hạn.";
    public static final String OTP_INVALID_OR_EXPIRED_SHORT = "Mã OTP không chính xác hoặc đã hết hạn.";
    public static final String SOCIAL_EMAIL_ERROR = "Không lấy được email từ ";
    public static final String SOCIAL_LOGIN_SUCCESS = "Đăng nhập thành công qua ";

    // User Profile
    public static final String USER_NOT_FOUND = "User not found";
    public static final String ITEM_NOT_FOUND = "Item not found";
    public static final String USER_PROFILE_NOT_FOUND = "Không tìm thấy hồ sơ người chơi";
    public static final String DISPLAY_NAME_SET_SUCCESS = "Display name set successfully!";
    public static final String PROFILE_UPDATED_SUCCESS = "Profile updated successfully!";
    public static final String USERNAME_FORMAT_ERROR = "Username chỉ cho phép chữ, số và dấu gạch dưới (3-20 ký tự)";
    public static final String USERNAME_IN_USE = "Username đã được sử dụng";
    public static final String DISPLAY_NAME_EMPTY = "Display name không được để trống";
    public static final String DISPLAY_NAME_LENGTH_ERROR = "Display name phải từ 2 đến 16 ký tự";
    public static final String DISPLAY_NAME_RESERVED = "Tên này không được phép sử dụng";
    public static final String DISPLAY_NAME_INVALID_CHARS = "Tên chứa ký tự không hợp lệ";
    public static final String DISPLAY_NAME_IN_USE = "Tên này đã được sử dụng bởi người chơi khác";
    public static final String CANNOT_LIKE_SELF = "Không thể tự thích hồ sơ của chính mình";
    public static final String LIKE_SUCCESS = "Đã thích hồ sơ thành công";
    public static final String UNLIKE_SUCCESS = "Đã bỏ thích hồ sơ";

    // Streak
    public static final String STREAK_NOT_ENOUGH_DIAMONDS = "Bạn không đủ Kim cương để khôi phục (Cần 500).";
    public static final String STREAK_ALREADY_MAX = "Ngọn lửa của bạn đang rực cháy ở mức cao nhất rồi! Hãy quay lại khi chuỗi bị gián đoạn nhé.";

    // Gift
    public static final String GIFT_SENT_SUCCESS = "Gift sent successfully! 🎁";
    public static final String GIFT_MISSING_FIELDS = "Missing cardId or receiverId";
    
    // Checkin
    public static final String CHECKIN_ERROR = "Không thể điểm danh: Ngoài khung giờ hoặc đã nhận rồi";

    // Stage i18n keys
    public static final String STAGE_ERR_TEAM_SIZE = "stage_err_team_size";
    public static final String STAGE_ERR_CARDS_NOT_EXIST = "stage_err_cards_not_exist";
    public static final String STAGE_ERR_CARD_NOT_OWNED = "stage_err_card_not_owned";
    public static final String STAGE_ERR_CARD_BUSY = "stage_err_card_busy";
    public static final String STAGE_ERR_SESSION_NOT_FOUND = "stage_err_session_not_found";
    public static final String STAGE_ERR_SESSION_ACCESS_DENIED = "stage_err_session_access_denied";
    public static final String STAGE_ERR_SESSION_NOT_FINISHED = "stage_err_session_not_finished";
    public static final String STAGE_ERR_SESSION_INVALID = "stage_err_session_invalid";
    public static final String STAGE_ERR_SESSION_ALREADY_FINISHED = "stage_err_session_already_finished";
    public static final String STAGE_ERR_SPEEDUP_NOT_ENOUGH_DIAMONDS = "stage_err_speedup_not_enough_diamonds";
    public static final String STAGE_ERR_MAP_NOT_FOUND = "stage_err_map_not_found";
    public static final String STAGE_ERR_LEVEL_LOCKED = "stage_err_level_locked";

    // Upgrade & Gacha i18n keys
    public static final String UPGRADE_ERR_MAX_LEVEL = "upgrade_err_max_level";
    public static final String UPGRADE_ERR_MATERIAL_INVALID = "upgrade_err_material_invalid";
    public static final String UPGRADE_ERR_CONFIG_ERROR = "upgrade_err_config_error";
    public static final String UPGRADE_ERR_LEVEL_CONFIG_ERROR = "upgrade_err_level_config_error";
    public static final String UPGRADE_ERR_BASE_CARD_NOT_FOUND = "upgrade_err_base_card_not_found";
    public static final String UPGRADE_ERR_MATERIAL_CARD_NOT_FOUND = "upgrade_err_material_card_not_found";
    public static final String UPGRADE_MSG_SUCCESS = "upgrade_msg_success";
    public static final String UPGRADE_MSG_FAILED = "upgrade_msg_failed";
    public static final String PACK_ERR_CONFIG_MISSING = "pack_err_config_missing";
    public static final String PACK_ERR_NOT_ENOUGH = "pack_err_not_enough";
    
    // Mailbox i18n keys
    public static final String MAIL_ERR_ALREADY_CLAIMED = "mail_err_already_claimed";
}
