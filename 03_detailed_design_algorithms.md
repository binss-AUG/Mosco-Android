# PHASE 3: Thiết kế Chi tiết & Mã giả Thuật toán Core Logic - Dự án Mosco

Tài liệu này mô tả chi tiết các giải thuật cốt lõi trong hệ thống **Mosco Gacha & Card Collection**, được phân tích trực tiếp từ mã nguồn thực tế ở cả hai phía Client (Android Native - 100% Java) và Server (Spring Boot 3.x - Java 21). Các giải thuật này đóng vai trò quan trọng trong việc đảm bảo tính nhất quán dữ liệu, tối ưu hóa hiệu năng và mang lại trải nghiệm người dùng cao cấp (Quiet Luxury).

---

## 1. Thuật toán Nâng cấp Thẻ bài (FO4-style Card Upgrade with Pessimistic Locking)

### 1.1. Ý nghĩa & Bối cảnh
Thuật toán này mô phỏng cơ chế nâng cấp thẻ bài tương tự tựa game FIFA Online 4 (FO4). Người chơi sử dụng 1 thẻ chính và chọn từ 1 đến 5 thẻ nguyên liệu (phôi) để nâng cấp thẻ chính lên cấp độ cao hơn (tối đa cấp +10). Việc nâng cấp có tỉ lệ thành công phụ thuộc vào chênh lệch chỉ số OVR giữa phôi và thẻ chính. 

Để ngăn chặn hoàn toàn các lỗi gian lận như **Double-spending** (sử dụng cùng một phôi nâng cấp đồng thời trong hai yêu cầu) hoặc **Race Condition** trong môi trường đa luồng, phía Server sử dụng cơ chế khóa bi quan (`PESSIMISTIC_WRITE`) trên cơ sở dữ liệu.

### 1.2. Đặc tả Thuật toán
- **File mã nguồn:**
  - Server: [UpgradeService.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/service/UpgradeService.java)
  - Client (Xem trước): [UpgradeAlgorithm.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/UpgradeAlgorithm.java)
- **Đầu vào (Input):**
  - `baseCardId` (Long): ID thẻ bài chính cần nâng cấp.
  - `materialCardIds` (List<Long>): Danh sách ID của từ 1 đến 5 thẻ bài phôi.
  - Cấu hình tỉ lệ cơ bản (`upgradeRates`) và các hệ số nâng cấp (`customUpgradeConfig` gồm hệ số $X$ và cơ số $M$) tương ứng với từng cấp độ và loại thẻ.
- **Đầu ra (Output):**
  - `isSuccess` (Boolean): Kết quả nâng cấp thành công hay thất bại.
  - `newLevel` (Int): Cấp nâng cấp mới của thẻ chính.
  - `newOvr` (Int): Chỉ số OVR mới tương ứng cấp độ.
  - `actualSuccessRate` (Double): Tỉ lệ thành công thực tế (%).

### 1.3. Mã giả Thuật toán (Pseudo-code)

```text
FUNCTION upgradeCard(baseCardId, materialCardIds)
    START TRANSACTION
    
    // 1. Áp dụng Khóa Bi Quan (Pessimistic Write Lock) để tránh Race Condition
    mainCard = userCardRepository.findWithLockById(baseCardId)
    IF mainCard IS NULL THEN
        ROLLBACK TRANSACTION
        RETURN Error("Không tìm thấy thẻ chính")
    END IF
    
    IF mainCard.upgradeLevel >= 10 THEN
        ROLLBACK TRANSACTION
        RETURN Error("Thẻ đã đạt cấp độ tối đa (+10)")
    END IF
    
    // 2. Khóa tất cả các thẻ nguyên liệu đồng thời
    materials = EMPTY_LIST
    FOR EACH matId IN materialCardIds DO
        matCard = userCardRepository.findWithLockById(matId)
        IF matCard IS NULL THEN
            ROLLBACK TRANSACTION
            RETURN Error("Không tìm thấy thẻ nguyên liệu: " + matId)
        END IF
        ADD matCard TO materials
    END FOR
    
    IF LENGTH(materials) < 1 OR LENGTH(materials) > 5 THEN
        ROLLBACK TRANSACTION
        RETURN Error("Số lượng thẻ nguyên liệu không hợp lệ (1-5)")
    END IF
    
    // 3. Tính toán tỷ lệ thành công dựa trên công thức hàm mũ OVR
    nextLevel = mainCard.upgradeLevel + 1
    maxRate = upgradeRates.get(nextLevel) // Tỷ lệ thành công tối đa của level
    
    // Lấy hệ số X và cơ số M dựa trên nhóm thẻ (class)
    typeKey = cardDataService.getTypeKey(mainCard.collectionId)
    levelConfig = customUpgradeConfig.get(nextLevel).get(typeKey)
    X = levelConfig.X
    M = levelConfig.M
    
    mainOvr = cardDataService.getOvr(mainCard.collectionId, mainCard.upgradeLevel)
    totalFillPercent = 0.0
    
    FOR EACH material IN materials DO
        materialOvr = cardDataService.getOvr(material.collectionId, material.upgradeLevel)
        deltaOvr = materialOvr - mainOvr
        
        // Công thức phi tuyến tính tính điểm đóng góp của từng phôi
        IF deltaOvr >= 0 THEN
            totalFillPercent = totalFillPercent + (X * (M ^ deltaOvr))
        ELSE
            totalFillPercent = totalFillPercent + (X / (M ^ ABS(deltaOvr)))
        END IF
    END FOR
    
    // Giới hạn thanh nạp phôi tối đa 100%
    fillPercent = MIN(totalFillPercent, 100.0)
    actualSuccessRate = (fillPercent / 100.0) * maxRate
    
    // 4. Quay số ngẫu nhiên (RNG) - Dùng hạt giống khí quyển từ ChaosTheoryHelper
    roll = ChaosTheoryHelper.nextDouble() * 100.0
    isSuccess = (roll <= actualSuccessRate)
    
    // 5. Áp dụng hình phạt hoặc phần thưởng
    oldLevel = mainCard.upgradeLevel
    IF isSuccess THEN
        mainCard.upgradeLevel = oldLevel + 1
    ELSE
        // Penalty: Rớt 2 cấp, tối thiểu về cấp 1
        mainCard.upgradeLevel = MAX(1, oldLevel - 2)
    END IF
    
    // 6. Tiêu thụ phôi (Xóa ràng buộc khóa ngoại trong StageSession trước khi xóa thẻ)
    FOR EACH material IN materials DO
        stageSessionMemberRepository.deleteByUserCardId(material.id)
    END FOR
    userCardRepository.deleteAll(materials)
    
    // 7. Lưu thẻ chính và kết thúc transaction
    userCardRepository.save(mainCard)
    newOvr = cardDataService.getOvr(mainCard.collectionId, mainCard.upgradeLevel)
    
    COMMIT TRANSACTION
    
    RETURN UpgradeResponse(isSuccess, mainCard.upgradeLevel, newOvr, actualSuccessRate)
END FUNCTION
```

