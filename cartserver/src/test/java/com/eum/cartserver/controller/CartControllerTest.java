package com.eum.cartserver.controller;

import com.eum.cartserver.dto.CartSliceResponse;
import com.eum.cartserver.dto.CartResponse;
import com.eum.cartserver.exception.CartItemNotFoundException;
import com.eum.cartserver.exception.CartNotFoundException;
import com.eum.cartserver.exception.GlobalExceptionHandler;
import com.eum.cartserver.service.CartQueryService;
import com.eum.cartserver.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(GlobalExceptionHandler.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CartQueryService cartQueryService;

    @Test
    void addItem_returnsValidationErrorForInvalidRequest() throws Exception {
        mockMvc.perform(post("/cart/additem")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "optionId": 2001,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.productId").exists());
    }

    @Test
    void updateQuantity_returnsNotFoundWhenCartItemDoesNotExist() throws Exception {
        when(cartService.updateItem(7L, 1001L, 2001L, 3L))
                .thenThrow(new CartItemNotFoundException("장바구니에 해당 상품이 없습니다."));

        mockMvc.perform(put("/cart/quantity")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "optionId": 2001,
                                  "quantity": 3
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("장바구니에 해당 상품이 없습니다."));
    }

    @Test
    void addItem_returnsConflictWhenDatabaseConstraintFails() throws Exception {
        when(cartService.addItem(eq(7L), eq(1001L), eq(2001L), eq(2L)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        mockMvc.perform(post("/cart/additem")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "optionId": 2001,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void updateOption_returnsBadRequestForInvalidBusinessInput() throws Exception {
        mockMvc.perform(put("/cart/option")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "optionId": 2001
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.newOptionId").exists());
    }

    @Test
    void removeItem_returnsNotFoundWhenCartDoesNotExist() throws Exception {
        when(cartService.removeItem(7L, 1001L, 2001L))
                .thenThrow(new CartNotFoundException("장바구니가 없습니다."));

        mockMvc.perform(delete("/cart/selected")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "optionId": 2001
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("장바구니가 없습니다."));
    }

    @Test
    void addItem_returnsInvalidJsonWhenRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/cart/additem")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "optionId":
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void getCart_returnsBadRequestWhenUserHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/cart/additem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "optionId": 2001,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.message").value("필수 요청 헤더가 누락되었습니다: X-User-Id"));
    }

    @Test
    void getCart_returnsBadRequestWhenUserHeaderTypeIsInvalid() throws Exception {
        mockMvc.perform(put("/cart/select-all")
                        .header("X-User-Id", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectAllRequest(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value("요청값 형식이 올바르지 않습니다: X-User-Id"));
    }

    @Test
    void getCart_returnsAllItemsResponse() throws Exception {
        when(cartQueryService.getCartSlice(7L, 1))
                .thenReturn(CartSliceResponse.builder()
                        .userId(7L)
                        .selectedItemCount(0)
                        .allSelected(false)
                        .hasSelectedItems(false)
                        .page(1)
                        .size(10)
                        .hasNext(true)
                        .items(List.of())
                        .build());

        mockMvc.perform(get("/cart/all")
                        .header("X-User-Id", 7L)
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7L))
                .andExpect(jsonPath("$.selectedItemCount").value(0))
                .andExpect(jsonPath("$.allSelected").value(false))
                .andExpect(jsonPath("$.hasSelectedItems").value(false))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void getCart_supportsLegacyBasePathAlias() throws Exception {
        when(cartQueryService.getCartSlice(7L, 0))
                .thenReturn(CartSliceResponse.builder()
                        .userId(7L)
                        .selectedItemCount(0)
                        .allSelected(false)
                        .hasSelectedItems(false)
                        .page(0)
                        .size(10)
                        .hasNext(false)
                        .items(List.of())
                        .build());

        mockMvc.perform(get("/cart")
                        .header("X-User-Id", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7L))
                .andExpect(jsonPath("$.selectedItemCount").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void selectAll_returnsSuccessResponse() throws Exception {
        when(cartService.updateAllSelection(7L, true))
                .thenReturn(CartResponse.builder().userId(7L).items(List.of()).build());

        mockMvc.perform(put("/cart/select-all")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectAllRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7L));
    }

    @Test
    void removeItems_returnsSuccessResponse() throws Exception {
        when(cartService.removeItems(eq(7L), any()))
                .thenReturn(CartResponse.builder().userId(7L).items(List.of()).build());

        mockMvc.perform(delete("/cart/selecteditems")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "productId": 1001,
                                      "optionId": 2001
                                    },
                                    {
                                      "productId": 1002,
                                      "optionId": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7L));
    }

    @Test
    void removeItem_returnsSuccessResponse() throws Exception {
        when(cartService.removeItem(7L, 1001L, 2001L))
                .thenReturn(CartResponse.builder().userId(7L).items(List.of()).build());

        mockMvc.perform(delete("/cart/selected")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "optionId": 2001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7L));
    }

    @Test
    void addItem_returnsBadRequestWhenContentTypeIsUnsupported() throws Exception {
        mockMvc.perform(post("/cart/additem")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void selectAll_returnsSuccessResponseWithoutLocalTryCatch() throws Exception {
        when(cartService.updateAllSelection(7L, true))
                .thenReturn(CartResponse.builder().userId(7L).items(List.of()).build());

        mockMvc.perform(put("/cart/select-all")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectAllRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7L));
    }

    @Test
    void addItem_returnsCreatedResponse() throws Exception {
        when(cartService.addItem(7L, 1001L, 2001L, 2L))
                .thenReturn(CartResponse.builder().userId(7L).items(List.of()).build());

        mockMvc.perform(post("/cart/additem")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "optionId": 2001,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7L));
    }

    @Test
    void addItem_withoutOptionId_returnsCreatedResponse() throws Exception {
        when(cartService.addItem(7L, 1001L, null, 2L))
                .thenReturn(CartResponse.builder().userId(7L).items(List.of()).build());

        mockMvc.perform(post("/cart/additem")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1001,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7L));
    }

    private record SelectAllRequest(Boolean isSelectedAll) {
    }
}
