package com.whenloveyou.testthreadproxy;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
                try {
                    //获取headers
                    Multimap headers = request.getHeaders().getMultiMap();

                    //获取key
                    Iterator headersIterator = headers.keySet().iterator();

                    //头部的header
                    HashMap<String, String> requestMaps = new HashMap<>();

                    //请求开始的offset
                    long rangeStart = 0;

                    //请求结束的offset
                    long rangeStopd = 0;

                    //取出请求的参数
                    while (headersIterator.hasNext()) {

                        //获取请求中的key
                        String key = (String) headersIterator.next();

                        //获取请求中的value
                        String value = headers.getString(key);

                        //M3u8主文件我们就不进行断点续传处理了，没见过哪个播放器这么搞的
                        if (key != null &&
                                !key.toLowerCase().equals("host") &&
                                !key.toLowerCase().equals("range")) {
                            requestMaps.put(key, value);
                        }


                        //vlc过来的奇葩请求Range
                        if (key.toLowerCase().equals("range")) {

                            //获取到的Range
                            try {
                                //开始的位置
                                String start = value.toLowerCase().replace("bytes=", "");
                                //开始的位置
                                start = start.trim().split("-")[0];
                                //跳过多少
                                rangeStart = Long.parseLong(start);
                            } catch (Exception ex) {
                                //从零开始
                                rangeStart = 0;
                            }

                            //获取到end
                            try {
                                //开始的位置
                                String stop = value.toLowerCase().replace("bytes=", "");
                                //结束的位置
                                stop = stop.trim().split("-")[1];
                                //跳过多少
                                rangeStopd = Long.parseLong(stop);
                            } catch (Exception ex) {
                                //从零开始
                                rangeStopd = 0;
                            }
                        }
                        response.getHeaders().add(key,value);
                    }

                    //实际的Range
                    if (rangeStopd != 0) {
                        requestMaps.put("Range", "bytes=" + rangeStart + "-" + rangeStopd);
                    } else {
                        requestMaps.put("Range", "bytes=" + rangeStart + "-");
                    }


                    //url开始连接
                    URL url = null;

                    url = new URL("https://hk1.nspace.live/animi/asian/quanzhifashi3/03.mp4");

                    //打开链接
                    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                    //迭代器
                    Iterator reqIterator = requestMaps.keySet().iterator();
                    //遍历
                    while (reqIterator.hasNext()) {
                        //获取请求中的key
                        String key = (String) reqIterator.next();
                        //获取请求中的value
                        String value = requestMaps.get(key);
                        //添加
                        conn.setRequestProperty(key, value);
                    }
                    //设置
                    InputStream inputStream = conn.getInputStream();


                    //缓存大小
                    byte[] buffer = new byte[1024];
                    //长度
                    int len = 0;
                    //循环读取
                    while ((len = inputStream.read(buffer)) != -1) {

                        //返回connection的Code
                        byte[] proxByte = new byte[len];
                        //内存拷贝
                        System.arraycopy(buffer, 0, proxByte, 0, len);
                        //写入数据
                        ByteBufferList bufferList = new ByteBufferList(proxByte);
                        //写入进去
                        response.write(bufferList);

                    }
                    response.code(conn.getResponseCode());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            });


// listen on port 5000
        server.listen(5000);



    }

}
