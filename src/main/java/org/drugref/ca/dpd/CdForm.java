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
package org.drugref.ca.dpd;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_form", catalog = "drugref2")
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
        return "org.drugref.ca.dpd.CdForm[id=" + id + "]";
    }

}
