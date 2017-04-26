package com.os.operando.okhttp3.jsonrpc.logging;

import okhttp3.*;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.platform.Platform;
import okio.Buffer;
import okio.BufferedSource;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static okhttp3.internal.platform.Platform.INFO;

public class JsonRpcLoggingInterceptor implements Interceptor {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public enum Level {
        /**
         * No logs.
         */
        NONE,
        /**
         * Logs request and response lines and their respective headers and bodies (if present).
         * <p>
         * <p>Example:
         * <pre>{@code
         * --> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 100
         *
         * [
         *   {
         *     "jsonrpc": "2.0",
         *     "method": "Hello",
         *     "params": {
         *     "name": "Shinobu Bob"
         *   },
         *     "id": "2490418a-2eba-4e32-8cca-210c3000a14b"
         *   }
         * ]
         * --> END POST
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 100
         * [
         *   {
         *     "jsonrpc": "2.0",
         *     "result": {
         *       "message": "Hello, Shinobu Bob"
         *     },
         *     "id": "2490418a-2eba-4e32-8cca-210c3000a14b"
         *   }
         * ]
         * <-- END HTTP
         * }</pre>
         */
        BODY,
        /**
         * Logs request lines and their respective headers and bodies (if present).
         * <p>
         * <p>Example:
         * <pre>{@code
         * --> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 100
         *
         * [
         *   {
         *     "jsonrpc": "2.0",
         *     "method": "Hello",
         *     "params": {
         *     "name": "Shinobu Bob"
         *   },
         *     "id": "2490418a-2eba-4e32-8cca-210c3000a14b"
         *   }
         * ]
         * --> END POST
         * }</pre>
         */
        REQUEST_BODY,
        /**
         * Logs response lines and their respective headers and bodies (if present).
         * <p>
         * <p>Example:
         * <pre>{@code
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 100
         *
         * [
         *   {
         *     "jsonrpc": "2.0",
         *     "result": {
         *       "message": "Hello, Shinobu Bob"
         *     },
         *     "id": "2490418a-2eba-4e32-8cca-210c3000a14b"
         *   }
         * ]
         * <-- END HTTP
         * }</pre>
         */
        RESPONSE_BODY
    }

    public interface Logger {
        void log(String message);

        /**
         * A {@link Logger} defaults output appropriate for the current platform.
         */
        Logger DEFAULT = new Logger() {
            @Override
            public void log(String message) {
                Platform.get().log(INFO, message, null);
            }
        };
    }

    public interface JsonFormatter {
        String format(String json);
    }

    public JsonRpcLoggingInterceptor(JsonFormatter jsonFormatter) {
        this(Logger.DEFAULT, jsonFormatter);
    }

    public JsonRpcLoggingInterceptor(Logger logger, JsonFormatter jsonFormatter) {
        this.logger = logger;
        this.jsonFormatter = jsonFormatter;
    }

    private final Logger logger;
    private final JsonFormatter jsonFormatter;

    private volatile Level level = Level.NONE;

    /**
     * Change the level at which this interceptor logs.
     */
    public JsonRpcLoggingInterceptor setLevel(Level level) {
        if (level == null) {
            throw new NullPointerException("level == null. Use Level.NONE instead.");
        }
        this.level = level;
        return this;
    }

    public Level getLevel() {
        return level;
    }

