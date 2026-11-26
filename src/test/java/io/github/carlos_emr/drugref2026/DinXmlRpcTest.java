/* Copyright (c) 2026 CARLOS EMR Project Contributors. SPDX-License-Identifier: AGPL-3.0-or-later */
package io.github.carlos_emr.drugref2026;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Vector;

import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.PersistenceException;

import io.github.carlos_emr.drugref2026.ca.dpd.TablesDao;
import io.github.carlos_emr.drugref2026.util.SimpleXmlRpcServer;
import io.github.carlos_emr.drugref2026.util.XmlRpcFaultException;
import io.github.carlos_emr.drugref2026.util.XmlRpcUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DinXmlRpcTest {
    @Test
    void sendsAnEmptyArrayForAnUnknownDin() throws Exception {
        assertThat(lookup(null)).isEqualTo(new Vector<>());
    }

    @ParameterizedTest
    @MethodSource("catalogueFailures")
    void sendsANonzeroFaultWhenTheCatalogueCannotAnswer(RuntimeException failure) {
        assertThatThrownBy(() -> lookup(failure))
                .isInstanceOfSatisfying(XmlRpcFaultException.class, fault -> {
                    assertThat(fault.code).isNotZero();
                    assertThat(fault.getMessage()).isEqualTo("DrugRef DIN lookup failed");
                });
    }

    static RuntimeException[] catalogueFailures() {
        return new RuntimeException[]{new NonUniqueResultException("fixture duplicate"),
                new PersistenceException("fixture database failure")};
    }

    private static Object lookup(RuntimeException failure) throws Exception {
        var dao = new TablesDao() {
            @Override
            public Vector<Hashtable<String, Object>> getDrugByDIN(String din) {
                assertThat(din).isEqualTo("02247521");
                if (failure != null) {
                    throw failure;
                }
                return new Vector<>();
            }
        };
        var request = new ByteArrayInputStream("""
                <methodCall>
                  <methodName>get_drug_by_DIN</methodName>
                  <params>
                    <param><value><string>02247521</string></value></param>
                    <param><value><boolean>1</boolean></value></param>
                  </params>
                </methodCall>
                """.getBytes(StandardCharsets.UTF_8));
        var response = new ByteArrayOutputStream();
        new SimpleXmlRpcServer(new Drugref(dao)).execute(request, response);
        return XmlRpcUtils.parseResponse(response.toString(StandardCharsets.UTF_8));
    }
}
