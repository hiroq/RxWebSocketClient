/**
 * MIT License
 * <p>
 * Copyright (c) 2016 Hiroki Oizumi
 * <p>
 * This library is ported from following libraries
 * 1. Copyright (c) 2010-2016 James Coglan
 * faye's faye-websocket-node
 * https://github.com/faye/faye-websocket-node
 * 2. Copyright (c) 2012 Eric Butler
 * codebutler's android-websockets.
 * https://github.com/codebutler/android-websockets
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.hiroq.rxwsc;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Base64;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import rx.Observable;
import rx.Subscriber;

public class RxWebSocketClient {
    /**
     * Streaming EventTypes
     */
    public enum EventType {
        CONNECT,
        MESSAGE_STRING,
        MESSAGE_BINARY,
        DISCONNECT,
    }

    public static class Event {
        /**
         * EventType
         */
        private EventType mType = null;

        /**
         * Payload byte data from WebSocketServer
         */
        private byte[] mBytes = null;

        /**
         * Payload String data from WebSocketServer
         */
        private String mString = null;

        /**
         * Constructor
         *
         * @param type
         */
        Event(EventType type) {
            this.mType = type;
        }

        /**
         * Constructor
         *
         * @param type
         * @param bytes
         */
        Event(EventType type, byte[] bytes) {
            this.mType = type;
            this.mBytes = bytes;
        }

        /**
         * Constructor
         *
         * @param type
         * @param string
         */
        Event(EventType type, String string) {
            this.mType = type;
            this.mString = string;
        }

        /**
         * Get EventType value
         *
         * @return
         */
        public EventType getType() {
            return mType;
        }

        /**
         * Get payload byte data.
         * It will return valid data if EventType is MESSAGE_BINARY.
         * When the other EventTypes, it will return null.
         *
         * @return string value. Return null if no received data,
         */
        public byte[] getBytes() {
            return mBytes;
        }

        /**
         * Get payload String data.
         * It will return valid data if EventType is MESSAGE_STRING.
         * When the other EventTypes, it will return null.
         *
         * @return string value. Return null if no received data,
         */
        public String getString() {
            return mString;
        }
    }

    /**
     * SSL/TSL TrustedManagers
     */
    private static TrustManager[] sTrustManagers;

    /**
     * Connection Uri
     */
    private Uri mUri;

    /**
     * Raw Socket Object
     */
    private Socket mSocket;

    /**
     * Thread with message queue
     */
    private HandlerThread mHandlerThread;

    /**
     * Handler
     */
    private Handler mHandler;

    /**
     * Additional HttpHeader
     */
    private List<BasicNameValuePair> mExtraHeaders;

    /**
     * WebSocket Message Parser
     */
    private HybiParser mParser;

    /**
     * RxJava Subscriber
     */
    private Subscriber<? super Event> mSubscriber;


    /**
     * Connect to WebSocketServer with additional Header.
     *
     * @param uri
     * @param extraHeaders
     * @return
     */
    public Observable<Event> connect(Uri uri, List<BasicNameValuePair> extraHeaders) {
        this.mUri = uri;
        this.mExtraHeaders = extraHeaders;
        this.mParser = new HybiParser(this);

        this.mHandlerThread = new HandlerThread(getClass().getName());
        this.mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());

        return Observable.create(new Observable.OnSubscribe<Event>() {
            @Override
            public void call(Subscriber<? super Event> subscriber) {
                try {
                    mSubscriber = subscriber;

                    String secret = createSecret();
                    String scheme = mUri.getScheme();

                    // uri have invalid scheme throw MalformedURLException
                    if (scheme == null || !(scheme.equals("ws") || scheme.equals("wss"))) {
                        subscriber.onError(new MalformedURLException("Url scheme has to be specified as \"ws\" or \"wss\"."));
                    }

                    int port = (mUri.getPort() != -1) ? mUri.getPort() : (scheme.equals("wss") ? 443 : 80);
                    String path = TextUtils.isEmpty(mUri.getPath()) ? "/" : mUri.getPath();
                    if (!TextUtils.isEmpty(mUri.getQuery())) {
                        path += "?" + mUri.getQuery();
                    }

                    String originScheme = scheme.equals("wss") ? "https" : "http";
                    Uri origin = Uri.parse(originScheme + "://" + mUri.getHost());

                    SocketFactory factory = scheme.equals("wss") ? getSSLSocketFactory() : SocketFactory.getDefault();
                    mSocket = factory.createSocket(mUri.getHost(), port);

                    PrintWriter out = new PrintWriter(mSocket.getOutputStream());
                    out.print("GET " + path + " HTTP/1.1\r\n");
                    out.print("Upgrade: websocket\r\n");
                    out.print("Connection: Upgrade\r\n");
                    out.print("Host: " + mUri.getHost() + "\r\n");
                    out.print("Origin: " + origin.toString() + "\r\n");
                    out.print("Sec-WebSocket-Key: " + secret + "\r\n");
                    out.print("Sec-WebSocket-Version: 13\r\n");
                    if (mExtraHeaders != null) {
                        for (NameValuePair pair : mExtraHeaders) {
                            out.print(String.format("%s: %s\r\n", pair.getName(), pair.getValue()));
                        }
                    }
                    out.print("\r\n");
                    out.flush();

                    HybiParser.HappyDataInputStream stream = new HybiParser.HappyDataInputStream(mSocket.getInputStream());

                    // Read HTTP response status line.
                    StatusLine statusLine = parseStatusLine(readLine(stream));
                    if (statusLine == null) {
                        throw new HttpException("Received no reply from server.");
                    } else if (statusLine.getStatusCode() != HttpStatus.SC_SWITCHING_PROTOCOLS) {
                        throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
                    }

                    // Read HTTP response headers.
                    String line;
                    boolean validated = false;

                    while (!TextUtils.isEmpty(line = readLine(stream))) {
                        Header header = parseHeader(line);
                        if (header.getName().equals("Sec-WebSocket-Accept")) {
                            String expected = createSecretValidation(secret);
                            String actual = header.getValue().trim();

                            if (!expected.equals(actual)) {
                                throw new HttpException("Bad Sec-WebSocket-Accept header value.");
                            }

                            validated = true;
                        }
                    }

                    if (!validated) {
                        throw new HttpException("No Sec-WebSocket-Accept header.");
                    }

                    subscriber.onStart();
                    subscriber.onNext(new Event(EventType.CONNECT));

                    // Now decode websocket frames.
                    mParser.start(stream);
                } catch (Exception e) {
                    mSubscriber.onError(e);
                }
            }
        });
    }

    /**
     * Connect to WebSocketServer.
     *
     * @param uri
     * @return
     */
    public Observable<Event> connect(Uri uri) {
        return connect(uri, null);
    }

    /**
     * Send string data to WebSocketServer.
     *
     * @param message
     */
    public void send(String message) {
        sendFrame(mParser.frame(message));
    }

    /**
     * Send raw data to WebSocketServer.
     *
     * @param message
     */
    public void send(byte[] message) {
        sendFrame(mParser.frame(message));
    }

    /**
     * Disconnect WebSocket, emit onNext with EventType.DISCONNECT and finally onComplete to Streaming
     */
    public void diconnect() {
        if (mSocket != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mParser.stop();
                        mHandlerThread.join();
                        mSocket.close();
                    } catch (Exception e) {
                        subscriberOnError(e);
                    }
                }
            });
        }
    }

    /**
     * Parse HttpStatusLine
     *
     * @param line
     * @return
     */
    private StatusLine parseStatusLine(String line) {
        if (TextUtils.isEmpty(line)) {
            return null;
        }
        return BasicLineParser.parseStatusLine(line, new BasicLineParser());
    }

    /**
     * Parse HttpHeader
     *
     * @param line
     * @return
     */
    private Header parseHeader(String line) {
        return BasicLineParser.parseHeader(line, new BasicLineParser());
    }

    /**
     * Read string data from parsed WebSocket message.
     *
     * @param reader
     * @return
     * @throws IOException
     */
    private String readLine(HybiParser.HappyDataInputStream reader) throws IOException {
        int readChar = reader.read();
        if (readChar == -1) {
            return null;
        }
        StringBuilder string = new StringBuilder("");
        while (readChar != '\n') {
            if (readChar != '\r') {
                string.append((char) readChar);
            }

            readChar = reader.read();
            if (readChar == -1) {
                return null;
            }
        }
        return string.toString();
    }

    /**
     * create Secret specified RFC6455.
     *
     * @return
     */
    private String createSecret() {
        byte[] nonce = new byte[16];
        for (int i = 0; i < 16; i++) {
            nonce[i] = (byte) (Math.random() * 256);
        }
        return Base64.encodeToString(nonce, Base64.DEFAULT).trim();
    }

    /**
     * create SecretValidation specified RFC6455
     *
     * @param secret
     * @return
     */
    private String createSecretValidation(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((secret + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes());
            return Base64.encodeToString(md.digest(), Base64.DEFAULT).trim();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set SSL/TSL TrustedManager
     *
     * @param tm
     */
    public static void setTrustManagers(TrustManager[] tm) {
        sTrustManagers = tm;
    }


    /**
     * Init SSL/TSL context and get SocketFactory
     *
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    private SSLSocketFactory getSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, sTrustManagers, null);
        return context.getSocketFactory();
    }

    /**
     * send frame data to SocketStream
     *
     * @param frame
     */
    void sendFrame(final byte[] frame) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (Thread.currentThread()) {
                        if (mSocket == null) {
                            throw new IllegalStateException("Socket not connected");
                        }
                        OutputStream outputStream = mSocket.getOutputStream();
                        outputStream.write(frame);
                        outputStream.flush();
                    }
                } catch (IOException e) {
                    subscriberOnError(e);
                }
            }
        });
    }

    /**
     * Emit onNext to Streaming
     */
    void subscriberOnNext(Event event) {
        if (mSubscriber != null && mSubscriber.isUnsubscribed()) {
            mSubscriber = null;
            return;
        }
        mSubscriber.onNext(event);
    }

    /**
     * Emit onError to Streaming
     *
     * @param e
     */
    void subscriberOnError(Throwable e) {
        if (mSubscriber != null && mSubscriber.isUnsubscribed()) {
            mSubscriber = null;
            return;
        }
        mSubscriber.onError(e);
    }

    /**
     * Emit onComplete to Streaming
     */
    void subscriberOnCompleted() {
        if (mSubscriber != null && mSubscriber.isUnsubscribed()) {
            mSubscriber = null;
            return;
        }
        if (mSubscriber != null) {
            mSubscriber.onCompleted();
        }
        mSubscriber = null;
    }
}