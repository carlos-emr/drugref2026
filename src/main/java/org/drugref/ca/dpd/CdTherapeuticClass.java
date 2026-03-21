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
@Table(name = "cd_therapeutic_class", catalog = "drugref2")
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
        return "org.drugref.ca.dpd.CdTherapeuticClass[id=" + id + "]";
    }

}
