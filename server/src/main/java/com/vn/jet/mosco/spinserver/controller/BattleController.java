package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.BattleRequest;
import com.vn.jet.mosco.spinserver.dto.BattleResponse;
import com.vn.jet.mosco.spinserver.service.BattleEngineService;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/battle")
public class BattleController {

    private final BattleEngineService battleEngineService;
    private final UserRepository userRepository;
    private final UserCardRepository userCardRepository;
    private final com.vn.jet.mosco.spinserver.service.CardDataService cardDataService;

    public BattleController(BattleEngineService battleEngineService, 
                            UserRepository userRepository, 
                            UserCardRepository userCardRepository,
                            com.vn.jet.mosco.spinserver.service.CardDataService cardDataService) {
        this.battleEngineService = battleEngineService;
        this.userRepository = userRepository;
        this.userCardRepository = userCardRepository;
        this.cardDataService = cardDataService;
    }

    @PostMapping
    public ResponseEntity<BattleResponse> calculateBattleOvr(@RequestBody BattleRequest request) {
        BattleResponse response = battleEngineService.calculateFormationOvr(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Preview OVR và Synergy cho đội hình (Realtime Preview)
     */
    @PostMapping("/preview")
    public ResponseEntity<BattleResponse> previewFormation(@RequestBody BattleRequest request) {
        BattleResponse response = battleEngineService.calculateFormationOvr(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/formation/{userId}")
    public ResponseEntity<List<com.vn.jet.mosco.spinserver.dto.UserCardDTO>> getUserFormation(@PathVariable Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        String activeFormat = user.getActiveFormation();
        if (activeFormat == null || activeFormat.trim().isEmpty()) {
            activeFormat = "null,null,null,null,null,null";
        }

        String[] ids = activeFormat.split(",");
        List<com.vn.jet.mosco.spinserver.dto.UserCardDTO> formation = new ArrayList<>();

        for (String idStr : ids) {
            if (idStr.trim().equalsIgnoreCase("null") || idStr.trim().isEmpty()) {
                formation.add(null);
            } else {
                try {
                    Long userCardId = Long.parseLong(idStr.trim());
                    UserCard card = userCardRepository.findById(userCardId).orElse(null);
                    if (card != null) {
                        formation.add(cardDataService.toDTO(card));
                    } else {
                        formation.add(null);
                    }
                } catch (NumberFormatException e) {
                    formation.add(null);
                }
            }
        }
        
        // Ensure exact 6 slots
        while (formation.size() < 6) {
            formation.add(null);
        }

        return ResponseEntity.ok(formation);
    }

    @PostMapping("/formation/{userId}/save")
    public ResponseEntity<?> saveUserFormation(@PathVariable Long userId, @RequestBody List<Long> slotIds) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (slotIds == null) {
            slotIds = new ArrayList<>();
        }
        
        // Pad to 6
        while (slotIds.size() < 6) {
            slotIds.add(null);
        }

        String formationStr = slotIds.stream()
                .limit(6)
                .map(id -> id != null ? String.valueOf(id) : "null")
                .collect(Collectors.joining(","));

        user.setActiveFormation(formationStr);
        userRepository.save(user);

        return ResponseEntity.ok(java.util.Map.of("status", 200, "message", "Formation saved successfully"));
    }
}
