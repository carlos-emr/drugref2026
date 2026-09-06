/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.carlos_emr.drugref2026.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The last check standing between an empty import and the destruction of the previous dataset.
 *
 * <p>Every step of the update reports failure by throwing, and the worker's rollback covers
 * those. This guards the other case: a step that fails <em>silently</em> and returns normally
 * reaches the commit point, and past it the {@code *_prev} tables — the only remaining copy of
 * the drug data — are dropped. A review of this branch found exactly that path: a swallowed
 * read failure in {@code RecordParser} produced zero rows, and the run went on to report
 * "0 products" as a success while discarding the backup.
 */
class RxUpdateDBWorkerGateTest {

    private static Map<String, Long> counts(long products, long search) {
        Map<String, Long> hm = new HashMap<>();
        hm.put("CdDrugProduct", products);
        hm.put("CdDrugSearch", search);
        hm.put("CdCompanies", 1000L);
        return hm;
    }

    @Test
    @DisplayName("should refuse to commit when the rebuild produced no drug products")
    void shouldRefuseToCommit_whenNoDrugProducts() {
        assertThatThrownBy(() -> RxUpdateDBWorker.requireNonEmptyRebuild(counts(0L, 80065L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CdDrugProduct")
                .hasMessageContaining("keeping the previous dataset");
    }

    @Test
    @DisplayName("should refuse to commit when the search index came out empty")
    void shouldRefuseToCommit_whenTheSearchIndexIsEmpty() {
        // Products present but no search entries is the shape a failed ConfigureSearchData
        // leaves: drug lookups answer "None found" even though the product table looks fine.
        assertThatThrownBy(() -> RxUpdateDBWorker.requireNonEmptyRebuild(counts(39198L, 0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CdDrugSearch");
    }

    @Test
    @DisplayName("should let a real rebuild through")
    void shouldLetARealRebuildThrough_withPopulatedTables() {
        assertThatCode(() -> RxUpdateDBWorker.requireNonEmptyRebuild(counts(39198L, 80065L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should not block the commit when a count is missing or unreadable")
    void shouldNotBlockTheCommit_whenACountIsMissing() {
        // A floor of zero, not a plausibility threshold, and it fires only on a count it
        // actually read as zero. An absent or non-numeric entry means numberTableRows() did not
        // report on that table, which is not evidence the import failed -- and refusing to
        // commit a good rebuild would be its own outage.
        assertThatCode(() -> RxUpdateDBWorker.requireNonEmptyRebuild(new HashMap<>()))
                .doesNotThrowAnyException();
        assertThatCode(() -> RxUpdateDBWorker.requireNonEmptyRebuild(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept a genuinely small extract rather than second-guess Health Canada")
    void shouldAcceptAGenuinelySmallExtract_withOneRowPerTable() {
        assertThat(1L).isPositive();
        assertThatCode(() -> RxUpdateDBWorker.requireNonEmptyRebuild(counts(1L, 1L)))
                .doesNotThrowAnyException();
    }
}
