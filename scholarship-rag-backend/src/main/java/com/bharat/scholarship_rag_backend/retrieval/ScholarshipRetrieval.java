package com.bharat.scholarship_rag_backend.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.repository.ScholarshipChunkRepo;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Finds the chunks that can answer a scholarship question.
 *
 * <p>Two arms run against the same query. The vector arm catches paraphrases
 * ("can more than two siblings get it" against a passage about sibling limits);
 * the full-text arm catches the exact tokens that matter for eligibility,
 * income ceilings and dates. Neither is reliable alone, so both run and their
 * rankings are fused with reciprocal rank fusion.
 *
 * <p>Fusion is used instead of adding normalised scores because {@code ts_rank}
 * and cosine distance live on unrelated scales. RRF only reads positions, so no
 * weight tuning is needed and a chunk that both arms like is promoted
 * automatically.
 *
 * <p>The scheme filter is a hard filter, never a ranking hint. A question about
 * one scheme must not return another scheme's rules, because the caller is
 * about to state them as fact.
 */
@Service
@Slf4j
public class ScholarshipRetrieval {

    /** Candidate depth per arm, wider than {@code topK} so fusion has room. */
    private static final int CANDIDATE_MULTIPLIER = 4;

    /** Minimum candidate depth, so small corpora still fill {@code topK}. */
    private static final int MIN_CANDIDATES = 8;

    /** Constant that damps the top of each ranking in RRF. */
    private static final int RRF_K = 60;

    /** Cosine distance cut-off. 1.2 accepts every direction. */
    private static final double MAX_DISTANCE = 1.2;

    /**
     * Normative text outranks a restatement of it. Applied after fusion so it
     * cannot distort the relative order inside either arm.
     */
    private static final Map<ContentType, Double> TYPE_WEIGHTS = Map.of(
            ContentType.GUIDELINE, 1.00,
            ContentType.ANNEXURE, 1.00,
            ContentType.FAQ, 0.90,
            ContentType.PORTAL_PAGE, 0.85);

    private final ScholarshipChunkRepo chunkRepo;
    private final QueryEmbedding queryEmbedding;
    private final int topK;

    public ScholarshipRetrieval(
            ScholarshipChunkRepo chunkRepo,
            QueryEmbedding queryEmbedding,
            @Value("${retrieval.top-k:8}") int topK) {
        this.chunkRepo = chunkRepo;
        this.queryEmbedding = queryEmbedding;
        this.topK = topK;
    }

    /**
     * Retrieves chunks for an already-reformulated query.
     *
     * @param query      text embedded for the vector arm and parsed for the
     *                   full-text arm
     * @param candidates schemes to search; an empty list means discovery mode
     *                   and searches every scheme
     * @return best matches, highest score first, never null
     */
    public List<ScholarshipChunkMatch> search(String query, List<SchemeType> candidates) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        boolean scoped = candidates != null && !candidates.isEmpty();
        String schemeArray = PgVectorLiteral.array(
                scoped ? candidates.stream().map(Enum::name).toList() : List.of());
        int candidateDepth = Math.max(MIN_CANDIDATES, topK * CANDIDATE_MULTIPLIER);

        // The keyword arm does not need the embedding, so both arms run even if
        // one of them fails; a broken vector extension should not silence search
        // outright.
        List<Object[]> vectorRows = runVectorArm(query, schemeArray, scoped, candidateDepth);
        List<Object[]> keywordRows = runKeywordArm(query, schemeArray, scoped, candidateDepth);

        if (vectorRows.isEmpty() && keywordRows.isEmpty()) {
            return List.of();
        }

