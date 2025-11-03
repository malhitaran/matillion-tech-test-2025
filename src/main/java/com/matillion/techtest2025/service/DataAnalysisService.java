package com.matillion.techtest2025.service;

import com.matillion.techtest2025.controller.response.DataAnalysisResponse;
import com.matillion.techtest2025.model.ColumnStatistics;
import com.matillion.techtest2025.repository.ColumnStatisticsRepository;
import com.matillion.techtest2025.repository.DataAnalysisRepository;
import com.matillion.techtest2025.repository.entity.ColumnStatisticsEntity;
import com.matillion.techtest2025.repository.entity.DataAnalysisEntity;
import com.matillion.techtest2025.exception.BadRequestException;
import com.matillion.techtest2025.exception.NotFoundException;
import com.matillion.techtest2025.model.ColumnProfile;
import com.matillion.techtest2025.model.InferredType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Service layer containing business logic for data analysis.
 * <p>
 * Responsible for parsing data, calculating statistics, and persisting results.
 */
@Service
@RequiredArgsConstructor
public class DataAnalysisService {

    private final DataAnalysisRepository dataAnalysisRepository;
    private final ColumnStatisticsRepository columnStatisticsRepository;

    /**
     * Analyzes CSV data and returns statistics.
     * <p>
     * Parses the CSV, calculates statistics (row count, column count, character count,
     * null counts per column), persists the results to the database, and returns the analysis.
     * <p>
     * <b>Note:</b> Current implementation is incomplete. Part 1 of the tech test
     * requires implementing the CSV parsing and analysis logic.
     *
     * @param data raw CSV data (rows separated by newlines, columns by commas)
     * @return analysis results
     */
    public DataAnalysisResponse analyzeCsvData(String data) {
        if (data == null || data.isEmpty()) {
            throw new BadRequestException("CSV data must not be empty");
        }

        // Split into non-empty lines, preserving order; ignore fully blank lines
        String[] rawLines = data.split("\\R");
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            if (line != null && !line.isEmpty()) {
                lines.add(line);
            }
        }

        if (lines.isEmpty()) {
            throw new BadRequestException("CSV data must contain a header row");
        }

        // Header parsing
        String header = lines.get(0);
        String[] headerColumns = header.split(",", -1);
        int numberOfColumns = headerColumns.length;

        // Prepare null counters and unique value sets per column
        int[] nullCounts = new int[numberOfColumns];
        @SuppressWarnings("unchecked")
        Set<String>[] uniqueSets = new Set[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) {
            uniqueSets[i] = new HashSet<>();
        }

        // Validate and process data rows
        int numberOfRows = 0;
        for (int i = 1; i < lines.size(); i++) {
            String row = lines.get(i);
            if (row.isEmpty()) {
                continue; // already filtered, but keep guard
            }

            String[] values = row.split(",", -1); // keep empty strings
            if (values.length != numberOfColumns) {
                throw new BadRequestException("Malformed CSV: inconsistent column counts");
            }

            numberOfRows++;
            for (int c = 0; c < numberOfColumns; c++) {
                String value = values[c];
                if (value.isEmpty()) {
                    nullCounts[c]++;
                } else {
                    uniqueSets[c].add(value);
                }
            }
        }

        long totalCharacters = data.length();
        OffsetDateTime creationTimestamp = OffsetDateTime.now();

        // Build parent entity
        DataAnalysisEntity dataAnalysisEntity = DataAnalysisEntity.builder()
                .originalData(data)
                .numberOfRows(numberOfRows)
                .numberOfColumns(numberOfColumns)
                .totalCharacters(totalCharacters)
                .createdAt(creationTimestamp)
                .build();

        // Build child column statistics and set bidirectional relationship
        List<ColumnStatisticsEntity> columnStatisticsEntities = new ArrayList<>();
        for (int c = 0; c < numberOfColumns; c++) {
            ColumnStatisticsEntity stat = ColumnStatisticsEntity.builder()
                    .dataAnalysis(dataAnalysisEntity)
                    .columnName(headerColumns[c])
                    .nullCount(nullCounts[c])
                    .uniqueCount(uniqueSets[c].size())
                    .build();
            columnStatisticsEntities.add(stat);
        }

        // Attach children to parent so cascade operations work (persist/delete)
        dataAnalysisEntity.getColumnStatistics().addAll(columnStatisticsEntities);

