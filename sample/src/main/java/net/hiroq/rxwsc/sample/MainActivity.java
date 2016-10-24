/**
 * MIT License
 * <p>
 * Copyright (c) 2016 Hiroki Oizumi
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
package net.hiroq.rxwsc.sample;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import net.hiroq.rxwsc.RxWebSocketClient;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "rxwsc/" + MainActivity.class.getSimpleName();
    private RxWebSocketClient mSocketClient;
    private Subscription mSubscription;
    private RecyclerAdapter mRecyclerAdapter;

    private int mColorIndigo;
    private int mColorTeal;
    private int mColorRed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSocketClient = new RxWebSocketClient();

        mColorIndigo = getResources().getColor(R.color.indigo);
        mColorRed = getResources().getColor(R.color.red);
        mColorTeal = getResources().getColor(R.color.teal);

        // setup RecyclerView
        RecyclerView view = ((RecyclerView) findViewById(R.id.pannel_messages));
        mRecyclerAdapter = new RecyclerAdapter(view);
        view.setAdapter(mRecyclerAdapter);
        view.setLayoutManager(new LinearLayoutManager(this));
        view.setItemAnimator(null);

        // set click event
        findViewById(R.id.send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = ((TextView) findViewById(R.id.message)).getText().toString();
                if (mSocketClient != null && mSocketClient.isConnected()) {
                    mRecyclerAdapter.addClientMessage(mColorIndigo, msg);
                    mSocketClient.send(msg);
                    ((TextView) findViewById(R.id.message)).setText("");
                } else {
                    mRecyclerAdapter.addClientMessage(mColorRed, msg + "[NOT CONNECTED]");
                }
            }
        });

        findViewById(R.id.connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Emulator connect to local PC
                mSubscription = mSocketClient.connect(Uri.parse("ws://10.0.2.2:8080"))
                        .retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {
                            @Override
                            public Observable<?> call(final Observable<? extends Throwable> observable) {
                                return observable.flatMap(new Func1<Throwable, Observable<?>>() {
                                    @Override
                                    public Observable<?> call(Throwable throwable) {
                                        // If the exception is ConnectionException, retry after 1 seconds.
                                        if (throwable instanceof ConnectException) {
                                            mRecyclerAdapter.addClientMessage(mColorRed, "[CONNECTION ERROR RETRY AFTER 1sec]");
                                            return Observable.timer(1, TimeUnit.SECONDS);
                                        } else {
                                            return Observable.error(throwable);
                                        }
                                    }
                                });
                            }
                        })
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<RxWebSocketClient.Event>() {
                            @Override
                            public void call(RxWebSocketClient.Event event) {
                                switch (event.getType()) {
                                    case CONNECT:
                                        mRecyclerAdapter.addClientMessage(mColorTeal, "[CONNECTED]");
                                        break;
                                    case DISCONNECT:
                                        mRecyclerAdapter.addClientMessage(mColorRed, "[DISCONNECTED]");
                                        break;
                                    case MESSAGE_BINARY:
                                        break;
                                    case MESSAGE_STRING:
                                        mRecyclerAdapter.addServerMessage(mColorTeal, event.getString() + "[server echos message]");
                                        break;
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                mRecyclerAdapter.addClientMessage(mColorRed, "[ERROR]");
                                throwable.printStackTrace();
                            }
                        }, new Action0() {
                            @Override
                            public void call() {
                                mRecyclerAdapter.addClientMessage(mColorTeal, "[COMPLETE]");
                            }
                        });
            }
        });
        findViewById(R.id.disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // In this case, DISCONNECT and COMPLETE message WILL deliver in next message loop.
                if (mSocketClient != null) {
                    mSocketClient.disconnect();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        // In this case, DISCONNECT and COMPLETE message WILL NOT deliver,
        // because mSubscription unsubscribes completely in same message loop.
        if (mSubscription != null) {
            mSubscription.unsubscribe();
            mSubscription = null;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