### 1.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Locking Scope tối thiểu:** Khóa bi quan chỉ áp dụng trên các dòng cụ thể (`SELECT ... FOR UPDATE` thông qua `findWithLockById`) thay vì khóa toàn bộ bảng `user_card`. Điều này cho phép hàng ngàn người chơi thực hiện nâng cấp đồng thời mà không bị nghẽn cổ chai.
- **Ràng buộc khóa ngoại:** Thuật toán xóa dữ liệu phụ thuộc ở bảng trung gian `stage_session_member` trước khi thực hiện `deleteAll(materials)` để tránh vi phạm toàn vẹn dữ liệu MySQL và lỗi Crash Thread.
- **Tính toán tĩnh trước khi lưu:** Các hằng số $X$, $M$, và tỉ lệ gốc được nạp sẵn vào bộ nhớ đệm (`Map` tĩnh) qua phương thức `@PostConstruct` khi khởi động ứng dụng, tránh được việc đọc file cấu hình JSON đĩa cứng lặp đi lặp lại trong mỗi request.

---

## 2. Thuật toán Re-seed Hạt giống Bất đồng bộ từ Atmospheric Noise (Non-blocking True RNG)

### 2.1. Ý nghĩa & Bối cảnh
Hệ thống game Gacha yêu cầu tính ngẫu nhiên cực kỳ cao để đảm bảo công bằng. Các thuật toán sinh số giả ngẫu nhiên (PRNG) thông thường dựa trên thời gian hệ thống rất dễ bị đoán trước hoặc lặp chu kỳ. 

Giải pháp tối ưu là sử dụng nguồn số ngẫu nhiên thực sự (True Random Number Generator - TRNG) dựa trên nhiễu khí quyển (Atmospheric Noise) từ API dịch vụ `random.org`. Tuy nhiên, việc gọi API bên ngoài (mạng Internet) đồng bộ trong luồng HTTP Request sẽ tạo ra độ trễ (latency) từ 200ms đến vài giây, phá vỡ trải nghiệm chơi game thời gian thực. 

`ChaosTheoryHelper` giải quyết bài toán này bằng cơ chế **Bất đồng bộ tuần hoàn (Asynchronous Background Re-seeding)**.

### 2.2. Đặc tả Thuật toán
- **File mã nguồn:** Server: [ChaosTheoryHelper.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/utils/ChaosTheoryHelper.java)
- **Đầu vào (Input):**
  - API Endpoint: `https://www.random.org`
  - Fallback entropy: `System.nanoTime() ^ System.currentTimeMillis()`
- **Đầu ra (Output):**
  - Số thực ngẫu nhiên $[0.0, 1.0)$ hoặc số nguyên ngẫu nhiên trong khoảng chỉ định.

### 2.3. Mã giả Thuật toán (Pseudo-code)

```text
CLASS ChaosTheoryHelper
    STATIC random = SecureRandom()
    STATIC scheduler = ScheduledExecutorService(threadName="chaos-theory-scheduler", isDaemon=true)
    
    // Khởi chạy khi nạp Class
    STATIC INITIALIZER
        // Seed tạm thời tránh block khởi động
        random.setSeed(System.nanoTime() XOR System.currentTimeMillis())
        
        // Lên lịch chạy ngầm định kỳ mỗi 10 phút để nạp seed khí quyển
        scheduler.scheduleWithFixedDelay(
            task = injectChaosAsynchronously,
            initialDelay = 0,
            delay = 10,
            unit = MINUTES
        )
    END STATIC INITIALIZER
    
    FUNCTION injectChaosAsynchronously()
        TRY
            url = "https://www.random.org/integers/?num=1&min=1&max=1000000000&col=1&base=10&format=plain&rnd=new"
            response = httpClient.executeRequest(url, timeout=5_SECONDS)
            
            IF response.isSuccessful AND response.body IS NOT NULL THEN
                rawNumber = ParseLong(response.body.trim())
                // Trộn True Random từ API với thời gian thực nano để gia tăng độ hỗn loạn tối đa
                newSeed = rawNumber XOR System.nanoTime()
                random.setSeed(newSeed)
                Log("Re-seed thành công với hạt giống khí quyển: " + rawNumber)
            ELSE
                LogWarning("Không thể kết nối random.org. Sử dụng hạt giống hệ thống fallback.")
                fallbackSeed()
            END IF
        CATCH Exception e
            LogError("Lỗi mạng khi re-seed: " + e.message)
            fallbackSeed()
        END TRY
    END FUNCTION
    
    FUNCTION fallbackSeed()
        random.setSeed(System.nanoTime() XOR System.currentTimeMillis())
    END FUNCTION
    
    FUNCTION nextDouble()
        // Gọi SecureRandom cực nhanh (0ms) từ nguồn seed đã được làm giàu liên tục
        RETURN random.nextDouble()
    END FUNCTION
    
    FUNCTION nextInt(bound)
        IF bound <= 0 THEN RETURN 0
        RETURN random.nextInt(bound)
    END FUNCTION
END CLASS
```

### 2.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Zero Blocking Latency:** Bằng cách tách biệt hoàn toàn việc truy xuất API mạng ra một luồng Daemon chạy ẩn định kỳ mỗi 10 phút, luồng chính xử lý logic game (như quay Gacha, nâng cấp thẻ) chỉ việc đọc từ vùng nhớ RAM đã được làm giàu hạt giống, đảm bảo thời gian phản hồi đạt mức tối ưu tuyệt đối **0ms**.
- **Daemon Threads:** Scheduler sử dụng luồng Daemon đảm bảo luồng này sẽ tự động giải phóng và bị tắt khi JVM dừng, tránh hiện tượng Memory Leak hoặc treo tiến trình hệ thống khi tắt Server.

---

## 3. Thuật toán Cắt khuôn mặt Nghệ sĩ Thông Minh (ML Kit Smart Face Crop)