        // Persist parent; children will be cascaded
        dataAnalysisRepository.save(dataAnalysisEntity);

        // Map to response model
        List<ColumnStatistics> responseStats = columnStatisticsEntities.stream()
                .map(e -> new ColumnStatistics(e.getColumnName(), e.getNullCount(), e.getUniqueCount()))
                .toList();

        return new DataAnalysisResponse(
                numberOfRows,
                numberOfColumns,
                totalCharacters,
                responseStats,
                creationTimestamp
        );
    }

    /**
     * Retrieves a previously persisted analysis by id and maps it to response.
     */
    public DataAnalysisResponse getAnalysisById(Long id) {
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found with id: " + id));

        List<ColumnStatistics> stats = entity.getColumnStatistics().stream()
                .map(e -> new ColumnStatistics(e.getColumnName(), e.getNullCount(), e.getUniqueCount()))
                .toList();

        return new DataAnalysisResponse(
                entity.getNumberOfRows(),
                entity.getNumberOfColumns(),
                entity.getTotalCharacters(),
                stats,
                entity.getCreatedAt()
        );
    }

    /**
     * Deletes an analysis by id; throws if not found.
     */
    public void deleteAnalysisById(Long id) {
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found with id: " + id));
        dataAnalysisRepository.delete(entity);
    }

    /**
     * Builds per-column profiles (type inference and numeric summaries) for a persisted analysis.
     */
    public List<ColumnProfile> getColumnProfiles(Long id) {
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found with id: " + id));

        String data = entity.getOriginalData();
        String[] rawLines = data.split("\\R");
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            if (line != null && !line.isEmpty()) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw new BadRequestException("CSV data must contain a header row");
        }

        String header = lines.get(0);
        String[] headerColumns = header.split(",", -1);
        int numberOfColumns = headerColumns.length;

        int[] nullCounts = new int[numberOfColumns];
        @SuppressWarnings("unchecked")
        Set<String>[] uniqueSets = new Set[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) {
            uniqueSets[i] = new HashSet<>();
        }

        // For numeric summaries
        double[] sums = new double[numberOfColumns];
        int[] numericCounts = new int[numberOfColumns];
        Double[] mins = new Double[numberOfColumns];
        Double[] maxs = new Double[numberOfColumns];
        boolean[] anyDecimal = new boolean[numberOfColumns];
        boolean[] allNumeric = new boolean[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) allNumeric[i] = true;
        boolean[] allBoolean = new boolean[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) allBoolean[i] = true;

        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(",", -1);
            if (values.length != numberOfColumns) continue; // skip malformed rows here
            for (int c = 0; c < numberOfColumns; c++) {
                String v = values[c];
                if (v.isEmpty()) {
                    nullCounts[c]++;
                } else {
                    uniqueSets[c].add(v);

                    // boolean inference
                    String lv = v.toLowerCase();
                    boolean isBoolean = lv.equals("true") || lv.equals("false");
                    allBoolean[c] = allBoolean[c] && isBoolean;

                    // numeric inference
                    boolean isInteger = v.matches("[-+]?[0-9]+");
                    boolean isDecimal = v.matches("[-+]?[0-9]*\\.[0-9]+") || isInteger;
                    if (!isDecimal) {
                        allNumeric[c] = false;
                    } else {
                        double d = Double.parseDouble(v);
                        anyDecimal[c] = anyDecimal[c] || v.contains(".");
                        sums[c] += d;
                        numericCounts[c]++;
                        if (mins[c] == null || d < mins[c]) mins[c] = d;
                        if (maxs[c] == null || d > maxs[c]) maxs[c] = d;
                    }
                }
            }
        }

        List<ColumnProfile> profiles = new ArrayList<>();
        for (int c = 0; c < numberOfColumns; c++) {
            InferredType type;
            if (allBoolean[c]) {
                type = InferredType.BOOLEAN;
            } else if (allNumeric[c] && numericCounts[c] > 0) {
                type = anyDecimal[c] ? InferredType.DECIMAL : InferredType.INTEGER;
            } else {
                type = InferredType.STRING;
            }

            Double mean = numericCounts[c] > 0 ? (sums[c] / numericCounts[c]) : null;
            profiles.add(new ColumnProfile(
                    headerColumns[c],
                    type,
                    nullCounts[c],
                    uniqueSets[c].size(),
                    mins[c],
                    maxs[c],
                    mean
            ));
        }

        return profiles;
    }

}
