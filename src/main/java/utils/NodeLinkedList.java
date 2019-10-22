package utils;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class NodeLinkedList<T> {

    public static class Node<T> {
        public final T value;

        Node<T> next;
        Node<T> prev;

        Node(T value){
            this.value = value;
        }
    }

    private Node<T> head;

    public NodeLinkedList(){
        head = new Node<T>(null);
        head.next = head;
        head.prev = head;
    }

    public Node<T> push(T value) {
        Node<T> node = new Node<T>(value);
        Node<T> tail = head.prev;
        node.prev = tail;
        node.next = head;
        head.prev = node;
        tail.next = node;
        return node;
    }

    public Node<T> addToTail(T value) {
        Node<T> node = new Node<>(value);
        head.prev = node;
        node.next = head;
        return node;
    }

    public boolean isEmpty() {
        return head == head.prev;
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    public Node<T> getHeadNode() {
        if(isEmpty()) {
            throw new IllegalStateException("cannot get head of an empty list");
        }
        return head.next;
    }

    public boolean isHeadNode(Node<T> node){
        return head.next == node;
    }

    public Node<T> pull () {
        if(isEmpty()) {
            throw new IllegalStateException("cannot pull from an empty list");
        }
        Node<T> node = head.next;
        head.next = node.next;
        node.next.prev = head;
        return node;
    }

    public void remove (Node<T> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    public Node<T> pullSpecificNodeAndReturn(Predicate<T> pred, Node<T> curr, Node<T> firstNodeToSearch) {

        if (firstNodeToSearch.value != null && pred.test(firstNodeToSearch.value) && firstNodeToSearch != curr) {
            // To pull
            firstNodeToSearch.prev.next = firstNodeToSearch.next;
            firstNodeToSearch.next.prev = firstNodeToSearch.prev;
            return firstNodeToSearch;
        }

        return firstNodeToSearch.value == null ? new Node<>(null) :
                pullSpecificNodeAndReturn(pred, curr, firstNodeToSearch.next);
    }

    public Node<T> searchNodeAndReturn (Predicate<T> pred, Node<T> curr, Node<T> firstNodeToSearch) {

        if (firstNodeToSearch.value != null && pred.test(firstNodeToSearch.value) && firstNodeToSearch != curr) {
            return firstNodeToSearch;
        }

        return firstNodeToSearch.value == null ? new Node<>(null) : searchNodeAndReturn(pred, curr, firstNodeToSearch.next);
    }


    public boolean contains(Predicate<T> pred, Node<T> headNode) {

        if (headNode.value != null && pred.test(headNode.value)) {
            return true;
        }

        return headNode.value != null && contains(pred, headNode.next);
    }

    public void foreach(Consumer<Node<T>> cons, Node<T> firstNodeToIterate) {

        if (firstNodeToIterate.value != null) {
            cons.accept(firstNodeToIterate);
        }

        if (firstNodeToIterate.next.value != null) {
            foreach(cons, firstNodeToIterate.next);
        }
    }
}
