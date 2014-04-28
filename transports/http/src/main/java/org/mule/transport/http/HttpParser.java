/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.ProtocolException;
import org.apache.http.message.BasicHeader;

/**
 *
 */
//TODO(pablo.kraan): HTTPCLIENT - added this class to simplify migration. Must be cleaned up
public class HttpParser
{

    /**
     * Read up to <tt>"\n"</tt> from an (unchunked) input stream.
     * If the stream ends before the line terminator is found,
     * the last part of the string will still be returned.
     * If no input data available, <code>null</code> is returned.
     *
     * @param inputStream the stream to read from
     * @param charset charset of HTTP protocol elements
     *
     * @throws java.io.IOException if an I/O problem occurs
     * @return a line from the stream
     *
     * @since 3.0
     */
    public static String readLine(InputStream inputStream, String charset) throws IOException
    {
        //LOG.trace("enter HttpParser.readLine(InputStream, String)");
        byte[] rawdata = readRawLine(inputStream);
        if (rawdata == null) {
            return null;
        }
        // strip CR and LF from the end
        int len = rawdata.length;
        int offset = 0;
        if (len > 0) {
            if (rawdata[len - 1] == '\n') {
                offset++;
                if (len > 1) {
                    if (rawdata[len - 2] == '\r') {
                        offset++;
                    }
                }
            }
        }
        final String result = getString(rawdata, 0, len - offset, charset);
        //if (Wire.HEADER_WIRE.enabled()) {
        //    String logoutput = result;
        //    if (offset == 2)
        //        logoutput = result + "\r\n";
        //    else if (offset == 1)
        //        logoutput = result + "\n";
        //    Wire.HEADER_WIRE.input(logoutput);
        //}
        return result;
    }

    /**
     * Return byte array from an (unchunked) input stream.
     * Stop reading when <tt>"\n"</tt> terminator encountered
     * If the stream ends before the line terminator is found,
     * the last part of the string will still be returned.
     * If no input data available, <code>null</code> is returned.
     *
     * @param inputStream the stream to read from
     *
     * @throws IOException if an I/O problem occurs
     * @return a byte array from the stream
     */
    public static byte[] readRawLine(InputStream inputStream) throws IOException {
        //LOG.trace("enter HttpParser.readRawLine()");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int ch;
        while ((ch = inputStream.read()) >= 0) {
            buf.write(ch);
            if (ch == '\n') { // be tolerant (RFC-2616 Section 19.3)
                break;
            }
        }
        if (buf.size() == 0) {
            return null;
        }
        return buf.toByteArray();
    }

    /**
     * Converts the byte array of HTTP content characters to a string. If
     * the specified charset is not supported, default system encoding
     * is used.
     *
     * @param data the byte array to be encoded
     * @param offset the index of the first byte to encode
     * @param length the number of bytes to encode
     * @param charset the desired character encoding
     * @return The result of the conversion.
     *
     * @since 3.0
     */
    public static String getString(
            final byte[] data,
            int offset,
            int length,
            String charset
    ) {

        if (data == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }

        if (charset == null || charset.length() == 0) {
            throw new IllegalArgumentException("charset may not be null or empty");
        }

        try {
            return new String(data, offset, length, charset);
        } catch (UnsupportedEncodingException e) {

            //if (LOG.isWarnEnabled()) {
            //    LOG.warn("Unsupported encoding: " + charset + ". System encoding used");
            //}
            return new String(data, offset, length);
        }
    }

    /**
     * Parses headers from the given stream.  Headers with the same name are not
     * combined.
     *
     * @param is the stream to read headers from
     * @param charset the charset to use for reading the data
     *
     * @return an array of headers in the order in which they were parsed
     *
     * @throws IOException if an IO error occurs while reading from the stream
     * @throws HttpException if there is an error parsing a header value
     *
     * @since 3.0
     */
    public static Header[] parseHeaders(InputStream is, String charset) throws IOException, HttpException
    {
        //LOG.trace("enter HeaderParser.parseHeaders(InputStream, String)");

        ArrayList headers = new ArrayList();
        String name = null;
        StringBuffer value = null;
        for (; ;) {
            String line = HttpParser.readLine(is, charset);
            if ((line == null) || (line.trim().length() < 1)) {
                break;
            }

            // Parse the header name and value
            // Check for folded headers first
            // Detect LWS-char see HTTP/1.0 or HTTP/1.1 Section 2.2
            // discussion on folded headers
            if ((line.charAt(0) == ' ') || (line.charAt(0) == '\t')) {
                // we have continuation folded header
                // so append value
                if (value != null) {
                    value.append(' ');
                    value.append(line.trim());
                }
            } else {
                // make sure we save the previous name,value pair if present
                if (name != null) {
                    headers.add(new BasicHeader(name, value.toString()));
                }

                // Otherwise we should have normal HTTP header line
                // Parse the header name and value
                int colon = line.indexOf(":");
                if (colon < 0) {
                    throw new ProtocolException("Unable to parse header: " + line);
                }
                name = line.substring(0, colon).trim();
                value = new StringBuffer(line.substring(colon + 1).trim());
            }

        }

        // make sure we save the last name,value pair if present
        if (name != null) {
            headers.add(new BasicHeader(name, value.toString()));
        }

        return (Header[]) headers.toArray(new Header[headers.size()]);
    }

}