        return fuse(vectorRows, keywordRows).stream()
                .limit(topK)
                .toList();
    }

    private List<Object[]> runVectorArm(String query, String schemeArray, boolean scoped, int limit) {
        try {
            float[] vector = queryEmbedding.embed(query);
            String literal = PgVectorLiteral.vector(vector);
            if (literal == null) {
                return List.of();
            }
            return chunkRepo.searchByVector(literal, schemeArray, scoped, MAX_DISTANCE, limit);
        } catch (RuntimeException e) {
            log.warn("Vector arm of scholarship retrieval failed, falling back to full text", e);
            return List.of();
        }
    }

    private List<Object[]> runKeywordArm(String query, String schemeArray, boolean scoped, int limit) {
        try {
            return chunkRepo.searchByKeyword(query, schemeArray, scoped, limit);
        } catch (RuntimeException e) {
            log.warn("Full-text arm of scholarship retrieval failed", e);
            return List.of();
        }
    }

    /**
     * Combines both ranked lists into one ordering.
     *
     * <p>A chunk found by both arms scores the sum of its two reciprocal ranks,
     * so agreement between a semantic and a lexical match is what lifts it to
     * the top. Duplicates are merged rather than returned twice: the same
     * passage legitimately appears in both result sets.
     */
    List<ScholarshipChunkMatch> fuse(List<Object[]> vectorRows, List<Object[]> keywordRows) {

        Map<UUID, ScholarshipChunkMatch> merged = new LinkedHashMap<>();
        Map<UUID, Double> scores = new LinkedHashMap<>();

        absorb(vectorRows, true, merged, scores);
        absorb(keywordRows, false, merged, scores);

        List<ScholarshipChunkMatch> ranked = new ArrayList<>(merged.size());
        for (Map.Entry<UUID, ScholarshipChunkMatch> entry : merged.entrySet()) {
            ScholarshipChunkMatch match = entry.getValue();
            double weight = TYPE_WEIGHTS.getOrDefault(match.contentType(), 1.0);
            ranked.add(match.withRanking(scores.get(entry.getKey()) * weight,
                    citation(match)));
        }

        ranked.sort(Comparator.comparingDouble(ScholarshipChunkMatch::score).reversed()
                // Stable tie-break on provenance keeps ordering deterministic
                // across runs, which matters for reproducible answers.
                .thenComparing(ScholarshipChunkMatch::sourceDocument,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(match -> match.chunkIndex() == null ? 0 : match.chunkIndex()));

        log.info("Fused {} vector and {} keyword rows into {} chunks",
                vectorRows.size(), keywordRows.size(), ranked.size());
        return ranked;
    }

    private void absorb(List<Object[]> rows,
                        boolean vectorArm,
                        Map<UUID, ScholarshipChunkMatch> merged,
                        Map<UUID, Double> scores) {

        int rank = 0;
        for (Object[] row : rows) {
            rank++;

            ScholarshipChunkMatch match = map(row, vectorArm);
            if (match == null || match.id() == null) {
                continue;
            }

            merged.putIfAbsent(match.id(), match);
            scores.merge(match.id(), 1.0 / (RRF_K + rank), Double::sum);
        }
    }

    /**
     * Maps a native row onto the match record.
     *
     * <p>Column positions follow the {@code SELECT} list in
     * {@link ScholarshipChunkRepo} and are read by index. Every conversion is
     * defensive because a native query hands back whatever the driver produced,
     * including {@code null}s and enum names as strings.
     *
     * @return the match, or null when the row is unusable
     */
    ScholarshipChunkMatch map(Object[] row, boolean vectorArm) {

        if (row == null || row.length < 12) {
            return null;
        }

        SchemeType scheme = scheme(row[1]);
        String content = asString(row[2]);
        if (content == null || content.isBlank()) {
            return null;
        }

        return new ScholarshipChunkMatch(
                asUuid(row[0]),
                scheme,
                content,
                contentType(row[3]),
                asString(row[4]),
                asString(row[5]),
                asString(row[6]),
                asInteger(row[7]),
                asInteger(row[8]),
                asInteger(row[9]),
                asInteger(row[10]),
                vectorArm ? asDouble(row[11]) : null,
                vectorArm ? null : asDouble(row[11]),
                0.0,
                null);
    }

    /** Scheme enum value, or null when the stored value is not recognised. */
    static SchemeType scheme(Object value) {
        String name = asString(value);
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return SchemeType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Content type enum value, defaulting to guideline when unreadable. */
    static ContentType contentType(Object value) {
        String name = asString(value);
        if (name == null || name.isBlank()) {
            return ContentType.GUIDELINE;
        }
        try {
            return ContentType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ContentType.GUIDELINE;
        }
    }

    private static String citation(ScholarshipChunkMatch match) {
        StringBuilder citation = new StringBuilder(
                match.scheme() == null
                        ? "Scholarship document"
                        : match.scheme().name().replace('_', ' '));

        String section = match.sourceSection();
        if (section != null && !section.isBlank()) {
            citation.append(", ").append(section.strip());
        }

        String page = match.pageLabel();
        if (!page.isEmpty()) {
            citation.append(", ").append(page);
        }
        return citation.toString();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static UUID asUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}