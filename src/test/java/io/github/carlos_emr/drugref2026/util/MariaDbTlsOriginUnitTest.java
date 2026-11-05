/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.drugref2026.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.HostAddress;
import org.mariadb.jdbc.export.ExceptionFactory;
import org.mariadb.jdbc.export.SslMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Distinguishes native MariaDB TLS flags from legacy MySQL flags after URL normalization. */
@Tag("unit")
class MariaDbTlsOriginUnitTest {
    @TempDir Path temporary;

    /** Exercise both schemes before and after URL storage so normalization cannot change TLS policy. */
    @ParameterizedTest
    @CsvSource({
            "mysql,useSSL=true,ALLOW",
            "mysql,useSSL=false,DISABLE",
            "mysql,useSSL=true&requireSSL=true,TRUST",
            "mysql,useSSL=true&verifyServerCertificate=true,VERIFY_CA",
            "mysql,sslMode=DISABLED&useSSL=true,DISABLE",
            "mysql,useSSL=true&verifyServerCertificate=true&trustServerCertificate=true,VERIFY_CA",
            "mariadb,useSsl=true,VERIFY_FULL",
            "mariadb,useSSL=true,VERIFY_FULL",
            "mariadb,useSSL=true&useSsl=false,VERIFY_FULL",
            "mariadb,useSsl=false,DISABLE",
            "mariadb,useSsl=true&trustServerCertificate=true,TRUST",
            "mariadb,useSsl=true&disableSslHostnameVerification=true,VERIFY_CA",
            "mariadb,useSsl=true&trustServerCertificate=true&disableSslHostnameVerification=true,TRUST",
            "mariadb,trustServerCertificate=true,DISABLE",
            "mariadb,disableSslHostnameVerification=true,DISABLE",
            "mariadb,sslMode=disable&useSsl=true,VERIFY_FULL",
            "mariadb,sslMode=verify-full&useSsl=true&trustServerCertificate=true,TRUST",
            "mariadb,sslMode=verify-full&useSsl=false,VERIFY_FULL",
            "mariadb,sslMode=verify-full&trustServerCertificate=true,VERIFY_FULL"})
    void shouldKeepSchemeSpecificTlsPolicy_throughRepeatedNormalization(String scheme, String options, SslMode expected) throws Exception {
        String url = url(scheme, options);
        for (int rounds = 0; rounds <= 2; rounds++) {
            assertThat(configuration(url, new Properties(), rounds).sslMode()).isEqualTo(expected);
        }
    }

    /** Compare native source precedence with the real parser and retain the opposite legacy precedence. */
    @ParameterizedTest
    @CsvSource({
            "mysql,useSSL=false,useSSL,true,ALLOW",
            "mysql,useSSL=true,verifyServerCertificate,true,VERIFY_CA",
            "mysql,useSSL=true,sslMode,DISABLED,DISABLE",
            "mariadb,useSsl=false,useSsl,true,DISABLE",
            "mariadb,useSsl=true,useSsl,false,VERIFY_FULL",
            "mariadb,useSsl=false,useSSL,true,VERIFY_FULL",
            "mariadb,useSsl=true&trustServerCertificate=true,trustServerCertificate,false,TRUST",
            "mariadb,useSsl=true&trustServerCertificate=false,trustServerCertificate,true,VERIFY_FULL",
            "mariadb,useSsl=true&disableSslHostnameVerification=true,disableSslHostnameVerification,false,VERIFY_CA",
            "mariadb,sslMode=verify-full,sslMode,disable,VERIFY_FULL",
            "mariadb,sslMode=disable,useSsl,true,VERIFY_FULL",
            "mariadb,useSsl=true,sslMode,disable,VERIFY_FULL",
            "mariadb,useSsl=true,trustServerCertificate,true,TRUST",
            "mariadb,useSsl=true&trustServerCertificate=true,useSsl,false,TRUST"})
    void shouldApplyDriverSpecificSourcePrecedence_withoutLosingTlsOrigin(String scheme, String options, String name,
                                                                        String value, SslMode expected) throws Exception {
        Properties supplied = new Properties();
        supplied.setProperty(name, value);
        Properties original = (Properties) supplied.clone();
        if (scheme.equals("mariadb")) {
            assertThat(Configuration.parse(url(scheme, options), (Properties) supplied.clone()).sslMode()).isEqualTo(expected);
        }
        for (int rounds = 0; rounds <= 2; rounds++) {
            assertThat(configuration(url(scheme, options), supplied, rounds).sslMode()).isEqualTo(expected);
            assertThat(supplied).isEqualTo(original);
        }
    }

