package com.whenloveyou.testthreadproxy;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class ThreadProxy  {
    //用于处理的回调
    private HttpServerRequestCallback callback;


    public ThreadProxy(){

    }


    //添加action
    public void addAction() {
        AsyncHttpServer server = new AsyncHttpServer();

        List<WebSocket> _sockets = new ArrayList<WebSocket>();

        server.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {




                response.send("Hello!!!");
            }
        });

// listen on port 5000
        server.listen(5000);
    }

}
