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
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/**
 * JPA entity mapping to the {@code cd_schedule} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Represents the regulatory scheduling classification of drug products in Canada. Drug
 * scheduling determines how a product may be sold and whether a prescription is required.
 * A single drug product may have multiple schedule designations. Common schedules include:</p>
 * <ul>
 *   <li>"OTC" -- Over-the-counter; available without a prescription</li>
 *   <li>"Prescription" -- Requires a valid prescription from an authorized prescriber</li>
 *   <li>"Schedule I" through "Schedule IV" -- Controlled substance schedules under the
 *       Controlled Drugs and Substances Act (CDSA), with Schedule I being the most restricted</li>
 *   <li>"Targeted" -- Targeted substances requiring additional tracking</li>
 * </ul>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier linking this schedule to its drug product.</li>
 *   <li>{@code schedule} -- The schedule designation string.</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code schedule.txt} (active products)
 * and {@code schedule_ia.txt} (inactive products).</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_schedule")
@NamedQueries({@NamedQuery(name = "CdSchedule.findAll", query = "SELECT c FROM CdSchedule c"), @NamedQuery(name = "CdSchedule.findByDrugCode", query = "SELECT c FROM CdSchedule c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdSchedule.findBySchedule", query = "SELECT c FROM CdSchedule c WHERE c.schedule = :schedule"), @NamedQuery(name = "CdSchedule.findById", query = "SELECT c FROM CdSchedule c WHERE c.id = :id")})
public class CdSchedule implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "schedule", length = 40)
    private String schedule;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdSchedule() {
    }

    public CdSchedule(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
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
        if (!(object instanceof CdSchedule)) {
            return false;
        }
        CdSchedule other = (CdSchedule) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdSchedule[id=" + id + "]";
    }

}
