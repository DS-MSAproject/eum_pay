package com.eum.productserver.service;

import com.eum.productserver.dto.admin.AdminBulkUploadResult;
import com.eum.productserver.dto.admin.AdminProductCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 형식 대량 상품 업로드 서비스
 *
 * CSV 컬럼 순서:
 * productName, categoryId, price, brandName, content, tags, allergens, ingredients, deliveryFee, optionNames
 *
 * optionNames: 세미콜론(;) 구분 옵션명 목록 (예: "1kg;2kg;3kg")
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBulkUploadService {

    private final AdminProductService adminProductService;

    public AdminBulkUploadResult uploadFromCsv(MultipartFile csvFile) {
        int totalRows = 0;
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // 헤더 행 건너뜀
                if (firstLine) {
                    firstLine = false;
                    // 헤더 감지: 첫 셀이 숫자가 아니면 헤더로 간주
                    String[] firstCells = parseCsvLine(line);
                    if (firstCells.length > 0 && !isNumeric(firstCells[0].trim())) {
                        log.debug("[BulkUpload] 헤더 행 건너뜀: {}", line);
                        continue;
                    }
                }

                totalRows++;
                try {
                    AdminProductCreateRequest req = parseLine(line, totalRows);
                    adminProductService.createProduct(req);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    String errorMsg = String.format("행 %d 처리 실패: %s", totalRows, e.getMessage());
                    errors.add(errorMsg);
                    log.warn("[BulkUpload] {}", errorMsg);
                }
            }
        } catch (Exception e) {
            log.error("[BulkUpload] CSV 파일 읽기 오류", e);
            errors.add("CSV 파일 읽기 오류: " + e.getMessage());
        }

        log.info("[BulkUpload] 완료 - total={}, success={}, fail={}", totalRows, successCount, failCount);

        return AdminBulkUploadResult.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .failCount(failCount)
                .errors(errors)
                .build();
    }

    /**
     * CSV 한 행 파싱 → AdminProductCreateRequest 변환
     *
     * 컬럼 순서: productName(0), categoryId(1), price(2), brandName(3),
     *           content(4), tags(5), allergens(6), ingredients(7),
     *           deliveryFee(8), optionNames(9)
     */
    private AdminProductCreateRequest parseLine(String line, int rowNum) {
        String[] cols = parseCsvLine(line);

        if (cols.length < 5) {
            throw new IllegalArgumentException(
                    String.format("행 %d: 최소 5개 컬럼 필요 (productName,categoryId,price,brandName,content) — 실제 컬럼 수: %d",
                            rowNum, cols.length));
        }

        AdminProductCreateRequest req = new AdminProductCreateRequest();

        req.setProductName(col(cols, 0));
        req.setCategoryId(parseLong(col(cols, 1), "categoryId", rowNum));
        req.setPrice(parseLong(col(cols, 2), "price", rowNum));
        req.setBrandName(col(cols, 3));
        req.setContent(col(cols, 4));
        req.setTags(col(cols, 5));
        req.setAllergens(col(cols, 6));
        req.setIngredients(col(cols, 7));

        String deliveryFeeStr = col(cols, 8);
        if (deliveryFeeStr != null && !deliveryFeeStr.isBlank()) {
            req.setDeliveryFee(parseLong(deliveryFeeStr, "deliveryFee", rowNum));
        }

        // 옵션명 파싱 (세미콜론 구분)
        String optionNames = col(cols, 9);
        if (optionNames != null && !optionNames.isBlank()) {
            List<AdminProductCreateRequest.OptionDto> optionDtos = new ArrayList<>();
            for (String optName : optionNames.split(";")) {
                String trimmed = optName.trim();
                if (!trimmed.isEmpty()) {
                    AdminProductCreateRequest.OptionDto optDto = new AdminProductCreateRequest.OptionDto();
                    optDto.setOptionName(trimmed);
                    optDto.setExtraPrice(0L);
                    optionDtos.add(optDto);
                }
            }
            if (!optionDtos.isEmpty()) {
                req.setOptions(optionDtos);
            }
        }

        return req;
    }

    /**
     * 간단한 CSV 행 파싱 (쉼표 구분, 따옴표 내 쉼표 처리)
     */
    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++; // escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    private String col(String[] cols, int index) {
        if (index >= cols.length) return null;
        String val = cols[index].trim();
        return val.isEmpty() ? null : val;
    }

    private Long parseLong(String value, String fieldName, int rowNum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    String.format("행 %d: '%s' 필드는 비어있을 수 없습니다.", rowNum, fieldName));
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("행 %d: '%s' 필드 값 '%s'은 숫자여야 합니다.", rowNum, fieldName, value));
        }
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isBlank()) return false;
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