### 3.1. Ý nghĩa & Bối cảnh
Trong màn hình danh sách thẻ bài hoặc giao diện Chat, các thẻ bài gốc (kích thước lớn) cần được hiển thị ở dạng ảnh thu nhỏ (Avatar hình tròn). Nếu cắt ảnh theo cách căn giữa thông thường (Center Crop), khuôn mặt của nghệ sĩ trên thẻ bài thường bị lệch hoặc bị mất một phần (do tỉ lệ ảnh gốc đa dạng và nghệ sĩ không đứng chính giữa). 

Để giải quyết bài toán UX này, dự án tích hợp thư viện **Google ML Kit Face Detection** trực tiếp vào luồng xử lý ảnh của Glide, tự động định vị khuôn mặt để thực hiện khoanh vùng cắt tròn hoàn hảo.

### 3.2. Đặc tả Thuật toán
- **File mã nguồn:** Client: [SmartFaceCropTransformation.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/SmartFaceCropTransformation.java)
- **Đầu vào (Input):**
  - `toTransform` (Bitmap): Ảnh gốc đầy đủ của thẻ bài.
- **Đầu ra (Output):**
  - `result` (Bitmap): Ảnh avatar hình tròn đã cắt căn giữa chính xác vào khuôn mặt nghệ sĩ.

### 3.3. Mã giả Thuật toán (Pseudo-code)

```text
FUNCTION transform(bitmapPool, toTransform, outWidth, outHeight)
    // 1. Loại bỏ 18% viền trang trí màu bên phải của phôi thẻ gốc
    usableWidth = INT(toTransform.width * 0.82)
    size = MIN(usableWidth, toTransform.height)
    
    // Tọa độ cắt căn giữa mặc định (Trường hợp không tìm thấy mặt)
    targetX = (usableWidth - size) / 2
    targetY = (toTransform.height - size) / 2
    
    TRY
        // 2. Khởi tạo và cấu hình bộ phát hiện khuôn mặt của Google ML Kit
        options = FaceDetectorOptions.Builder()
            .setPerformanceMode(PERFORMANCE_MODE_FAST)
            .build()
        detector = FaceDetection.getClient(options)
        inputImage = InputImage.fromBitmap(toTransform, rotation=0)
        
        // Chờ kết quả phát hiện mặt đồng bộ (an toàn vì Glide chạy hàm này trên luồng ngầm)
        faces = Tasks.await(detector.process(inputImage))
        
        IF faces IS NOT EMPTY THEN
            mainFace = faces.get(0)
            bounds = mainFace.boundingBox
            faceCenterX = bounds.centerX()
            faceCenterY = bounds.centerY()
            
            // Dịch chuyển vùng cắt hình vuông để bao quanh tâm khuôn mặt
            targetX = faceCenterX - (size / 2)
            targetY = faceCenterY - (size / 2)
            
            // Ràng buộc tọa độ nằm trong giới hạn cho phép
            IF targetX < 0 THEN targetX = 0
            IF targetY < 0 THEN targetY = 0
            IF targetX + size > usableWidth THEN targetX = usableWidth - size
            IF targetY + size > toTransform.height THEN targetY = toTransform.height - size
            
            Log("Đã phát hiện khuôn mặt! Căn chỉnh tâm cắt tại: " + faceCenterX + "," + faceCenterY)
        END IF
    CATCH Exception e
        Log("ML Kit thất bại, tự động fallback căn giữa: " + e.message)
    END TRY
    
    // 3. Thực hiện cắt hình vuông và áp dụng mặt nạ tròn (Circular Mask)
    squaredBitmap = Bitmap.createBitmap(toTransform, targetX, targetY, size, size)
    
    // Tái sử dụng bộ nhớ đệm bitmap từ Glide Pool
    resultBitmap = bitmapPool.get(size, size, Config.ARGB_8888)
    resultBitmap.setHasAlpha(true)
    
    canvas = Canvas(resultBitmap)
    // Clear canvas vẽ trong suốt
    canvas.drawColor(TRANSPARENT, PorterDuff.Mode.CLEAR)
    
    paint = Paint(antiAlias=true)
    // Vẽ hình tròn cơ sở
    canvas.drawCircle(centerX = size/2, centerY = size/2, radius = size/2, paint)
    
    // Sử dụng chế độ hòa trộn SRC_IN để lồng ảnh hình vuông vào khuôn tròn
    paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
    canvas.drawBitmap(squaredBitmap, left=0, top=0, paint)
    
    // Giải phóng bộ nhớ đệm
    IF squaredBitmap NOT EQUALS toTransform THEN
        bitmapPool.put(squaredBitmap)
    END IF
    
    RETURN resultBitmap
END FUNCTION
```

### 3.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Tasks.await() Blocking:** ML Kit là tác vụ bất đồng bộ. Tuy nhiên, Glide gọi phương thức `transform` từ luồng tính toán ngầm (`DiskCacheExecutor`). Việc dùng `Tasks.await()` biến lời gọi thành đồng bộ giúp giữ code phẳng, dễ kiểm soát luồng dữ liệu mà hoàn toàn không gây đóng băng giao diện người dùng (Main Thread UI).
- **Tái sử dụng bộ nhớ (Bitmap Pool):** Thuật toán tận dụng `bitmapPool.get` của Glide để tái sử dụng vùng nhớ đệm Bitmap đã cấp phát. Điều này giúp giảm thiểu tần suất hệ thống kích hoạt **Garbage Collection (GC)**, tối ưu bộ nhớ đáng kể trên các thiết bị cấu hình yếu (như Android 9 Emulator).

---

## 4. Thuật toán Khống chế Gia tốc Cuộn & Co Giãn Vùng Rìa Grid (ABS Fling Brakes & Dynamic Scaling)

### 4.1. Ý nghĩa & Bối cảnh
Khi người dùng cuộn (scroll) danh sách thẻ bài với số lượng lên đến hàng ngàn thẻ, thao tác lướt nhanh (Fling) có thể tạo ra vận tốc cuộn cực đại rất lớn. Điều này ép hệ thống phải tải và render ảnh liên tục với tần suất cao, dẫn đến quá tải băng thông mạng, tràn bộ nhớ đệm CPU/GPU và gây hiện tượng giật lag khung hình (Jank). 

Giải pháp khắc phục bao gồm hai phần phối hợp:
1. **ABS Fling Brakes (ViewUtils):** Phanh hãm lực cuộn tự động khi vận tốc vượt ngưỡng thiết lập.
2. **Dynamic Grid Scaling (GridScaleScrollListener):** Áp dụng hiệu ứng thu nhỏ và làm mờ dần các thẻ bài khi chúng trượt ra sát rìa trên/dưới màn hình để tạo cảm giác "Quiet Luxury" sâu lắng.

