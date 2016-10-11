# RxWebSocketClient
Simple RxJava WebSocketClient

# Install
```groovy
dependencies {
    compile 'net.hiroq:rxwsc:0.1.0'
}
```

# Usage
If you use lambda, the code will be simpler!

```java
mSocketClient = new RxWebSocketClient();
mSubscription = mSocketClient.connect(Uri.parse("ws://hogehoge"))
        .onBackpressureBuffer()
        .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<RxWebSocketClient.Event>() {
            @Override
            public void call(RxWebSocketClient.Event event) {
                Log.d(TAG, "== onNext ==");
                switch (event.getType()) {
                    case CONNECT:
                        Log.d(TAG, "  CONNECT");
                        mSocketClient.send("test");
                        break;
                    case DISCONNECT:
                        Log.d(TAG, "  DISCONNECT");
                        break;
                    case MESSAGE_BINARY:
                        Log.d(TAG, "  MESSAGE_BINARY : bytes = " + event.getBytes().length);
                        break;
                    case MESSAGE_STRING:
                        Log.d(TAG, "  MESSAGE_STRING = " + event.getString());
                        break;
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                Log.d(TAG, "== onError ==");
                throwable.printStackTrace();
            }
        }, new Action0() {
            @Override
            public void call() {
                Log.d(TAG, "== onComplete ==");
            }
        });
```


If you want to reconnect automatically, use retryWhen like below:
```java
mSocketClient = new RxWebSocketClient();
mSubscription = mSocketClient.connect(Uri.parse("ws://hogehoge"))
        .onBackpressureBuffer()
        .retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {
            @Override
            public Observable<?> call(final Observable<? extends Throwable> observable) {
                return observable.flatMap(new Func1<Throwable, Observable<?>>() {
                    @Override
                    public Observable<?> call(Throwable throwable) {
                        // If the exception is ConnectionException,
                        // retry with 5 second delay.
                        if (throwable instanceof ConnectException) {
                            Log.d(TAG, "retry with delay");
                            return Observable.timer(5, TimeUnit.SECONDS);
                        }else {
                            // Other exceptions will be handle onError
                            return Observable.error(throwable);
                        }
                    }
                });
            }
        })
        .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
```


# TODO
* make sample code
* make test


# Licence

```
MIT License

Copyright (c) 2016 Hiroki Oizumi

This library is ported from following libraries
1.  Copyright (c) 2010-2016 James Coglan<br>
    faye's faye-websocket-node<br>
    https://github.com/faye/faye-websocket-node
2. Copyright (c) 2012 Eric Butler<br>
    codebutler's android-websockets.<br>
    https://github.com/codebutler/android-websockets

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
