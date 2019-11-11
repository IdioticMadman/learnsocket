package com.robert.link.core.schedule;

import com.robert.link.core.Connector;
import com.robert.link.core.ScheduleJob;

import java.util.concurrent.TimeUnit;

public class IdleTimeoutSchedule extends ScheduleJob {

    public IdleTimeoutSchedule(long idleTime, TimeUnit unit, Connector connector) {
        super(idleTime, unit, connector);
    }

    @Override
    public void run() {
        long lastActiveTime = connector.getLastActiveTime();
        long idleTimeoutMilliseconds = this.idleTimeoutMilliseconds;
        long nextDelay = idleTimeoutMilliseconds - (System.currentTimeMillis() - lastActiveTime);
        Connector connector = this.connector;
        //如果在delay期间，有消息发送，根据上次活跃时间，继续delay
        if (nextDelay > 0) {
            //空闲还没到时间
            schedule(nextDelay);
        } else {
            //到时间了，要发送心跳包
            schedule(idleTimeoutMilliseconds);
            try {
                connector.fireIdleTimeoutEvent();
            } catch (Exception e) {
                connector.fireExceptionCaught(e);
            }
        }
    }
}