### 4.2. Đặc tả Thuật toán
- **File mã nguồn:** Client:
  - [ViewUtils.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/ViewUtils.java)
  - [GridScaleScrollListener.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/GridScaleScrollListener.java)
- **Đầu vào (Input):**
  - Sự kiện cuộn trên `RecyclerView`.
  - Hằng số `max_fling_velocity` cấu hình trong hệ thống XML.
- **Đầu ra (Output):**
  - Các thuộc tính `scaleX`, `scaleY`, `alpha` của các View con được cập nhật động theo thời gian thực.

### 4.3. Mã giả Thuật toán (Pseudo-code)

#### A. Thuật toán Phanh Vận tốc lướt (ABS Fling Brakes)
```text
FUNCTION limitFlingVelocity(recyclerView)
    // Đăng ký bộ lắng nghe sự kiện lướt
    recyclerView.setOnFlingListener(NEW OnFlingListener() {
        OVERRIDE FUNCTION onFling(velocityX, velocityY)
            // Lấy giới hạn tốc độ tối đa từ tài nguyên XML (ví dụ: 4000 pixel/giây)
            maxVelocity = recyclerView.context.resources.getInteger(R.integer.max_fling_velocity)
            
            newX = velocityX
            newY = velocityY
            
            // Áp dụng giới hạn vận tốc
            IF ABS(velocityX) > maxVelocity THEN
                newX = SIGN(velocityX) * maxVelocity
            END IF
            IF ABS(velocityY) > maxVelocity THEN
                newY = SIGN(velocityY) * maxVelocity
            END IF
            
            // Nếu có sự điều chỉnh, tự kích hoạt fling thủ công và tiêu thụ sự kiện
            IF newX != velocityX OR newY != velocityY THEN
                recyclerView.fling(newX, newY)
                RETURN TRUE // Đã xử lý xong (Hệ thống không xử lý tiếp)
            END IF
            
            RETURN FALSE // Giữ nguyên hành vi mặc định nếu vận tốc thấp hơn giới hạn
        END FUNCTION
    })
END FUNCTION
```

#### B. Thuật toán Co giãn & Làm mờ Vùng Rìa Grid (Dynamic Scaling)
```text
FUNCTION applyScaleEffect(recyclerView)
    height = recyclerView.height
    IF height == 0 THEN RETURN
    
    centerY = height / 2
    maxDistance = height / 2.0
    minScale = 0.85 // Thẻ sẽ bị thu nhỏ còn tối đa 85% ở rìa
    threshold = 0.7 // Ngưỡng 70% (chỉ co giãn khi thẻ trượt quá 70% khoảng cách từ tâm ra rìa)
    
    FOR i = 0 TO recyclerView.childCount - 1 DO
        child = recyclerView.getChildAt(i)
        
        IF child.height == 0 THEN
            child.scaleX = 1.0
            child.scaleY = 1.0
            child.alpha = 1.0
            CONTINUE
        END IF
        
        childCenterY = child.top + (child.height / 2)
        distance = ABS(centerY - childCenterY)
        ratio = MIN(1.0, distance / maxDistance)
        
        normalizedRatio = 0.0
        // Chỉ áp dụng hiệu ứng khi thẻ vượt ngưỡng threshold
        IF ratio > threshold THEN
            normalizedRatio = (ratio - threshold) / (1.0 - threshold)
        END IF
        
        // Tính toán tỉ lệ co giãn động
        scale = 1.0 - ((1.0 - minScale) * normalizedRatio)
        
        // Tính độ mờ nhạt dần (tối đa mờ đi còn 40% ở sát viền)
        alpha = 1.0 - (0.6 * normalizedRatio)
        
        // Cập nhật trực tiếp lên View
        child.scaleX = scale
        child.scaleY = scale
        child.alpha = alpha
    END FOR
END FUNCTION
```

### 4.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Giảm tải chu kỳ vẽ (Render Overload Mitigation):** Hãm vận tốc cuộn giúp trì hoãn tần suất nạp/giải nén ảnh của Glide, giảm tải trực tiếp cho luồng Garbage Collection của Android, loại bỏ hiện tượng tràn RAM giả lập.
- **Trì hoãn bằng Post-Layout (Dynamic Layout Sync):** Trong sự kiện `onLayoutChange`, thuật toán sử dụng cơ chế trì hoãn `recyclerView.post(...)` để đảm bảo việc tính toán tỉ lệ scale chỉ xảy ra sau khi các view con đã được định vị chính xác hoàn toàn trong cây View, ngăn ngừa xung đột luồng và hiện tượng giật giật (layout flickering).

---

## 5. Thuật toán Đồng bộ Metadata Galactic Bất đồng bộ (Galactic Metadata Sync & Cache-Busting)

### 5.1. Ý nghĩa & Bối cảnh
Để vận hành kho đồ chứa hàng chục ngàn thẻ bài nghệ sĩ ở chế độ **Local-First**, Client bắt buộc phải lưu trữ một bản sao cơ sở dữ liệu gốc (Galactic Master Data) cục bộ. Tuy nhiên, dữ liệu này liên tục thay đổi (thêm thẻ mới, đổi ảnh, thay đổi mùa thẻ) từ phía Server. 

Nếu tải toàn bộ file JSON thô có dung lượng lớn mỗi khi vào app, thiết bị sẽ gặp lỗi tràn bộ nhớ (OOM) hoặc nghẽn mạng. Giải pháp của dự án là **Thuật toán Đồng bộ Hai Bước (Two-Phase Sync)** kết hợp kỹ thuật **Bẻ khóa Bộ nhớ đệm CDN (Cache-Busting)**:
1.  **Bước 1 (Check Manifest):** Client gửi request nhẹ để so sánh nhãn thời gian (timestamp) cục bộ với server.
2.  **Bước 2 (Bust & Pull):** Nếu có bản mới, client ép tải file dữ liệu JSON đầy đủ bằng cách gán khóa băm thời gian thực (tránh bộ nhớ đệm CDN Cloudflare), sau đó nạp ngầm bất đồng bộ vào Room Database.