    @Override
    public okhttp3.Response intercept(Chain chain) throws IOException {
        Level level = this.level;

        Request request = chain.request();
        if (level == Level.NONE) {
            return chain.proceed(request);
        }

        boolean logRequestBody = level == Level.BODY || level == Level.REQUEST_BODY;
        RequestBody requestBody = request.body();
        boolean hasRequestBody = requestBody != null;

        Connection connection = chain.connection();
        Protocol protocol = connection != null ? connection.protocol() : Protocol.HTTP_1_1;
        String requestStartMessage = "--> " + request.method() + ' ' + request.url() + ' ' + protocol;
        if (!logRequestBody && hasRequestBody) {
            requestStartMessage += " (" + requestBody.contentLength() + "-byte body)";
        }
        logger.log(requestStartMessage);

        if (logRequestBody) {
            if (hasRequestBody) {
                // Request body headers are only present when installed as a network interceptor. Force
                // them to be included (when available) so there values are known.
                if (requestBody.contentType() != null) {
                    logger.log("Content-Type: " + requestBody.contentType());
                }
                if (requestBody.contentLength() != -1) {
                    logger.log("Content-Length: " + requestBody.contentLength());
                }
            }

            Headers headers = request.headers();
            for (int i = 0, count = headers.size(); i < count; i++) {
                String name = headers.name(i);
                // Skip headers from the request body as they are explicitly logged above.
                if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                    logger.log(name + ": " + headers.value(i));
                }
            }

            if (!logRequestBody || !hasRequestBody) {
                logger.log("--> END " + request.method());
            } else if (bodyEncoded(request.headers())) {
                logger.log("--> END " + request.method() + " (encoded body omitted)");
            } else {
                Buffer buffer = new Buffer();
                requestBody.writeTo(buffer);

                Charset charset = UTF8;
                MediaType contentType = requestBody.contentType();
                if (contentType != null) {
                    charset = contentType.charset(UTF8);
                }

                logger.log("");
                if (isPlaintext(buffer)) {
                    logger.log(jsonFormatter.format(buffer.readString(charset)));
                    logger.log("--> END " + request.method()
                            + " (" + requestBody.contentLength() + "-byte body)");
                } else {
                    logger.log("--> END " + request.method() + " (binary "
                            + requestBody.contentLength() + "-byte body omitted)");
                }
            }
        }

        long startNs = System.nanoTime();
        Response response;
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
            logger.log("<-- HTTP FAILED: " + e);
            throw e;
        }
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        boolean logResponseBody = level == Level.BODY || level == Level.RESPONSE_BODY;
        ResponseBody responseBody = response.body();
        long contentLength = responseBody.contentLength();
        String bodySize = contentLength != -1 ? contentLength + "-byte" : "unknown-length";
        logger.log("<-- " + response.code() + ' ' + response.message() + ' '
                + response.request().url() + " (" + tookMs + "ms" + (!logResponseBody ? ", "
                + bodySize + " body" : "") + ')');

        if (logResponseBody) {
            Headers headers = response.headers();
            for (int i = 0, count = headers.size(); i < count; i++) {
                logger.log(headers.name(i) + ": " + headers.value(i));
            }

            if (!logResponseBody || !HttpHeaders.hasBody(response)) {
                logger.log("<-- END HTTP");
            } else if (bodyEncoded(response.headers())) {
                logger.log("<-- END HTTP (encoded body omitted)");
            } else {
                BufferedSource source = responseBody.source();
                source.request(Long.MAX_VALUE); // Buffer the entire body.
                Buffer buffer = source.buffer();

                Charset charset = UTF8;
                MediaType contentType = responseBody.contentType();
                if (contentType != null) {
                    charset = contentType.charset(UTF8);
                }

                if (!isPlaintext(buffer)) {
                    logger.log("");
                    logger.log("<-- END HTTP (binary " + buffer.size() + "-byte body omitted)");
                    return response;
                }

                if (contentLength != 0) {
                    logger.log("");
                    logger.log(jsonFormatter.format(buffer.clone().readString(charset)));
                }

                logger.log("<-- END HTTP (" + buffer.size() + "-byte body)");
            }
        }

        return response;
    }

    /**
     * Returns true if the body in question probably contains human readable text. Uses a small sample
     * of code points to detect unicode control characters commonly used in binary file signatures.
     */
    static boolean isPlaintext(Buffer buffer) {
        try {
            Buffer prefix = new Buffer();
            long byteCount = buffer.size() < 64 ? buffer.size() : 64;
            buffer.copyTo(prefix, 0, byteCount);
            for (int i = 0; i < 16; i++) {
                if (prefix.exhausted()) {
                    break;
                }
                int codePoint = prefix.readUtf8CodePoint();
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false;
                }
            }
            return true;
        } catch (EOFException e) {
            return false; // Truncated UTF-8 sequence.
        }
    }

    private boolean bodyEncoded(Headers headers) {
        String contentEncoding = headers.get("Content-Encoding");
        return contentEncoding != null && !contentEncoding.equalsIgnoreCase("identity");
    }
}