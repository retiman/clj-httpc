package clj_httpc;

import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.cert.X509Certificate;
import org.apache.http.conn.ssl.SSLSocketFactory;

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
