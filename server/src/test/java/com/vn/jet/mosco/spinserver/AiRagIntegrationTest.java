package com.vn.jet.mosco.spinserver;

import com.vn.jet.mosco.spinserver.controller.AiChatController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import com.vn.jet.mosco.spinserver.dto.ApiResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class AiRagIntegrationTest {

    @Autowired
    private AiChatController aiChatController;

    private String chat(String message) {
        try {
            Thread.sleep(4500); // Sleep to avoid 429 Too Many Requests (15 RPM limit)
        } catch (InterruptedException e) {}

        Map<String, Object> body = new HashMap<>();
        body.put("biasId", "S1_Seoyeon");
        body.put("language", "vi");
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("text", message);
        messages.add(msg);
        
        body.put("messages", messages);
        
        // Pass userId = 1 for context injection
        ResponseEntity<ApiResponse<String>> response = aiChatController.chatWithAi(1L, body);
        return response.getBody().getData();
    }

    @Test
    public void testUpgradeRate() {
        String res = chat("Tỉ lệ nâng cấp thẻ lên cấp 10 là bao nhiêu?");
        System.out.println("Q: Tỉ lệ nâng cấp thẻ lên cấp 10 là bao nhiêu?\nA: " + res);
        assertTrue(res.contains("2") || res.toLowerCase().contains("thấp") || res.toLowerCase().contains("giảm dần"), "Must mention low rate or 2%");
    }

    @Test
    public void testMetalPackPrice() {
        String res = chat("Mua gói Metal tốn bao nhiêu tiền?");
        System.out.println("Q: Mua gói Metal tốn bao nhiêu tiền?\nA: " + res);
        assertTrue(res.contains("1000") || res.contains("1.000") || res.toLowerCase().contains("nghìn") || res.toLowerCase().contains("ngàn"), "Must mention 1000 Coins");
    }

    @Test
    public void testSpinMechanic() {
        String res = chat("Cách thức hoạt động của chức năng spin?");
        System.out.println("Q: Cách thức hoạt động của chức năng spin?\nA: " + res);
        assertTrue(res.toLowerCase().contains("coin") || res.toLowerCase().contains("kim cương") || res.toLowerCase().contains("tiền"), "Must explain Spin uses Coins or Diamonds");
    }

    @Test
    public void testObjetPurpose() {
        String res = chat("Mấy objet để làm gì?");
        System.out.println("Q: Mấy objet để làm gì?\nA: " + res);
        assertTrue(res.toLowerCase().contains("avatar") || res.toLowerCase().contains("đại diện") || res.toLowerCase().contains("stage") || res.toLowerCase().contains("chỉ số"), "Must explain Objet purpose");
    }

    @Test
    public void testUpgradeFailure() {
        String res = chat("Ê sao tao không nâng cấp được(xem thử nó thêm nguyên liệu vô chưa? hoặc trang bị objet chính vô chưa?).");
        System.out.println("Q: Sao tôi không nâng cấp được?\nA: " + res);
        assertTrue(res.toLowerCase().contains("nguyên liệu") || res.toLowerCase().contains("khóa") || res.toLowerCase().contains("đội hình") || res.toLowerCase().contains("chọn"), "Must suggest checking material or lock status");
    }

    @Test
    public void testStreakPurpose() {
        String res = chat("Streak để làm gì v mày?");
        System.out.println("Q: Streak để làm gì v mày?\nA: " + res);
        assertTrue(res.toLowerCase().contains("tương tác") || res.toLowerCase().contains("bạn bè") || res.toLowerCase().contains("khoe") || res.toLowerCase().contains("chuỗi"), "Must explain Streak");
    }

    @Test
    public void testStagePurpose() {
        String res = chat("Chức năng stage là gì?");
        System.out.println("Q: Chức năng stage là gì?\nA: " + res);
        assertTrue(res.toLowerCase().contains("treo máy") || res.toLowerCase().contains("afk") || res.toLowerCase().contains("phần thưởng") || res.toLowerCase().contains("nhận thưởng"), "Must explain Stage");
    }

    @Test
    public void testShopLocation() {
        String res = chat("Ủa shop ở đâu?");
        System.out.println("Q: Ủa shop ở đâu?\nA: " + res);
        assertTrue(res.toLowerCase().contains("menu") || res.toLowerCase().contains("chính") || res.toLowerCase().contains("giao diện"), "Must explain Shop location");
    }

    @Test
    public void testGiftObjet() {
        String res = chat("Tôi muốn gửi objet cho bạn tôi thì làm gì?");
        System.out.println("Q: Tôi muốn gửi objet cho bạn tôi thì làm gì?\nA: " + res);
        assertTrue(res.toLowerCase().contains("tặng") || res.toLowerCase().contains("quà") || res.toLowerCase().contains("gift") || res.toLowerCase().contains("phí"), "Must explain Gifting");
    }

    // ===================== RAG-SPECIFIC TESTS =====================
    // These tests verify the RAG system correctly retrieves tripleS wiki data.
    // Run with: .\gradlew.bat test --tests "com.vn.jet.mosco.spinserver.AiRagIntegrationTest" -Dspring.profiles.active=test

    @Test
    public void testRagLatestAlbum() {
        String res = chat("album mới nhất là gì?");
        System.out.println("Q: album mới nhất là gì?\nA: " + res);
        // MUST mention the latest album (LOVE&POP pt.1 / ASSEMBLE26, June 2026), not ASSEMBLE24
        assertTrue(res.contains("LOVE") || res.contains("POP") || res.contains("ASSEMBLE26") || res.contains("2026") || res.contains("Baby Flower"),
                "BUG: AI still thinks ASSEMBLE24 is the latest album! Expected LOVE&POP pt.1 (ASSEMBLE26)");
    }

    @Test
    public void testRagBabyFlowerAlbum() {
        String res = chat("Baby Flower nằm trong album nào?");
        System.out.println("Q: Baby Flower nằm trong album nào?\nA: " + res);
        assertTrue(res.contains("LOVE") || res.contains("POP") || res.contains("LOVE&POP"),
                "BUG: AI doesn't know Baby Flower is from LOVE&POP pt.1");
    }

    @Test
    public void testRagMemberCount() {
        String res = chat("tripleS có bao nhiêu thành viên?");
        System.out.println("Q: tripleS có bao nhiêu thành viên?\nA: " + res);
        assertTrue(res.contains("24") || res.contains("hai mươi bốn") || res.contains("hai tư"),
                "BUG: AI doesn't know tripleS has 24 members!");
    }

    @Test
    public void testRagGroupNameMeaning() {
        String res = chat("tripleS viết tắt của từ gì?");
        System.out.println("Q: tripleS viết tắt của từ gì?\nA: " + res);
        assertTrue(res.contains("Social") || res.contains("Sonyo") || res.contains("Seoul"),
                "BUG: AI doesn't know tripleS = Social Sonyo Seoul");
    }

    @Test
    public void testRagDebutDate() {
        String res = chat("tripleS debut ngày nào?");
        System.out.println("Q: tripleS debut ngày nào?\nA: " + res);
        assertTrue(res.contains("2023") || res.contains("13") || res.contains("ASSEMBLE"),
                "BUG: AI doesn't know tripleS debut date (Feb 13, 2023)");
    }

    @Test
    public void testRagLatestSubUnit() {
        String res = chat("sub-unit mới nhất của tripleS là gì?");
        System.out.println("Q: sub-unit mới nhất của tripleS là gì?\nA: " + res);
        // Should mention recent sub-units (post-2024), not just AAA/KRE
        assertTrue(res.contains("Alphie") || res.contains("Zenith") || res.contains("Neptune") || res.contains("Hatchi") || res.contains("Vision") || res.contains("Glow") || res.contains("two big waves"),
                "BUG: AI doesn't know recent tripleS sub-units (missing Alphie/Zenith/Hatchi/Visionary)");
    }

    @Test
    public void testRagIdentity() {
        String res = chat("Em là thành viên của nhóm nào?");
        System.out.println("Q: Em là thành viên của nhóm nào?\nA: " + res);
        // After identity fix, AI MUST know it's a tripleS member
        assertTrue(res.contains("tripleS") || res.contains("트리플"),
                "BUG: AI doesn't know it belongs to tripleS!");
    }

    @Test
    public void testRagLatestAlbumEnglish() {
        String res = chat("What is tripleS's latest album?");
        System.out.println("Q: What is tripleS's latest album?\nA: " + res);
        assertTrue(res.contains("LOVE") || res.contains("POP") || res.contains("ASSEMBLE26") || res.contains("2026"),
                "BUG: AI doesn't know latest album LOVE&POP pt.1 when asked in English");
    }

    @Test
    public void testRagDebutAlbum() {
        String res = chat("album debut của tripleS?");
        System.out.println("Q: album debut của tripleS?\nA: " + res);
        assertTrue(res.contains("ASSEMBLE") || res.contains("ASSEMBLE10") || res.contains("ACCESS"),
                "BUG: AI doesn't know tripleS debut album (ASSEMBLE/ACCESS)");
    }

    @Test
    public void testRagMemberInfo() {
        String res = chat("S16 là ai?");
        System.out.println("Q: S16 là ai?\nA: " + res);
        assertTrue(res.contains("Mayu") || res.contains("S16"),
                "BUG: AI doesn't know S16 = Mayu");
    }

    @Test
    public void testRagAssemble25Exists() {
        String res = chat("ASSEMBLE25 có bao nhiêu bài?");
        System.out.println("Q: ASSEMBLE25 có bao nhiêu bài?\nA: " + res);
        assertTrue(res.contains("10") || res.contains("mười") || res.contains("Are You Alive") || res.contains("Alpha Percent"),
                "BUG: AI doesn't know ASSEMBLE25 has 10 tracks");
    }

    @Test
    public void testRagFullGroupCount() {
        String res = chat("OT24 là gì?");
        System.out.println("Q: OT24 là gì?\nA: " + res);
        assertTrue(res.contains("24") || res.contains("All 24") || res.contains("full group"),
                "BUG: AI doesn't know OT24 means all 24 members");
    }
}
