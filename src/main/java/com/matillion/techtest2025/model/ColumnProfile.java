package com.matillion.techtest2025.model;

public record ColumnProfile(
        String columnName,
        InferredType inferredType,
        int nullCount,
        int uniqueCount,
        Double min,
        Double max,
        Double mean
) {
}


