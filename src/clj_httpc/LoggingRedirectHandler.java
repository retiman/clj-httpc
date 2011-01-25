package clj_httpc;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.impl.client.DefaultRedirectHandler;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.protocol.HttpContext;

/**
 * A redirect handler that overrides the default, and logs all the URIs that have
 * been encountered while handling redirects.
 */
public class LoggingRedirectHandler extends DefaultRedirectHandler {
  private final Log log = LogFactory.getLog(getClass());
  private static final String REDIRECT_LOCATIONS = "http.protocol.redirect-locations";
  private Set<URI> uris = new HashSet<URI>();

  public Set<URI> getURIs() {
    return this.uris;
  }

  /**
   * DefaultRedirectHandler#getLocationURI will create and set a RedirectLocations
   * object (which wraps a HashSet) that holds all of the URIs that DefaultHttpClient
   * has encountered while handling redirects for you.
   *
   * Unfortunately, the uris field in RedirectLocations is private, so we can't get
   * to it without some reflection hackery.
   */
  @Override
  public URI getLocationURI(final HttpResponse response, final HttpContext context) throws ProtocolException {
    URI uri = super.getLocationURI(response, context);
    try {
      RedirectLocations redirectLocations = (RedirectLocations) context.getAttribute(REDIRECT_LOCATIONS);
      if (redirectLocations != null) {
        Field field = RedirectLocations.class.getDeclaredField("uris");
        field.setAccessible(true);
        this.uris = (Set<URI>) field.get(redirectLocations);
      }
    } catch (NoSuchFieldException e) {
      log.warn(e);
    } catch (IllegalAccessException e) {
      log.warn(e);
    }

    // so ugly, but needed as GoDaddy can redirect domains from http://a.com/
    // to http://b.com// . This double slash breaks apache client
    // TODO: Upgrade to http-client-4.1-beta1 to fix this problem.
    String uriStr = uri.toString();
    Pattern r = Pattern.compile("//$");
    Matcher m = r.matcher(uriStr);
    String uriStr2 = m.replaceAll("/");

    try {
        return new URI(uriStr2);
    } catch (java.net.URISyntaxException e) {
        log.warn(e);
        return uri;
    }

  }
}
