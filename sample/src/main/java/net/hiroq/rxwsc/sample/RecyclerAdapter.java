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

import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Pair<Integer, String>> mData = new ArrayList<>();
    private Handler mHandler = new Handler();
    private WeakReference<RecyclerView> mRecyclerView = new WeakReference<>(null);

    private static final class Holder extends RecyclerView.ViewHolder {
        public Holder(View itemView) {
            super(itemView);
        }
    }

    public RecyclerAdapter(RecyclerView recyclerView) {
        mRecyclerView = new WeakReference<>(recyclerView);
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                RecyclerView view = mRecyclerView.get();
                view.scrollToPosition(mData.size() > 0 ? mData.size() - 1 : 0);
                super.onChanged();
            }
        });
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new Holder(new TextView(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Pair data = mData.get(position);
        ((TextView) holder.itemView).setText(data.second.toString());
        ((TextView) holder.itemView).setTextColor((Integer) data.first);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void addClientMessage(int color, String msg) {
        mData.add(new Pair<>(color, msg));
        final int position = mData.size();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyItemInserted(position);
            }
        });
    }

    public void addServerMessage(int color, String msg) {
        mData.add(new Pair<>(color, msg));
        final int position = mData.size();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyItemInserted(position);
            }
        });
    }
}
