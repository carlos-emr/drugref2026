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
 * JPA entity mapping to the {@code cd_therapeutic_class} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Represents the therapeutic classification codes assigned to each drug product. The primary
 * classification system is the WHO ATC (Anatomical Therapeutic Chemical) system, which classifies
 * drugs hierarchically by organ system, therapeutic intent, and chemical substance. Prior to
 * July 2022, the DPD also included AHFS (American Hospital Formulary Service) classification
 * codes, but these have been deprecated in newer DPD extracts.</p>
 *
 * <p>The ATC code is critical for drug interaction checking, allergy cross-referencing, and
 * therapeutic duplicate detection in clinical decision support systems.</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier linking this classification to its drug product.</li>
 *   <li>{@code tcAtcNumber} -- The ATC code (e.g., "N02BE01" for paracetamol/acetaminophen). Up to 8 characters.</li>
 *   <li>{@code tcAtc} -- The English-language ATC description (e.g., "ACETAMINOPHEN").</li>
 *   <li>{@code tcAhfsNumber} -- The AHFS classification number (deprecated since July 2022; may be null in newer data).</li>
 *   <li>{@code tcAhfs} -- The AHFS classification description (deprecated since July 2022).</li>
 *   <li>{@code tcAtcf} -- The French-language ATC description.</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code ther.txt} (active products)
 * and {@code ther_ia.txt} (inactive products).</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_therapeutic_class")
@NamedQueries({@NamedQuery(name = "CdTherapeuticClass.findAll", query = "SELECT c FROM CdTherapeuticClass c"), @NamedQuery(name = "CdTherapeuticClass.findByDrugCode", query = "SELECT c FROM CdTherapeuticClass c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdTherapeuticClass.findByTcAtcNumber", query = "SELECT c FROM CdTherapeuticClass c WHERE c.tcAtcNumber = :tcAtcNumber"), @NamedQuery(name = "CdTherapeuticClass.findByTcAtc", query = "SELECT c FROM CdTherapeuticClass c WHERE c.tcAtc = :tcAtc"), @NamedQuery(name = "CdTherapeuticClass.findByTcAhfsNumber", query = "SELECT c FROM CdTherapeuticClass c WHERE c.tcAhfsNumber = :tcAhfsNumber"), @NamedQuery(name = "CdTherapeuticClass.findByTcAhfs", query = "SELECT c FROM CdTherapeuticClass c WHERE c.tcAhfs = :tcAhfs"), @NamedQuery(name = "CdTherapeuticClass.findById", query = "SELECT c FROM CdTherapeuticClass c WHERE c.id = :id")})
public class CdTherapeuticClass implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code", length = 8)
    private Integer drugCode;
    @Column(name = "tc_atc_number", length = 8)
    private String tcAtcNumber;
    @Column(name = "tc_atc", length = 120)
    private String tcAtc;
    @Column(name = "tc_ahfs_number", length = 20)
    private String tcAhfsNumber;
    @Column(name = "tc_ahfs", length = 80)
    private String tcAhfs;
	@Column(name = "tc_atc_f", length = 240)
	private String tcAtcf;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdTherapeuticClass() {
    }

    public CdTherapeuticClass(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public String getTcAtcNumber() {
        return tcAtcNumber;
    }

    public void setTcAtcNumber(String tcAtcNumber) {
        this.tcAtcNumber = tcAtcNumber;
    }

    public String getTcAtc() {
        return tcAtc;
    }

    public void setTcAtc(String tcAtc) {
        this.tcAtc = tcAtc;
    }

    public String getTcAhfsNumber() {
        return tcAhfsNumber;
    }

    public void setTcAhfsNumber(String tcAhfsNumber) {
        this.tcAhfsNumber = tcAhfsNumber;
    }

    public String getTcAhfs() {
        return tcAhfs;
    }

    public void setTcAhfs(String tcAhfs) {
        this.tcAhfs = tcAhfs;
    }
	
	public String getTcAtcf() {
	    return tcAtcf;
	}
	
	public void setTcAtcf(String tcAtcf) {
		this.tcAtcf = tcAtcf;
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
        if (!(object instanceof CdTherapeuticClass)) {
            return false;
        }
        CdTherapeuticClass other = (CdTherapeuticClass) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdTherapeuticClass[id=" + id + "]";
    }

}
