/*
 * Copyright (C) 2026 The FundamentalOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.fundamental;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Reads a keybox XML file and reports what certificate chains it contains.
 *
 * <p>The XML groups material under one or more {@code <Key algorithm="...">} blocks, each holding a
 * PEM private key and a {@code <CertificateChain>} of PEM {@code <Certificate>} elements ordered
 * leaf-first up to a self-signed root. This helper only inspects the CERTIFICATES with
 * {@link CertificateFactory}; it does not read the private keys. It is used to show the user a
 * summary of a file's contents and whether each chain is internally consistent before the file is
 * saved.
 */
public final class IntegritySpoofKeyboxValidator {

    private static final String TAG = "IntegrityKeyboxCheck";

    private IntegritySpoofKeyboxValidator() {}

    /** Per-algorithm-block outcome. */
    public static final class AlgoResult {
        public final String algorithm;
        public final int certCount;
        @Nullable public final String leafSubject;
        @Nullable public final String rootSubject;
        public final boolean chainVerified;

        AlgoResult(String algorithm, int certCount, @Nullable String leafSubject,
                @Nullable String rootSubject, boolean chainVerified) {
            this.algorithm = algorithm;
            this.certCount = certCount;
            this.leafSubject = leafSubject;
            this.rootSubject = rootSubject;
            this.chainVerified = chainVerified;
        }
    }

    /** Whole-document outcome. */
    public static final class Result {
        public final boolean valid;
        @NonNull public final List<AlgoResult> algorithms;
        @Nullable public final String error;

        Result(boolean valid, @NonNull List<AlgoResult> algorithms, @Nullable String error) {
            this.valid = valid;
            this.algorithms = algorithms;
            this.error = error;
        }

        /** A one-line, human-readable summary, e.g. "EC ok (root O=Google) - RSA ok". */
        @NonNull
        public String toSummary() {
            if (!valid || algorithms.isEmpty()) {
                return error != null ? error : "Invalid file";
            }
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < algorithms.size(); i++) {
                final AlgoResult a = algorithms.get(i);
                if (i > 0) {
                    sb.append(" - ");
                }
                sb.append(a.algorithm.toUpperCase());
                sb.append(a.chainVerified ? " ok" : " bad");
                final String root = shortName(a.rootSubject);
                if (root != null) {
                    sb.append(" (root ").append(root).append(")");
                }
            }
            return sb.toString();
        }
    }

    /**
     * Parse and check the given bytes. Never throws; a failure comes back as a {@link Result} with
     * {@code valid=false} and a non-null {@code error}.
     */
    @NonNull
    public static Result validate(@Nullable byte[] xmlBytes) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            return new Result(false, new ArrayList<>(), "Empty file");
        }
        try {
            final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // The file is plain data, never an entity source: disable DTDs/entities.
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
                // Feature unsupported on this parser; entity expansion is disabled below regardless.
            }
            dbf.setExpandEntityReferences(false);
            dbf.setNamespaceAware(false);
            final DocumentBuilder db = dbf.newDocumentBuilder();
            final Element root = db.parse(new ByteArrayInputStream(xmlBytes)).getDocumentElement();
            if (root == null) {
                return new Result(false, new ArrayList<>(), "Not an XML document");
            }

            final CertificateFactory cf = CertificateFactory.getInstance("X.509");
            final List<AlgoResult> results = new ArrayList<>();
            final NodeList keyNodes = root.getElementsByTagName("Key");
            for (int i = 0; i < keyNodes.getLength(); i++) {
                final Node n = keyNodes.item(i);
                if (!(n instanceof Element)) {
                    continue;
                }
                final Element keyEl = (Element) n;
                String algo = keyEl.getAttribute("algorithm");
                if (TextUtils.isEmpty(algo)) {
                    algo = "key";
                }
                final List<X509Certificate> chain = new ArrayList<>();
                final NodeList certNodes = keyEl.getElementsByTagName("Certificate");
                for (int j = 0; j < certNodes.getLength(); j++) {
                    final String pem = certNodes.item(j).getTextContent();
                    if (TextUtils.isEmpty(pem)) {
                        continue;
                    }
                    try {
                        final byte[] der = pemToDer(pem);
                        if (der == null) {
                            continue;
                        }
                        chain.add((X509Certificate) cf.generateCertificate(
                                new ByteArrayInputStream(der)));
                    } catch (Exception e) {
                        Log.w(TAG, "Skipping unparseable certificate in " + algo + " block", e);
                    }
                }
                if (chain.isEmpty()) {
                    continue;
                }
                final String leafSubject = chain.get(0).getSubjectX500Principal().getName();
                final String rootSubject =
                        chain.get(chain.size() - 1).getSubjectX500Principal().getName();
                results.add(new AlgoResult(algo, chain.size(), leafSubject, rootSubject,
                        verifyChain(chain)));
            }

            if (results.isEmpty()) {
                return new Result(false, results, "No certificates found");
            }
            boolean allVerified = true;
            for (AlgoResult a : results) {
                allVerified &= a.chainVerified;
            }
            return new Result(allVerified, results, allVerified ? null : "Chain does not verify");
        } catch (Exception e) {
            Log.w(TAG, "Validation failed", e);
            return new Result(false, new ArrayList<>(), "Parse error: " + e.getMessage());
        }
    }

    /** Each cert must be signed by the next; the last must be self-signed. */
    private static boolean verifyChain(@NonNull List<X509Certificate> chain) {
        try {
            for (int i = 0; i < chain.size() - 1; i++) {
                final PublicKey issuerKey = chain.get(i + 1).getPublicKey();
                chain.get(i).verify(issuerKey);
            }
            final X509Certificate last = chain.get(chain.size() - 1);
            last.verify(last.getPublicKey());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Chain verification failed", e);
            return false;
        }
    }

    @Nullable
    private static byte[] pemToDer(@NonNull String pem) {
        final int begin = pem.indexOf("-----BEGIN");
        final int end = pem.indexOf("-----END");
        String body;
        if (begin >= 0 && end > begin) {
            final int bodyStart = pem.indexOf('\n', begin);
            if (bodyStart < 0) {
                return null;
            }
            body = pem.substring(bodyStart, end);
        } else {
            body = pem; // no armor: treat element text as raw base64
        }
        body = body.replaceAll("\\s", "");
        if (body.isEmpty()) {
            return null;
        }
        return Base64.decode(body, Base64.DEFAULT);
    }

    /** Pull an O=/CN= token out of an X.500 name for a compact summary. */
    @Nullable
    private static String shortName(@Nullable String x500) {
        if (x500 == null) {
            return null;
        }
        for (String part : x500.split(",")) {
            final String p = part.trim();
            if (p.startsWith("O=") || p.startsWith("CN=")) {
                return p;
            }
        }
        return x500.length() > 24 ? x500.substring(0, 24) : x500;
    }
}
