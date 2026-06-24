package ru.smetrix.dto;

import java.util.List;

public class MaterialSearchResponse {
    public List<MaterialDto> items;
    public int total;
    public int limit;
    public int offset;

    public MaterialSearchResponse(List<MaterialDto> items, int total, int limit, int offset) {
        this.items = items;
        this.total = total;
        this.limit = limit;
        this.offset = offset;
    }
}
