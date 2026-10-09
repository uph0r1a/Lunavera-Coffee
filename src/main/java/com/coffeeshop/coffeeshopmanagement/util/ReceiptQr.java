package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;

import java.time.format.DateTimeFormatter;

/**
 * Text encoded in the receipt's QR code. It is a plain, readable summary of the receipt, so a phone
 * scan shows something meaningful. It is deliberately not a payment request: the receipt is for an
 * order that is already paid, and a payment QR on it could be paid a second time.
 */
public final class ReceiptQr {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private ReceiptQr() {
    }

    public static String payload(Order order) {
        StringBuilder text = new StringBuilder("Lunavera Coffee\n")
                .append("Hóa đơn #").append(order.getId()).append('\n')
                .append("Tổng: ").append(CurrencyUtil.format(order.getTotal()));
        if (order.getOrderDate() != null) {
            text.append('\n').append(order.getOrderDate().format(TIME));
        }
        return text.toString();
    }
}