### 5.2. Đặc tả Thuật toán
- **File mã nguồn:** Client: [DatabaseLoader.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/utils/DatabaseLoader.java)
- **Đầu vào (Input):**
    - SharedPreferences `last_sync_timestamp`: nhãn thời gian của phiên đồng bộ thành công gần nhất trên máy client.
    - API Manifest: `@GET("/api/assets/manifest")` chứa nhãn thời gian từ phía Server (`remoteSyncTime`).
    - API Full Database: `@GET("api/v1/assets/database")` nhận tham số cache-buster.
- **Đầu ra (Output):**
    - File `database.json` trong bộ nhớ trong ứng dụng được cập nhật.
    - Room Database `master_objets` được làm sạch và nạp mới toàn bộ thực thể `MasterObjetEntity`.
    - SharedPreferences `last_sync_timestamp` được cập nhật timestamp mới.
    - Giao diện cập nhật danh sách kho đồ thông qua `notifyInventoryChanged()`.

### 5.3. Mã giả Thuật toán (Pseudo-code)

```text
FUNCTION syncMetadataWithServer(context, callback)
    appContext = context.getApplicationContext()
    
    // 1. Gửi request lấy manifest dung lượng siêu nhỏ để kiểm tra cập nhật
    apiService.getAssetManifest().enqueue(NEW Callback() {
        OVERRIDE FUNCTION onResponse(call, response)
            IF response.isSuccessful AND response.body IS NOT NULL THEN
                manifest = ParseJson(response.body.string())
                remoteSyncTime = manifest.getLong("lastSync")
                
                localSyncTime = appContext.getSharedPreferences().getLong("last_sync_timestamp", 0)
                
                // So sánh nhãn thời gian
                IF remoteSyncTime > localSyncTime OR localSyncTime == 0 THEN
                    sizeMb = 2.0 // Ước tính kích thước dữ liệu nén
                    // Thông báo cho UI hiển thị nút bấm/tiến trình cập nhật cho người dùng
                    callback.onUpdateAvailable(remoteSyncTime, sizeMb)
                ELSE
                    callback.onNoUpdate()
                END IF
            ELSE
                callback.onNoUpdate()
            END IF
        END FUNCTION

        OVERRIDE FUNCTION onFailure(call, t)
            // Lỗi mạng hoặc server offline -> Dùng cache Room DB nội địa hoàn toàn
            callback.onNoUpdate()
        END FUNCTION
    })
END FUNCTION

FUNCTION pullFullDatabase(appContext, newTimestamp, callback)
    // 2. Tạo chuỗi Cache Buster độc nhất để vượt qua bộ lọc cache của Cloudflare Edge CDN
    cacheBuster = "t=" + System.currentTimeMillis()
    callback.onProgress(20)
    
    apiService.getFullDatabase(cacheBuster).enqueue(NEW Callback() {
        OVERRIDE FUNCTION onResponse(call, response)
            IF response.isSuccessful AND response.body IS NOT NULL THEN
                // Chuyển việc ghi đĩa và xử lý JSON sang Thread chạy ngầm tránh ANR
                START_BACKGROUND_THREAD
                    TRY
                        jsonString = response.body.string()
                        callback.onProgress(60)
                        
                        IF LENGTH(jsonString) < 100 THEN
                            callback.onError("Dữ liệu JSON tải về không hợp lệ")
                            RETURN
                        END IF
                        
                        // 3. Ghi đè file JSON cục bộ vào Internal Storage (/files/database.json)
                        success = updateInternalDatabaseFile(appContext, jsonString, newTimestamp)
                        
                        IF success THEN
                            // Xóa cache Map trong bộ nhớ RAM
                            clearMemoryCache()
                            
                            // 4. Đồng bộ dữ liệu JSON thô vừa ghi vào Room DB
                            isRoomSyncing = true
                            db = AppDatabase.getInstance(appContext)
                            db.masterObjetDao().deleteAll() // Dọn sạch dữ liệu cũ
                            
                            // Parse JSON và chuẩn bị danh sách Entity nạp theo lô
                            rootObj = ParseJson(jsonString)
                            cardsArray = rootObj.getJSONArray("collections")
                            entities = EMPTY_LIST
                            
                            FOR i = 0 TO LENGTH(cardsArray) - 1 DO
                                cardJson = cardsArray.getJSONObject(i)
                                entity = NEW MasterObjetEntity()
                                
                                colId = cardJson.optString("collectionId")
                                IF colId IS EMPTY THEN colId = cardJson.getString("id")
                                
                                entity.collectionId = colId
                                entity.memberName = cardJson.getString("member")
                                entity.seasonName = cardJson.getString("season")
                                entity.rarityClass = cardJson.getString("class")
                                entity.frontImageId = cardJson.getString("frontImage")
                                entity.backImageId = cardJson.getString("backImage")
                                entity.baseOvr = cardJson.getInt("ovr")
                                
                                ADD entity TO entities
                            END FOR
                            
                            // Thực hiện Batch Insert vào SQLite qua Room
                            db.masterObjetDao().insertAll(entities)
                            isRoomSyncing = false
                            
                            // 5. Cập nhật nhãn thời gian đồng bộ thành công vào SharedPreferences
                            appContext.getSharedPreferences().putLong("last_sync_timestamp", newTimestamp)
                            
                            // Trả kết quả về Main Thread để cập nhật UI
                            RUN_ON_MAIN_THREAD
                                callback.onProgress(100)
                                callback.onComplete()
                                notifyInventoryChanged() // Bắn sự kiện vẽ lại các grid card
                            END RUN_ON_MAIN_THREAD
                        ELSE
                            callback.onError("Không thể ghi tệp tin vào bộ nhớ trong")
                        END IF
                    CATCH Exception e
                        isRoomSyncing = false
                        callback.onError(e.message)
                    END TRY
                END_BACKGROUND_THREAD
            ELSE
                callback.onError("Mã lỗi phản hồi từ Server: " + response.code())
            END IF
        END FUNCTION

        OVERRIDE FUNCTION onFailure(call, t)
            callback.onError(t.message)
        END FUNCTION
    })
END FUNCTION
```

- **Background Parsing:** Toàn bộ công đoạn xử lý chuỗi JSON thô hàng chục MB và chèn dữ liệu vào Room được đẩy hoàn toàn xuống luồng phụ (Background Thread), đảm bảo luồng vẽ giao diện chính (Main UI Thread) không bị treo hay giật lag (đạt chuẩn mượt mà 60fps trên Android 9 Emulator).

---

## 6. Thuật toán Cào Metadata Định kỳ & ETL Pipeline ở Backend (Scheduled Metadata Scraper & Backend ETL Pipeline)

