/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package clj_httpc;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.apache.http.HttpEntity;
import org.apache.http.util.ByteArrayBuffer;

public final class EntityUtils {
  public static byte[] toByteArray(final HttpEntity entity,
                                   final Integer length) throws IOException {
    if (length == null) {
      return org.apache.http.util.EntityUtils.toByteArray(entity);
    }
    if (entity == null) {
      throw new IllegalArgumentException("HTTP entity may not be null");
    }
    InputStream instream = entity.getContent();
    if (instream == null) {
      return null;
    }
    if (entity.getContentLength() > Integer.MAX_VALUE) {
      String msg = "HTTP entity too large to be buffered in memory";
      throw new IllegalArgumentException(msg);
    }
    int i = (int)entity.getContentLength();
    if (i < 0) {
      i = 4096;
    }
    ByteArrayBuffer buffer = new ByteArrayBuffer(i);
    try {
      byte[] tmp = new byte[4096];
      int l;
      while((l = instream.read(tmp)) != -1) {
        buffer.append(tmp, 0, l);
        if (buffer.length() > length) {
          String msg = "HTTP entity too large; limit is " + length;
          InterruptedIOException e = new InterruptedIOException(msg);
          e.bytesTransferred = buffer.length();
          throw e;
        }
      }
    } finally {
      instream.close();
    }
    return buffer.toByteArray();
  }
}