    /** Check that credentials, timeouts and certificate selections follow the same scheme-specific source order. */
    @ParameterizedTest
    @CsvSource({
            "mysql,enabledSslProtocolSuites,TLSv1.2,TLSv1.3",
            "mariadb,enabledSslProtocolSuites,TLSv1.2,TLSv1.3",
            "mysql,enabledSslCipherSuites,TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384",
            "mariadb,enabledSslCipherSuites,TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384",
            "mysql,trustStore,url-roots.p12,property-roots.p12",
            "mariadb,trustStore,url-roots.p12,property-roots.p12",
            "mysql,keyStore,url-identity.p12,property-identity.p12",
            "mariadb,keyStore,url-identity.p12,property-identity.p12",
            "mysql,trustStoreType,JKS,PKCS12",
            "mariadb,trustStoreType,JKS,PKCS12",
            "mysql,keyStoreType,JKS,PKCS12",
            "mariadb,keyStoreType,JKS,PKCS12",
            "mysql,user,url-user,property-user",
            "mariadb,user,url-user,property-user",
            "mysql,connectTimeout,1234,5678",
            "mariadb,connectTimeout,1234,5678"})
    void shouldPreserveSourcePrecedence_forSecurityAndConnectionOptions(String scheme, String name,
                                                                        String queryValue, String propertyValue) throws Exception {
        Properties supplied = new Properties();
        supplied.setProperty(name, propertyValue);
        String url = url(scheme, name + "=" + queryValue);
        String expected = scheme.equals("mysql") ? propertyValue : queryValue;
        if (scheme.equals("mariadb")) {
            Configuration nativeConfiguration = Configuration.parse(url, (Properties) supplied.clone());
            assertThat(configurationValue(nativeConfiguration, name)).isEqualTo(expected);
        }
        for (int rounds = 0; rounds <= 2; rounds++) {
            assertThat(configurationValue(configuration(url, supplied, rounds), name)).isEqualTo(expected);
            assertThat(supplied.getProperty(name)).isEqualTo(propertyValue);
        }
    }

    /** Refuse conflicting aliases before property iteration can choose a weaker security setting. */
    @ParameterizedTest
    @CsvSource({
            "SSLMODE,disable,sslMode,verify-full",
            "TRUSTSTORE,url-roots.p12,trustStore,property-roots.p12",
            "ENABLEDSSLPROTOCOLSUITES,TLSv1.2,enabledSslProtocolSuites,TLSv1.3",
            "ENABLEDSSLCIPHERSUITES,TLS_AES_128_GCM_SHA256,enabledSslCipherSuites,TLS_AES_256_GCM_SHA384",
            "trustCertificateKeyStoreUrl,url-roots.p12,trustStore,property-roots.p12",
            "clientCertificateKeyStoreUrl,url-identity.p12,keyStore,property-identity.p12",
            "fallbacktosystemtruststore,true,fallbackToSystemTrustStore,false",
            "allowlocalinfile,true,allowLocalInfile,false"})
    void shouldRejectConflictingNativeSecuritySpellings_beforeOrAfterNormalization(String alias, String queryValue,
                                                                                   String canonical, String propertyValue) {
        for (boolean reverse : new boolean[]{false, true}) {
            String queryName = reverse ? canonical : alias;
            String propertyName = reverse ? alias : canonical;
            Properties supplied = new Properties();
            supplied.setProperty(propertyName, propertyValue);
            String nativeUrl = url("mariadb", queryName + "=" + queryValue);
            String queryConflict = nativeUrl + "&" + propertyName + "=" + propertyValue;
            for (int rounds = 0; rounds <= 2; rounds++) {
                int selectedRounds = rounds;
                assertInvalidConfiguration(() -> configuration(nativeUrl, supplied, selectedRounds));
                assertInvalidConfiguration(() -> configuration(queryConflict, new Properties(), selectedRounds));
            }
        }
        Properties propertyConflict = new Properties();
        propertyConflict.setProperty(alias, queryValue);
        propertyConflict.setProperty(canonical, propertyValue);
        assertInvalidConfiguration(() -> configuration(url("mariadb", ""), propertyConflict, 0));
    }

