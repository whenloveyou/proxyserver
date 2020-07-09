package com.flappygo.proxyserver.ProxyServer.ServerHttp.Request;

import com.flappygo.proxyserver.Config.ServerConfig;
import com.flappygo.proxyserver.Interface.ProxyServer;
import com.flappygo.proxyserver.ProxyServer.ServerHttp.Interface.ProxyServerHttpSegmentListener;
import com.flappygo.proxyserver.Tools.ToolLog;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

// 多线程请求数据类
class NetworkThread implements Runnable {
    // 线程计数器
    private CountDownLatch cbRef;

    // 多线程下载的分片大小
    public static long FILE_SEGMENHT_SIZE = 1024 * 200;

    // 真实请求地址
    private String urlStr;

    // 请求头
    private HashMap<String, String> headers;

    // 获取分片的开始位置
    private long startPos;

    // 获取分片的长度
    private long length;

    // 分片索引
    private long index;



    // 下载结果列表
    public HashMap<Long, byte[]> contentList;

    private NetworkThread(String url, HashMap<String, String> headers,
                          long startPos, long length, long index, HashMap<Long, byte[]> contentList,
                          CountDownLatch cbRef){
        this.urlStr = url;
        this.headers = headers;
        this.startPos = startPos;
        this.length = length;
        this.index = index;
        this.cbRef = cbRef;
        this.contentList = contentList;
    }

    /**
     * 主线程打开网络文件,先分割为指定的大小，然后开启多线程下载
     */
    public NetworkThread(String urlFile, HashMap<String, String> headers,
                         long contentStartIndex, long contentLength)
            throws InterruptedException {
        // 设置分片大小
        long segmentSize = FILE_SEGMENHT_SIZE; // FILE_SEGMENHT_SIZE;


        // 计算需要多少线程进行下载
        long threadCount = contentLength / segmentSize;
        long remain = contentLength % segmentSize;
        if ( remain > 0 ) {
            threadCount++;
        }

        // 初始化线程计数
        final CountDownLatch cbRef = new CountDownLatch((int)threadCount);

        long startPos = 0;
        long length = 0;
        HashMap<Long, byte[]> contentList = new HashMap<>();
        for (long i = 0; i < threadCount; i++) {
            // 计算下载的开始位置
            startPos = contentStartIndex + i * segmentSize;

            // 计算下载的长度
            if (contentLength >= (i+1)*segmentSize ){
                length = segmentSize;
            }else{
                length = remain;
            }

            // 开启下载线程

            NetworkThread work = new NetworkThread(urlFile, headers, startPos, length, i, contentList, cbRef);
            new Thread(work).start();

        }

        cbRef.await();

        this.contentList = contentList;
    }

