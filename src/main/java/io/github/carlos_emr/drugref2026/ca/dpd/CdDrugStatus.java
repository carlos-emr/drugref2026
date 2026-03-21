/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * Originally: Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.drugref2026.ca.dpd;

import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * JPA entity mapping to the {@code cd_drug_status} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Tracks the regulatory lifecycle status of each drug product authorized in Canada. A drug
 * product may pass through several statuses over its lifetime (e.g., "APPROVED", "MARKETED",
 * "CANCELLED POST-MARKET", "DORMANT"). Each status change is recorded as a separate row
 * with a history date, allowing a full audit trail of the drug's regulatory history.</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier for the drug product.</li>
 *   <li>{@code currentStatusFlag} -- "Y" if this row represents the current (most recent) status;
 *       otherwise blank or "N".</li>
 *   <li>{@code status} -- The status label (e.g., "MARKETED", "CANCELLED POST MARKET",
 *       "DORMANT", "APPROVED").</li>
 *   <li>{@code historyDate} -- The date on which this status became effective.</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code status.txt} (active products)
 * and {@code status_ia.txt} (inactive products).</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_drug_status")
@NamedQueries({@NamedQuery(name = "CdDrugStatus.findAll", query = "SELECT c FROM CdDrugStatus c"), @NamedQuery(name = "CdDrugStatus.findByDrugCode", query = "SELECT c FROM CdDrugStatus c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdDrugStatus.findByCurrentStatusFlag", query = "SELECT c FROM CdDrugStatus c WHERE c.currentStatusFlag = :currentStatusFlag"), @NamedQuery(name = "CdDrugStatus.findByStatus", query = "SELECT c FROM CdDrugStatus c WHERE c.status = :status"), @NamedQuery(name = "CdDrugStatus.findByHistoryDate", query = "SELECT c FROM CdDrugStatus c WHERE c.historyDate = :historyDate"), @NamedQuery(name = "CdDrugStatus.findById", query = "SELECT c FROM CdDrugStatus c WHERE c.id = :id")})
public class CdDrugStatus implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "current_status_flag")
    private String currentStatusFlag;
    @Column(name = "status", length = 40)
    private String status;
    @Column(name = "history_date")
    @Temporal(TemporalType.DATE)
    private Date historyDate;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdDrugStatus() {
    }

    public CdDrugStatus(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public String getCurrentStatusFlag() {
        return currentStatusFlag;
    }

    public void setCurrentStatusFlag(String currentStatusFlag) {
        this.currentStatusFlag = currentStatusFlag;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getHistoryDate() {
        return historyDate;
    }

    public void setHistoryDate(Date historyDate) {
        this.historyDate = historyDate;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof CdDrugStatus)) {
            return false;
        }
        CdDrugStatus other = (CdDrugStatus) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdDrugStatus[id=" + id + "]";
    }

}
