package com.robert.link.handler;

public abstract class ConnectorHandlerChain<Model> {

    private volatile ConnectorHandlerChain<Model> next;

    /**
     * 将节点添加到最后
     */
    public ConnectorHandlerChain<Model> appendLast(ConnectorHandlerChain<Model> newChain) {
        if (this == newChain || this.getClass().equals(newChain.getClass())) {
            return this;
        }
        synchronized (this) {
            if (next == null) {
                return next = newChain;
            } else {
                return next.appendLast(newChain);
            }
        }
    }

    /**
     * 移除队列中的节点
     */
    public boolean remove(Class<? extends ConnectorHandlerChain<Model>> clazz) {
        if (this.getClass() == clazz) {
            return false;
        }
        synchronized (this) {
            if (next == null) {
                return false;
            } else if (next.getClass().equals(clazz)) {
                next = next.next;
                return true;
            } else {
                return next.remove(clazz);
            }
        }
    }


    /**
     * 处理这个model
     */
    synchronized boolean handle(ConnectorHandler handler, Model model) {
        ConnectorHandlerChain<Model> next = this.next;
        if (consume(handler, model)) {
            return true;
        }
        if (next != null) {
            return next.handle(handler, model);
        }
        return consumeAgain(handler, model);
    }

    /**
     * 是否处理这个model
     */
    protected abstract boolean consume(ConnectorHandler handler, Model model);

    /**
     * 如果后面的节点不处理，当前节点是否再次处理
     */
    protected boolean consumeAgain(ConnectorHandler handler, Model model) {
        return false;
    }
}