    /** Accept equivalent aliases while preserving their normalized certificate or transport selection. */
    @ParameterizedTest
    @CsvSource({"SSLMODE,sslMode,verify-full", "TRUSTSTORE,trustStore,roots.p12",
            "trustCertificateKeyStoreUrl,trustStore,roots.p12",
            "ENABLEDSSLPROTOCOLSUITES,enabledSslProtocolSuites,TLSv1.2"})
    void shouldAllowEquivalentNativeSecuritySpellings_withoutChangingTheirValues(String alias, String canonical,
                                                                                String value) throws Exception {
        Properties supplied = new Properties();
        supplied.setProperty(canonical, value);
        String nativeUrl = url("mariadb", alias + "=" + value);
        for (int rounds = 0; rounds <= 2; rounds++) {
            Configuration configuration = configuration(nativeUrl, supplied, rounds);
            if (canonical.equals("sslMode")) assertThat(configuration.sslMode()).isEqualTo(SslMode.VERIFY_FULL);
            else assertThat(configurationValue(configuration, canonical)).isEqualTo(value);
        }
    }

    /** Keep the source scheme available when a pool supplies TLS properties after normalizing its URL. */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb"})
    void shouldRetainUrlOrigin_whenTlsPropertiesAreSuppliedAfterNormalization(String scheme) throws Exception {
        Properties supplied = new Properties();
        supplied.setProperty("useSsl", "true");
        SslMode expected = scheme.equals("mysql") ? SslMode.ALLOW : SslMode.VERIFY_FULL;
        for (int rounds = 0; rounds <= 2; rounds++) {
            assertThat(configuration(url(scheme, ""), supplied, rounds).sslMode()).isEqualTo(expected);
        }
    }

    /** Retain optional, non-verifying TLS for the existing application default on either URL scheme. */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb"})
    void shouldPreserveCompatibilityDefault_whenNoTlsOptionWasConfigured(String scheme) throws Exception {
        for (int rounds = 0; rounds <= 2; rounds++) {
            Configuration configuration = configuration(url(scheme, ""), new Properties(), rounds);
            assertThat(configuration.sslMode()).isEqualTo(SslMode.ALLOW);
            assertThat(configuration.nonMappedOptions()).containsEntry("carlosLegacyPreferredTls", "true");
        }
    }

    /** Compare deprecated native flag tokens directly with MariaDB instead of broadening truthy values. */
    @ParameterizedTest
    @ValueSource(strings = {"true", "1", "", "false", "0", "TRUE", "yes", "2", "invalid", " true "})
    void shouldMatchNativeBooleanTokens_withoutMakingIgnoredValuesWeakenVerification(String value) throws Exception {
        for (String options : new String[]{"useSsl=" + value, "useSsl=true&trustServerCertificate=" + value,
                "useSsl=true&disableSslHostnameVerification=" + value}) {
            String url = url("mariadb", options);
            SslMode nativeMode = Configuration.parse(url, new Properties()).sslMode();
            for (int rounds = 0; rounds <= 2; rounds++) {
                assertThat(configuration(url, new Properties(), rounds).sslMode()).isEqualTo(nativeMode);
            }
        }
    }

    /** Preserve native file-access and system-store Boolean tokens, including the empty true value. */
    @ParameterizedTest
    @ValueSource(strings = {"fallbackToSystemTrustStore", "fallbackToSystemKeyStore", "allowLocalInfile"})
    void shouldMatchNativeSecurityBooleanTokens_withoutChangingLegacyAliases(String name) throws Exception {
        for (String value : new String[]{"true", "TRUE", "1", "", "false", "FALSE", "0"}) {
            String nativeUrl = url("mariadb", name + "=" + value);
            boolean expected = securityBoolean(Configuration.parse(nativeUrl, new Properties()), name);
            for (int rounds = 0; rounds <= 2; rounds++) {
                assertThat(securityBoolean(configuration(nativeUrl, new Properties(), rounds), name)).isEqualTo(expected);
            }
        }
        for (String value : new String[]{"yes", "no"}) {
            for (int rounds = 0; rounds <= 2; rounds++) {
                assertThat(securityBoolean(configuration(url("mysql", name + "=" + value), new Properties(), rounds), name))
                        .isEqualTo(value.equals("yes"));
            }
        }
    }

