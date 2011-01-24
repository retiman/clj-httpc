package clj_httpc;

import org.apache.http.conn.ssl.*;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.HostNameResolver;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;

public class TrustEveryoneSSLSocketFactory {
  public static SSLSocketFactory getSocketFactory() {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(
        null,
        new TrustManager[] {
          new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
              return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
          }
        },
        null
      );

      SSLSocketFactory factory = new SSLSocketFactory(context);
      factory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
      return factory;
    } catch (java.security.KeyManagementException e) {
      return SSLSocketFactory.getSocketFactory();
    } catch (java.security.NoSuchAlgorithmException e) {
      return SSLSocketFactory.getSocketFactory();
    }
  }
}
