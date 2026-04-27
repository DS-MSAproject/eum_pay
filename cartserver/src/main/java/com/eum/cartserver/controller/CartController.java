package com.eum.cartserver.controller;

import com.eum.cartserver.dto.CartItemAddRequest;
import com.eum.cartserver.dto.CartItemDeleteRequest;
import com.eum.cartserver.dto.CartItemsDeleteRequest;
import com.eum.cartserver.dto.CartItemOptionUpdateRequest;
import com.eum.cartserver.dto.CartItemSelectRequest;
import com.eum.cartserver.dto.CartSliceResponse;
import com.eum.cartserver.dto.CartItemUpdateRequest;
import com.eum.cartserver.dto.CartResponse;
import com.eum.cartserver.dto.CartSelectAllRequest;
import com.eum.cartserver.service.CartQueryService;
import com.eum.cartserver.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@Validated
@Slf4j
public class CartController {

    private final CartService cartService;
    private final CartQueryService cartQueryService;

    @GetMapping("/get")

    public ResponseEntity<CartSliceResponse> getCart(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") Integer page) {
        log.info("getCart: {}", page);
        return ResponseEntity.ok(cartQueryService.getCartSlice(userId, page));
    }



    @PostMapping("/additem")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemAddRequest request) {
        log.info("addItem: {}", request.getProductId());

        CartResponse response = cartService.addItem(
                userId,
                request.getProductId(),
                request.getOptionId(),
                request.getQuantity()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 전체 선택
    @PutMapping("/select-all")
    public ResponseEntity<CartResponse> selectAll(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody(required = false) CartSelectAllRequest request) {
        log.info("selectAll");

        Boolean isSelectedAll = request != null ? request.getIsSelectedAll() : Boolean.TRUE;
        CartResponse response = cartService.updateAllSelection(userId, isSelectedAll);
        return ResponseEntity.ok(response);
    }

    // 장바구니안의 단건 선택
    @PutMapping("/select")
    public ResponseEntity<CartResponse> selectItem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemSelectRequest request) {
        log.info("selectItem");

        Boolean isSelected = request.getSelected() != null
                ? request.getSelected()
                : Boolean.TRUE;
        CartResponse response = cartService.updateItemSelection(
                userId,
                request.getProductId(),
                request.getOptionId(),
                isSelected
        );
        return ResponseEntity.ok(response);
    }

    // 장바구니안의 단건의 옵션 변경
    @PutMapping("/option")
    public ResponseEntity<CartResponse> updateOption(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemOptionUpdateRequest request) {
        log.info("updateOption");

        CartResponse response = cartService.updateOption(
                userId,
                request.getProductId(),
                request.getOptionId(),
                request.getNewOptionId()
        );
        return ResponseEntity.ok(response);
    }

    // 장바구니안의 단건의 수량 변경
    @PutMapping("/quantity")
    public ResponseEntity<CartResponse> updateQuantity(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemUpdateRequest request) {

        log.info("updateQuantity");

        CartResponse response = cartService.updateItem(
                userId,
                request.getProductId(),
                request.getOptionId(),
                request.getQuantity()
        );
        return ResponseEntity.ok(response);
    }


    //  장바구니안의 단건의 삭제: 프론트엔드의 장바구니 ui에서 렌더링되는 각 개별 상품 섹션이 있는 삭제 버튼을 사용자가 선택한 경우
    @DeleteMapping("/selected")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemDeleteRequest request) {

        log.info("removeItem");

        CartResponse response = cartService.removeItem(userId, request.getProductId(), request.getOptionId());
        return ResponseEntity.ok(response);
    }

    //  장바구니안의 1개이상의 상품 삭제
    @DeleteMapping("/selecteditems")
    public ResponseEntity<CartResponse> removeItems(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemsDeleteRequest request) {

        log.info("removeItems");

        CartResponse response = cartService.removeItems(userId, request.getItems());
        return ResponseEntity.ok(response);
    }
}