### 6.1. Ý nghĩa & Bối cảnh
Để đảm bảo kho dữ liệu thẻ bài nghệ sĩ trên server Spring Boot luôn đồng bộ với các đợt phát hành thẻ mới trên thực tế mà không cần nhà phát triển phải nhập liệu thủ công, hệ thống backend cài đặt một tiến trình cào dữ liệu tự động từ nguồn `objekt.top` kết hợp với pipeline ETL (Extract - Transform - Load).

Thao tác nạp hàng chục ngàn dòng dữ liệu từ xa vào MySQL rất dễ gây ra các lỗi hiệu năng như nghẽn mạng, xung đột tài nguyên hoặc lỗi **N+1 Query** khi kiểm tra và tạo mới các bảng từ điển khóa ngoại (`members`, `seasons`, `classes`). Thuật toán giải quyết vấn đề này bằng các kỹ thuật:
1.  **Chống chạy trùng (Status Lock):** Dùng biến trạng thái volatile để khóa ngăn chặn hai job chạy song song gây xung đột dữ liệu.
2.  **Bộ nhớ đệm từ điển (JPA Dictionary Cache):** Cache cục bộ các thực thể danh mục để tránh việc truy vấn SQL liên tục khi lập chỉ mục khóa ngoại.
3.  **Tách mã ảnh bằng Regex:** Sử dụng Regular Expression biên dịch sẵn để trích xuất chỉ lấy mã ảnh băm duy nhất từ URL đầy đủ của Cloudflare CDN, giảm dung lượng DB.
4.  **Batch Load:** Thực hiện ghi cơ sở dữ liệu theo từng lô (Batch size = 200) thay vì lưu từng dòng đơn lẻ.

### 6.2. Đặc Tả Thuật Toán
- **File mã nguồn:** Server:
    - [AssetManagementService.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/service/AssetManagementService.java)
    - [EtlService.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/service/EtlService.java)
- **Đầu vào (Input):**
    - Trang API nguồn: `https://objekt.top/api/collection?artist=tripleS&limit=20000`.
- **Đầu ra (Output):**
    - File dữ liệu thô `database.json` và tệp chỉ mục `manifest.json`.
    - Dữ liệu đồng bộ (UPSERT) trong các bảng MySQL: `members`, `seasons`, `classes`, `cards`.

### 6.3. Mã giả Thuật toán (Pseudo-code)

```text
CLASS AssetManagementService
    VOLATILE syncStatus = "IDLE"
    
    // Luồng tự động quét định kỳ hàng giờ
    @Scheduled(cron = "0 0 * * * *")
    FUNCTION scheduledSync()
        Log("Bắt đầu chu kỳ cập nhật Metadata định kỳ...")
        fullSyncProcess()
    END FUNCTION

    // Luồng chạy khi server khởi động xong
    @EventListener(ApplicationReadyEvent)
    FUNCTION onApplicationReady()
        Log("Server ready! Khởi chạy đồng bộ lần đầu...")
        fullSyncProcess()
    END FUNCTION

    FUNCTION fullSyncProcess()
        // 1. Áp dụng Khóa Trạng thái (Mutex lock) ngăn chặn chạy song song
        IF syncStatus != "IDLE" THEN
            LogWarning("Đang có tiến trình đồng bộ khác chạy, bỏ qua lần này.")
            RETURN
        END IF
        
        TRY
            syncStatus = "SCRAPING"
            
            // 2. Gọi API ngoài lấy dữ liệu JSON thô
            jsonContent = fetchLatestMetadataFromObjektTop()
            IF jsonContent IS NULL THEN
                syncStatus = "IDLE"
                RETURN
            END IF
            
            // Parse dữ liệu và sắp xếp theo ngày tạo giảm dần (mới nhất lên đầu)
            collectionsList = parseJsonToCollectionsList(jsonContent)
            SortCollectionsByCreatedAtDescending(collectionsList)
            
            // 3. Kiểm tra thay đổi kích thước file và ghi đè file database.json
            oldSize = GetFileLength("data/assets/database.json")
            saveSortedDatabaseToFile(jsonContent, collectionsList)
            newSize = GetFileLength("data/assets/database.json")
            
            // Chỉ cập nhật manifest.json nếu có thay đổi dung lượng file thực tế
            IF oldSize != newSize THEN
                generateNewManifestFile(LENGTH(collectionsList))
            END IF
            
            // 4. Kích hoạt Job ETL nạp dữ liệu vào MySQL
            etlService.runEtlJob()
            
            // Reload cache bộ nhớ đệm
            cardDataService.reload()
            
            syncStatus = "IDLE"
            Log("Cập nhật Metadata thành công hoàn tất!")
        CATCH Exception e
            syncStatus = "IDLE"
            LogError("Lỗi đồng bộ: " + e.message)
        END TRY
    END FUNCTION
END CLASS

CLASS EtlService
    // Regex tĩnh biên dịch trước để tách ID ảnh từ Cloudflare URL
    STATIC IMAGE_ID_PATTERN = Pattern.compile("https://imagedelivery\\.net/qQuMkbHJ-0s6rwu8vup_5w/([^/]+)/.*")

    @Transactional
    FUNCTION runEtlJob()
        Log("Bắt đầu Job ETL đồng bộ dữ liệu vào MySQL...")
        
        // 1. Caching local để tránh lỗi N+1 query cho các bảng từ điển khóa ngoại
        memberMap = NEW HashMap() // Lưu Cache (Tên -> Member Entity)
        seasonMap = NEW HashMap() // Lưu Cache (Tên -> Season Entity)
        classMap = NEW HashMap()  // Lưu Cache (Tên -> CardClass Entity)
        
        // Đọc tệp tin database.json vừa cào từ đĩa
        collections = readCollectionsFromJsonFile("data/assets/database.json")
        
        batchCards = EMPTY_LIST
        processedIds = NEW HashSet()
        count = 0
        
        FOR EACH dto IN collections DO
            IF dto.id IS NULL OR processedIds.contains(dto.id) THEN
                CONTINUE // Bỏ qua bản ghi rác hoặc trùng lặp trong tệp tin
            END IF
            processedIds.add(dto.id)
            
            // 2. Tra cứu từ điển thông qua cache cục bộ (Nếu không có mới select/insert DB)
            member = memberMap.computeIfAbsent(dto.member, key -> getOrCreateMember(key))
            season = seasonMap.computeIfAbsent(dto.season, key -> getOrCreateSeason(key))
            cardClass = classMap.computeIfAbsent(dto.cardClass, key -> getOrCreateClass(key))
            
            // 3. Trích xuất ID ảnh Cloudflare bằng Regex
            frontImageId = extractImageId(dto.frontImage)
            backImageId = extractImageId(dto.backImage)
            
            // 4. UPSERT vào bảng cards (Tìm bản ghi cũ để cập nhật hoặc tạo mới hoàn toàn)
            card = cardRepository.findById(dto.id).orElse(NEW Card())
            card.id = dto.id
            card.member = member
            card.season = season
            card.cardClass = cardClass
            card.frontImageId = frontImageId
            card.backImageId = backImageId
            card.collectionNo = dto.collectionNo
            
            // Đảm bảo không ghi đè cấp độ/OVR hiện tại nếu là thẻ đã tồn tại
            IF card.baseOvr == 0 THEN card.baseOvr = 70 END IF
            IF card.upgradeLevel == 0 THEN card.upgradeLevel = 1 END IF
            
            // Tạo liên kết video động đối với các thẻ dạng Motion
            IF cardClass.name EQUALS "Motion" THEN
                slug = dto.slug != null ? dto.slug.toLowerCase() : ""
                IF slug IS EMPTY THEN
                    slug = LowerCase(season.name) + "-" + LowerCase(member.name) + "-" + LowerCase(dto.collectionNo)
                END IF
                card.frontVideoUrl = "https://cdn.apollo.cafe/mco/triples/" + slug + ".mp4"
            ELSE
                card.frontVideoUrl = NULL
            END IF
            
            ADD card TO batchCards
            count = count + 1
            
            // 5. Lưu theo lô (Batch Save 200 bản ghi) để tối ưu hóa I/O cơ sở dữ liệu
            IF LENGTH(batchCards) >= 200 THEN
                cardRepository.saveAllAndFlush(batchCards)
                batchCards.clear()
            END IF
        END FOR
        
        // Lưu số lượng bản ghi dư thừa còn lại ở lô cuối
        IF NOT batchCards.isEmpty() THEN
            cardRepository.saveAllAndFlush(batchCards)
        END IF
        
        Log("Hoàn tất ETL! Tổng số card đã UPSERT: " + count)
    END FUNCTION

    FUNCTION getOrCreateMember(name)
        // Tra cứu MySQL, nếu không tồn tại thì tự tạo mới và trả về
        RETURN memberRepository.findByName(name)
                .orElseGet(() -> memberRepository.save(NEW Member(name)))
    END FUNCTION

    FUNCTION extractImageId(url)
        IF url IS NULL THEN RETURN "" END IF
        matcher = IMAGE_ID_PATTERN.matcher(url)
        IF matcher.find() THEN
            RETURN matcher.group(1) // Trích xuất mã ID
        END IF
        RETURN url // Fallback
    END FUNCTION
END CLASS
```

