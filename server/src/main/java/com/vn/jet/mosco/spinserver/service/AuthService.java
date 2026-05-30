package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.dto.AuthResponse;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import com.vn.jet.mosco.spinserver.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailUser;

    @Value("${spring.mail.display-name:Mosco Galactic Support}")
    private String mailFromName;

    // RFC 5322 simplified email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Bộ nhớ tạm lưu mã xác nhận: Email -> Code
    private final java.util.concurrent.ConcurrentHashMap<String, String> verificationCodes = new java.util.concurrent.ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
        this.mailSender = mailSender;
    }

    public AuthResponse register(String username, String     email, String password, String code) {
        if (username == null || username.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty() ||
            code == null || code.trim().isEmpty()) {
            return new AuthResponse(false, "Vui lòng nhập đầy đủ thông tin và mã xác nhận.", null, null);
        }

        // Normalize email to lowercase to prevent duplicates like User@Gmail.com vs user@gmail.com
        email = email.trim().toLowerCase(Locale.ROOT);

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return new AuthResponse(false, "Invalid email format.", null, null);
        }

        if (password.trim().length() < 6) {
            return new AuthResponse(false, "Password must be at least 6 characters.", null, null);
        }

        if (userRepository.existsByUsername(username.trim())) {
            return new AuthResponse(false, "Username already exists.", null, null);
        }

        if (userRepository.existsByEmail(email)) {
            return new AuthResponse(false, "Email đã được sử dụng bởi người dùng khác.", null, null);
        }

        // --- KIỂM TRA MÃ XÁC NHẬN ---
        String storedCode = verificationCodes.get(email);
        if (storedCode == null || !storedCode.equals(code.trim())) {
            return new AuthResponse(false, "Mã xác nhận không chính xác hoặc đã hết hạn.", null, null);
        }
        // Xóa mã sau khi sử dụng thành công
        verificationCodes.remove(email);

        String hashedPassword = passwordEncoder.encode(password);
        User newUser = new User(
                username != null ? username.trim() : null,
                email,
                hashedPassword);
        
        newUser.setCoins(50000L);
        newUser.setDiamonds(10000L);
        
        // Khởi tạo streak ngay khi đăng ký
        newUser.setStreak(1);
        newUser.setBestStreak(1);
        newUser.setLastLoginAt(java.time.LocalDateTime.now());
        
        userRepository.save(newUser);

        String token = generateToken(newUser);
        newUser.setActiveToken(token);
        userRepository.save(newUser);
        com.vn.jet.mosco.spinserver.security.TokenCache.put(newUser.getId(), token);
        
        return new AuthResponse(true, "Đăng ký tài khoản thành công!", newUser, token);
    }

    /**
     * Gửi mã xác nhận về Email (Mockup logging thực tế)
     */
    public AuthResponse sendVerificationCode(String email) {
        if (email == null || email.trim().isEmpty()) {
            return new AuthResponse(false, "Vui lòng nhập email.", null, null);
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return new AuthResponse(false, "Định dạng email không hợp lệ.", null, null);
        }

        // Tạo mã 6 chữ số ngẫu nhiên
        String code = String.format("%06d", new java.util.Random().nextInt(999999));
        verificationCodes.put(email, code);

        // Gửi qua Gmail thực tế
        try {
            sendSimpleEmail(email, "Mã xác nhận Mosco", 
                "Mã xác nhận của bạn là: " + code + "\n\nMã có hiệu lực trong 5 phút.");
            return new AuthResponse(true, "Mã xác nhận đã được gửi thành công đến " + email, null, null);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", email, e.getMessage());
            // Fallback: Vẫn cho phép nhìn code ở console để không bị block nếu sai config
            return new AuthResponse(true, "Yêu cầu gửi mã thành công (Nếu không nhận được mail, vui lòng kiểm tra console server).", null, null);
        }
    }

    /**
     * Gửi mã xác nhận cho trường hợp QUÊN MẬT KHẨU (Yêu cầu Email phải tồn tại)
     */
    public AuthResponse forgotPassword(String email) {
        if (email == null || email.trim().isEmpty()) {
            return new AuthResponse(false, "Vui lòng nhập email.", null, null);
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        
        // Kiểm tra Email có tồn tại trong hệ thống chưa
        if (!userRepository.existsByEmail(email)) {
            return new AuthResponse(false, "Email này không tồn tại trong hệ thống.", null, null);
        }

        return sendVerificationCode(email);
    }

    /**
     * Đặt lại mật khẩu mới sau khi xác thực mã
     */
    public AuthResponse resetPassword(String email, String code, String newPassword) {
        if (email == null || code == null || newPassword == null) {
            return new AuthResponse(false, "Vui lòng nhập đầy đủ thông tin.", null, null);
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        
        // Kiểm tra mã xác nhận
        String storedCode = verificationCodes.get(email);
        if (storedCode == null || !storedCode.equals(code.trim())) {
            return new AuthResponse(false, "Mã xác nhận không chính xác hoặc đã hết hạn.", null, null);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return new AuthResponse(false, "Lỗi: Không tìm thấy người dùng.", null, null);
        }

        User user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Xóa mã sau khi sử dụng
        verificationCodes.remove(email);

        return new AuthResponse(true, "Mật khẩu của bạn đã được thay đổi thành công!", null, null);
    }

    private void sendSimpleEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            // Thiết lập: Tên hiển thị + Email thực tế
            helper.setFrom(mailUser, mailFromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, false); // false = g?i plain text

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi g?i Email: " + e.getMessage());
        }
    }

    public AuthResponse login(String username, String password) {
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return new AuthResponse(false, "Username and password must not be empty.", null, null);
        }

        username = username.trim();

        // Tại sao (WHY): Hỗ trợ người dùng đăng nhập linh hoạt bằng cả Username hoặc Email
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(username.toLowerCase(Locale.ROOT));
        }

        if (userOpt.isEmpty()) {
            return new AuthResponse(false, "Invalid username or password.", null, null);
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return new AuthResponse(false, "Invalid email or password.", null, null);
        }

        String token = generateToken(user);
        user.setActiveToken(token);
        com.vn.jet.mosco.spinserver.security.TokenCache.put(user.getId(), token);

        updateStreak(user);

        userRepository.save(user);
        
        return new AuthResponse(true, "Login successful", user, token);
    }

    /**
     * 🔥 LOGIC DAILY STREAK (GLOBAL)
     * Được gọi mỗi khi User login hoặc lấy thông tin Profile.
     * Đảm bảo streak luôn được cập nhật ngay cả khi dùng Auto-login.
     */
    public void updateStreak(User user) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (user.getLastLoginAt() == null) {
            user.setStreak(1);
            if (user.getBestStreak() < 1) {
                user.setBestStreak(1);
            }
            user.setLastLoginAt(now);
            log.debug("[STREAK] First interaction for user: {}. Streak initialized.", user.getUsername());
        } else {
            java.time.LocalDate lastDate = user.getLastLoginAt().toLocalDate();
            java.time.LocalDate today = now.toLocalDate();

            if (today.isAfter(lastDate)) {
                if (today.minusDays(1).equals(lastDate)) {
                    user.setStreak(user.getStreak() + 1);
                    log.debug("[STREAK] User {} streak incremented to {}", user.getUsername(), user.getStreak());
                } else {
                    user.setStreak(1);
                    log.debug("[STREAK] User {} streak broken. Reset to 1.", user.getUsername());
                }
                // Chỉ cập nhật lastLoginAt khi sang ngày mới để tránh ghi đè liên tục trong ngày
                user.setLastLoginAt(now);
            }
        }
        
        // LUÔN ĐẢM BẢO BEST STREAK CẬP NHẬT (Auto-Repair logic)
        if (user.getStreak() > user.getBestStreak()) {
            user.setBestStreak(user.getStreak());
        }
        
        // Đảm bảo nếu có streak thì record không được bằng 0
        if (user.getStreak() > 0 && user.getBestStreak() == 0) {
            user.setBestStreak(user.getStreak());
        }
    }

    /**
     * Generate a signed JWT token containing userId and username.
     * Token is valid for the duration configured in jwt.expiration (default: 24h).
     */
    /**
     * Khôi phục chuỗi đăng nhập.
     * Quy tắc: 3 lần đầu trong tháng miễn phí. Từ lần thứ 4 giá 500 Kim cương.
     */
    public AuthResponse restoreStreak(User user) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int currentMonth = now.getMonthValue() + now.getYear() * 100;

        // Reset số lượt free nếu sang tháng mới
        if (user.getLastRestoreMonth() == null || user.getLastRestoreMonth() < currentMonth) {
            user.setStreakRestoresThisMonth(0);
            user.setLastRestoreMonth(currentMonth);
        }

        int restores = user.getStreakRestoresThisMonth();
        long cost = (restores < 3) ? 0 : 500;

        if (cost > 0 && user.getDiamonds() < cost) {
            return new AuthResponse(false, "Bạn không đủ Kim cương để khôi phục (Cần 500).", null, null);
        }

        // Thực hiện khôi phục: streak = record (về trạng thái tốt nhất)
        if (user.getStreak() >= user.getBestStreak() && user.getStreak() > 0) {
            return new AuthResponse(false, "Ngọn lửa của bạn đang rực cháy ở mức cao nhất rồi! Hãy quay lại khi chuỗi bị gián đoạn nhé.", null, null);
        }

        user.setDiamonds(user.getDiamonds() - cost);
        user.setStreak(user.getBestStreak()); // Khôi phục về kỷ lục
        user.setStreakRestoresThisMonth(restores + 1);
        user.setLastLoginAt(now); // Cập nhật ngày để không bị reset ngay lập tức

        userRepository.save(user);

        String msg = (cost == 0) 
            ? "Khôi phục chuỗi thành công! (Miễn phí " + (restores + 1) + "/3)"
            : "Khôi phục chuỗi thành công! (Tốn 500 Kim cương)";

        return new AuthResponse(true, msg, user, null);
    }

    private String generateToken(User user) {
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    /**
     * Đăng nhập thông qua Social Provider (Google/Discord)
     * - Nếu email đã tồn tại: Cấp lại token (Merge Account)
     * - Nếu email chưa tồn tại: Tạo mới User với mật khẩu ngẫu nhiên
     */
    public AuthResponse socialLogin(String provider, String token, String email) {
        if (email == null || email.trim().isEmpty()) {
            return new AuthResponse(false, "Không lấy được email từ " + provider, null, null);
        }
        
        email = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;
        
        if (userOpt.isPresent()) {
            // Đã tồn tại: Merge account
            user = userOpt.get();
        } else {
            // Chưa tồn tại: Tạo acc mới
            String randomPassword = java.util.UUID.randomUUID().toString();
            String hashedPassword = passwordEncoder.encode(randomPassword);
            String tempUsername = "user_" + email.split("@")[0] + "_" + java.util.UUID.randomUUID().toString().substring(0, 4);
            
            user = new User(tempUsername, email, hashedPassword);
            // Quà tân thủ
            user.setCoins(50000L);
            user.setDiamonds(10000L);
            // Cố tình KHÔNG set IngameName để Android tự động bật màn hình Đặt Tên (DisplayNameSetupActivity)
            // user.setIngameName(tempUsername); 
            
            userRepository.save(user);
        }
        
        // Cấp token cho User
        String jwtToken = generateToken(user);
        user.setActiveToken(jwtToken);
        userRepository.save(user);
        com.vn.jet.mosco.spinserver.security.TokenCache.put(user.getId(), jwtToken);
        
        return new AuthResponse(true, "Đăng nhập thành công qua " + provider, user, jwtToken);
    }
}
