/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Virtual collection that provides paged data in a streaming manner
 * Enables processing of large datasets page by page without loading all data into memory.
 *
 * @param <T> Data type
 */
@RequiredArgsConstructor
public class StreamingCollection<T> extends AbstractCollection<T> {

    private final Function<PageRequest, Page<T>> pageSupplier;
    private final int pageSize;
    private final long totalSize;

    /**
     * Create virtual collection from page supplier
     * 
     * @param pageSupplier Page data supply function
     * @param pageSize Page size
     */
    public StreamingCollection(Function<PageRequest, Page<T>> pageSupplier, int pageSize) {
        this.pageSupplier = pageSupplier;
        this.pageSize = pageSize;
        
        // Query first page to get total size
        Page<T> firstPage = pageSupplier.apply(PageRequest.of(0, pageSize));
        this.totalSize = firstPage.getTotalElements();
    }

    @Override
    public Iterator<T> iterator() {
        return new StreamingIterator<>(pageSupplier, pageSize, totalSize);
    }

    @Override
    public int size() {
        return (int) totalSize;
    }

    /**
     * Iterator that traverses pages in a streaming manner
     */
    private static class StreamingIterator<T> implements Iterator<T> {
        private final Function<PageRequest, Page<T>> pageSupplier;
        private final int pageSize;
        private final long totalSize;
        
        private int currentIndex = 0;
        private int currentPageIndex = 0;
        private Page<T> currentPage;
        private Iterator<T> currentPageIterator;

        public StreamingIterator(Function<PageRequest, Page<T>> pageSupplier, int pageSize, long totalSize) {
            this.pageSupplier = pageSupplier;
            this.pageSize = pageSize;
            this.totalSize = totalSize;
            loadNextPage();
        }

        @Override
        public boolean hasNext() {
            if (currentIndex >= totalSize) {
                return false;
            }
            
            if (currentPageIterator != null && currentPageIterator.hasNext()) {
                return true;
            }
            
            loadNextPage();
            return currentPageIterator != null && currentPageIterator.hasNext();
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            
            currentIndex++;
            return currentPageIterator.next();
        }

        private void loadNextPage() {
            if (currentIndex >= totalSize) {
                return;
            }
            
            currentPage = pageSupplier.apply(PageRequest.of(currentPageIndex++, pageSize));
            currentPageIterator = currentPage.getContent().iterator();
        }
    }
} 