    public void run() {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        try {
            //开始连接
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            Date startDate = new Date();

            URL url = new URL(this.urlStr);
            //打开链接
            conn = (HttpURLConnection) url.openConnection();
            //get
            conn.setRequestMethod("GET");
            //迭代器
            Iterator reqIterator = headers.keySet().iterator();
            //遍历
            while (reqIterator.hasNext()) {
                //获取请求中的key
                String key = (String) reqIterator.next();
                //获取请求中的value
                String value = headers.get(key);
                //添加
                conn.setRequestProperty(key, value);
            }
            //设置请求的Range
            conn.setRequestProperty("Range", "bytes=" + startPos + "-");
            conn.setReadTimeout(1500);
            inputStream = conn.getInputStream();

            byte[] content = new byte[(int)length];
            //缓存大小
            byte[] buffer = new byte[1024];
            //长度
            int len = 0;
            int startLen = 0;
            //循环读取
            while ((len = inputStream.read(buffer)) != -1) {
                if ( startLen + len > length ){
                    len = (int)(length) - startLen;
                }

                //内存拷贝
                System.arraycopy(buffer, 0, content, startLen, len);
                startLen += len;

                if ( startLen + len == length ){
                    break;
                }
            }

            this.contentList.put(index, content);

            //关闭连接
            inputStream.close();

            //停止
            conn.disconnect();

            Date endDate = new Date();
            long consumeTime=endDate.getTime()-startDate.getTime();

            System.out.println("thread time: "+index + " begin：" + format.format(startDate) +
                    " end:" + format.format(endDate) + " consumeTime: " + consumeTime);

            // 线程计数处理
            this.cbRef.countDown();
        } catch (IOException e) {
            try {
                if ( inputStream != null ){
                    inputStream.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if ( conn != null ){
                conn.disconnect();
            }

            this.run();
            //e.printStackTrace();
        }
    }
}

public class HttpRequestNetwork {
    //父类
    private ProxyServer proxyServer;
    //请求
    private HashMap<String, String> headers;
    //返回消息
    private AsyncHttpServerResponse response;
    //下载器
    private String urlStr;

    //重试的时间
    private long retryTime = 0;

    //当前的range
    private long rangeNower = 0;

    //请求开始的offset
    private long rangeStart = 0;

    //请求结束的offset
    private long rangeLength = 0;

    //监听
    private ProxyServerHttpSegmentListener listener;

    //当前是否暂停
    private boolean awaitFlag = false;

    private boolean isThread = false;

    //请求
    public HttpRequestNetwork(ProxyServer parent,
                              HashMap<String, String> headers,
                              final AsyncHttpServerResponse response,
                              ProxyServerHttpSegmentListener listener,
                              String urlStr,
                              long rangeStart,
                              long rangeLength) {
        this.proxyServer = parent;
        this.headers = headers;
        this.response = response;
        this.urlStr = urlStr;
        this.rangeStart = rangeStart;
        this.rangeNower = rangeStart;
        this.rangeLength = rangeLength;
        this.listener = listener;
        this.response.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                //如果正在等待
                if (awaitFlag == true) {
                    //停止等待
                    awaitFlag = false;
                    //继续写入数据
                    if ( isThread ){
                        doResponseNetThread();
                    }else{
                        doResponseNet();
                    }
                }
            }
        });
    }

    // 多线程处理net
    public void doResponseNetThread(){
        //此处对请求进行相应的代理处理
        try {
            this.isThread = true;

            NetworkThread networkThread = new NetworkThread(urlStr, headers, rangeStart, rangeLength);

            // 获取数据总长度
            int contentLength = 0;
            for ( long i = 0; i < networkThread.contentList.size(); i++ ){
                byte[] value = networkThread.contentList.get(i);
                if ( value != null ){
                    contentLength += value.length;
                }
            }

            // 组装分片数据
            byte[] contentStr = new byte[contentLength];
            int startPos = 0;
            for ( long i = 0; i < networkThread.contentList.size(); i++ ){
                byte[] value = networkThread.contentList.get(i);
                if ( value != null ){
                    System.arraycopy(value, 0, contentStr, startPos, value.length);
                    startPos += value.length;
                }
            }

            // 将 byte 转换为 inputStream
            InputStream inputStream = new ByteArrayInputStream(contentStr);

            // 推送给 VLC 播放器
            // 缓存大小
            byte[] buffer = new byte[1024];
            // 长度
            int len = 0;
            // 循环读取
            while ((len = inputStream.read(buffer)) != -1) {
                //整个服务已经停止，不再相应
                if (proxyServer.isStoped()) {
                    //提醒监听结束
                    if (listener != null) {
                        listener.segmentProxyStoped();
                    }
                    break;
                }

                //跳出
                if (rangeNower == rangeStart + rangeLength){
                    break;
                }

                //如果读取的数据已经超出我们的限制，那么我们只写入我们需要的长度
                if (rangeNower + len >= rangeStart + rangeLength) {
                    //限制长度为这么多
                    len = (int) (rangeStart + rangeLength - rangeNower);
                }

                //如果不是等待状态
                if (awaitFlag == false) {
                    // 创建
                    byte[] proxByte = new byte[len];
                    // 内存拷贝
                    System.arraycopy(buffer, 0, proxByte, 0, len);
                    // 写入数据
                    ByteBufferList bufferList = new ByteBufferList(proxByte);
                    // 写入进去
                    response.write(bufferList);
                    // 写入了多少
                    rangeNower += len;
                    //等待
                    if (bufferList.remaining() > 0) {
                        //减去没有写入的
                        rangeNower -= bufferList.remaining();
                        //等待下一波
                        awaitFlag = true;
                        //跳出循环
                        break;
                    }
                }
            }

            //关闭连接
            inputStream.close();

            // 结束分片处理
            if (rangeNower == rangeStart + rangeLength) {
                if (listener != null) {
                    listener.segmentProxyEnd();
                }
            }
        } catch (InterruptedException | IOException e ) {
            //断线重连
            if (isNeedRetry()) {
                //等待300毫秒
                waitMilliseconds(250);
                //重新请求
                if (!proxyServer.isStoped()) {
                    doResponseNetThread();
                }
            } else {
                //异常结束
                if (listener != null) {
                    listener.segmentProxyStoped();
                }
            }
        }
    }

    //处理net
    public void doResponseNet() {
        //此处对请求进行相应的代理处理
        try {
            this.isThread = false;
            //开始连接
            URL url = new URL(urlStr);
            //打开链接
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            //get
            conn.setRequestMethod("GET");
            //迭代器
            Iterator reqIterator = headers.keySet().iterator();
            //遍历
            while (reqIterator.hasNext()) {
                //获取请求中的key
                String key = (String) reqIterator.next();
                //获取请求中的value
                String value = headers.get(key);
                //添加
                conn.setRequestProperty(key, value);
            }
            //设置请求的Range
            conn.setRequestProperty("Range", "bytes=" + rangeNower + "-");
            //设置连接保持
            conn.setRequestProperty("Connection", "Keep-Alive");

            //设置
            InputStream inputStream = conn.getInputStream();
            //重试
            resetRetryTime();
            //缓存大小
            byte[] buffer = new byte[1024];
            //长度
            int len = 0;
            //循环读取
            while ((len = inputStream.read(buffer)) != -1) {
                //整个服务已经停止，不再相应
                if (proxyServer.isStoped()) {
                    //提醒监听结束
                    if (listener != null) {
                        listener.segmentProxyStoped();
                    }
                    break;
                }

                //跳出
                if (rangeNower == rangeStart + rangeLength){
                    break;
                }

                //如果读取的数据已经超出我们的限制，那么我们只写入我们需要的长度
                if (rangeNower + len >= rangeStart + rangeLength) {
                    //限制长度为这么多
                    len = (int) (rangeStart + rangeLength - rangeNower);
                }

                //如果不是等待状态
                if (awaitFlag == false) {
                    //创建
                    byte[] proxByte = new byte[len];
                    //内存拷贝
                    System.arraycopy(buffer, 0, proxByte, 0, len);
                    //写入数据
                    ByteBufferList bufferList = new ByteBufferList(proxByte);
                    //写入进去
                    response.write(bufferList);
                    //写入了多少
                    rangeNower += len;
                    //等待
                    if (bufferList.remaining() > 0) {
                        //减去没有写入的
                        rangeNower -= bufferList.remaining();
                        //等待下一波
                        awaitFlag = true;
                        //跳出循环
                        break;
                    }
                }
            }

            //关闭连接
            inputStream.close();

            //停止
            conn.disconnect();

            //分段处理结束
            if (rangeNower == rangeStart + rangeLength) {
                if (listener != null) {
                    listener.segmentProxyEnd();
                }
            }
        } catch (IOException e) {
            //断线重连
            if (isNeedRetry()) {
                //等待300毫秒
                waitMilliseconds(250);
                //重新请求
                if (!proxyServer.isStoped()) {
                    doResponseNet();
                }
            } else {
                //异常结束
                if (listener != null) {
                    listener.segmentProxyStoped();
                }
            }
        }
    }

    //等待多少毫秒
    private void waitMilliseconds(int milli) {
        try {
            Thread.sleep(milli);
        } catch (Exception exception) {
            //等待失败
        }
    }

    //重置时间
    private void resetRetryTime() {
        retryTime = 0;
    }


    //是否需要重新
    private boolean isNeedRetry() {
        //已经停止了的就不需要
        if (proxyServer.isStoped()) {
            return false;
        }
        //重试时间初始化
        if (retryTime == 0) {
            retryTime = System.currentTimeMillis();
        }
        //检查是否在重试时间之内
        if (System.currentTimeMillis() - retryTime < ServerConfig.NETWORK_RETRY_TIME) {
            return true;
        } else {
            return false;
        }
    }
}
