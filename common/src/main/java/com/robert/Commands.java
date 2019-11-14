package com.robert;

public interface Commands {

    //添加到一个群组
    String COMMAND_GROUP_JOIN = "--m g join";
    //退出群组
    String COMMAND_GROUP_LEAVE = "--m g leave";
    //绑定stream到一个命令链接(带参数)
    String COMMAND_CONNECTOR_BIND = "--m c bind ";
    //创建对话房间
    String COMMAND_AUDIO_CREATE_ROOM = "--m a create";
    //加入对话房间(带参数)
    String COMMAND_AUDIO_JOIN_ROOM = "--m a join ";
    //主动离开对话房间
    String COMMAND_AUDIO_LEAVE_ROOM = "--m a leave";
    //回送服务器上的唯一标识(带参数)
    String COMMAND_INFO_NAME = "--i server ";
    //回送语音群名(带参数)
    String COMMAND_INFO_AUDIO_ROOM = "--i a room ";
    //回送语音开始(带参数)
    String COMMAND_INFO_AUDIO_START = "--i a start ";
    //回送语音结束
    String COMMAND_INFO_AUDIO_STOP = "--i a stop";
    //回送语音操作错误
    String COMMAND_INFO_AUDIO_ERROR = "--i a error";
}
