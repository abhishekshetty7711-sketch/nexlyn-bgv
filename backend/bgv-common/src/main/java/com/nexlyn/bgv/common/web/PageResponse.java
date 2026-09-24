package com.nexlyn.bgv.common.web;

import java.util.List;

/** Standard paged list body: {@code { items, page, size, total }} (CLAUDE.md {@literal §16}). Pages start at 0. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
