package com.avragramm;

import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Old Android (6.0 and older) misses the root certificates GitHub uses now (USERTrust ECC, Sectigo
 * E46/R46, ISRG Root X1/X2), so the updater failed with "Trust anchor for certification path not
 * found". On those versions HTTPS trusts the system roots plus these bundled ones (res/raw/avra_roots.pem).
 * Nothing is relaxed: every certificate must still chain to a real, public root.
 */
public final class AvraTrust {

    private AvraTrust() {
    }

    private static boolean installed;

    public static synchronized void install() {
        if (installed || Build.VERSION.SDK_INT >= 25) return;
        installed = true;
        try {
            TrustManagerFactory sys = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            sys.init((KeyStore) null);
            final X509TrustManager system = first(sys.getTrustManagers());

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream in = ApplicationLoader.applicationContext.getResources().openRawResource(R.raw.avra_roots)) {
                int i = 0;
                for (java.security.cert.Certificate c : cf.generateCertificates(in)) ks.setCertificateEntry("avra" + i++, c);
            }
            TrustManagerFactory extra = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            extra.init(ks);
            final X509TrustManager bundled = first(extra.getTrustManagers());

            X509TrustManager both = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    system.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    try {
                        system.checkServerTrusted(chain, authType);
                    } catch (CertificateException e) {
                        bundled.checkServerTrusted(chain, authType);
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    ArrayList<X509Certificate> all = new ArrayList<>();
                    for (X509Certificate c : system.getAcceptedIssuers()) all.add(c);
                    for (X509Certificate c : bundled.getAcceptedIssuers()) all.add(c);
                    return all.toArray(new X509Certificate[0]);
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{both}, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static X509TrustManager first(TrustManager[] list) {
        for (TrustManager t : list) if (t instanceof X509TrustManager) return (X509TrustManager) t;
        throw new IllegalStateException("no X509TrustManager");
    }
}
