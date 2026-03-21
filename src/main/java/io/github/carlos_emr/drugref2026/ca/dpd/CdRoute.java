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
 * JPA entity mapping to the {@code cd_route} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Represents the approved routes of administration for each drug product. A drug product
 * may have multiple approved routes (e.g., a solution that can be given both orally and
 * intravenously). Common routes include ORAL, INTRAVENOUS, TOPICAL, INTRAMUSCULAR,
 * SUBCUTANEOUS, RECTAL, OPHTHALMIC, INHALATION, etc.</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier linking this route to its drug product.</li>
 *   <li>{@code routeOfAdministrationCode} -- Health Canada's numeric code for the route of administration.</li>
 *   <li>{@code routeOfAdministration} -- The human-readable route name (e.g., "ORAL", "TOPICAL").</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code route.txt} (active products)
 * and {@code route_ia.txt} (inactive products).</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_route")
@NamedQueries({@NamedQuery(name = "CdRoute.findAll", query = "SELECT c FROM CdRoute c"), @NamedQuery(name = "CdRoute.findByDrugCode", query = "SELECT c FROM CdRoute c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdRoute.findByRouteOfAdministrationCode", query = "SELECT c FROM CdRoute c WHERE c.routeOfAdministrationCode = :routeOfAdministrationCode"), @NamedQuery(name = "CdRoute.findByRouteOfAdministration", query = "SELECT c FROM CdRoute c WHERE c.routeOfAdministration = :routeOfAdministration"), @NamedQuery(name = "CdRoute.findById", query = "SELECT c FROM CdRoute c WHERE c.id = :id")})
public class CdRoute implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "route_of_administration_code")
    private Integer routeOfAdministrationCode;
    @Column(name = "route_of_administration")
    private String routeOfAdministration;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    public CdRoute() {
    }

    public CdRoute(Long id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public Integer getRouteOfAdministrationCode() {
        return routeOfAdministrationCode;
    }

    public void setRouteOfAdministrationCode(Integer routeOfAdministrationCode) {
        this.routeOfAdministrationCode = routeOfAdministrationCode;
    }

    public String getRouteOfAdministration() {
        return routeOfAdministration;
    }

    public void setRouteOfAdministration(String routeOfAdministration) {
        this.routeOfAdministration = routeOfAdministration;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
        if (!(object instanceof CdRoute)) {
            return false;
        }
        CdRoute other = (CdRoute) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdRoute[id=" + id + "]";
    }

}