    /** Reject unsupported Boolean spellings before they can enable file access or system-store fallback. */
    @ParameterizedTest
    @ValueSource(strings = {"fallbackToSystemTrustStore", "fallbackToSystemKeyStore", "allowLocalInfile"})
    void shouldRejectInvalidNativeSecurityBooleans_beforeEnablingAdditionalAccess(String name) {
        for (String value : new String[]{"yes", "no", "2", "invalid"}) {
            String nativeUrl = url("mariadb", name + "=" + value);
            assertThatThrownBy(() -> Configuration.parse(nativeUrl, new Properties())).isInstanceOf(SQLException.class);
            for (int rounds = 0; rounds <= 2; rounds++) {
                int selectedRounds = rounds;
                Properties supplied = new Properties();
                assertThatThrownBy(() -> configuration(nativeUrl, supplied, selectedRounds))
                        .isInstanceOfAny(SQLException.class, IllegalArgumentException.class)
                        .hasMessage("Invalid database connection configuration").hasNoCause();
            }
        }
    }

    /** Ensure unknown flag capitalization cannot turn verified TLS into an unverified connection. */
    @ParameterizedTest
    @ValueSource(strings = {"TRUSTSERVERCERTIFICATE", "trustservercertificate", "disableSSLHostnameVerification"})
    void shouldIgnoreNoncanonicalNativeFlagNames_withoutRelaxingVerifiedTls(String name) throws Exception {
        String url = url("mariadb", "useSsl=true&" + name + "=true");
        assertThat(Configuration.parse(url, new Properties()).sslMode()).isEqualTo(SslMode.VERIFY_FULL);
        for (int rounds = 0; rounds <= 2; rounds++) {
            assertThat(configuration(url, new Properties(), rounds).sslMode()).isEqualTo(SslMode.VERIFY_FULL);
        }
    }

    /** Use a self-signed certificate to distinguish verified native policies from legacy optional TLS. */
    @ParameterizedTest
    @CsvSource({"mysql,useSSL=true,false", "mariadb,useSsl=true,true",
            "mariadb,useSsl=true&disableSslHostnameVerification=true,true",
            "mariadb,useSsl=true&trustServerCertificate=true,false"})
    void shouldEnforceCertificatePolicy_afterNormalizingDeprecatedFlags(String scheme, String options, boolean refuse) throws Exception {
        Properties supplied = new Properties();
        supplied.setProperty("fallbackToSystemTrustStore", "false");
        X509Certificate[] chain = chain();
        for (int rounds = 0; rounds <= 2; rounds++) {
            Configuration configuration = configuration(url(scheme, options), supplied, rounds);
            X509TrustManager trust = trustManager(new MariaDbPreferredTlsPlugin(), configuration, HostAddress.from("localhost", 3306));
            if (refuse) assertRejectsUntrustedCertificate(trust, chain);
            else assertThatCode(() -> trust.checkServerTrusted(chain, "RSA")).doesNotThrowAnyException();
        }
    }

    /** Require every explicitly verified host to reject an untrusted certificate despite global trust flags. */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb"})
    void shouldKeepHostVerificationPolicy_aboveGlobalDeprecatedFlags(String scheme) throws Exception {
        String options = scheme.equals("mysql") ? "useSSL=true" : "useSsl=true&trustServerCertificate=true";
        Properties supplied = new Properties();
        supplied.setProperty("fallbackToSystemTrustStore", "false");
        X509Certificate[] chain = chain();
        for (String hosts : new String[]{"address=(host=localhost)(sslMode=verify-full)",
                "address=(host=localhost)(sslMode=verify-full),address=(host=localhost)(sslMode=verify-ca)"}) {
            String url = "jdbc:" + scheme + "://" + hosts + "/test?" + options;
            for (int rounds = 0; rounds <= 2; rounds++) {
                Configuration configuration = configuration(url, supplied, rounds);
                for (HostAddress host : configuration.addresses()) {
                    SslMode effective = host.sslMode == null ? configuration.sslMode() : host.sslMode;
                    assertThat(effective).isIn(SslMode.VERIFY_FULL, SslMode.VERIFY_CA);
                    X509TrustManager trust = trustManager(new MariaDbPreferredTlsPlugin(), configuration, host);
                    assertRejectsUntrustedCertificate(trust, chain);
                }
            }
        }
    }

