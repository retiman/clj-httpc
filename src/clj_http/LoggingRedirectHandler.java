package clj_http;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.impl.client.DefaultRedirectHandler;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.protocol.HttpContext;

public class LoggingRedirectHandler extends DefaultRedirectHandler {
  private final Log log = LogFactory.getLog(getClass());
  private static final String REDIRECT_LOCATIONS = "http.protocol.redirect-locations";
  private Set<URI> uris = null;

  public Set<URI> getURIs() {
    return this.uris;
  }

  public URI getLocationURI(final HttpResponse response, final HttpContext context)
      throws ProtocolException {
    try {
      // We can record the URIs ourselves, but DefaultRedirectHandler already
      // does this for us.  However, it stores the URIs in a class that does
      // not expose the set of redirect URIs!
      RedirectLocations redirectLocations = (RedirectLocations) context.getAttribute(REDIRECT_LOCATIONS);
      Field field = RedirectLocations.class.getDeclaredField("uris");
      field.setAccessible(true);
      this.uris = (Set<URI>) field.get(redirectLocations);
    } catch (NoSuchFieldException e) {
      log.warn(e);
    } catch (IllegalAccessException e) {
      log.warn(e);
    } finally {
      return super.getLocationURI(response, context);
    }
  }

  public boolean isRedirectRequested(final HttpResponse response, final HttpContext context) {
    return super.isRedirectRequested(response, context);
  }
}
