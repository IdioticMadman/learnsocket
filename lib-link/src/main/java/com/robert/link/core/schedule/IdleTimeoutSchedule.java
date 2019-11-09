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
        if (nextDelay > 0) {

        } else {

        }
    }
}
