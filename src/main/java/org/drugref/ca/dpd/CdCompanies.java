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
@Table(name = "cd_companies", catalog = "drugref2")
@NamedQueries({@NamedQuery(name = "CdCompanies.findAll", query = "SELECT c FROM CdCompanies c"), @NamedQuery(name = "CdCompanies.findByDrugCode", query = "SELECT c FROM CdCompanies c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdCompanies.findByMfrCode", query = "SELECT c FROM CdCompanies c WHERE c.mfrCode = :mfrCode"), @NamedQuery(name = "CdCompanies.findByCompanyCode", query = "SELECT c FROM CdCompanies c WHERE c.companyCode = :companyCode"), @NamedQuery(name = "CdCompanies.findByCompanyName", query = "SELECT c FROM CdCompanies c WHERE c.companyName = :companyName"), @NamedQuery(name = "CdCompanies.findByCompanyType", query = "SELECT c FROM CdCompanies c WHERE c.companyType = :companyType"), @NamedQuery(name = "CdCompanies.findByAddressMailingFlag", query = "SELECT c FROM CdCompanies c WHERE c.addressMailingFlag = :addressMailingFlag"), @NamedQuery(name = "CdCompanies.findByAddressBillingFlag", query = "SELECT c FROM CdCompanies c WHERE c.addressBillingFlag = :addressBillingFlag"), @NamedQuery(name = "CdCompanies.findByAddressNotificationFlag", query = "SELECT c FROM CdCompanies c WHERE c.addressNotificationFlag = :addressNotificationFlag"), @NamedQuery(name = "CdCompanies.findByAddressOther", query = "SELECT c FROM CdCompanies c WHERE c.addressOther = :addressOther"), @NamedQuery(name = "CdCompanies.findBySuiteNumber", query = "SELECT c FROM CdCompanies c WHERE c.suiteNumber = :suiteNumber"), @NamedQuery(name = "CdCompanies.findByStreetName", query = "SELECT c FROM CdCompanies c WHERE c.streetName = :streetName"), @NamedQuery(name = "CdCompanies.findByCityName", query = "SELECT c FROM CdCompanies c WHERE c.cityName = :cityName"), @NamedQuery(name = "CdCompanies.findByProvince", query = "SELECT c FROM CdCompanies c WHERE c.province = :province"), @NamedQuery(name = "CdCompanies.findByCountry", query = "SELECT c FROM CdCompanies c WHERE c.country = :country"), @NamedQuery(name = "CdCompanies.findByPostalCode", query = "SELECT c FROM CdCompanies c WHERE c.postalCode = :postalCode"), @NamedQuery(name = "CdCompanies.findByPostOfficeBox", query = "SELECT c FROM CdCompanies c WHERE c.postOfficeBox = :postOfficeBox"), @NamedQuery(name = "CdCompanies.findById", query = "SELECT c FROM CdCompanies c WHERE c.id = :id")})
public class CdCompanies implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "mfr_code", length = 5)
    private String mfrCode;
    @Column(name = "company_code")
    private Integer companyCode;
    @Column(name = "company_name", length = 80)
    private String companyName;
    @Column(name = "company_type", length = 40)
    private String companyType;
    @Column(name = "address_mailing_flag")
    private String addressMailingFlag;
    @Column(name = "address_billing_flag")
    private String addressBillingFlag;
    @Column(name = "address_notification_flag")
    private String addressNotificationFlag;
    @Column(name = "address_other", length = 20)
    private String addressOther;
    @Column(name = "suite_number", length = 20)
    private String suiteNumber;
    @Column(name = "street_name", length = 80)
    private String streetName;
    @Column(name = "city_name", length = 60)
    private String cityName;
    @Column(name = "province", length = 40)
    private String province;
    @Column(name = "country", length = 40)
    private String country;
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    @Column(name = "post_office_box", length = 15)
    private String postOfficeBox;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdCompanies() {
    }

    public CdCompanies(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public String getMfrCode() {
        return mfrCode;
    }

    public void setMfrCode(String mfrCode) {
        this.mfrCode = mfrCode;
    }

    public Integer getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(Integer companyCode) {
        this.companyCode = companyCode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyType() {
        return companyType;
    }

    public void setCompanyType(String companyType) {
        this.companyType = companyType;
    }

    public String getAddressMailingFlag() {
        return addressMailingFlag;
    }

    public void setAddressMailingFlag(String addressMailingFlag) {
        this.addressMailingFlag = addressMailingFlag;
    }

    public String getAddressBillingFlag() {
        return addressBillingFlag;
    }

    public void setAddressBillingFlag(String addressBillingFlag) {
        this.addressBillingFlag = addressBillingFlag;
    }

    public String getAddressNotificationFlag() {
        return addressNotificationFlag;
    }

    public void setAddressNotificationFlag(String addressNotificationFlag) {
        this.addressNotificationFlag = addressNotificationFlag;
    }

    public String getAddressOther() {
        return addressOther;
    }

    public void setAddressOther(String addressOther) {
        this.addressOther = addressOther;
    }

    public String getSuiteNumber() {
        return suiteNumber;
    }

    public void setSuiteNumber(String suiteNumber) {
        this.suiteNumber = suiteNumber;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getPostOfficeBox() {
        return postOfficeBox;
    }

    public void setPostOfficeBox(String postOfficeBox) {
        this.postOfficeBox = postOfficeBox;
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
        if (!(object instanceof CdCompanies)) {
            return false;
        }
        CdCompanies other = (CdCompanies) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.drugref.ca.dpd.CdCompanies[id=" + id + "]";
    }

}
