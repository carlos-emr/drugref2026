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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.drugref2026.Drugref;
import io.github.carlos_emr.drugref2026.ca.dpd.fetch.DPDImport;
import io.github.carlos_emr.drugref2026.ca.dpd.fetch.DpdDownloader;
import io.github.carlos_emr.drugref2026.ca.dpd.fetch.DpdTableSwap;
import io.github.carlos_emr.drugref2026.ca.dpd.fetch.TempNewGenericImport;
import io.github.carlos_emr.drugref2026.ca.dpd.history.HistoryUtil;

/**
 * Background worker thread that orchestrates a full database update from Health Canada's
 * Drug Product Database (DPD).
 *
 * <p>This thread is launched by {@link Drugref#updateDB()} and performs the following
 * sequential steps:
 * <ol>
 *   <li>Download and validate all three DPD archives ({@link DpdDownloader}). Nothing
 *       in the database is touched until every archive is on local disk.</li>
 *   <li>Move the current dataset aside as {@code *_prev} tables ({@link DpdTableSwap})</li>
 *   <li>Import raw DPD data (drug products, active ingredients, therapeutic classes,
 *       routes, forms, etc.) and build the search index</li>
 *   <li>Generate generic drug search entries from the imported data</li>
 *   <li>Flag ISMP (Institute for Safe Medication Practices) high-alert medications</li>
 *   <li>Enhance search data by adding descriptors and strength information to drug names</li>
 *   <li>Check the rebuild is not empty, then move the swap marker to {@code DISCARD} — <b>the
 *       commit point</b></li>
 *   <li>Record the update in the History table for auditing</li>
 *   <li>Store update statistics (timing, row counts) in {@link Drugref#DB_INFO} and discard
 *       the {@code *_prev} tables</li>
 * </ol>
 *
 * <p>If any step up to and including the marker move fails, the {@code *_prev} tables are put
 * back, so a failed update leaves the dataset exactly as it was. The marker move is the commit,
 * not the history row: the history insert comes after it deliberately, because a crash between
 * the two must leave the NEW data in place with a stale timestamp rather than a history row
 * claiming an update that had been rolled back. So a history failure does <em>not</em> restore
 * anything — the dataset is already committed and the run reports the failure with the new data
 * live. Nothing after the marker can fail the run: the history row and the {@code *_prev} drop
 * are both best-effort and report themselves in the success message instead. That keeps a
 * {@code FAILED} status meaning exactly one thing — the update was abandoned and the previous
 * dataset is what prescribers are searching — which is what the admin page tells the operator.
 * The outcome, success or failure with its reason, is recorded in {@link UpdateStatus} for
 * {@link Drugref#getUpdateStatus()}.
 *
 * <p>The {@link Drugref#UPDATE_DB} flag is set for the duration of the update to prevent
 * concurrent updates and to inform clients that data is being refreshed; it is cleared in
 * a {@code finally} block so that no failure can leave it set.
 */
public class RxUpdateDBWorker extends Thread{

    private static final Logger logger = MiscUtils.getLogger();

    /** Default constructor. */
    public RxUpdateDBWorker(){
        super("drugref-update");
    }