    /** Match MariaDB by ignoring deprecated TLS flags embedded in native host descriptors. */
    @ParameterizedTest
    @ValueSource(strings = {"trustServerCertificate", "disableSslHostnameVerification"})
    void shouldIgnoreNativeDescriptorFlags_thatWouldWeakenGlobalVerification(String name) throws Exception {
        String url = "jdbc:mariadb://address=(host=localhost)(" + name + "=true)/test?useSsl=true";
        assertThat(Configuration.parse(url, new Properties()).sslMode()).isEqualTo(SslMode.VERIFY_FULL);
        for (int rounds = 0; rounds <= 2; rounds++) {
            Configuration configuration = configuration(url, new Properties(), rounds);
            HostAddress host = configuration.addresses().getFirst();
            assertThat(host.sslMode == null ? configuration.sslMode() : host.sslMode).isEqualTo(SslMode.VERIFY_FULL);
        }
    }

    /** Retain legacy host overrides while leaving unsupported native fields unable to change global policy. */
    @ParameterizedTest
    @CsvSource({
            "mysql,fallbackToSystemTrustStore,true,false",
            "mariadb,fallbackToSystemTrustStore,true,false",
            "mysql,fallbackToSystemKeyStore,true,false",
            "mariadb,fallbackToSystemKeyStore,true,false",
            "mysql,allowLocalInfile,true,false",
            "mariadb,allowLocalInfile,true,false",
            "mysql,trustStore,/tmp/host-roots.p12,/tmp/global-roots.p12",
            "mariadb,trustStore,/tmp/host-roots.p12,/tmp/global-roots.p12",
            "mysql,keyStore,/tmp/host-identity.p12,/tmp/global-identity.p12",
            "mariadb,keyStore,/tmp/host-identity.p12,/tmp/global-identity.p12",
            "mysql,enabledSslProtocolSuites,TLSv1.2,TLSv1.3",
            "mariadb,enabledSslProtocolSuites,TLSv1.2,TLSv1.3",
            "mysql,enabledSslCipherSuites,TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384",
            "mariadb,enabledSslCipherSuites,TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384",
            "mysql,trustStoreType,JKS,PKCS12",
            "mariadb,trustStoreType,JKS,PKCS12"})
    void shouldKeepDescriptorOptionsSchemeSpecific_withoutWeakeningGlobalSecurity(String scheme, String name,
                                                                                 String hostValue, String globalValue) throws Exception {
        String descriptor = "address=(host=localhost)(" + name + "=" + hostValue + ")";
        String[] hostLists = scheme.equals("mysql") ? new String[]{descriptor}
                : new String[]{descriptor, descriptor + "," + descriptor};
        String expected = scheme.equals("mysql") ? hostValue : globalValue;
        for (String hosts : hostLists) {
            String url = "jdbc:" + scheme + "://" + hosts + "/test?useSsl=true&" + name + "=" + globalValue;
            if (scheme.equals("mariadb")) {
                assertThat(configurationValue(Configuration.parse(url, new Properties()), name)).isEqualTo(expected);
            }
            for (int rounds = 0; rounds <= 2; rounds++) {
                assertThat(configurationValue(configuration(url, new Properties(), rounds), name)).isEqualTo(expected);
            }
        }
    }

