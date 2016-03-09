package com.kaazing.gateway.jms.client.demo;

/**
 * Created by AZaharia on 3/7/2016.
 */
import android.os.Handler;
import android.os.HandlerThread;

public class DispatchQueue extends HandlerThread {

    private Handler handler;

    public DispatchQueue(String name){

        super(name);
    }

    public void waitUntilReady(){

        handler = new Handler(getLooper());
    }

    public void dispatchAsync(Runnable task){

        handler.post(task);
    }

    public void removePendingJobs(){
        handler.removeCallbacksAndMessages(null);
    }


}
