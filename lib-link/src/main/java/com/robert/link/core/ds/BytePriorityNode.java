package com.robert.link.core.ds;

public class BytePriorityNode<Item> {

    private byte priority;
    private Item item;

    private BytePriorityNode<Item> next;

    public BytePriorityNode(Item item) {
        this.item = item;
    }

    /**
     * 返回当前item
     */
    public Item item() {
        return item;
    }

    /**
     * 设置当前node的优先级
     *
     * @param priority 优先级
     */
    public void priority(byte priority) {
        this.priority = priority;
    }

    /**
     * 按优先级插入node到当前链表中
     *
     * @param node 待插入的节点
     */
    public void appendWithPriority(BytePriorityNode<Item> node) {
        if (next == null) {
            next = node;
        } else {
            BytePriorityNode<Item> after = this.next;
            //根据priority从大到小的规则塞入
            if (after.priority < node.priority) {
                this.next = node;
                node.next = after;
            } else {
                after.appendWithPriority(node);
            }
        }
    }
}
