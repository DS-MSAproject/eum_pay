package com.eum.searchserver.dto.response;

import java.util.List;

public record NavigationMenuResponse(
        String status,
        List<NavigationTabResponse> data
) {
    public static NavigationMenuResponse success(List<NavigationTabResponse> data) {
        return new NavigationMenuResponse("success", data);
    }
}
