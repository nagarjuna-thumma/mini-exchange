package dev.nagarjuna.exchange.order;

public class OrderNotOpenException extends RuntimeException {
    public OrderNotOpenException(Order order) {
        super("Order " + order.getId() + " is already " + order.getStatus().name().toLowerCase());
    }
}
