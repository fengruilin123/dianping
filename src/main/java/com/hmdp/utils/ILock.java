package com.hmdp.utils;
//分布式锁
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeOutSec
     * @return 成功返回true，失败返回false
     */
    boolean tryLock(long timeOutSec);

    /**
     * 释放锁
     */
    void unlock();
}