    /**
     * Executes the full database update sequence: download, swap, import, post-process.
     * Runs alone: {@link Drugref#updateDB()} refuses to start a second worker while
     * {@link Drugref#UPDATE_DB} is set.
     */
    @Override
    public void run(){
        UpdateStatus status = UpdateStatus.get();
        DpdTableSwap swap = new DpdTableSwap();
        DpdDownloader.DpdArchives archives = null;
        boolean previousDatasetMovedAside = false;
        long startedAt = System.currentTimeMillis();
        try {
            // Set the global flag so other threads/requests know an update is in progress
            // UPDATE_DB and UpdateStatus.begin() are both set by Drugref.updateDB()
            // under the class monitor, before this thread starts, so a client polling
            // the moment updateDB() returns "running" already sees RUNNING.
            logger.info("DrugRef database update started");

            // Step 1: fetch everything first. A download failure here costs nothing:
            // the previous pipeline dropped every table BEFORE opening the first URL.
            status.step("downloading Health Canada DPD archives");
            archives = DpdDownloader.download(DPDImport.dpdBaseUrl());

            // Step 2: keep the current dataset as *_prev so a failure below can restore it.
            // The flag goes up BEFORE the call, not after: backupLiveTables() renames
            // fifteen tables one at a time, and a failure partway through leaves some of
            // them moved. Setting the flag afterwards meant the catch block skipped the
            // restore for exactly that case, and the next attempt would then drop those
            // *_prev tables as stale -- discarding the only copy of the data.
            status.step("moving the current dataset aside");
            previousDatasetMovedAside = true;
            swap.backupLiveTables();

            // Step 3: import the DPD data and build the search index
            status.step("importing DPD data and building the search index");
            DPDImport dpdImport = new DPDImport();
            long timeDataImport = dpdImport.importDpd(archives);
            timeDataImport = (timeDataImport / 1000) / 60; // Convert milliseconds to minutes

            // Step 4: generic drug entries (categories 18/19), synthesized from the DPD data
            status.step("generating generic search entries");
            TempNewGenericImport newGenericImport = new TempNewGenericImport();
            long timeGenericImport = newGenericImport.run(); // milliseconds
            timeGenericImport = (timeGenericImport / 1000) / 60;

            // Step 5: ISMP high-alert medication flags (TALLman lettering etc.)
            status.step("applying ISMP medication safety names");
            dpdImport.setISMPmeds();

            // Step 6: search-name enhancement (form descriptors, strengths). Still
            // inside the rollback window -- these rewrite cd_drug_search, so a failure
            // here leaves the search index half-enhanced and the update is abandoned.
            status.step("enhancing search names");
            HashMap hm = dpdImport.numberTableRows();
            List<Integer> addedDescriptor = dpdImport.addDescriptorToSearchName();
            List<Integer> addedStrength = dpdImport.addStrengthToBrandName();

            // Step 7: THE COMMIT POINT. Moving the marker to DISCARD is a single atomic
            // statement, and it is what makes the new dataset the good one -- not the
            // history row. The order matters and is the opposite of the obvious one:
            // between the history insert and the marker move there is a window, and a
            // crash inside it must not be resolvable as "restore the old data", because
            // the history row would then claim an update that had been rolled back. With
            // the marker first, a crash in the window leaves the NEW data in place and a
            // stale timestamp -- correct data with a pessimistic date, which is the safe
            // direction for a drug database.
            status.step("committing the new dataset");
            requireNonEmptyRebuild(hm);
            swap.markDiscarding();
            previousDatasetMovedAside = false;

            // Step 8: record the update in the History table, which is what
            // getLastUpdateTime() reports. The history table is not swapped, so this
            // comes after every step that could still have abandoned the update.
            //
            // Best-effort, and it must be: the marker moved a moment ago, so the new dataset
            // IS the live one and there is nothing left to roll back. Throwing here used to
            // report the run as FAILED, which the admin page renders as "the previous drug
            // data was kept" -- a statement that is false past the commit point, and the
            // worst kind of false, because it tells the operator to expect the old data
            // while the new data is what prescribers are searching. A missing history row
            // costs a stale "last updated" date on correct data, which is the pessimistic
            // direction the commit ordering was chosen for in the first place.
            status.step("recording update history");
            String historyCaveat = "";
            try {
                if (!new HistoryUtil().addUpdateHistory()) {
                    historyCaveat = "; the update history row could NOT be written, so the"
                            + " reported \"last updated\" date stays at the previous run";
                }
            } catch (RuntimeException e) {
                logger.warn("DrugRef update: the new dataset is committed but the history row"
                        + " could not be written; the reported last-update date will be stale", e);
                historyCaveat = "; the update history row could NOT be written (" + describe(e)
                        + "), so the reported \"last updated\" date stays at the previous run";
            }
            if (!historyCaveat.isEmpty()) {
                logger.warn("DrugRef update: committed, but the history row was not written");
            }

            // Step 9: statistics for the admin interface, then let go of the old dataset
            Drugref.DB_INFO.put("tableRowNum", hm);
            Drugref.DB_INFO.put("timeImportDataMinutes", timeDataImport);
            Drugref.DB_INFO.put("timeImportGenericMinutes", timeGenericImport);
            Drugref.DB_INFO.put("descriptor", addedDescriptor);
            Drugref.DB_INFO.put("strength", addedStrength);

            // Step 10: drop the previous dataset. Best-effort by design: the update is
            // already committed, so a failure here leaves harmless *_prev tables that
            // the next start (or the next update) clears, and must not fail the run.
            status.step("discarding the previous dataset");
            try {
                swap.discardBackupTables();
            } catch (SQLException | RuntimeException e) {
                logger.warn("DrugRef update: the update succeeded but the previous dataset could not be"
                        + " dropped; the " + DpdTableSwap.BACKUP_SUFFIX + " tables will be cleared on the"
                        + " next start", e);
            }

            long minutes = (System.currentTimeMillis() - startedAt) / 60000L;
            String summary = "updated in " + minutes + " min; " + hm.get("CdDrugProduct") + " products, "
                    + hm.get("CdDrugSearch") + " search entries" + historyCaveat;
            status.succeed(summary);
            logger.info("DrugRef database update finished: " + summary);
        } catch (Throwable t) {
            // Throwable, so that an Error still gets the dataset put back -- but see the
            // rethrow at the end of this block: a fatal Error is not swallowed.
            // Every failure is reported: the thread used to die on an uncaught exception
            // with UPDATE_DB still true, and clients saw "updating" forever.
            logger.error("DrugRef database update failed during '" + status.getStep() + "'", t);
            String reason = "failed during '" + status.getStep() + "': " + describe(t);
            if (previousDatasetMovedAside) {
                try {
                    // recoverInterruptedSwap(), not restoreBackupTables(): the marker
                    // decides, not this flag. markDiscarding() can commit on the server and
                    // still throw on the way back (the connection dropping after the UPDATE
                    // lands is enough), and the flag is only cleared once the call returns
                    // normally. A blind restore in that window put the previous dataset back
                    // over one the marker had already accepted -- the exact splice the
                    // commit point exists to prevent. Reading the marker resolves all three
                    // cases: BACKUP restores, DISCARD finishes the cleanup, and a failure
                    // before the marker moved still reads BACKUP and restores as before.
                    reason += " -- " + swap.recoverInterruptedSwap();
                } catch (Exception restoreFailure) {
                    logger.error("DrugRef: could not restore the previous dataset after the failed update",
                            restoreFailure);
                    reason += " -- AND the previous dataset could NOT be restored: "
                            + describe(restoreFailure) + "; reload the drug reference seed";
                }
            }
            status.fail(reason);
            if (t instanceof Error) {
                // OutOfMemoryError and friends mean the JVM is in no state to keep
                // serving. The rollback above was the best-effort part; the process
                // must not carry on as though this were an ordinary failed update.
                // The finally below still runs, so the flag is cleared either way.
                throw (Error) t;
            }
        } finally {
            if (archives != null) {
                archives.deleteAll();
            }
            // Clear the update flag so the system returns to normal operation. UPDATE_DB is
            // volatile, so a reader outside the lock sees this write regardless; the monitor
            // is here because updateDB() tests the flag and starts a worker under it, and
            // this clear has to be serialized against that check-then-act or a retry racing
            // the end of this run could start a second worker.
            synchronized (Drugref.class) {
                Drugref.UPDATE_DB = false;
            }
        }
    }

