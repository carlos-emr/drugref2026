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
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/**
 * JPA entity mapping to the {@code cd_form} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Represents the pharmaceutical dosage form of each drug product. The dosage form describes
 * the physical form in which the drug is manufactured and administered (e.g., "TABLET",
 * "CAPSULE", "SOLUTION", "CREAM", "POWDER FOR SOLUTION"). A drug product may have
 * multiple associated dosage forms.</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier linking this form to its drug product.</li>
 *   <li>{@code pharmCdFormCode} -- A numeric code identifying the specific dosage form. Used in
 *       combination with {@code aiGroupNo} from {@link CdDrugProduct} to construct "new generic"
 *       drug search entries (categories 18 and 19).</li>
 *   <li>{@code pharmaceuticalCdForm} -- The human-readable name of the dosage form
 *       (e.g., "TABLET", "CAPSULE", "CREAM").</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code form.txt} (active products)
 * and {@code form_ia.txt} (inactive products).</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_form")
@NamedQueries({@NamedQuery(name = "CdForm.findAll", query = "SELECT c FROM CdForm c"), @NamedQuery(name = "CdForm.findByDrugCode", query = "SELECT c FROM CdForm c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdForm.findByPharmCdFormCode", query = "SELECT c FROM CdForm c WHERE c.pharmCdFormCode = :pharmCdFormCode"), @NamedQuery(name = "CdForm.findByPharmaceuticalCdForm", query = "SELECT c FROM CdForm c WHERE c.pharmaceuticalCdForm = :pharmaceuticalCdForm"), @NamedQuery(name = "CdForm.findById", query = "SELECT c FROM CdForm c WHERE c.id = :id")})
public class CdForm implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "pharm_cd_form_code")
    private Integer pharmCdFormCode;
    @Column(name = "pharmaceutical_cd_form", length = 40)
    private String pharmaceuticalCdForm;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdForm() {
    }

    public CdForm(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public Integer getPharmCdFormCode() {
        return pharmCdFormCode;
    }

    public void setPharmCdFormCode(Integer pharmCdFormCode) {
        this.pharmCdFormCode = pharmCdFormCode;
    }

    public String getPharmaceuticalCdForm() {
        return pharmaceuticalCdForm;
    }

    public void setPharmaceuticalCdForm(String pharmaceuticalCdForm) {
        this.pharmaceuticalCdForm = pharmaceuticalCdForm;
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
        if (!(object instanceof CdForm)) {
            return false;
        }
        CdForm other = (CdForm) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdForm[id=" + id + "]";
    }

}
