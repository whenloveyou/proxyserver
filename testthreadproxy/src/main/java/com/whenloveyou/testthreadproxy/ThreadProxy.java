package com.whenloveyou.testthreadproxy;

public class ThreadProxy extends Thread {
    public ThreadProxy(){

    }
    @Override
    public void  run(){
        try {
            while(true){
                Thread.sleep(1000);
                System.out.println("测试插件");
            }
        }
        catch (Exception e){

        }
    }

    public void proxy(){
        System.out.println("测试插件");
    }
}
