package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptQrTest {

    private Order order() {
        Order order = new Order();
        order.setId(12);
        order.setTotal(new BigDecimal("120000"));
        order.setOrderDate(LocalDateTime.of(2026, 10, 10, 14, 3));
        return order;
    }

    @Test
    public void payloadIsAReadableSummaryOfTheReceipt() {
        String payload = ReceiptQr.payload(order());
        String[] lines = payload.split("\n");
        assertEquals("Lunavera Coffee", lines[0]);
        assertEquals("Hóa đơn #12", lines[1]);
        assertTrue(lines[2].startsWith("Tổng: "));
        assertTrue(lines[2].contains("120"));
        assertEquals("14:03 10/10/2026", lines[3]);
    }

    @Test
    public void payloadIsNotAPaymentRequest() {
        String payload = ReceiptQr.payload(order());
        assertFalse(payload.contains("000201")); // EMVCo / VietQR start marker
    }

    @Test
    public void payloadWithoutADateOmitsTheTimeLine() {
        Order order = order();
        order.setOrderDate(null);
        assertEquals(3, ReceiptQr.payload(order).split("\n").length);
    }

    @Test
    public void payloadFitsInAQrCode() {
        QrCode qr = QrCode.encodeText(ReceiptQr.payload(order()));
        assertTrue(qr.getSize() <= 41);
    }
}
