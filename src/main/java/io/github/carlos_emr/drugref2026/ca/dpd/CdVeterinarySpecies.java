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
 * JPA entity mapping to the {@code cd_veterinary_species} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Represents the animal species for which a veterinary drug product has been approved.
 * This table only contains entries for products classified as "Veterinary" in
 * {@link CdDrugProduct#getClass1()}. A single veterinary drug may be approved for multiple
 * species (e.g., cattle, swine, poultry).</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier linking this species approval to its drug product.</li>
 *   <li>{@code vetSpecies} -- The approved animal species (e.g., "CATTLE", "SWINE", "DOGS", "CATS").</li>
 *   <li>{@code vetSubSpecies} -- A more specific sub-species designation, if applicable.</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code vet.txt} (active products)
 * and {@code vet_ia.txt} (inactive products).</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_veterinary_species")
@NamedQueries({@NamedQuery(name = "CdVeterinarySpecies.findAll", query = "SELECT c FROM CdVeterinarySpecies c"), @NamedQuery(name = "CdVeterinarySpecies.findByDrugCode", query = "SELECT c FROM CdVeterinarySpecies c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdVeterinarySpecies.findByVetSpecies", query = "SELECT c FROM CdVeterinarySpecies c WHERE c.vetSpecies = :vetSpecies"), @NamedQuery(name = "CdVeterinarySpecies.findByVetSubSpecies", query = "SELECT c FROM CdVeterinarySpecies c WHERE c.vetSubSpecies = :vetSubSpecies"), @NamedQuery(name = "CdVeterinarySpecies.findById", query = "SELECT c FROM CdVeterinarySpecies c WHERE c.id = :id")})
public class CdVeterinarySpecies implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "vet_species", length = 80)
    private String vetSpecies;
    @Column(name = "vet_sub_species", length = 80)
    private String vetSubSpecies;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdVeterinarySpecies() {
    }

    public CdVeterinarySpecies(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public String getVetSpecies() {
        return vetSpecies;
    }

    public void setVetSpecies(String vetSpecies) {
        this.vetSpecies = vetSpecies;
    }

    public String getVetSubSpecies() {
        return vetSubSpecies;
    }

    public void setVetSubSpecies(String vetSubSpecies) {
        this.vetSubSpecies = vetSubSpecies;
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
        if (!(object instanceof CdVeterinarySpecies)) {
            return false;
        }
        CdVeterinarySpecies other = (CdVeterinarySpecies) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdVeterinarySpecies[id=" + id + "]";
    }

}
