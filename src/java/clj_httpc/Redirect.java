package clj_httpc;

import java.net.URI;

/**
 * A class to represent a pairing between a redirect and a status code
 * because Java does not have a Pair<A, B> class.
 */
public class Redirect {
  private final Integer status;
  private final URI uri;

  public Redirect(final URI uri, final Integer status) {
    this.uri = uri;
    this.status = status;
  }

  public Integer getStatus() {
    return this.status;
  }

  public URI getURI() {
    return this.uri;
  }
}
