package com.eum.cartserver.controller;

import com.eum.cartserver.dto.CartItemAddRequest;
import com.eum.cartserver.dto.CartItemDeleteRequest;
import com.eum.cartserver.dto.CartItemOptionUpdateRequest;
import com.eum.cartserver.dto.CartItemSelectRequest;
import com.eum.cartserver.dto.CartItemUpdateRequest;
import com.eum.cartserver.dto.CartResponse;
import com.eum.cartserver.dto.CartSelectAllRequest;
import com.eum.cartserver.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@Validated
public class CartController {

    private final CartService cartService;

    @GetMapping("/all")
    public ResponseEntity<CartResponse> getCart(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/additem")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemAddRequest request) {
        CartResponse response = cartService.addItem(
                userId,
                request.getProductId(),
                request.getOptionId(),
                request.getQuantity()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/item/select-all")
    public ResponseEntity<CartResponse> selectAll(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody(required = false) CartSelectAllRequest request) {
        Boolean isSelectedAll = request != null ? request.getIsSelectedAll() : Boolean.TRUE;
        CartResponse response = cartService.updateAllSelection(userId, isSelectedAll);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/item/select")
    public ResponseEntity<CartResponse> selectItem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemSelectRequest request) {
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

    @PutMapping("/item/option")
    public ResponseEntity<CartResponse> updateOption(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemOptionUpdateRequest request) {
        CartResponse response = cartService.updateOption(
                userId,
                request.getProductId(),
                request.getOptionId(),
                request.getNewOptionId()
        );
        return ResponseEntity.ok(response);
    }

    @PutMapping("/item/quantity")
    public ResponseEntity<CartResponse> updateQuantity(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemUpdateRequest request) {
        CartResponse response = cartService.updateItem(
                userId,
                request.getProductId(),
                request.getOptionId(),
                request.getQuantity()
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/item")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartItemDeleteRequest request) {
        CartResponse response = cartService.removeItem(userId, request.getProductId(), request.getOptionId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/item/selected")
    public ResponseEntity<CartResponse> removeSelectedItems(@RequestHeader("X-User-Id") Long userId) {
        CartResponse response = cartService.removeSelectedItems(userId);
        return ResponseEntity.ok(response);
    }
}