    /** Compare literal and encoded socket paths with MariaDB across single-host, multi-host and stored URLs. */
    @ParameterizedTest
    @CsvSource({"localSocket,/tmp/MariaDB-test.sock", "localSocket,%2Ftmp%2FMariaDB-test.sock",
            "localSocket,/tmp/MariaDB+100%.sock", "pipe,\\\\.\\pipe\\MariaDB-test",
            "pipe,%5C%5C.%5Cpipe%5CMariaDB-test", "pipe,\\\\.\\pipe\\MariaDB+100%"})
    void shouldPreserveNativeSocketDescriptors_forSingleAndMultipleHosts(String name, String value) throws Exception {
        String descriptor = "address=(" + name + "=" + value + ")(sslMode=verify-full)";
        for (String hosts : new String[]{descriptor, descriptor + "," + descriptor}) {
            String url = "jdbc:mariadb://" + hosts + "/test?useSsl=true";
            Configuration nativeConfiguration = Configuration.parse(url, new Properties());
            assertThat(nativeConfiguration.addresses()).allSatisfy(host ->
                    assertThat(name.equals("localSocket") ? host.localSocket : host.pipe).isEqualTo(value));
            for (int rounds = 0; rounds <= 2; rounds++) {
                Configuration adapted = configuration(url, new Properties(), rounds);
                assertThat(adapted.addresses()).hasSameSizeAs(nativeConfiguration.addresses());
                for (HostAddress host : adapted.addresses()) {
                    assertThat(name.equals("localSocket") ? host.localSocket : host.pipe).isEqualTo(value);
                    assertThat(host.sslMode == null ? adapted.sslMode() : host.sslMode).isEqualTo(SslMode.VERIFY_FULL);
                }
            }
        }
    }

    /** Trust the fixture certificate explicitly, then prove native TLS still rejects a different hostname. */
    @ParameterizedTest
    @ValueSource(strings = {"useSsl", "useSSL"})
    void shouldRejectWrongHostname_whenNativeTlsHasTrustedCertificate(String flag) throws Exception {
        X509Certificate[] chain = chain();
        KeyStore roots = KeyStore.getInstance("PKCS12");
        roots.load(null, null);
        roots.setCertificateEntry("synthetic-server", chain[0]);
        Path store = temporary.resolve("native-roots.p12");
        try (var output = Files.newOutputStream(store)) {
            roots.store(output, "synthetic-native-roots".toCharArray()); // ggignore - isolated temporary trust-store fixture
        }
        Properties supplied = new Properties();
        supplied.setProperty("trustStore", store.toString());
        supplied.setProperty("trustStoreType", "PKCS12");
        supplied.setProperty("trustStorePassword", "synthetic-native-roots"); // ggignore - isolated temporary trust-store fixture
        SSLSession session = (SSLSession) java.lang.reflect.Proxy.newProxyInstance(
                SSLSession.class.getClassLoader(), new Class<?>[]{SSLSession.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPeerCertificates")) return chain;
                    throw new UnsupportedOperationException(method.getName());
                });
        for (int rounds = 0; rounds <= 2; rounds++) {
            Configuration configuration = configuration(url("mariadb", flag + "=true"), supplied, rounds);
            assertThat(configuration.sslMode()).isEqualTo(SslMode.VERIFY_FULL);
            var plugin = new MariaDbPreferredTlsPlugin();
            X509TrustManager trust = trustManager(plugin, configuration, HostAddress.from("localhost", 3306));
            assertThatCode(() -> trust.checkServerTrusted(chain, "RSA")).doesNotThrowAnyException();
            assertThatThrownBy(() -> plugin.verify("other.example", session, 1)).isInstanceOf(SSLException.class);
        }
    }

