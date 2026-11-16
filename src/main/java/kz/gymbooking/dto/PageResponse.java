package kz.gymbooking.dto;

import java.util.List;
import java.util.function.Function;

import kz.gymbooking.repository.Page;

public record PageResponse<T>(List<T> items, long total) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.items().stream().map(mapper).toList(), page.total());
    }
}