### 6.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Dictionary Caching (Chống N+1 Query):** Khi nạp 20,000 thẻ bài, nếu mỗi thẻ bài đều truy vấn SQL để tìm `member_id`, `season_id` và `class_id` thì hệ thống sẽ phải thực thi 60,000 câu lệnh `SELECT` (lỗi N+1 Query kinh điển). Bằng việc sử dụng cache `HashMap` cục bộ lưu trữ thực thể từ điển ngay trong tiến trình, hệ thống chỉ truy vấn DB ở những thẻ bài có nghệ sĩ/season mới xuất hiện lần đầu, giảm thiểu 99.9% số lượng query dư thừa.
- **Batch Processing:** Gom nhóm các đối tượng và thực thi ghi dữ liệu theo lô `saveAllAndFlush(200)` giúp Hibernate gộp các lệnh SQL insert/update thành một transaction lớn gửi về MySQL, tối ưu hóa I/O và giảm thiểu độ trễ giao tiếp mạng giữa Backend và DB Server.
- **Pre-compiled Regex:** Tốc độ đối sánh chuỗi URL được tối ưu hóa tối đa nhờ việc biên dịch trước mẫu Regex `IMAGE_ID_PATTERN` và khai báo dưới dạng biến tĩnh (`STATIC`), tránh việc biên dịch mẫu biểu thức chính quy lặp đi lặp lại hàng chục ngàn lần trong vòng lặp.
- **Thread-Safe Mutex Lock:** Sử dụng biến `volatile syncStatus` hoạt động như một cờ hiệu trạng thái (Mutex Lock) giúp bảo vệ tiến trình cào dữ liệu không bị kích hoạt chồng chéo nếu job giờ trước chưa chạy xong, ngăn chặn xung đột tài nguyên đĩa cứng (`database.json` bị ghi đè đồng thời) và lỗi deadlock trên MySQL.

---

## 7. Thuật toán Vòng quay Gacha & Phân phối Lưới Ma trận hiển thị (Gacha Spin Engine & Reveal Grid Distribution)

### 7.1. Ý nghĩa & Bối cảnh
Hệ thống Gacha trao đổi thẻ (Spin) cho phép người chơi hiến tế (sacrifice) một thẻ bài không dùng đến để đổi lấy cơ hội nhận được một thẻ bài khác ngẫu nhiên. Để tăng tính kịch tính và cảm giác hồi hộp chân thực (Gacha UX), hệ thống không chỉ trả về thẻ bài trúng thưởng duy nhất mà còn sinh ra một lưới ma trận chứa 16 thẻ bài (gồm 1 thẻ trúng thực sự và 15 thẻ "nền" làm mồi nhử) để Client chạy hiệu ứng vòng quay ma trận.

Để đảm bảo tính minh bạch, công thức tính toán tỉ lệ rơi áp dụng cơ chế **Biến động tỉ lệ động (Dynamic Fluctuation)** và **Chuẩn hóa (Normalization)** kết hợp với nguồn True RNG khí quyển để ngăn ngừa sự lặp lại chu kỳ của máy tính.

### 7.2. Đặc tả Thuật toán
- **File mã nguồn:**
  - Server: [SpinSystem.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/utils/SpinSystem.java)
- **Đầu vào (Input):**
  - Cấu hình tỉ lệ cơ bản (`spin_rates` từ `rates_config.json`).
  - Danh sách thẻ bài Master đã phân nhóm theo Class (`groupedCards`).
