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

import java.util.HashMap;
import java.util.List;

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
 *   <li>Record the update in the History table for auditing</li>
 *   <li>Enhance search data by adding descriptors and strength information to drug names</li>
 *   <li>Store update statistics (timing, row counts) in {@link Drugref#DB_INFO} and discard
 *       the {@code *_prev} tables</li>
 * </ol>
 *
 * <p>If any step fails the {@code *_prev} tables are put back, so a failed update leaves
 * the dataset exactly as it was. The outcome, success or failure with its reason, is
 * recorded in {@link UpdateStatus} for {@link Drugref#getUpdateStatus()}.
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
            Drugref.UPDATE_DB = true;
            status.begin();
            logger.info("DrugRef database update started");

            // Step 1: fetch everything first. A download failure here costs nothing:
            // the previous pipeline dropped every table BEFORE opening the first URL.
            status.step("downloading Health Canada DPD archives");
            archives = DpdDownloader.download(DPDImport.dpdBaseUrl());

            // Step 2: keep the current dataset as *_prev so a failure below can restore it
            status.step("moving the current dataset aside");
            swap.backupLiveTables();
            previousDatasetMovedAside = true;

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

            // Step 6: record this update in the History table, which is what
            // getLastUpdateTime() reports. A silent failure here would make a
            // completed update look like it never happened.
            status.step("recording update history");
            if (!new HistoryUtil().addUpdateHistory()) {
                throw new IllegalStateException("could not record the update in the history table");
            }

            // Step 7: search-name enhancement (form descriptors, strengths)
            status.step("enhancing search names");
            HashMap hm = dpdImport.numberTableRows();
            List<Integer> addedDescriptor = dpdImport.addDescriptorToSearchName();
            List<Integer> addedStrength = dpdImport.addStrengthToBrandName();

            // Step 8: statistics for the admin interface, then let go of the old dataset
            Drugref.DB_INFO.put("tableRowNum", hm);
            Drugref.DB_INFO.put("timeImportDataMinutes", timeDataImport);
            Drugref.DB_INFO.put("timeImportGenericMinutes", timeGenericImport);
            Drugref.DB_INFO.put("descriptor", addedDescriptor);
            Drugref.DB_INFO.put("strength", addedStrength);

            status.step("discarding the previous dataset");
            swap.discardBackupTables();
            previousDatasetMovedAside = false;

            long minutes = (System.currentTimeMillis() - startedAt) / 60000L;
            String summary = "updated in " + minutes + " min; " + hm.get("CdDrugProduct") + " products, "
                    + hm.get("CdDrugSearch") + " search entries";
            status.succeed(summary);
            logger.info("DrugRef database update finished: " + summary);
        } catch (Throwable t) {
            // Every failure is reported: the thread used to die on an uncaught exception
            // with UPDATE_DB still true, and clients saw "updating" forever.
            logger.error("DrugRef database update failed during '" + status.getStep() + "'", t);
            String reason = "failed during '" + status.getStep() + "': " + describe(t);
            if (previousDatasetMovedAside) {
                try {
                    int restored = swap.restoreBackupTables();
                    reason += " -- previous dataset restored (" + restored + " tables)";
                } catch (Exception restoreFailure) {
                    logger.error("DrugRef: could not restore the previous dataset after the failed update",
                            restoreFailure);
                    reason += " -- AND the previous dataset could NOT be restored: "
                            + describe(restoreFailure) + "; reload the drug reference seed";
                }
            }
            status.fail(reason);
        } finally {
            if (archives != null) {
                archives.deleteAll();
            }
            // Clear the update flag so the system returns to normal operation
            Drugref.UPDATE_DB = false;
        }
    }

    /** Root-cause text for the operator: the innermost message, prefixed by the exception type. */
    static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? "" : root.getMessage();
        return root.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }
}