    /** The tables an update that worked cannot leave empty, whatever else it did. */
    private static final String[] MUST_NOT_BE_EMPTY = {"CdDrugProduct", "CdDrugSearch"};

    /**
     * Refuses to commit a rebuild that produced no drugs.
     *
     * <p>The last thing standing between an empty import and the destruction of the previous
     * dataset. Every step before this reports failure by throwing, so the worker's own rollback
     * covers them — but a step that fails <em>silently</em> and returns normally reaches the
     * commit point, and past it {@link DpdTableSwap#discardBackupTables()} drops the
     * {@code *_prev} tables that are the only remaining copy of the drug data. The row counts
     * are already in hand ({@link DPDImport#numberTableRows()} runs a step earlier and feeds the
     * success message), so the check costs nothing; it simply was not being made, and the run
     * would announce "0 products" as a success while deleting the backup.
     *
     * <p>Deliberately a floor of zero rather than a plausibility threshold: this guards against
     * a broken pipeline, not against Health Canada publishing a short extract, and a worker that
     * second-guesses a genuinely smaller dataset would be its own outage. It runs before the
     * marker moves, so the throw lands in the rollback window and the previous dataset comes
     * back.
     *
     * @param rowCounts entity-name to row-count, from {@link DPDImport#numberTableRows()}
     * @throws IllegalStateException if a table an update cannot legitimately empty is empty
     */
    static void requireNonEmptyRebuild(Map<String, ?> rowCounts) {
        for (String table : MUST_NOT_BE_EMPTY) {
            Object count = rowCounts == null ? null : rowCounts.get(table);
            long rows = count instanceof Number ? ((Number) count).longValue() : -1L;
            if (rows == 0L) {
                throw new IllegalStateException("the rebuilt " + table + " table is empty, so the"
                        + " import produced no drug data; abandoning the update and keeping the"
                        + " previous dataset rather than discarding it");
            }
        }
    }

    /** Root-cause text for the operator: the innermost message, prefixed by the exception type. */
    public static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? "" : root.getMessage();
        return root.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }
}
