package com.flappygo.proxyserver.Download.Actor;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * 多线程下载模型
 *
 * @author nobody
 */
public class MutiThreadDownLoad {
    /**
     * 同时下载的线程数
     */
    private int threadCount;
    /**
     * 服务器请求路径
     */
    private String serverUrlPath;
    /**
     * 本地路径
     */
    private String localPath;

    private String tempPath;
    /**
     * 线程计数同步辅助
     */
    public CountDownLatch latch;

    private List<StreamList> streamList;




    //真实下载的文件
    private File fileActure;

    /**
     *
     * 下载长度
     *
     * */
    private long length;

    /**
     * 开始位置
     */
    private long rangeStart;


    private long blockSize;
    public MutiThreadDownLoad(int threadCount, String serverPath, String localPath,long rangeStart, long length,  File fileActure,CountDownLatch latch) {
        this.threadCount = threadCount;
        this.serverUrlPath = serverPath;
        this.localPath = localPath;
        this.latch = latch;
        this.rangeStart = rangeStart;
        this.length = length;
        this.streamList = streamList;
        this.fileActure = fileActure;

    }

    public void executeDownLoad() {
        try {

            tempPath = localPath + ".data";

            Executor threadPool = Executors.newFixedThreadPool(threadCount+1);
            //服务器返回的数据的长度，实际上就是文件的长度,单位是字节
            //System.out.println(localPath+"文件总长度:" + length + "字节(B)");
            //分割文件
           // RandomAccessFile raf = new RandomAccessFile(tempPath, "rwd");
          //  raf.setLength(length);
          //  raf.close();
            blockSize = length / threadCount;
            long startIndex = rangeStart;

            for (int threadId = 1; threadId <= threadCount; threadId++) {
                //临时大小
                long tempLength = rangeStart + (threadId  * blockSize);
                long endIndex = 0;
                endIndex = endIndex + tempLength;
                if (threadId == threadCount) {
                    //最后一个线程下载的长度稍微长一点
                    endIndex = rangeStart + length-1;
                }
              //  System.out.println("线程" + threadId + "下载:" + startIndex + "字节~" + endIndex + "字节");
                new DownLoadThread(serverUrlPath,localPath,threadCount,threadId,blockSize,startIndex,endIndex,latch).start();

                startIndex=endIndex;
            }



            /*

            MutiThreadDownLoad.DownLoadThread thread =new MutiThreadDownLoad.DownLoadThread(1, startIndex, endIndex-1,fileDatas);
            threadPool.execute(thread);
            DownloadListener listener=new DownloadListener(localPath,latch);
            threadPool.execute(listener);

             */

        } catch (Exception e) {
           // System.out.println("线程外报错");
            e.printStackTrace();
        }
    }

    public class DownLoadThread extends Thread {
        /**
         * 线程ID
         */
        private long threadId;
        /**
         * 下载起始位置
         */
        private long startIndex;
        /**
         * 下载结束位置
         */
        private long endIndex;

        //下载的数据文件
        private File fileDatas;

        private String filePath;

        private int threadCount;

        private long blockSize;

        private String serverUrlPath;


        public DownLoadThread(String serverUrlPath, String filePath, int threadCount, long threadId, long blockSize, long startIndex, long endIndex, CountDownLatch latch) {
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.filePath = filePath;
            this.threadCount = threadCount;
            this.blockSize = blockSize;
            this.serverUrlPath = serverUrlPath;

        }


        @Override
        public synchronized void run() {

            try {
                //取得apk文件的写入
                String tempPath = filePath + ".data";
                RandomAccessFile raf = new RandomAccessFile(tempPath, "rw");
                long offset = 0;

                if (threadId > 1 || threadId < threadCount) {
                    offset = (threadId - 1) * blockSize;
                } else if (threadId == threadCount) {
                    offset = (threadId - 1) * blockSize;
                }

                //定位到开始的地方
                raf.seek(offset);
                //url开始连接
                URL url = new URL(serverUrlPath);
                //打开链接
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                //设置RequestProperty
                conn.setRequestProperty("Accept-Encoding", "identity");
                //设置 User-Agent
                conn.setRequestProperty("User-Agent", "NetFox");
                //设置断点续传的开始位置
                conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);

                //获取返回值
                int responseCode = conn.getResponseCode();

                //失败抛异常
                if (!(responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL)) {
                    throw new Exception("Connection error:" + responseCode + " url:" + url);
                }
                //成功继续执行
                else {
                    //获取到input
                    InputStream inputStream = conn.getInputStream();
                    //缓存大小
                    byte[] buffer = new byte[1024];
                    //长度
                    int readedLen = 0;
                    //循环读取
                    while ((readedLen = inputStream.read(buffer)) != -1) {
                        //写入数据
                        raf.write(buffer, 0, readedLen);
                    }
                    inputStream.close();

                }
                raf.close();
                latch.countDown();
                // System.out.println("线程" + threadId + "下载完毕");
                //计数值减一
            } catch (Exception e) {
                latch.countDown();
            //    System.out.println("线程内报错");
                e.printStackTrace();

            }

        }
    }

    public class DownloadListener extends Thread{




        public DownloadListener() {

        }

        @Override
        public void run() {
            try {
                latch.await();
                /*
                Collections.sort(streamList,new Comparator<StreamList>(){
                    public int compare(StreamList arg0, StreamList arg1) {
                        return arg0.getSort().compareTo(arg1.getSort());
                    }
                });

                 */
                //重新进行命名
                File fileDatas =new File(tempPath);
                fileDatas.renameTo(fileActure);

            }
            catch (Exception e)
            {
                System.out.println("监听线程内报错");
                e.printStackTrace();
            }
        }
    }
}



class StreamList {
    private Integer sort;

    private InputStream stream;

    public StreamList(Integer sort,InputStream stream){
        this.sort=sort;
        this.stream=stream;
    }
    public Integer getSort() {
        return sort;
    }
    public void setSort(Integer sort) {
        this.sort = sort;
    }



}