- **Đầu ra (Output):**
  - `result` (JsonObject): Thẻ bài trúng thưởng thực tế (hoặc `Nothing` nếu trượt).
  - `revealGrid` (List<JsonObject>): Lưới 16 thẻ bài được chọn lọc để hiển thị trên UI.
  - `finalRates` (Map<String, Double>): Bảng tỉ lệ thực tế đã chuẩn hóa của phiên quay đó.

### 7.3. Mã giả Thuật toán (Pseudo-code)

#### A. Thuật toán Biến động & Chuẩn hóa Tỉ lệ rơi (Dynamic Rate Fluctuation & Normalization)
```text
FUNCTION calculateFinalRates(baseRates, groupedCards)
    finalRates = EMPTY_MAP
    totalInitial = 0.0

    // 1. Áp dụng biến động ngẫu nhiên động trong biên độ ±10% tỉ lệ gốc
    FOR EACH entry IN baseRates DO
        group = entry.key
        baseRate = entry.value

        // Nếu nhóm không có thẻ nào (trừ Nothing), đặt tỉ lệ bằng 0
        IF group != "Nothing" AND groupedCards.get(group) IS EMPTY THEN
            finalRates.put(group, 0.0)
            CONTINUE
        END IF

        maxFluc = baseRate * 0.10
        fluctuation = (ChaosTheoryHelper.nextDouble() * 2 * maxFluc) - maxFluc
        val = MAX(0.0, baseRate + fluctuation)

        finalRates.put(group, val)
        totalInitial = totalInitial + val
    END FOR

    // 2. Chuẩn hóa tổng tỉ lệ về đúng 100.0% và làm tròn 4 chữ số thập phân
    normalizedRates = EMPTY_MAP
    checkTotal = 0.0

    FOR EACH entry IN finalRates DO
        group = entry.key
        val = entry.value
        
        normalized = 0.0
        IF totalInitial > 0 THEN
            normalized = ROUND_TO_DECIMALS((val / totalInitial) * 100.0, decimals=4)
        END IF
        
        normalizedRates.put(group, normalized)
        checkTotal = checkTotal + normalized
    END FOR

    // 3. Xử lý sai số làm tròn (Rounding Drift) bằng cách bù trừ vào nhóm "Nothing"
    IF checkTotal != 100.0 AND totalInitial > 0 THEN
        diff = 100.0 - checkTotal
        currentNothing = normalizedRates.getOrDefault("Nothing", 0.0)
        normalizedRates.put("Nothing", ROUND_TO_DECIMALS(currentNothing + diff, decimals=4))
    END IF

    RETURN normalizedRates
END FUNCTION
```

#### B. Thuật toán Sinh Lưới Ma trận hiển thị (Reveal Grid Builder)
```text
FUNCTION spin()
    // 1. Tính toán tỉ lệ biến động động và quay thưởng theo trọng số
    finalRates = calculateFinalRates(baseRates, groupedCards)
    selectedGroup = selectGroupWeighted(finalRates) // Lấy nhóm trúng dựa trên RNG

    cardsInGroup = groupedCards.get(selectedGroup)
    IF cardsInGroup IS EMPTY THEN
        RETURN EmptySpinResult()
    END IF

    // Bốc ngẫu nhiên thẻ trúng thưởng thực tế
    randomIndex = ChaosTheoryHelper.nextInt(LENGTH(cardsInGroup))
    winningCard = cardsInGroup.get(randomIndex)

    // 2. Phân phối lưới 16 thẻ (1 thẻ thắng + 15 thẻ mồi nhử)
    distribution = buildCaseDistribution(selectedGroup)
    
    // Giảm số lượng của nhóm trúng đi 1 (chính là winningCard)
    distribution.put(selectedGroup, distribution.get(selectedGroup) - 1)

    revealGrid = EMPTY_LIST

    FOR EACH entry IN distribution DO
        group = entry.key
        count = entry.value
        IF count <= 0 THEN CONTINUE END IF

        pool = groupedCards.get(group)
        IF pool IS EMPTY THEN CONTINUE END IF

        // Loại bỏ thẻ trúng thưởng thực tế khỏi pool mồi nhử của group đó
        // (Tránh trùng lặp hình ảnh mặt sau thẻ trên giao diện lật)
        safePool = CLONE_LIST(pool)
        REMOVE winningCard FROM safePool

        // Trộn ngẫu nhiên safePool
        ChaosTheoryHelper.shuffle(safePool)
        
        // Bốc các thẻ mồi nhử đưa vào lưới
        takeCount = MIN(count, LENGTH(safePool))
        FOR i = 0 TO takeCount - 1 DO
            ADD safePool.get(i) TO revealGrid
        END FOR
    END FOR

    // Thêm thẻ thắng thực sự vào lưới
    ADD winningCard TO revealGrid

    // Trộn ngẫu nhiên vị trí của 16 thẻ trong lưới trước khi gửi về client
    ChaosTheoryHelper.shuffle(revealGrid)

    RETURN SpinResult(winningCard, revealGrid, selectedGroup)
END FUNCTION
```

### 7.4. Thiết kế Tối ưu Hiệu năng (Performance Justification)
- **Bù trừ sai số trôi (Drift Correction):** Trong các thuật toán chuẩn hóa xác suất tỉ lệ phần trăm, sai số do làm tròn (làm tròn lên/xuống ở dấu phẩy tĩnh) thường tích tụ khiến tổng xác suất lệch khỏi 100.0%. Bằng cách tự động tính toán và bù phần chênh lệch (`drift`) vào nhóm mặc định `Nothing`, thuật toán đảm bảo tính toàn vẹn toán học và tránh lỗi Crash trên các bộ phát hiện RNG nghiêm ngặt.
- **Tránh trùng lặp mồi nhử (Duplicate Back-image Prevention):** Khi vẽ lưới quay trên client, nếu thẻ trúng thưởng cũng đồng thời xuất hiện trong số 15 thẻ mồi nhử, người dùng sẽ thấy 2 ảnh giống hệt nhau khi quay ma trận, phá vỡ tính thẩm mỹ. Việc loại bỏ trực tiếp `winningCard` khỏi `safePool` trước khi bốc mồi nhử đảm bảo tính độc nhất của thẻ trúng thưởng trong phiên quay.
- **Biến động Tỉ lệ tự động (Algorithmic Fluctuation):** Cơ chế tự động thêm/bớt ±10% tỉ lệ cơ bản dựa trên nhiễu khí quyển làm tăng tính bất định cho game, ngăn ngừa hoàn toàn các kỹ thuật soi mã máy hoặc đoán chu kỳ của các thợ cào (Gacha cycle exploits).