    /** Prevent connection properties and host fields from replacing the URL-owned provenance markers. */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb"})
    void shouldRejectProvenanceOverrides_fromPropertiesOrHostFields(String scheme) {
        for (String marker : new String[]{"carlosLegacyMysqlScheme", "carlosHostSslMode"}) {
            Properties supplied = new Properties();
            supplied.setProperty(marker, "true");
            assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings(url(scheme, "useSsl=true"), supplied))
                    .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration").hasNoCause();
            String hostOverride = "jdbc:" + scheme + "://address=(host=localhost)(" + marker + "=true)/test?useSsl=true";
            assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings(hostOverride, new Properties()))
                    .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration").hasNoCause();
        }
    }

    /** Reject malformed provenance consistently without exposing option values in the exception. */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb"})
    void shouldRejectInvalidProvenanceMarker_beforeAndDuringNormalization(String scheme) {
        String url = url(scheme, "carlosLegacyMysqlScheme=false&useSsl=true");
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings(url, new Properties()))
                .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration").hasNoCause();
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.normalizeUrl(url))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid database connection configuration").hasNoCause();
    }

    /** Build native configuration after the requested number of URL normalization round trips. */
    private static Configuration configuration(String url, Properties supplied, int rounds) throws SQLException {
        String selected = url;
        for (int index = 0; index < rounds; index++) selected = MariaDbJdbcCompatibility.normalizeUrl(selected);
        var settings = MariaDbJdbcCompatibility.settings(selected, supplied);
        return Configuration.parse(settings.url(), settings.properties());
    }

    /** Construct a fixture URL with an optional query while keeping the scheme explicit. */
    private static String url(String scheme, String options) {
        return "jdbc:" + scheme + "://localhost/test" + (options.isEmpty() ? "" : "?" + options);
    }

    /** Read the selected native option for source-precedence assertions. */
    private static String configurationValue(Configuration configuration, String name) {
        return switch (name) {
            case "enabledSslProtocolSuites" -> configuration.enabledSslProtocolSuites();
            case "enabledSslCipherSuites" -> configuration.enabledSslCipherSuites();
            case "trustStore" -> configuration.trustStore();
            case "keyStore" -> configuration.keyStore();
            case "trustStoreType" -> configuration.trustStoreType();
            case "keyStoreType" -> configuration.keyStoreType();
            case "user" -> configuration.user();
            case "connectTimeout" -> Integer.toString(configuration.connectTimeout());
            case "fallbackToSystemTrustStore", "fallbackToSystemKeyStore", "allowLocalInfile" ->
                    Boolean.toString(securityBoolean(configuration, name));
            default -> throw new IllegalArgumentException("Unsupported fixture option: " + name);
        };
    }

    /** Read the native security Boolean covered by each parameterized regression. */
    private static boolean securityBoolean(Configuration configuration, String name) {
        return switch (name) {
            case "fallbackToSystemTrustStore" -> configuration.fallbackToSystemTrustStore();
            case "fallbackToSystemKeyStore" -> configuration.fallbackToSystemKeyStore();
            case "allowLocalInfile" -> configuration.allowLocalInfile();
            default -> throw new IllegalArgumentException("Unsupported fixture option: " + name);
        };
    }

    /** Select the compatibility provider's X.509 trust manager for one effective host policy. */
    private static X509TrustManager trustManager(MariaDbPreferredTlsPlugin plugin, Configuration configuration, HostAddress host)
            throws SQLException {
        return Arrays.stream(plugin.getTrustManager(configuration, new ExceptionFactory(configuration, host), host))
                .filter(X509TrustManager.class::isInstance).map(X509TrustManager.class::cast).findFirst().orElseThrow();
    }

    /** Accept only certificate rejection or the known JSSE empty-trust-anchor setup failure. */
    private static void assertRejectsUntrustedCertificate(X509TrustManager trust, X509Certificate[] chain) {
        assertThatThrownBy(() -> trust.checkServerTrusted(chain, "RSA"))
                .isInstanceOfAny(CertificateException.class, RuntimeException.class).satisfies(failure -> {
            if (failure instanceof RuntimeException) {
                // JSSE may report an empty trust-anchor set as an unchecked validation setup error.
                assertThat(failure).hasRootCauseInstanceOf(InvalidAlgorithmParameterException.class);
            }
        });
    }

    /** Require a sanitized configuration error without a credential-bearing cause. */
    private static void assertInvalidConfiguration(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfAny(SQLException.class, IllegalArgumentException.class)
                .hasMessage("Invalid database connection configuration").hasNoCause();
    }

    /** Load the public certificate fixture; these tests never store a private key in the repository. */
    private static X509Certificate[] chain() throws java.security.cert.CertificateException, java.io.IOException {
        try (var input = MariaDbTlsOriginUnitTest.class.getResourceAsStream("/jdbc/compatibility-server-cert.pem")) {
            return new X509Certificate[]{(X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input)};
        }
    }
}
