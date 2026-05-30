package com.vn.jet.mosco.spinserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jet.mosco.spinserver.dto.CardJsonDto;
import com.vn.jet.mosco.spinserver.dto.DatabaseJsonWrapper;
import com.vn.jet.mosco.spinserver.model.Card;
import com.vn.jet.mosco.spinserver.model.CardClass;
import com.vn.jet.mosco.spinserver.model.Member;
import com.vn.jet.mosco.spinserver.model.Season;
import com.vn.jet.mosco.spinserver.repository.CardClassRepository;
import com.vn.jet.mosco.spinserver.repository.CardRepository;
import com.vn.jet.mosco.spinserver.repository.MemberRepository;
import com.vn.jet.mosco.spinserver.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EtlService {

    private final CardRepository cardRepository;
    private final MemberRepository memberRepository;
    private final SeasonRepository seasonRepository;
    private final CardClassRepository cardClassRepository;
    private final ObjectMapper objectMapper;

    // Regex bóc tách image_id từ Cloudflare URL
    private static final Pattern IMAGE_ID_PATTERN = Pattern.compile("https://imagedelivery\\.net/qQuMkbHJ-0s6rwu8vup_5w/([^/]+)/.*");

    /**
     * Tác vụ ETL chạy định kỳ để đồng bộ dữ liệu từ JSON vào MySQL.
     * Chạy mỗi ngày một lần (ví dụ) hoặc có thể kích hoạt thủ công.
     */
    @Scheduled(fixedDelay = 86400000) // Chạy sau mỗi 24h
    @Transactional
    public void runEtlJob() {
        log.info("Starting ETL process to sync card data...");
        
        try {
            // Caching local để tránh N+1 query cho bảng từ điển
            java.util.Map<String, Member> memberMap = new java.util.HashMap<>();
            java.util.Map<String, Season> seasonMap = new java.util.HashMap<>();
            java.util.Map<String, CardClass> classMap = new java.util.HashMap<>();

            // 1. Đọc file JSON từ thư mục data/assets/ (Dữ liệu động đã cào)
            java.io.File dbFile = new java.io.File("data/assets/database.json");
            InputStream inputStream;
            if (!dbFile.exists()) {
                log.warn("Dynamic database.json file not found. Falling back to static resource...");
                ClassPathResource resource = new ClassPathResource("database.json");
                inputStream = resource.getInputStream();
            } else {
                inputStream = new java.io.FileInputStream(dbFile);
            }
            DatabaseJsonWrapper wrapper = objectMapper.readValue(inputStream, DatabaseJsonWrapper.class);
            List<CardJsonDto> collections = wrapper.getCollections();

            if (collections == null || collections.isEmpty()) {
                log.warn("No card data found in database.json");
                return;
            }

            log.info("Found {} cards to process.", collections.size());
            java.util.Set<String> processedIds = new java.util.HashSet<>();
            List<Card> batchCards = new ArrayList<>();
            int count = 0;

            for (CardJsonDto dto : collections) {
                if (dto.getId() == null || processedIds.contains(dto.getId())) continue;
                processedIds.add(dto.getId());

                // 2. Xử lý Dictionary Tables (Auto-create với Cache)
                Member member = memberMap.computeIfAbsent(dto.getMember(), this::getOrCreateMember);
                Season season = seasonMap.computeIfAbsent(dto.getSeason(), this::getOrCreateSeason);
                CardClass cardClass = classMap.computeIfAbsent(dto.getCardClass(), this::getOrCreateClass);

                // 3. Bóc tách image_id bằng Regex
                String frontImageId = extractImageId(dto.getFrontImage());
                String backImageId = extractImageId(dto.getBackImage());

                // 4. Khởi tạo/Cập nhật Card entity - Fetch để đảm bảo UPSERT đúng
                Card card = cardRepository.findById(dto.getId()).orElse(new Card());
                card.setId(dto.getId());
                card.setMember(member);
                card.setSeason(season);
                card.setCardClass(cardClass);
                card.setFrontImageId(frontImageId);
                card.setBackImageId(backImageId);
                card.setCollectionNo(dto.getCollectionNo());
                // Không ghi đè baseOvr và upgradeLevel nếu đã tồn tại (để giữ logic nâng cấp)
                if (card.getBaseOvr() == 0) card.setBaseOvr(70);
                if (card.getUpgradeLevel() == 0) card.setUpgradeLevel(1);

                // Phát triển cho thẻ Motion (Dynamic URL Generation từ Slug Apollo)
                if (cardClass != null && "Motion".equalsIgnoreCase(cardClass.getName())) {
                    String slug = dto.getSlug() != null ? dto.getSlug().toLowerCase() : "";
                    if (slug.isEmpty()) {
                        String seasonName = season != null ? season.getName().toLowerCase().replaceAll("\\s+", "") : "";
                        String memberName = member != null ? member.getName().toLowerCase().replaceAll("\\s+", "") : "";
                        String colNo = dto.getCollectionNo() != null ? dto.getCollectionNo().toLowerCase() : "";
                        slug = seasonName + "-" + memberName + "-" + colNo;
                    }
                    String videoUrl = "https://cdn.apollo.cafe/mco/triples/" + slug + ".mp4";
                    card.setFrontVideoUrl(videoUrl);
                } else {
                    card.setFrontVideoUrl(null);
                }

                batchCards.add(card);
                count++;

                // 5. Thực hiện Batch Save mỗi 200 bản ghi (giảm batch size vì có findById)
                if (batchCards.size() >= 200) {
                    cardRepository.saveAllAndFlush(batchCards);
                    batchCards.clear();
                    log.debug("Successfully upserted {} records...", count);
                }
            }

            if (!batchCards.isEmpty()) {
                cardRepository.saveAllAndFlush(batchCards);
            }

            log.info("ETL process completed. Total processed cards: {}", count);

        } catch (Exception e) {
            log.error("Error occurred during ETL execution: ", e);
        }
    }

    private Member getOrCreateMember(String name) {
        if (name == null) return null;
        // Vì JSON chỉ có tên, ta dùng tên làm key. Nếu muốn "update" tên cũ, 
        // ta cần một ID ổn định từ JSON (nhưng JSON không có member_id).
        // Tuy nhiên, theo yêu cầu test của user, ta sẽ tìm theo tên.
        // Nếu user đổi tên trong DB thành JiWoo_Test, findByName("JiWoo") sẽ không thấy.
        // Để pass test "update trở lại", ta cần logic tìm kiếm linh hoạt hoặc chấp nhận tạo mới.
        // NHƯNG: Nếu ta muốn "fix" dữ liệu sai, ta nên xóa các bản ghi rác.
        return memberRepository.findByName(name)
                .orElseGet(() -> memberRepository.save(new Member(name)));
    }

    private Season getOrCreateSeason(String name) {
        if (name == null) return null;
        return seasonRepository.findByName(name)
                .orElseGet(() -> seasonRepository.save(new Season(name)));
    }

    private CardClass getOrCreateClass(String name) {
        if (name == null) return null;
        return cardClassRepository.findByName(name)
                .orElseGet(() -> cardClassRepository.save(new CardClass(name)));
    }

    private String extractImageId(String url) {
        if (url == null) return "";
        Matcher matcher = IMAGE_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return url; // Fallback nếu không khớp regex
    }
}
